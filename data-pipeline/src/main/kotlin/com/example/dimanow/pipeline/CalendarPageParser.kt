package com.example.dimanow.pipeline

import java.time.LocalDate
import kotlin.math.abs
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * One dated row of the school's public 학사일정 page (`https://www.dima.ac.kr/?p=78`, D-131(2)), with its dates
 * resolved to calendar dates. [yearCorrection] is set when the row printed another year (the 2026 page writes
 * `07.01 10시 ~ 2025.07.03 12시`) and that year was read as the page's year instead.
 */
internal data class CalendarPageRow(
    val month: Int?,
    val dateText: String,
    val name: String,
    val note: String?,
    val start: LocalDate,
    val end: LocalDate,
    val yearCorrection: String? = null,
)

/** A row whose printed date could not be read; it is reported, never guessed. */
internal data class UnreadableCalendarRow(val dateText: String, val name: String)

internal data class CalendarPage(
    val year: Int,
    val rows: List<CalendarPageRow>,
    val unreadable: List<UnreadableCalendarRow>,
)

/**
 * Reads the public 학사일정 page: the year from its `NNNN년도 학사일정` heading, then every row of the month lists
 * (`p.date` with `MM.DD`, an optional `NN시` time, an optional `~` range and an optional other-year prefix; the name
 * in the row's `span`s and an optional note in its `b`). A date paragraph may hold several ranges on separate lines.
 */
internal object CalendarPageParser {
    fun parse(html: String): CalendarPage {
        val document = Jsoup.parse(html)
        val schedule = document.selectFirst("div.schedule") ?: throw IllegalArgumentException("학사일정 영역을 찾지 못했어요.")
        val heading = schedule.selectFirst(".year_select p")?.text().orEmpty()
        val year = (HEADING.find(normalize(heading)) ?: HEADING.find(normalize(schedule.text())))
            ?.groupValues?.get(1)?.toInt()
            ?: throw IllegalArgumentException("학사일정 연도 제목을 찾지 못했어요.")
        require(year in 2000..2099) { "학사일정 연도가 올바르지 않아요: $year" }

        val rows = mutableListOf<CalendarPageRow>()
        val unreadable = mutableListOf<UnreadableCalendarRow>()
        for (item in schedule.select("li:has(> p.date)")) {
            val dateElement = item.selectFirst("> p.date") ?: continue
            val body = item.selectFirst("> div")
            val name = body?.select("> span")?.joinToString(" ") { normalize(it.text()) }?.let(::normalize)
                ?.ifEmpty { null }
                ?: normalize(body?.ownText().orEmpty())
            val note = body?.select("> b")?.joinToString(" ") { normalize(it.text()) }?.let(::normalize)?.ifEmpty { null }
            val month = item.parents().firstOrNull { it.hasClass("month_cont") }
                ?.selectFirst(".left a[name]")?.attr("name")?.toIntOrNull()
            val lines = dateElement.lines().flatMap { line -> line.split(',').map(::normalize) }.filter(String::isNotEmpty)
            if (name.isEmpty() && lines.isEmpty()) continue
            val parsed = lines.map { line -> line to resolve(line, year) }
            if (lines.isEmpty() || parsed.any { it.second == null }) {
                unreadable += UnreadableCalendarRow(lines.joinToString(", ").ifEmpty { normalize(dateElement.text()) }, name)
                continue
            }
            parsed.forEach { (line, dates) ->
                val (start, end, correction) = checkNotNull(dates)
                rows += CalendarPageRow(month, line, name, note, start, end, correction)
            }
        }
        return CalendarPage(year, rows, unreadable)
    }

    private data class ResolvedDates(val start: LocalDate, val end: LocalDate, val correction: String?)

    private fun resolve(text: String, pageYear: Int): ResolvedDates? {
        val match = RANGE.matchEntire(text) ?: return null
        val groups = match.groupValues
        val corrections = mutableListOf<String>()
        fun yearOf(printed: String, fallback: Int): Int {
            val value = printed.toIntOrNull() ?: return fallback
            if (abs(value - pageYear) <= 1) return value
            corrections += "$value → $pageYear"
            return pageYear
        }
        fun date(year: Int, month: String, day: String): LocalDate? =
            runCatching { LocalDate.of(year, month.toInt(), day.toInt()) }.getOrNull()

        // Groups: 1-3 start year, month, day; 4-6 the same for the optional end.
        val startYear = yearOf(groups[1], pageYear)
        val start = date(startYear, groups[2], groups[3]) ?: return null
        if (groups[5].isEmpty()) return ResolvedDates(start, start, corrections.firstOrNull())
        var endYear = yearOf(groups[4], startYear)
        var end = date(endYear, groups[5], groups[6]) ?: return null
        if (end.isBefore(start)) {
            if (groups[4].isNotEmpty() && endYear != startYear) {
                // An other-year label that would end the row before it starts is read as the row's own year.
                corrections += "${groups[4]} → $startYear"
                endYear = startYear
                end = date(endYear, groups[5], groups[6]) ?: return null
            } else if (groups[4].isEmpty() && !end.plusYears(1).isAfter(start.plusDays(MAX_YEAR_CROSSING_DAYS))) {
                // `12.28 ~ 01.05` crosses into the next year; `03.10 ~ 03.02` is a typo, not an 11-month period.
                end = end.plusYears(1)
            }
        }
        if (end.isBefore(start)) return null
        return ResolvedDates(start, end, corrections.distinct().joinToString(", ").ifEmpty { null })
    }

    /** The element's text split at `<br>`; source line breaks are ordinary whitespace. */
    private fun Element.lines(): List<String> {
        val buffer = StringBuilder()
        fun walk(node: Node) {
            when {
                node is TextNode -> buffer.append(node.wholeText.replace(LINE_BREAKS, " "))
                node is Element && node.normalName() == "br" -> buffer.append('\n')
                node is Element -> node.childNodes().forEach(::walk)
            }
        }
        childNodes().forEach(::walk)
        return buffer.split('\n').map(::normalize).filter(String::isNotEmpty)
    }

    private fun normalize(value: String): String = value.replace(' ', ' ').replace(WHITESPACE, " ").trim()

    private const val MAX_YEAR_CROSSING_DAYS = 92L
    private val WHITESPACE = Regex("\\s+")
    private val LINE_BREAKS = Regex("[\\r\\n\\t]+")
    private val HEADING = Regex("""(\d{4})\s*년도?\s*학사\s*일정""")
    private const val DATE =
        """(?:(\d{4})\s*\.\s*)?(\d{1,2})\s*\.\s*(\d{1,2})\.?(?:\s*\([월화수목금토일]\))?(?:\s*\d{1,2}\s*(?:시(?:\s*\d{1,2}\s*분)?|:\s*\d{2}))?"""
    private val RANGE = Regex("""$DATE(?:\s*[~∼～〜]\s*$DATE)?""")
}
