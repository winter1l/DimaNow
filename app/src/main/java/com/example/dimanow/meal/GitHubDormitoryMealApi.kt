package com.example.dimanow.meal

import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class DormitoryMealImage(
    val bytes: ByteArray,
    val mimeType: String,
    val extension: String,
)

data class DormitoryMealSubmission(
    val submissionId: String,
    val uploadedAt: Instant,
)

data class DormitoryDeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

sealed interface DormitoryAuthorizationPoll {
    data class Authorized(val token: String) : DormitoryAuthorizationPoll
    data object Pending : DormitoryAuthorizationPoll
    data object SlowDown : DormitoryAuthorizationPoll
    data class Failed(val message: String) : DormitoryAuthorizationPoll
}

class GitHubDormitoryMealApi(
    private val githubApiRoot: String = "https://api.github.com",
    private val githubLoginRoot: String = "https://github.com",
    private val pagesRoot: String = "https://winter1l.github.io/DimaNow/data/v1",
    private val now: () -> Instant = Instant::now,
    private val randomId: () -> String = { UUID.randomUUID().toString() },
) : DormitoryMealSubmissionGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun beginAuthorization(clientId: String): DormitoryDeviceAuthorization = withContext(Dispatchers.IO) {
        require(clientId.matches(Regex("[A-Za-z0-9._-]{8,128}"))) { "GitHub 업로드 연결이 준비되지 않았습니다." }
        val root = postForm(
            "$githubLoginRoot/login/device/code",
            "client_id=$clientId",
        )
        DormitoryDeviceAuthorization(
            deviceCode = root.getValue("device_code").jsonPrimitive.content,
            userCode = root.getValue("user_code").jsonPrimitive.content,
            verificationUri = root.getValue("verification_uri").jsonPrimitive.content,
            expiresInSeconds = root.getValue("expires_in").jsonPrimitive.content.toInt(),
            intervalSeconds = root.getValue("interval").jsonPrimitive.content.toInt(),
        )
    }

    override suspend fun pollAuthorization(clientId: String, deviceCode: String): DormitoryAuthorizationPoll = withContext(Dispatchers.IO) {
        require(clientId.matches(Regex("[A-Za-z0-9._-]{8,128}")) && deviceCode.isNotBlank()) { "GitHub 업로드 연결 정보가 올바르지 않습니다." }
        val root = postForm(
            "$githubLoginRoot/login/oauth/access_token",
            "client_id=$clientId&device_code=$deviceCode&grant_type=urn:ietf:params:oauth:grant-type:device_code",
        )
        root["access_token"]?.jsonPrimitive?.contentOrNull?.let { return@withContext DormitoryAuthorizationPoll.Authorized(it) }
        when (root["error"]?.jsonPrimitive?.contentOrNull) {
            "authorization_pending" -> DormitoryAuthorizationPoll.Pending
            "slow_down" -> DormitoryAuthorizationPoll.SlowDown
            else -> DormitoryAuthorizationPoll.Failed(root["error_description"]?.jsonPrimitive?.contentOrNull ?: "GitHub 연결에 실패했습니다.")
        }
    }

    private fun postForm(url: String, bodyText: String): JsonObject {
        val body = bodyText.toByteArray()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            require(connection.responseCode in 200..299) { "GitHub 연결 응답 ${connection.responseCode}" }
            return json.parseToJsonElement(connection.inputStream.bufferedReader().readText()) as JsonObject
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun upload(token: String, image: DormitoryMealImage): DormitoryMealSubmission = withContext(Dispatchers.IO) {
        require(token.isNotBlank()) { "GitHub 연결이 필요합니다." }
        require(image.bytes.size in 1..MAX_IMAGE_BYTES) { "식단 이미지는 15MB 이하여야 합니다." }
        require(image.mimeType in MIME_EXTENSIONS.keys && MIME_EXTENSIONS.getValue(image.mimeType) == image.extension.lowercase()) {
            "지원하지 않는 식단 이미지 형식입니다."
        }
        val submissionId = randomId().also { require(it.matches(Regex("[A-Za-z0-9_-]{1,64}"))) }
        val path = "dorm-submissions/$submissionId.${image.extension.lowercase()}"
        val body = buildJsonObject {
            put("message", "data: submit dormitory meal $submissionId")
            put("content", Base64.getEncoder().encodeToString(image.bytes))
            put("branch", SUBMISSION_BRANCH)
        }.toString().toByteArray()
        val connection = URL("$githubApiRoot/repos/$OWNER/$REPOSITORY/contents/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PUT"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) throw DormitoryMealAuthorizationException()
            require(connection.responseCode == HttpURLConnection.HTTP_CREATED) { "GitHub 식단 업로드 응답 ${connection.responseCode}" }
            connection.inputStream.close()
            DormitoryMealSubmission(submissionId, now())
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun submissionStatus(submissionId: String): DormitoryMealSubmissionStatus? = withContext(Dispatchers.IO) {
        require(submissionId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "식단 제출 번호가 올바르지 않습니다." }
        val connection = URL("$pagesRoot/dorm-submissions/$submissionId.json?t=${now().toEpochMilli()}").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> null
                in 200..299 -> json.decodeFromString<DormitoryMealSubmissionStatus>(connection.inputStream.bufferedReader().readText())
                else -> error("식단 처리 상태 응답 ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val OWNER = "winter1l"
        const val REPOSITORY = "DimaNow"
        const val SUBMISSION_BRANCH = "dorm-submissions"
        const val MAX_IMAGE_BYTES = 15 * 1024 * 1024
        val MIME_EXTENSIONS = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp")
    }
}
