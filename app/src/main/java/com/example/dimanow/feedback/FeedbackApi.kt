package com.example.dimanow.feedback

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** What a 문의·오류 제보 is about (D-119); [wire] is the Worker's category. */
enum class FeedbackCategory(val wire: String, val label: String) {
    BUG("bug", "오류"),
    QUESTION("question", "문의"),
    IDEA("idea", "제안"),
}

sealed interface FeedbackResult {
    /** The report is GitHub issue [number] at [url]. */
    data class Posted(val number: Int, val url: String) : FeedbackResult

    /** This network sent one a moment ago, or today's reports are used up. */
    data object TooOften : FeedbackResult

    data object Failed : FeedbackResult
}

/** The limits the Worker checks, shown on the form as the student types. */
const val FEEDBACK_MIN_CHARS = 5
const val FEEDBACK_MAX_CHARS = 2000

/**
 * Sends a 문의·오류 제보 to the DIMA Now gateway (the same Worker as the dormitory photos), which posts it as a
 * public GitHub issue (D-119). Only the text, its category and, if the student leaves it on, the app version and
 * Android SDK level are sent.
 */
class FeedbackApi(private val root: String) {
    suspend fun send(
        category: FeedbackCategory,
        message: String,
        appVersion: String?,
        androidSdk: Int?,
        log: String? = null,
        screenshots: List<FeedbackScreenshot> = emptyList(),
    ): FeedbackResult =
        withContext(Dispatchers.IO) {
            if (!(root.startsWith("https://") || root.startsWith("http://127.0.0.1:"))) return@withContext FeedbackResult.Failed
            runCatching {
                val connection = URL("${root.trimEnd('/')}/v1/feedback").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 60_000
                    connection.doOutput = true
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Content-Type", "application/json")
                    val body = feedbackRequestJson(category, message, appVersion, androidSdk, log, screenshots).toByteArray()
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                    val code = connection.responseCode
                    val text = (if (code < 400) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    feedbackResult(code, text)
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(FeedbackResult.Failed)
        }
}

/** D-120: the log and screenshots go only with an 오류 report; the Worker refuses them on the others. */
internal fun feedbackRequestJson(
    category: FeedbackCategory,
    message: String,
    appVersion: String?,
    androidSdk: Int?,
    log: String? = null,
    screenshots: List<FeedbackScreenshot> = emptyList(),
): String = buildJsonObject {
    put("category", category.wire)
    put("message", message.trim())
    appVersion?.let { put("appVersion", it) }
    androidSdk?.let { put("androidSdk", it) }
    if (category == FeedbackCategory.BUG) {
        log?.takeIf { it.isNotBlank() }?.let { put("log", it) }
        if (screenshots.isNotEmpty()) {
            putJsonArray("images") {
                screenshots.take(FEEDBACK_MAX_SCREENSHOTS).forEach { shot ->
                    addJsonObject {
                        put("type", "image/jpeg")
                        put("data", java.util.Base64.getEncoder().encodeToString(shot.bytes))
                    }
                }
            }
        }
    }
}.toString()

internal fun feedbackResult(code: Int, body: String): FeedbackResult = when (code) {
    201 -> runCatching {
        val json = Json.parseToJsonElement(body) as JsonObject
        val number = json["number"]?.jsonPrimitive?.intOrNull
        val url = json["url"]?.jsonPrimitive?.content?.takeIf { it.startsWith("https://github.com/winter1l/DimaNow/issues/") }
        if (number != null && url != null) FeedbackResult.Posted(number, url) else FeedbackResult.Failed
    }.getOrDefault(FeedbackResult.Failed)
    429 -> FeedbackResult.TooOften
    else -> FeedbackResult.Failed
}
