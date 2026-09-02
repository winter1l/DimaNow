package com.example.dimanow.shuttle

import com.example.dimanow.guidance.ShuttleReportAggregate
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ShuttleReportEventKey(
    val serviceDate: LocalDate,
    val scheduleRevision: Long,
    val runId: String,
    val stopCallId: String,
)

data class ShuttleReportState(
    val serviceDate: LocalDate? = null,
    val scheduleRevision: Long? = null,
    val reports: List<ShuttleReportAggregate> = emptyList(),
    val reportedByThisInstall: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface ShuttleReportActionResult {
    data object Success : ShuttleReportActionResult
    data class Failure(val message: String) : ShuttleReportActionResult
}

interface ShuttleReportSource {
    val state: StateFlow<ShuttleReportState>
    suspend fun refresh(serviceDate: LocalDate, scheduleRevision: Long): ShuttleReportActionResult
    suspend fun report(event: ShuttleReportEventKey): ShuttleReportActionResult
    suspend fun revoke(event: ShuttleReportEventKey): ShuttleReportActionResult
}

data class ShuttleReportHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null,
)

data class ShuttleReportHttpResponse(val status: Int, val body: String)

fun interface ShuttleReportTransport {
    suspend fun execute(request: ShuttleReportHttpRequest): ShuttleReportHttpResponse
}

class HttpShuttleReportSource(
    rootUrl: String,
    private val reporterTokenProvider: suspend () -> String,
    private val transport: ShuttleReportTransport = UrlConnectionShuttleReportTransport(),
) : ShuttleReportSource {
    private val endpoint = "${rootUrl.trimEnd('/')}/v1/shuttle-reports"
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow(ShuttleReportState())
    override val state: StateFlow<ShuttleReportState> = mutableState.asStateFlow()

    init {
        require(rootUrl.startsWith("https://") || rootUrl.startsWith("http://127.0.0.1:"))
    }

    constructor(
        rootUrl: String,
        reporterToken: String,
        transport: ShuttleReportTransport = UrlConnectionShuttleReportTransport(),
    ) : this(rootUrl, { reporterToken }, transport) {
        require(reporterToken.matches(Regex("[A-Za-z0-9_-]{20,128}")))
    }

    override suspend fun refresh(serviceDate: LocalDate, scheduleRevision: Long): ShuttleReportActionResult {
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)
        return runCatching {
            val response = transport.execute(
                ShuttleReportHttpRequest(
                    method = "GET",
                    url = "$endpoint?serviceDate=${encode(serviceDate.toString())}&scheduleRevision=$scheduleRevision",
                    headers = headers(),
                ),
            )
            require(response.status in 200..299) { response.messageOrDefault("신고 현황을 불러오지 못했습니다.") }
            val payload = json.parseToJsonElement(response.body).jsonObject
            require(payload.getValue("serviceDate").jsonPrimitive.content == serviceDate.toString())
            require(payload.getValue("scheduleRevision").jsonPrimitive.content.toLong() == scheduleRevision)
            val reportRows = payload.getValue("reports").jsonArray.map { it.jsonObject }
            val aggregates = reportRows.map { report ->
                val count = report.getValue("count").jsonPrimitive.int
                val sequence = report.getValue("stopSequence").jsonPrimitive.int
                require(count >= 0 && sequence >= 0)
                ShuttleReportAggregate(
                    report.getValue("runId").jsonPrimitive.content,
                    report.getValue("stopCallId").jsonPrimitive.content,
                    sequence,
                    count,
                )
            }
            mutableState.value = ShuttleReportState(
                serviceDate = serviceDate,
                scheduleRevision = scheduleRevision,
                reports = aggregates,
                reportedByThisInstall = reportRows.filter {
                    it["reportedByYou"]?.jsonPrimitive?.contentOrNull == "true"
                }.map { it.getValue("stopCallId").jsonPrimitive.content }.toSet(),
            )
            ShuttleReportActionResult.Success
        }.getOrElse { error ->
            val message = error.message ?: "신고 현황을 불러오지 못했습니다."
            mutableState.value = mutableState.value.copy(isLoading = false, error = message)
            ShuttleReportActionResult.Failure(message)
        }
    }

    override suspend fun report(event: ShuttleReportEventKey): ShuttleReportActionResult = mutate("POST", event)

    override suspend fun revoke(event: ShuttleReportEventKey): ShuttleReportActionResult = mutate("DELETE", event)

    private suspend fun mutate(method: String, event: ShuttleReportEventKey): ShuttleReportActionResult {
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)
        return runCatching {
            val response = transport.execute(
                ShuttleReportHttpRequest(
                    method = method,
                    url = endpoint,
                    headers = headers() + ("Content-Type" to "application/json"),
                    body = buildJsonObject {
                        put("serviceDate", event.serviceDate.toString())
                        put("scheduleRevision", event.scheduleRevision)
                        put("runId", event.runId)
                        put("stopCallId", event.stopCallId)
                    }.toString(),
                ),
            )
            require(response.status in 200..299 || (method == "DELETE" && response.status == 204)) {
                response.messageOrDefault(if (method == "POST") "신고하지 못했습니다." else "신고를 취소하지 못했습니다.")
            }
            refresh(event.serviceDate, event.scheduleRevision)
        }.getOrElse { error ->
            val message = error.message ?: if (method == "POST") "신고하지 못했습니다." else "신고를 취소하지 못했습니다."
            mutableState.value = mutableState.value.copy(isLoading = false, error = message)
            ShuttleReportActionResult.Failure(message)
        }
    }

    private suspend fun headers(): Map<String, String> {
        val reporterToken = reporterTokenProvider()
        require(reporterToken.matches(Regex("[A-Za-z0-9_-]{20,128}")))
        return mapOf(
            "Accept" to "application/json",
            "X-Dima-Reporter" to reporterToken,
        )
    }

    private fun ShuttleReportHttpResponse.messageOrDefault(default: String): String = runCatching {
        json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf(String::isNotBlank) ?: default

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

class UrlConnectionShuttleReportTransport : ShuttleReportTransport {
    override suspend fun execute(request: ShuttleReportHttpRequest): ShuttleReportHttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            request.headers.forEach(connection::setRequestProperty)
            request.body?.let { body ->
                val bytes = body.toByteArray()
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            ShuttleReportHttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }
}
