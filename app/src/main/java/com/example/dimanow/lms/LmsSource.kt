package com.example.dimanow.lms

import android.webkit.CookieManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
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
    data object CourseCatalogRequired : LmsRefreshResult
    data class Failure(val message: String) : LmsRefreshResult
}

sealed interface LmsDetailLoadResult {
    data class Fresh(val detail: LmsItemDetail, val attachmentsChanged: Boolean) : LmsDetailLoadResult
    data class Cached(val detail: LmsItemDetail) : LmsDetailLoadResult
    data object SessionExpired : LmsDetailLoadResult
    data class Failure(val message: String) : LmsDetailLoadResult
}

interface LmsSource {
    val snapshot: Flow<LmsSnapshot>
    suspend fun refresh(force: Boolean = false): LmsRefreshResult
    suspend fun loadDetail(item: LmsItem): LmsDetailLoadResult
    suspend fun downloadAttachment(attachment: LmsAttachment, destination: File, onProgress: (Long, Long?) -> Unit = { _, _ -> }): LmsRefreshResult
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
    suspend fun download(url: String, destination: File, maxBytes: Long, onProgress: (Long, Long?) -> Unit): LmsHttpResponse
}

class UrlConnectionLmsTransport(
    private val cookieProvider: (String) -> String? = { CookieManager.getInstance().getCookie(it) },
    private val cookieSink: (String, String) -> Unit = { url, cookie -> CookieManager.getInstance().setCookie(url, cookie) },
    private val userAgent: String = DEFAULT_BROWSER_USER_AGENT,
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
        connection.setRequestProperty("User-Agent", userAgent)
        cookieProvider(url)?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${java.net.URLEncoder.encode(key, "UTF-8")}" +
                "=${java.net.URLEncoder.encode(value, "UTF-8")}"
        }.toByteArray()
        connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }
        val status = connection.responseCode
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
            connection.setRequestProperty("User-Agent", userAgent)
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

    private companion object {
        const val MAX_REDIRECTS = 5
        const val DEFAULT_BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/140.0 Safari/537.36"
    }
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
        val cachedEntity = dao.getDetail(key)
        val cachedAttachments = dao.getAttachments(key)
        val openedItem = item.copy(isRead = true, changeState = LmsChangeState.NONE)
        val cachedDetail = cachedEntity?.let { cached ->
            LmsItemDetail(
                openedItem,
                cached.sanitizedHtml,
                cachedAttachments.map { LmsAttachment(it.sourceId, it.fileName, it.downloadUrl, it.sizeBytes) },
            )
        }
        try {
            val course = dao.getAllCourses().firstOrNull { it.id == item.courseId }
            if (course != null && !course.id.startsWith("local-")) {
                runCatching {
                    transport.postForm(
                        CLASS_SESSION_URL,
                        mapOf("course_id" to course.id, "class_no" to course.classNo),
                    )
                }
            }
            var response = transport.get(item.detailUrl)
            var html = response.htmlText()
            if (parser.isLoginPage(html, response.finalUrl)) {
                sessionController.transition(LmsSessionState.EXPIRED)
                return@withLock LmsDetailLoadResult.SessionExpired
            }
            parser.resolveLinkedDetailUrl(item, html, LMS_ORIGIN)?.let { nestedUrl ->
                response = transport.get(nestedUrl)
                html = response.htmlText()
                if (parser.isLoginPage(html, response.finalUrl)) {
                    sessionController.transition(LmsSessionState.EXPIRED)
                    return@withLock LmsDetailLoadResult.SessionExpired
                }
            }
            val detail = parser.parseDetail(openedItem, html, LMS_ORIGIN)
            val oldSignature = cachedAttachments.map { Triple(it.sourceId, it.fileName, it.sizeBytes) }
                .sortedBy { it.first }
            val newSignature = detail.attachments.map { Triple(it.id, it.fileName, it.sizeBytes) }
                .sortedBy { it.first }
            val attachmentsChanged = cachedEntity != null && oldSignature != newSignature
            dao.replaceDetailAndOpen(
                LmsDetailEntity(key, detail.sanitizedHtml, clock.millis()),
                detail.attachments.map { LmsAttachmentEntity("$key:${it.id}", key, it.id, it.fileName, it.downloadUrl, it.sizeBytes) },
            )
            LmsDetailLoadResult.Fresh(detail, attachmentsChanged)
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
    ): LmsRefreshResult = try {
        transport.download(attachment.downloadUrl, destination, MAX_ATTACHMENT_BYTES, onProgress)
        LmsRefreshResult.Success
    } catch (error: Throwable) {
        LmsRefreshResult.Failure(error.message ?: "첨부파일을 저장하지 못했습니다")
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
