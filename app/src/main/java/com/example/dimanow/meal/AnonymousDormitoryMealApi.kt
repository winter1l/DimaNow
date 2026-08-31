package com.example.dimanow.meal

import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DormitoryMealImage(
    val bytes: ByteArray,
    val mimeType: String,
    val extension: String,
)

data class DormitoryMealSubmission(
    val submissionId: String,
    val uploadedAt: Instant,
)

class AnonymousDormitoryMealApi(
    private val uploadRoot: String,
    private val pagesRoot: String = "https://winter1l.github.io/DimaNow/data/v1",
    private val now: () -> Instant = Instant::now,
) : DormitoryMealSubmissionGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun upload(image: DormitoryMealImage): DormitoryMealSubmission = withContext(Dispatchers.IO) {
        require(uploadRoot.startsWith("https://") || uploadRoot.startsWith("http://127.0.0.1:")) {
            "기숙사 식단 업로드 서비스가 준비되지 않았습니다."
        }
        require(image.bytes.size in 1..MAX_IMAGE_BYTES) { "식단 이미지는 15MB 이하여야 합니다." }
        require(image.mimeType in MIME_EXTENSIONS.keys && MIME_EXTENSIONS.getValue(image.mimeType) == image.extension.lowercase()) {
            "지원하지 않는 식단 이미지 형식입니다."
        }
        val connection = URL("${uploadRoot.trimEnd('/')}/v1/dormitory-meals").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", image.mimeType)
            connection.setRequestProperty("X-Dima-Image-Extension", image.extension.lowercase())
            connection.setFixedLengthStreamingMode(image.bytes.size)
            connection.outputStream.use { it.write(image.bytes) }
            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(responseCode == HttpURLConnection.HTTP_ACCEPTED) {
                runCatching { json.parseToJsonElement(responseText).jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                    ?: "식단 사진 업로드 응답 $responseCode"
            }
            val response = json.parseToJsonElement(responseText).jsonObject
            val submissionId = response.getValue("submissionId").jsonPrimitive.content
            val uploadedAt = response.getValue("uploadedAt").jsonPrimitive.content
            require(submissionId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "식단 제출 번호가 올바르지 않습니다." }
            DormitoryMealSubmission(submissionId, Instant.parse(uploadedAt))
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
        const val MAX_IMAGE_BYTES = 15 * 1024 * 1024
        val MIME_EXTENSIONS = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp")
    }
}
