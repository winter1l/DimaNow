package com.example.dimanow.lms

import android.webkit.CookieManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface LmsRefreshResult {
    data object Success : LmsRefreshResult
    data object Cached : LmsRefreshResult
    data object SessionExpired : LmsRefreshResult
    data class Failure(val message: String) : LmsRefreshResult
}

interface LmsSource {
    val snapshot: Flow<LmsSnapshot>
    suspend fun refresh(force: Boolean = false): LmsRefreshResult
    suspend fun loadDetail(item: LmsItem, force: Boolean = false): LmsItemDetail?
    suspend fun downloadAttachment(attachment: LmsAttachment, destination: File, onProgress: (Long, Long?) -> Unit = { _, _ -> }): LmsRefreshResult
    suspend fun clearPrivateData()
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
    suspend fun download(url: String, destination: File, maxBytes: Long, onProgress: (Long, Long?) -> Unit): LmsHttpResponse
}

class UrlConnectionLmsTransport(
    private val cookieProvider: (String) -> String? = { CookieManager.getInstance().getCookie(it) },
    private val cookieSink: (String, String) -> Unit = { url, cookie -> CookieManager.getInstance().setCookie(url, cookie) },
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

    override suspend fun postForm(url: String, fields: Map<String, String>, maxBytes: Long): LmsHttpResponse = withContext(Dispatchers.IO) {
        val uri = validateUrl(url)
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json,text/html,*/*")
        connection.setRequestProperty("User-Agent", "DIMA-Now-Android")
        cookieProvider(url)?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${java.net.URLEncoder.encode(key, "UTF-8")}" +
                "=${java.net.URLEncoder.encode(value, "UTF-8")}"
        }.toByteArray()
        connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }
        val status = connection.responseCode
        check(status in 200..299) { "LMS HTTP $status" }
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
        storeResponseCookies(url, connection)
        LmsHttpResponse(url, status, connection.contentType, safeHeaders(connection), responseBytes)
            .also { connection.disconnect() }
    }

    override suspend fun download(
        url: String,
        destination: File,
        maxBytes: Long,
        onProgress: (Long, Long?) -> Unit,
    ): LmsHttpResponse = withContext(Dispatchers.IO) {
        val part = File(destination.parentFile, destination.name + ".part")
        try {
            val response = execute(url, maxBytes) { connection, total ->
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
                    }
                }
                ByteArray(0) to total
            }.first
            check(!response.contentType.orEmpty().contains("text/html", ignoreCase = true)) { "로그인 세션이 만료되었습니다" }
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
        consume: (HttpURLConnection, Long?) -> Pair<T, Long?>,
    ): Pair<LmsHttpResponse, T> {
        var current = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val uri = validateUrl(current)
            val connection = URL(uri.toString()).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/octet-stream;q=0.9,*/*;q=0.8")
            connection.setRequestProperty("User-Agent", "DIMA-Now-Android")
            cookieProvider(current)?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
            val status = connection.responseCode
            if (status in 300..399) {
                storeResponseCookies(current, connection)
                val location = connection.getHeaderField("Location") ?: error("LMS redirect has no location")
                check(redirectCount < MAX_REDIRECTS) { "Too many LMS redirects" }
                current = uri.resolve(location).toString()
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

    private fun storeResponseCookies(url: String, connection: HttpURLConnection) {
        connection.headerFields.entries
            .filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
            .flatMap { it.value.orEmpty() }
            .forEach { cookieSink(url, it) }
    }

    private fun safeHeaders(connection: HttpURLConnection): Map<String, List<String>> = buildMap {
        connection.headerFields.forEach { (key, values) ->
            if (key != null && values != null) put(key, values)
        }
    }

    private companion object { const val MAX_REDIRECTS = 5 }
}

class RoomLmsSource(
    private val database: LmsCacheDatabase,
    private val sessionController: LmsSessionController,
    private val transport: LmsHttpTransport = UrlConnectionLmsTransport(),
    private val parser: LmsHtmlParser = LmsHtmlParser(),
    private val clock: Clock = Clock.systemUTC(),
) : LmsSource {
    private val dao = database.dao()
    private val refreshMutex = Mutex()
    override val snapshot: Flow<LmsSnapshot> = combine(
        dao.observeCourses(), dao.observeItems(), dao.observeSync(),
    ) { courses, items, sync ->
        LmsSnapshot(
            courses = courses.map { LmsCourse(it.id, it.name, it.professor) },
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
            val response = transport.get(DASHBOARD_URL)
            val html = response.body.toString(Charsets.UTF_8)
            if (parser.isLoginPage(html, response.finalUrl)) {
                sessionController.transition(LmsSessionState.EXPIRED)
                dao.setSync(LmsSyncEntity(status = LmsSyncState.ERROR.name, lastSuccessAtMillis = prior?.lastSuccessAtMillis, errorMessage = "로그인이 필요합니다"))
                return@withLock LmsRefreshResult.SessionExpired
            }
            val parsed = parser.parseDashboard(html, LMS_ORIGIN)
            val expandedItems = parsed.items.toMutableList()
            val previousItems = dao.getAllItems().map(::toModel)
            parsed.courses.filterNot { it.id.startsWith("local-") }.forEach { course ->
                val sessionReady = runCatching {
                    transport.postForm(CLASS_SESSION_URL, mapOf("class_no" to course.id))
                }.isSuccess
                BOARD_ROUTES.forEach { (kind, url) ->
                    val boardItems = if (sessionReady) runCatching {
                        val boardResponse = transport.get(url)
                        val boardHtml = boardResponse.body.toString(Charsets.UTF_8)
                        check(!parser.isLoginPage(boardHtml, boardResponse.finalUrl)) { "LMS session expired" }
                        parser.parseBoardItems(boardHtml, LMS_ORIGIN, course, kind)
                    }.getOrNull() else null
                    expandedItems += boardItems ?: previousItems.filter { it.courseId == course.id && it.kind == kind }
                }
            }
            dao.replaceDashboard(
                parsed.courses.map { LmsCourseEntity(it.id, it.name, it.professor) },
                expandedItems.distinctBy { Triple(it.courseId, it.kind, it.id) }.map(::toEntity),
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

    override suspend fun loadDetail(item: LmsItem, force: Boolean): LmsItemDetail? = refreshMutex.withLock {
        val key = itemKey(item)
        if (!force) {
            val cached = dao.getDetail(key)
            if (cached != null) {
                return@withLock LmsItemDetail(
                    item,
                    cached.sanitizedHtml,
                    dao.getAttachments(key).map { LmsAttachment(it.sourceId, it.fileName, it.downloadUrl, it.sizeBytes) },
                )
            }
        }
        val response = transport.get(item.detailUrl)
        val html = response.body.toString(Charsets.UTF_8)
        if (parser.isLoginPage(html, response.finalUrl)) {
            sessionController.transition(LmsSessionState.EXPIRED)
            return@withLock null
        }
        val detail = parser.parseDetail(item, html, LMS_ORIGIN)
        dao.replaceDetail(
            LmsDetailEntity(key, detail.sanitizedHtml, clock.millis()),
            detail.attachments.map { LmsAttachmentEntity("$key:${it.id}", key, it.id, it.fileName, it.downloadUrl, it.sizeBytes) },
        )
        detail
    }

    override suspend fun downloadAttachment(
        attachment: LmsAttachment,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
    ): LmsRefreshResult = try {
        transport.download(attachment.downloadUrl, destination, MAX_ATTACHMENT_BYTES, onProgress)
        LmsRefreshResult.Success
    } catch (error: Throwable) {
        LmsRefreshResult.Failure(error.message ?: "첨부파일을 저장하지 못했습니다")
    }

    override suspend fun clearPrivateData() {
        refreshMutex.withLock { dao.clearPrivateData() }
    }

    private fun toEntity(item: LmsItem) = LmsItemEntity(
        key = itemKey(item), sourceId = item.id, courseId = item.courseId, courseName = item.courseName,
        kind = item.kind.name, title = item.title, registeredAtMillis = item.registeredAt?.toEpochMilli(),
        dueAtMillis = item.dueAt?.toEpochMilli(), detailUrl = item.detailUrl,
    )

    private fun toModel(item: LmsItemEntity) = LmsItem(
        id = item.sourceId, courseId = item.courseId, courseName = item.courseName,
        kind = runCatching { LmsItemKind.valueOf(item.kind) }.getOrDefault(LmsItemKind.OTHER), title = item.title,
        registeredAt = item.registeredAtMillis?.let(Instant::ofEpochMilli), dueAt = item.dueAtMillis?.let(Instant::ofEpochMilli),
        detailUrl = item.detailUrl,
    )

    private fun itemKey(item: LmsItem) = "${item.kind}:${item.courseId}:${item.id}"

    private companion object {
        const val LMS_ORIGIN = "https://lms.dima.ac.kr"
        const val DASHBOARD_URL = "$LMS_ORIGIN/lms/myLecture/doListView.dunet?mnid=201008840728"
        const val CLASS_SESSION_URL = "$LMS_ORIGIN/lms/class/classroom/doSetSessionClassRoom.dunet"
        val BOARD_ROUTES = listOf(
            LmsItemKind.NOTICE to "$LMS_ORIGIN/lms/class/boardItem/doListView.dunet?board_no=7",
            LmsItemKind.MATERIAL to "$LMS_ORIGIN/lms/class/boardItem/doListView.dunet?board_no=6",
            LmsItemKind.ASSIGNMENT to "$LMS_ORIGIN/lms/class/report/doListView.dunet",
        )
        val CACHE_TTL: Duration = Duration.ofMinutes(5)
        const val MAX_ATTACHMENT_BYTES = 512L * 1024 * 1024
    }
}
