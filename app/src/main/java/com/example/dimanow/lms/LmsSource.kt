package com.example.dimanow.lms

import android.webkit.CookieManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.Charset
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface LmsRefreshResult {
    data object Success : LmsRefreshResult
    data object Cached : LmsRefreshResult
    data object SessionExpired : LmsRefreshResult
    data object CourseCatalogRequired : LmsRefreshResult
    data class Failure(val message: String) : LmsRefreshResult
}

sealed interface LmsDetailLoadResult {
    data class Fresh(val detail: LmsItemDetail, val attachmentsChanged: Boolean) : LmsDetailLoadResult
    data class Cached(val detail: LmsItemDetail) : LmsDetailLoadResult
    data object OfficialCoursePage : LmsDetailLoadResult
    data object SessionExpired : LmsDetailLoadResult
    data class Failure(val message: String) : LmsDetailLoadResult
}

private class RenderedLmsSessionExpiredException : Exception()

private class OfficialCoursePageRequiredException : Exception()

private class RejectedLmsAttachmentException(
    val sessionExpired: Boolean,
    message: String,
) : IOException(message)

interface LmsSource {
    val snapshot: Flow<LmsSnapshot>
    suspend fun refresh(force: Boolean = false): LmsRefreshResult
    suspend fun loadDetail(item: LmsItem): LmsDetailLoadResult
    suspend fun downloadAttachment(
        attachment: LmsAttachment,
        destination: File,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ): LmsAttachmentDownloadResult
    suspend fun markItemOpened(item: LmsItem) = Unit
    suspend fun clearPrivateData()
    suspend fun storeRenderedCourses(courses: List<LmsCourse>)
}

data class LmsHttpResponse(
    val finalUrl: String,
    val statusCode: Int,
    val contentType: String?,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
)

interface LmsHttpTransport {
    suspend fun get(url: String, maxBytes: Long = 8L * 1024 * 1024): LmsHttpResponse
    suspend fun postForm(url: String, fields: Map<String, String>, maxBytes: Long = 2L * 1024 * 1024): LmsHttpResponse
    suspend fun postAjax(url: String, fields: Map<String, String>, maxBytes: Long = 2L * 1024 * 1024): LmsHttpResponse =
        postForm(url, fields, maxBytes)
    suspend fun downloadAttachment(
        request: LmsAttachmentRequest,
        destination: File,
        suggestedFileName: String,
        maxBytes: Long,
        onProgress: (Long, Long?) -> Unit,
    ): LmsAttachmentDownloadResult
}

