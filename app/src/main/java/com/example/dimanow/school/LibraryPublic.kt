package com.example.dimanow.school

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * What the library shows everyone, read without signing in (D-132): the 이용시간 block of its front page (the page
 * fills it from `/LibtechApi/GetSpongeManagement`) and the notices about opening hours, since vacations, exams and
 * holidays change them by notice rather than on the front page. Observed 2026-10-26 from a Korean network; the
 * site turns away foreign IP ranges, so this runs on the phone, never in the data pipeline.
 */
object LibraryPublicSource {
    private val mutableInfo = MutableStateFlow<LibraryPublicInfo?>(null)
    val info: StateFlow<LibraryPublicInfo?> = mutableInfo.asStateFlow()

    /** Reads again when nothing was read or the last read is older than an hour. */
    fun isDue(now: Instant): Boolean = mutableInfo.value?.readAt?.isBefore(now.minusSeconds(3_600)) ?: true

    suspend fun refresh(now: Instant = Instant.now()): Result<LibraryPublicInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val hours = parseLibraryHoursApi(get(LIBRARY_HOURS_API_URL))
            // The notice list is a bonus: a failure there keeps the hours.
            val notices = runCatching { parseLibraryNotices(get(LIBRARY_NOTICE_LIST_URL)) }.getOrDefault(emptyList())
            LibraryPublicInfo(hours, hoursNotices(notices, LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))), now)
        }.onSuccess { mutableInfo.value = it }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "DIMA-Now-Android")
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "도서관 응답 ${connection.responseCode}" }
            return connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }
    }
}

data class LibraryNotice(val title: String, val url: String, val postedOn: LocalDate?)

data class LibraryPublicInfo(val hours: List<LibraryHours>, val notices: List<LibraryNotice>, val readAt: Instant)

internal val LIBRARY_HOURS_API_URL = "https://lib.dima.ac.kr/LibtechApi/GetSpongeManagement?l_module=" +
    URLEncoder.encode("메인", "UTF-8") + "&l_section=" + URLEncoder.encode("이용시간", "UTF-8")
internal const val LIBRARY_NOTICE_LIST_URL = "https://lib.dima.ac.kr/Board?n=notice"

/**
 * The hours API answers with a JSON string of HTML: `<dl><dt>리더스스테이션</dt><dd>09:00 ~ 20:00</dd></dl>`…
 * (observed 2026-10-26). A bare HTML answer is read the same way.
 */
internal fun parseLibraryHoursApi(body: String): List<LibraryHours> {
    val html = runCatching { (Json.parseToJsonElement(body) as? JsonPrimitive)?.contentOrNull }.getOrNull() ?: body
    return DL.findAll(html).mapNotNull { match ->
        val place = cleanHtml(match.groupValues[1])
        val text = cleanHtml(match.groupValues[2])
        if (place.isEmpty() || text.isEmpty()) null else LibraryHours(place, text.replace(Regex("""\s*~\s*"""), " ~ "))
    }.toList()
}

/** Notice rows link `/Board/Detail/<yyyyMMddHHmmss…>?n=notice…`; the id starts with the posting time. */
internal fun parseLibraryNotices(html: String): List<LibraryNotice> =
    NOTICE_LINK.findAll(html).mapNotNull { match ->
        val id = match.groupValues[1]
        val title = cleanHtml(match.groupValues[3])
        if (title.isEmpty()) return@mapNotNull null
        val posted = runCatching { LocalDate.parse(id.take(8), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
        LibraryNotice(title, "https://lib.dima.ac.kr/Board/Detail/$id?n=notice", posted)
    }.distinctBy { it.url }.toList()

/**
 * Notices that change when the library is open: vacations, exam weeks, holidays, festivals and term starts —
 * titles such as `추석 연휴 도서관 운영시간 안내`, `입시기간 도서관 이용 안내`, `개강 후 이용안내`. Newest first, within
 * the last 120 days, at most four.
 */
internal fun hoursNotices(notices: List<LibraryNotice>, today: LocalDate): List<LibraryNotice> =
    notices.filter { notice ->
        val title = notice.title.replace(" ", "")
        HOURS_WORDS.any { it in title } && EXCLUDED_WORDS.none { it in title } &&
            (notice.postedOn == null || !notice.postedOn.isBefore(today.minusDays(120)))
    }.sortedByDescending { it.postedOn }.take(4)

private val HOURS_WORDS = listOf("이용안내", "이용시간", "운영시간", "운영안내", "개관", "휴관", "방학", "단축")
private val EXCLUDED_WORDS = listOf("대여서비스", "WiFi", "와이파이", "회원신청", "연체")

private val DL = Regex("""<dt[^>]*>(.*?)</dt>\s*<dd[^>]*>(.*?)</dd>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val NOTICE_LINK = Regex(
    """<a[^>]+href="/Board/Detail/(\d{8,20})\?n=notice([^"]*)"[^>]*>(.*?)</a>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
)

private fun cleanHtml(value: String): String = value
    .replace(Regex("<[^>]+>"), " ")
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    .replace(Regex("""\s+"""), " ")
    .trim()