class UrlConnectionLmsTransport(
    private val cookieProvider: (String) -> String? = { CookieManager.getInstance().getCookie(it) },
    private val cookieSink: (String, String) -> Unit = { url, cookie -> CookieManager.getInstance().setCookie(url, cookie) },
    private val userAgent: String = DEFAULT_BROWSER_USER_AGENT,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : LmsHttpTransport {
    override suspend fun get(url: String, maxBytes: Long): LmsHttpResponse = withContext(Dispatchers.IO) {
        execute(url, maxBytes) { connection, total ->
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var count = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                    check(count <= maxBytes) { "LMS response is too large" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            bytes to total
        }.first
    }

    override suspend fun postForm(url: String, fields: Map<String, String>, maxBytes: Long): LmsHttpResponse =
        executePost(url, fields, maxBytes, ajax = false)

    override suspend fun postAjax(url: String, fields: Map<String, String>, maxBytes: Long): LmsHttpResponse =
        executePost(url, fields, maxBytes, ajax = true)

    private suspend fun executePost(
        url: String,
        fields: Map<String, String>,
        maxBytes: Long,
        ajax: Boolean,
    ): LmsHttpResponse = withContext(Dispatchers.IO) {
        val encodedBody = fields.entries.joinToString("&") { (key, value) ->
            "${java.net.URLEncoder.encode(key, "UTF-8")}" +
                "=${java.net.URLEncoder.encode(value, "UTF-8")}"
        }.toByteArray()
        var current = url
        var sendPostBody = true
        var redirectCookies: String? = null
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val uri = validateUrl(current)
            val connection = connectionFactory(URL(uri.toString()))
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty(
                "Accept",
                if (ajax) "*/*" else "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
            connection.setRequestProperty("User-Agent", userAgent)
            mergeCookieHeaders(cookieProvider(current), redirectCookies)
                ?.let { connection.setRequestProperty("Cookie", it) }
            if (sendPostBody) {
                connection.setRequestProperty("Origin", "${uri.scheme}://${uri.authority}")
                connection.setRequestProperty("Referer", LMS_DASHBOARD_REFERER)
                connection.setRequestProperty("Sec-Fetch-Site", "same-origin")
                connection.setRequestProperty("Sec-Fetch-Mode", if (ajax) "cors" else "navigate")
                connection.setRequestProperty("Sec-Fetch-Dest", if (ajax) "empty" else "document")
                if (ajax) {
                    connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                } else {
                    connection.setRequestProperty("Sec-Fetch-User", "?1")
                    connection.setRequestProperty("Upgrade-Insecure-Requests", "1")
                }
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    if (ajax) "application/x-www-form-urlencoded; charset=UTF-8" else "application/x-www-form-urlencoded",
                )
                connection.setFixedLengthStreamingMode(encodedBody.size)
                connection.outputStream.use { it.write(encodedBody) }
            }
            val status = connection.responseCode
            storeResponseCookies(current, connection)
            redirectCookies = mergeCookieHeaders(
                redirectCookies,
                connection.headerFields.entries
                    .filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
                    .flatMap { it.value.orEmpty() }
                    .map { it.substringBefore(';') }
                    .joinToString("; "),
            )
            if (status in 300..399) {
                val location = connection.getHeaderField("Location") ?: error("LMS redirect has no location")
                check(redirectCount < MAX_REDIRECTS) { "Too many LMS redirects" }
                current = uri.resolve(location).toString()
                if (status in 301..303) sendPostBody = false
                connection.disconnect()
                return@repeat
            }
            check(status in 200..299) { "LMS HTTP $status ${uri.path}" }
            val responseBytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var count = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                    check(count <= maxBytes) { "LMS response is too large" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            return@withContext LmsHttpResponse(current, status, connection.contentType, safeHeaders(connection), responseBytes)
                .also { connection.disconnect() }
        }
        error("Too many LMS redirects")
    }

    override suspend fun downloadAttachment(
        request: LmsAttachmentRequest,
        destination: File,
        suggestedFileName: String,
        maxBytes: Long,
        onProgress: (Long, Long?) -> Unit,
    ): LmsAttachmentDownloadResult {
        if (destination.exists()) {
            return LmsAttachmentDownloadResult.Failure("첨부파일 캐시가 이미 존재합니다")
        }
        return try {
            require(!request.refererUrl.isNullOrBlank()) { "첨부파일 Referer가 없습니다" }
            requireOfficialAttachmentUrl(request.url)
            requireOfficialAttachmentUrl(requireNotNull(request.refererUrl))
            val requestUrl = if (request.method == LmsHttpMethod.GET) {
                appendQueryFields(request.url, request.fields)
            } else {
                request.url
            }
            val response = downloadToFile(
                url = requestUrl,
                destination = destination,
                maxBytes = maxBytes,
                onProgress = onProgress,
                refererUrl = request.refererUrl,
                method = request.method,
                fields = request.fields,
            )
            val bytesWritten = destination.length()
            val declaredLength = response.headerValue("Content-Length")?.toLongOrNull()
            when {
                bytesWritten == 0L -> {
                    destination.delete()
                    LmsAttachmentDownloadResult.Failure("빈 첨부파일 응답입니다")
                }
                declaredLength != null && declaredLength != bytesWritten -> {
                    destination.delete()
                    LmsAttachmentDownloadResult.Failure("첨부파일 길이가 응답과 일치하지 않습니다")
                }
                else -> LmsAttachmentDownloadResult.Success(
                    bytesWritten = bytesWritten,
                    fileName = LmsAttachmentNaming.fromContentDisposition(
                        response.headerValue("Content-Disposition"),
                        suggestedFileName,
                    ),
                    contentType = response.contentType?.substringBefore(';')?.trim(),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (error is RejectedLmsAttachmentException && error.sessionExpired) {
                LmsAttachmentDownloadResult.SessionExpired
            } else {
                LmsAttachmentDownloadResult.Failure(error.message ?: "첨부파일을 저장하지 못했습니다")
            }
        }
    }

    private suspend fun downloadToFile(
        url: String,
        destination: File,
        maxBytes: Long,
        onProgress: (Long, Long?) -> Unit,
        refererUrl: String?,
        method: LmsHttpMethod = LmsHttpMethod.GET,
        fields: Map<String, String> = emptyMap(),
    ): LmsHttpResponse = withContext(Dispatchers.IO) {
        val part = File(destination.parentFile, destination.name + ".part")
        try {
            val response = execute(
                initialUrl = url,
                maxBytes = maxBytes,
                refererUrl = refererUrl,
                requireAttachmentHost = true,
                requestMethod = method,
                formFields = fields,
            ) { connection, total ->
                FileOutputStream(part).use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var count = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            count += read
                            check(count <= maxBytes) { "LMS attachment is too large" }
                            output.write(buffer, 0, read)
                            onProgress(count, total)
                        }
                        check(total == null || count == total) {
                            "첨부파일 길이가 응답과 일치하지 않습니다"
                        }
                    }
                }
                ByteArray(0) to total
            }.first
            val prefix = part.readPrefix(HTML_SNIFF_BYTES)
            val prefixText = normalizeMarkupPrefix(prefix)
            val scriptRedirectsToLogin = isLoginRedirectScript(prefixText)
            val declaredMarkup = response.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase() in setOf("text/html", "application/xhtml+xml")
            val isHtml = declaredMarkup ||
                HTML_DOCUMENT_START_REGEX.containsMatchIn(prefixText) ||
                scriptRedirectsToLogin
            if (isHtml) {
                val sessionExpired = isOfficialLmsCredentialPage(response.finalUrl) ||
                    scriptRedirectsToLogin ||
                    containsLoginForm(prefixText)
                throw RejectedLmsAttachmentException(
                    sessionExpired = sessionExpired,
                    message = if (sessionExpired) "로그인 세션이 만료되었습니다" else "첨부파일 대신 HTML 오류가 반환되었습니다",
                )
            }
            check(part.renameTo(destination)) { "첨부파일을 확정하지 못했습니다" }
            response
        } catch (error: Throwable) {
            part.delete()
            throw error
        }
    }

    private fun <T> execute(
        initialUrl: String,
        maxBytes: Long,
        refererUrl: String? = null,
        requireAttachmentHost: Boolean = false,
        requestMethod: LmsHttpMethod = LmsHttpMethod.GET,
        formFields: Map<String, String> = emptyMap(),
        consume: (HttpURLConnection, Long?) -> Pair<T, Long?>,
    ): Pair<LmsHttpResponse, T> {
        var current = initialUrl
        var sendPostBody = requestMethod == LmsHttpMethod.POST
        val encodedForm = encodeFields(formFields).toByteArray(Charsets.UTF_8)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val uri = if (requireAttachmentHost) requireOfficialAttachmentUrl(current) else validateUrl(current)
            val connection = connectionFactory(URL(uri.toString()))
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/octet-stream;q=0.9,*/*;q=0.8")
            connection.setRequestProperty("User-Agent", userAgent)
            refererUrl?.let { connection.setRequestProperty("Referer", it) }
            cookieProvider(current)?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
            if (sendPostBody) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Origin", "${uri.scheme}://${uri.authority}")
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.setFixedLengthStreamingMode(encodedForm.size)
                connection.outputStream.use { it.write(encodedForm) }
            }
            val status = connection.responseCode
            if (status in 300..399) {
                storeResponseCookies(current, connection)
                val location = connection.getHeaderField("Location") ?: error("LMS redirect has no location")
                check(redirectCount < MAX_REDIRECTS) { "Too many LMS redirects" }
                val redirectUrl = uri.resolve(location).toString()
                if (
                    requireAttachmentHost &&
                    (isExactOfficialLmsSessionConflictUrl(redirectUrl) || isOfficialLmsCredentialPage(redirectUrl))
                ) {
                    connection.disconnect()
                    throw RejectedLmsAttachmentException(
                        sessionExpired = true,
                        message = "로그인 세션이 만료되었습니다",
                    )
                }
                current = redirectUrl
                if (status in 301..303) sendPostBody = false
                connection.disconnect()
                return@repeat
            }
            check(status in 200..299) { "LMS HTTP $status" }
            val length = connection.contentLengthLong.takeIf { it >= 0 }
            check(length == null || length <= maxBytes) { "LMS response is too large" }
            val (bodyValue, _) = consume(connection, length)
            storeResponseCookies(current, connection)
            val response = LmsHttpResponse(
                finalUrl = current,
                statusCode = status,
                contentType = connection.contentType,
                headers = safeHeaders(connection),
                body = bodyValue as? ByteArray ?: ByteArray(0),
            )
            connection.disconnect()
            return response to bodyValue
        }
        error("Too many LMS redirects")
    }

    private fun validateUrl(value: String): URI = LmsUrlPolicy.requireAllowed(value)

    private fun requireOfficialAttachmentUrl(value: String): URI = URI.create(value).also { uri ->
        require(
            uri.scheme == "https" &&
                uri.host.equals(LMS_HOST, ignoreCase = true) &&
                uri.userInfo == null &&
                uri.port in setOf(-1, 443),
        ) { "허용되지 않은 LMS 첨부파일 주소입니다" }
    }

    private fun appendQueryFields(url: String, fields: Map<String, String>): String {
        if (fields.isEmpty()) return url
        val fragmentIndex = url.indexOf('#')
        val base = if (fragmentIndex >= 0) url.substring(0, fragmentIndex) else url
        val fragment = if (fragmentIndex >= 0) url.substring(fragmentIndex) else ""
        val separator = if (URI.create(base).rawQuery.isNullOrEmpty()) "?" else "&"
        return "$base$separator${encodeFields(fields)}$fragment"
    }

    private fun encodeFields(fields: Map<String, String>): String = fields.entries.joinToString("&") { (key, value) ->
            "${java.net.URLEncoder.encode(key, Charsets.UTF_8.name())}=" +
                java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun LmsHttpResponse.headerValue(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()

    private fun File.readPrefix(limit: Int): ByteArray = inputStream().use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(minOf(DEFAULT_BUFFER_SIZE, limit))
        while (output.size() < limit) {
            val read = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }

    private fun normalizeMarkupPrefix(bytes: ByteArray): String {
        var text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
        while (true) {
            text = when {
                text.startsWith("<?xml", ignoreCase = true) -> {
                    val end = text.indexOf("?>")
                    if (end < 0) return text.lowercase()
                    text.substring(end + 2).trimStart()
                }
                text.startsWith("<!--") -> {
                    val end = text.indexOf("-->")
                    if (end < 0) return text.lowercase()
                    text.substring(end + 3).trimStart()
                }
                else -> return text.lowercase()
            }
        }
    }

    private fun isLoginRedirectScript(prefixText: String): Boolean {
        if (!prefixText.startsWith("<script")) return false
        val navigates = SCRIPT_NAVIGATION_REGEX.containsMatchIn(prefixText)
        val targetsLogin = prefixText.contains("/login/dologinpage.dunet") ||
            prefixText.contains("portal.dima.ac.kr")
        return navigates && targetsLogin
    }

    private fun containsLoginForm(prefixText: String): Boolean {
        if (!prefixText.contains("<form")) return false
        if (prefixText.contains("/login/dologin")) return true
        return LOGIN_ID_INPUT_REGEX.containsMatchIn(prefixText) &&
            LOGIN_PASSWORD_INPUT_REGEX.containsMatchIn(prefixText)
    }

    private fun storeResponseCookies(url: String, connection: HttpURLConnection) {
        connection.headerFields.entries
            .filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
            .flatMap { it.value.orEmpty() }
            .forEach { cookieSink(url, it) }
    }

    private fun mergeCookieHeaders(vararg headers: String?): String? {
        val cookies = linkedMapOf<String, String>()
        headers.filterNotNull().forEach { header ->
            header.split(';').forEach { segment ->
                val cookie = segment.trim()
                val separator = cookie.indexOf('=')
                if (separator > 0) cookies[cookie.substring(0, separator)] = cookie
            }
        }
        return cookies.values.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun safeHeaders(connection: HttpURLConnection): Map<String, List<String>> = buildMap {
        connection.headerFields.forEach { (key, values) ->
            if (key != null && values != null) put(key, values)
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val HTML_SNIFF_BYTES = 64 * 1024
        const val LMS_HOST = "lms.dima.ac.kr"
        const val LMS_DASHBOARD_REFERER =
            "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?mnid=201008840728"
        const val DEFAULT_BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/140.0 Safari/537.36"
        val SCRIPT_NAVIGATION_REGEX = Regex(
            """(?:window\.|top\.)?location(?:\.href\s*=|\s*=|\.(?:replace|assign)\s*\()""",
        )
        val LOGIN_ID_INPUT_REGEX = Regex("""<input\b[^>]*(?:id|name)\s*=\s*["']id["']""")
        val LOGIN_PASSWORD_INPUT_REGEX = Regex("""<input\b[^>]*(?:id|name)\s*=\s*["']pass["']""")
        val HTML_DOCUMENT_START_REGEX = Regex(
            """^<(?:!doctype\s+html\b|html\b|head\b|body\b|meta\b|title\b|link\b|style\b|script\b|form\b)""",
        )
    }
}

class RoomLmsSource(
    private val database: LmsCacheDatabase,
    private val sessionController: LmsSessionController,
    private val transport: LmsHttpTransport = UrlConnectionLmsTransport(),
    private val parser: LmsHtmlParser = LmsHtmlParser(),
    private val clock: Clock = Clock.systemUTC(),
    private val renderedPageLoader: LmsRenderedPageLoader? = null,
) : LmsSource {
    private val dao = database.dao()
    private val refreshMutex = Mutex()
    override val snapshot: Flow<LmsSnapshot> = combine(
        dao.observeCourses(), dao.observeItems(), dao.observeSync(),
    ) { courses, items, sync ->
        LmsSnapshot(
            courses = courses.map { LmsCourse(it.id, it.name, it.professor, it.classNo) },
            items = items.map(::toModel),
            syncState = sync?.status?.let { runCatching { LmsSyncState.valueOf(it) }.getOrNull() } ?: LmsSyncState.IDLE,
            lastSuccessAt = sync?.lastSuccessAtMillis?.let(Instant::ofEpochMilli),
            errorMessage = sync?.errorMessage,
        )
    }

    override suspend fun refresh(force: Boolean): LmsRefreshResult = refreshMutex.withLock {
        val prior = dao.getSync()
        val fresh = prior?.lastSuccessAtMillis?.let { Duration.between(Instant.ofEpochMilli(it), clock.instant()) < CACHE_TTL } == true
        if (!force && fresh) return@withLock LmsRefreshResult.Cached
        dao.setSync(LmsSyncEntity(status = LmsSyncState.SYNCING.name, lastSuccessAtMillis = prior?.lastSuccessAtMillis, errorMessage = null))
        try {
            val previousCourses = dao.getAllCourses().map { LmsCourse(it.id, it.name, it.professor, it.classNo) }
            var response = transport.get(DASHBOARD_URL)
            var html = response.htmlText()
            if (parser.isLoginPage(html, response.finalUrl)) {
                sessionController.transition(LmsSessionState.EXPIRED)
                dao.setSync(LmsSyncEntity(status = LmsSyncState.ERROR.name, lastSuccessAtMillis = prior?.lastSuccessAtMillis, errorMessage = "로그인이 필요합니다"))
                return@withLock LmsRefreshResult.SessionExpired
            }
            var parsed = parser.parseDashboard(html, LMS_ORIGIN)
            if (parsed.courses.isEmpty() && previousCourses.isNotEmpty()) {
                parsed = parsed.copy(courses = previousCourses)
            } else if (parsed.courses.isEmpty()) {
                parser.parseSelectedTermSelection(html)?.let { term ->
                    transport.postForm(
                        COURSE_YEAR_URL,
                        mapOf(
                            "term_year" to term.year,
                            "term_cd" to term.code,
                            "term_nm" to term.name,
                        ),
                    )
                    val firstItems = parsed.items
                    response = transport.postForm(DASHBOARD_RELOAD_URL, emptyMap())
                    html = response.htmlText()
                    check(!parser.isLoginPage(html, response.finalUrl)) { "LMS session expired" }
                    val reloaded = parser.parseDashboard(html, LMS_ORIGIN)
                    parsed = ParsedLmsDashboard(
                        courses = reloaded.courses,
                        items = (firstItems + reloaded.items).distinctBy { it.kind to it.id },
                        statusPageRequests = (parsed.statusPageRequests + reloaded.statusPageRequests)
                            .distinctBy { Triple(it.completionState, it.url, it.formFields) },
                    )
                }
            }
            if (parsed.courses.isEmpty()) {
                dao.setSync(
                    LmsSyncEntity(
                        status = LmsSyncState.ERROR.name,
                        lastSuccessAtMillis = prior?.lastSuccessAtMillis,
                        errorMessage = "수업 목록 확인이 필요합니다",
                    ),
                )
                return@withLock LmsRefreshResult.CourseCatalogRequired
            }
            val previousItems = dao.getAllItems().map(::toModel)
            val statusByKey = linkedMapOf<String, LmsCompletionState>()
            val successfulStatusStates = mutableSetOf<LmsCompletionState>()
            parsed.statusPageRequests.forEach { request ->
                runCatching {
                    val statusResponse = if (request.formFields.isEmpty()) {
                        transport.get(request.url)
                    } else {
                        transport.postForm(request.url, request.formFields)
                    }
                    val statusHtml = statusResponse.htmlText()
                    check(!parser.isLoginPage(statusHtml, statusResponse.finalUrl)) { "LMS session expired" }
                    parser.parseDashboard(statusHtml, LMS_ORIGIN).items.forEach { statusItem ->
                        statusByKey[itemKey(statusItem)] = request.completionState
                    }
                    successfulStatusStates += request.completionState
                }
            }
            val hasCompleteStatusSet = successfulStatusStates.containsAll(
                setOf(LmsCompletionState.COMPLETE, LmsCompletionState.INCOMPLETE),
            )
            val coursesById = parsed.courses.associateBy { it.id }
            val previousItemsByKey = previousItems.associateBy(::itemKey)
            dao.replaceDashboard(
                parsed.courses.map { LmsCourseEntity(it.id, it.name, it.professor, it.classNo) },
                parsed.items
                    .distinctBy { Triple(it.courseId, it.kind, it.id) }
                    .map { item ->
                        val previousItem = previousItemsByKey[itemKey(item)]
                        item.copy(
                            courseName = coursesById[item.courseId]?.name ?: item.courseName,
                            isRead = previousItem?.isRead ?: item.isRead,
                            completionState = statusByKey[itemKey(item)]
                                ?: if (hasCompleteStatusSet) {
                                    LmsCompletionState.NOT_TRACKED
                                } else {
                                    previousItem?.completionState ?: LmsCompletionState.UNKNOWN
                                },
                            changeState = when {
                                previousItem == null && prior?.lastSuccessAtMillis != null -> LmsChangeState.NEW
                                previousItem == null -> LmsChangeState.NONE
                                previousItem.title != item.title ||
                                    previousItem.dueAt != item.dueAt ||
                                    previousItem.detailUrl != item.detailUrl -> LmsChangeState.UPDATED
                                else -> previousItem.changeState
                            },
                        )
                    }
                    .map(::toEntity),
                clock.millis(),
            )
            sessionController.transition(LmsSessionState.ACTIVE)
            LmsRefreshResult.Success
        } catch (error: Throwable) {
            val message = error.message ?: "LMS를 불러오지 못했습니다"
            dao.setSync(LmsSyncEntity(status = LmsSyncState.ERROR.name, lastSuccessAtMillis = prior?.lastSuccessAtMillis, errorMessage = message))
            LmsRefreshResult.Failure(message)
        }
    }

    override suspend fun loadDetail(item: LmsItem): LmsDetailLoadResult = refreshMutex.withLock {
        val key = itemKey(item)
        if (!canRenderLmsItemNatively(item.kind)) {
            return@withLock LmsDetailLoadResult.OfficialCoursePage
        }
        val cachedEntity = dao.getDetail(key)
        val cachedAttachments = dao.getAttachments(key)
        val openedItem = item.copy(isRead = true, changeState = LmsChangeState.NONE)
        val cachedDetail = cachedEntity?.let { cached ->
            LmsItemDetail(
                item = openedItem,
                sanitizedHtml = cached.sanitizedHtml,
                attachments = cachedAttachments.map(::toAttachmentModel),
                metadata = LmsDetailMetadata(
                    author = cached.author,
                    registeredAt = cached.registeredAtMillis?.let(Instant::ofEpochMilli),
                    submissionStartsAt = cached.submissionStartsAtMillis?.let(Instant::ofEpochMilli),
                    submissionEndsAt = cached.submissionEndsAtMillis?.let(Instant::ofEpochMilli),
                    maxScore = cached.maxScore,
                ),
            )
        }
        try {
            val course = dao.getAllCourses().firstOrNull { it.id == item.courseId }
            val courseModel = course?.let { LmsCourse(it.id, it.name, it.professor, it.classNo) }
            suspend fun loadFromRenderedSession(): LmsItemDetail {
                val loader = renderedPageLoader ?: throw RenderedLmsSessionExpiredException()
                val renderedCourse = courseModel ?: throw RenderedLmsSessionExpiredException()
                return when (val rendered = loader.load(openedItem, renderedCourse)) {
                    is LmsRenderedPageResult.Success ->
                        parser.parseDetail(openedItem, rendered.html, rendered.finalUrl)
                    LmsRenderedPageResult.OfficialCoursePage -> throw OfficialCoursePageRequiredException()
                    LmsRenderedPageResult.SessionExpired -> throw RenderedLmsSessionExpiredException()
                    is LmsRenderedPageResult.Failure -> throw InvalidLmsDetailException(rendered.message)
                }
            }
            if (course != null && !course.id.startsWith("local-")) {
                runCatching {
                    transport.postAjax(
                        CLASS_SESSION_URL,
                        mapOf("course_id" to course.id, "class_no" to course.classNo),
                    )
                }
            }
            var response = requestOfficialDetail(item.detailUrl)
            var html = response.htmlText()
            var detail: LmsItemDetail? = null
            if (parser.isLoginPage(html, response.finalUrl)) {
                detail = loadFromRenderedSession()
            }
            if (detail == null) {
                parser.resolveLinkedDetailUrl(item, html, response.finalUrl)?.let { nestedUrl ->
                    response = transport.get(nestedUrl)
                    html = response.htmlText()
                    if (parser.isLoginPage(html, response.finalUrl)) {
                        detail = loadFromRenderedSession()
                    }
                }
                if (detail == null) {
                    detail = try {
                        parser.parseDetail(openedItem, html, response.finalUrl)
                    } catch (invalid: InvalidLmsDetailException) {
                        if (renderedPageLoader == null || courseModel == null) throw invalid
                        loadFromRenderedSession()
                    }
                }
            }
            val loadedDetail = requireNotNull(detail)
            val oldSignature = cachedAttachments.map { attachmentSignature(it) }.sorted()
            val newSignature = loadedDetail.attachments.map { attachmentSignature(it) }.sorted()
            val attachmentsChanged = cachedEntity != null && oldSignature != newSignature
            dao.replaceDetailAndOpen(
                LmsDetailEntity(
                    itemKey = key,
                    sanitizedHtml = loadedDetail.sanitizedHtml,
                    fetchedAtMillis = clock.millis(),
                    author = loadedDetail.metadata.author,
                    registeredAtMillis = loadedDetail.metadata.registeredAt?.toEpochMilli(),
                    submissionStartsAtMillis = loadedDetail.metadata.submissionStartsAt?.toEpochMilli(),
                    submissionEndsAtMillis = loadedDetail.metadata.submissionEndsAt?.toEpochMilli(),
                    maxScore = loadedDetail.metadata.maxScore,
                ),
                loadedDetail.attachments.map { attachment ->
                    LmsAttachmentEntity(
                        key = "$key:${attachment.id}",
                        itemKey = key,
                        sourceId = attachment.id,
                        fileName = attachment.fileName,
                        downloadUrl = attachment.request?.url ?: attachment.downloadUrl,
                        sizeBytes = attachment.sizeBytes,
                        requestMethod = attachment.request?.method?.name,
                        requestFieldsJson = attachment.request?.fields?.let(::encodeRequestFields),
                        refererUrl = attachment.request?.refererUrl,
                    )
                },
            )
            LmsDetailLoadResult.Fresh(loadedDetail, attachmentsChanged)
        } catch (_: OfficialCoursePageRequiredException) {
            LmsDetailLoadResult.OfficialCoursePage
        } catch (_: RenderedLmsSessionExpiredException) {
            sessionController.transition(LmsSessionState.EXPIRED)
            LmsDetailLoadResult.SessionExpired
        } catch (error: InvalidLmsDetailException) {
            LmsDetailLoadResult.Failure(error.message ?: "게시글 본문을 찾지 못했습니다")
        } catch (error: Throwable) {
            if (cachedDetail != null) {
                dao.markItemOpened(key)
                LmsDetailLoadResult.Cached(cachedDetail)
            } else {
                LmsDetailLoadResult.Failure(error.message ?: "글을 불러오지 못했습니다")
            }
        }
    }

    override suspend fun downloadAttachment(
        attachment: LmsAttachment,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
    ): LmsAttachmentDownloadResult = try {
        val request = attachment.request ?: LmsAttachmentRequest(
            method = LmsHttpMethod.GET,
            url = attachment.downloadUrl,
            refererUrl = DASHBOARD_URL,
        )
        transport.downloadAttachment(
            request = request,
            destination = destination,
            suggestedFileName = attachment.fileName,
            maxBytes = MAX_ATTACHMENT_BYTES,
            onProgress = onProgress,
        )
    } catch (cancelled: CancellationException) {
        destination.delete()
        throw cancelled
    } catch (error: Throwable) {
        LmsAttachmentDownloadResult.Failure(error.message ?: "첨부파일을 저장하지 못했습니다")
    }

    override suspend fun markItemOpened(item: LmsItem) {
        refreshMutex.withLock { dao.markItemOpened(itemKey(item)) }
    }

    override suspend fun clearPrivateData() {
        refreshMutex.withLock { dao.clearPrivateData() }
    }

    override suspend fun storeRenderedCourses(courses: List<LmsCourse>) {
        if (courses.isEmpty()) return
        refreshMutex.withLock {
            dao.replaceCourses(courses.map { LmsCourseEntity(it.id, it.name, it.professor, it.classNo) })
        }
    }

    private fun toEntity(item: LmsItem) = LmsItemEntity(
        key = itemKey(item), sourceId = item.id, courseId = item.courseId, courseName = item.courseName,
        kind = item.kind.name, title = item.title, registeredAtMillis = item.registeredAt?.toEpochMilli(),
        dueAtMillis = item.dueAt?.toEpochMilli(), detailUrl = item.detailUrl, isRead = item.isRead,
        completionState = item.completionState.name, changeState = item.changeState.name,
    )

    private suspend fun requestOfficialDetail(url: String): LmsHttpResponse {
        val uri = runCatching { URI.create(url) }.getOrNull() ?: return transport.get(url)
        val fields = uri.rawQuery.orEmpty()
            .split('&')
            .mapNotNull { pair ->
                val name = pair.substringBefore('=', missingDelimiterValue = "")
                if (name.isBlank()) return@mapNotNull null
                URLDecoder.decode(name, Charsets.UTF_8.name()) to
                    URLDecoder.decode(pair.substringAfter('=', missingDelimiterValue = ""), Charsets.UTF_8.name())
            }
            .toMap(linkedMapOf())
        val isOfficialForm = uri.host.equals("lms.dima.ac.kr", ignoreCase = true) &&
            uri.path.orEmpty().startsWith("/lms/class/") &&
            fields.keys.containsAll(setOf("mnid", "course_id", "class_no"))
        if (!isOfficialForm) return transport.get(url)
        val actionUrl = URI(uri.scheme, uri.authority, uri.path, null, null).toString()
        return transport.postForm(actionUrl, fields)
    }

    private fun toModel(item: LmsItemEntity) = LmsItem(
        id = item.sourceId, courseId = item.courseId, courseName = item.courseName,
        kind = runCatching { LmsItemKind.valueOf(item.kind) }.getOrDefault(LmsItemKind.OTHER), title = item.title,
        registeredAt = item.registeredAtMillis?.let(Instant::ofEpochMilli), dueAt = item.dueAtMillis?.let(Instant::ofEpochMilli),
        detailUrl = item.detailUrl, isRead = item.isRead,
        completionState = runCatching { LmsCompletionState.valueOf(item.completionState) }
            .getOrDefault(LmsCompletionState.UNKNOWN),
        changeState = runCatching { LmsChangeState.valueOf(item.changeState) }
            .getOrDefault(LmsChangeState.NONE),
    )

    private fun toAttachmentModel(entity: LmsAttachmentEntity): LmsAttachment {
        val method = entity.requestMethod?.let { stored ->
            runCatching { LmsHttpMethod.valueOf(stored) }.getOrNull()
        }
        val request = method?.let {
            LmsAttachmentRequest(
                method = it,
                url = entity.downloadUrl,
                fields = decodeRequestFields(entity.requestFieldsJson),
                refererUrl = entity.refererUrl,
            )
        }
        return LmsAttachment(
            id = entity.sourceId,
            fileName = entity.fileName,
            downloadUrl = entity.downloadUrl,
            sizeBytes = entity.sizeBytes,
            request = request,
        )
    }

    private fun attachmentSignature(entity: LmsAttachmentEntity): String = listOf(
        entity.sourceId,
        entity.fileName,
        entity.sizeBytes?.toString().orEmpty(),
        entity.requestMethod.orEmpty(),
        entity.downloadUrl,
        entity.requestFieldsJson.orEmpty(),
        entity.refererUrl.orEmpty(),
    ).joinToString("\u0000")

    private fun attachmentSignature(attachment: LmsAttachment): String = listOf(
        attachment.id,
        attachment.fileName,
        attachment.sizeBytes?.toString().orEmpty(),
        attachment.request?.method?.name.orEmpty(),
        (attachment.request?.url ?: attachment.downloadUrl),
        attachment.request?.fields?.let(::encodeRequestFields).orEmpty(),
        attachment.request?.refererUrl.orEmpty(),
    ).joinToString("\u0000")

    private fun encodeRequestFields(fields: Map<String, String>): String = JSONObject().apply {
        fields.toSortedMap().forEach { (name, value) -> put(name, value) }
    }.toString()

    private fun decodeRequestFields(value: String?): Map<String, String> {
        if (value.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(value)
            buildMap {
                json.keys().forEach { name -> put(name, json.optString(name)) }
            }
        }.getOrDefault(emptyMap())
    }

    private fun itemKey(item: LmsItem) = "${item.kind}:${item.courseId}:${item.id}"

    private fun LmsHttpResponse.htmlText(): String {
        val declared = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() }
        val charset = declared?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return body.toString(charset)
    }

    private companion object {
        const val LMS_ORIGIN = "https://lms.dima.ac.kr"
        const val DASHBOARD_URL = "$LMS_ORIGIN/lms/myLecture/doListView.dunet?to_do_type=all"
        const val DASHBOARD_RELOAD_URL = "$LMS_ORIGIN/lms/myLecture/doListView.dunet?to_do_type=all"
        const val CLASS_SESSION_URL = "$LMS_ORIGIN/lms/class/classroom/doSetSessionClassRoom.dunet"
        const val COURSE_YEAR_URL = "$LMS_ORIGIN/main/doChangeCourseYear.dunet"
        val CACHE_TTL: Duration = Duration.ofMinutes(5)
        const val MAX_ATTACHMENT_BYTES = 512L * 1024 * 1024
    }
}
