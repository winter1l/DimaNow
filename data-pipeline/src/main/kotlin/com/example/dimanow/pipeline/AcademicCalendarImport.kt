package com.example.dimanow.pipeline

import com.example.dimanow.sync.CalendarEvent
import com.example.dimanow.sync.CalendarPeriod
import com.example.dimanow.sync.CalendarTerm
import com.example.dimanow.sync.ServiceCalendarPayload
import com.example.dimanow.sync.serviceCalendarProblem
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/** A page row the import did not use, with why (an exclusion rule, no rule, or another year). */
internal data class UnusedCalendarRow(val row: CalendarPageRow, val reason: String, val excluded: Boolean)

/** One year of the public 학사일정 page read into the calendar's shape (D-106 terms and exam weeks, D-107 events). */
internal data class ImportedAcademicYear(
    val year: Int,
    val terms: List<CalendarTerm>,
    val events: List<CalendarEvent>,
    val unused: List<UnusedCalendarRow>,
    val unreadable: List<UnreadableCalendarRow>,
    val corrected: List<CalendarPageRow>,
    val notes: List<String>,
)

/**
 * Maps page rows to terms, exam weeks and the student-relevant events of D-107 (등록, 수강신청, 정정, 포기, 학점인정,
 * 휴학, 복학, 지정보강, 수업평가, 성적열람) with a keyword table matched on the name without spaces. The names are the
 * curated ones of `data-source/calendar.json`; rows of one name that touch or nearly touch (the 1차/2차 수강 신청 days)
 * become one period.
 */
internal object AcademicCalendarMapper {
    private data class Rule(val keyword: String, val name: String, val keepRound: Boolean = false)

    /** Checked first: rows for other audiences that would otherwise match an event keyword. */
    private val EXCLUSIONS = listOf(
        "계절학기" to "계절학기",
        "수업소감" to "수업소감(중간 수업평가)",
        "예비수강" to "예비수강(장바구니)",
        "장바구니" to "예비수강(장바구니)",
    )

    /** First match wins, so the narrower keywords come first (성적 열람 rows also say 성적 정정). */
    private val RULES = listOf(
        Rule("성적열람", "성적 열람"),
        Rule("학점인정", "학점인정 신청"),
        Rule("수강신청정정", "수강신청 정정"),
        Rule("수강신청포기", "수강신청 포기"),
        Rule("수강신청", "수강신청"),
        Rule("복학신청", "복학 신청"),
        Rule("일반휴학", "일반휴학 신청", keepRound = true),
        Rule("보강주간", "지정보강"),
        Rule("지정보강", "지정보강"),
        Rule("수업평가", "수업평가"),
        Rule("등록", "등록금 납부"),
    )

    private enum class Role { START, END, MIDTERM, FINAL }

    private val TERM_ROW = Regex("""^([12])학기(개강|종강|중간시험|중간고사|기말시험|기말고사)(?:\(.*\))?$""")
    private val ROUND = Regex("""\((\d)차\)""")

    /** Rows of one name are joined when the next starts at most this many days after the previous ends. */
    private const val JOIN_GAP_DAYS = 3L

    fun map(page: CalendarPage): ImportedAcademicYear {
        val unused = mutableListOf<UnusedCalendarRow>()
        val notes = mutableListOf<String>()
        val termRows = mutableMapOf<Pair<Int, Role>, MutableList<CalendarPageRow>>()
        val eventRows = mutableListOf<Pair<String, CalendarPageRow>>()

        for (row in page.rows) {
            val compact = row.name.replace(Regex("\\s+"), "")
            if (row.start.year != page.year) {
                unused += UnusedCalendarRow(row, "${row.start.year}년 일정", excluded = true)
                continue
            }
            val termMatch = TERM_ROW.matchEntire(compact)
            if (termMatch != null) {
                val role = when (termMatch.groupValues[2]) {
                    "개강" -> Role.START
                    "종강" -> Role.END
                    "중간시험", "중간고사" -> Role.MIDTERM
                    else -> Role.FINAL
                }
                termRows.getOrPut(termMatch.groupValues[1].toInt() to role) { mutableListOf() } += row
                continue
            }
            val exclusion = EXCLUSIONS.firstOrNull { compact.contains(it.first) }
            if (exclusion != null) {
                unused += UnusedCalendarRow(row, exclusion.second, excluded = true)
                continue
            }
            val rule = RULES.firstOrNull { compact.contains(it.keyword) }
            if (rule == null) {
                unused += UnusedCalendarRow(row, "규칙 없음", excluded = false)
                continue
            }
            val round = if (rule.keepRound) ROUND.find(compact)?.let { "(${it.groupValues[1]}차)" }.orEmpty() else ""
            eventRows += (rule.name + round) to row
        }

        val terms = (1..2).mapNotNull { semester -> buildTerm(page.year, semester, termRows, unused, notes) }
        val events = eventRows.groupBy({ it.first }, { it.second })
            .flatMap { (name, rows) -> join(name, rows) }
            .sortedWith(compareBy<CalendarEvent>({ it.start }, { it.end }, { it.name }))
        return ImportedAcademicYear(
            year = page.year,
            terms = terms,
            events = events,
            unused = unused,
            unreadable = page.unreadable,
            corrected = page.rows.filter { it.yearCorrection != null },
            notes = notes,
        )
    }

    private fun buildTerm(
        year: Int,
        semester: Int,
        rows: Map<Pair<Int, Role>, List<CalendarPageRow>>,
        unused: MutableList<UnusedCalendarRow>,
        notes: MutableList<String>,
    ): CalendarTerm? {
        fun single(role: Role, label: String): CalendarPageRow? {
            val found = rows[semester to role].orEmpty().sortedBy { it.start }
            if (found.size > 1) notes += "${semester}학기 $label 행이 ${found.size}번 나와 가장 이른 행을 썼어요."
            found.drop(1).forEach { unused += UnusedCalendarRow(it, "${semester}학기 $label 중복", excluded = false) }
            return found.firstOrNull()
        }
        val start = single(Role.START, "개강")
        val end = single(Role.END, "종강")
        val midterm = single(Role.MIDTERM, "중간시험")
        val final = single(Role.FINAL, "기말시험")
        if (start == null) {
            notes += "${semester}학기 개강 행이 없어 $year-$semester 학기를 만들지 않았어요."
            listOfNotNull(end, midterm, final).forEach { unused += UnusedCalendarRow(it, "${semester}학기 개강 없음", excluded = false) }
            return null
        }
        val termEnd = when {
            end != null -> end.end
            final != null -> {
                notes += "${semester}학기 종강 행이 없어 기말시험 마지막 날(${final.end})을 학기 끝으로 썼어요."
                final.end
            }
            else -> {
                notes += "${semester}학기 종강과 기말시험 행이 없어 $year-$semester 학기를 만들지 않았어요."
                listOfNotNull(start, midterm).forEach { unused += UnusedCalendarRow(it, "${semester}학기 끝 없음", excluded = false) }
                return null
            }
        }
        fun exam(row: CalendarPageRow?, label: String): CalendarPeriod? {
            row ?: return null
            if (row.start.isBefore(start.start) || row.end.isAfter(termEnd) || row.start.plusDays(14).isBefore(row.end)) {
                notes += "${semester}학기 $label(${row.start} ~ ${row.end})이 학기 밖이거나 너무 길어 넣지 않았어요."
                unused += UnusedCalendarRow(row, "${semester}학기 $label 범위 밖", excluded = false)
                return null
            }
            return CalendarPeriod(row.start.toString(), row.end.toString())
        }
        return CalendarTerm(
            id = "$year-$semester",
            start = start.start.toString(),
            end = termEnd.toString(),
            midterm = exam(midterm, "중간시험"),
            finalExam = exam(final, "기말시험"),
        )
    }

    private fun join(name: String, rows: List<CalendarPageRow>): List<CalendarEvent> {
        val periods = mutableListOf<Pair<LocalDate, LocalDate>>()
        for (row in rows.sortedBy { it.start }) {
            val last = periods.lastOrNull()
            if (last != null && !row.start.isAfter(last.second.plusDays(JOIN_GAP_DAYS))) {
                periods[periods.lastIndex] = last.first to maxOf(last.second, row.end)
            } else {
                periods += row.start to row.end
            }
        }
        return periods.map { (start, end) -> CalendarEvent(name, start.toString(), end.toString()) }
    }
}

internal sealed interface CalendarImportResult {
    /** A merged `calendar.json` ready for review. */
    data class Candidate(val payload: ServiceCalendarPayload, val imported: ImportedAcademicYear) : CalendarImportResult

    data class NothingToDo(val reason: String) : CalendarImportResult
}

internal object AcademicCalendarMerger {
    /** The years `calendar.json` already has terms for; these are never changed by an import. */
    fun termYears(payload: ServiceCalendarPayload): Set<Int> = payload.terms.mapNotNull { it.id.take(4).toIntOrNull() }.toSet()

    /**
     * Adds [imported]'s terms and events when its year is newer than every term in [existing]. Existing terms,
     * holidays, shuttle suspensions and events stay exactly as they are; an event already present is not added twice.
     */
    fun merge(existing: ServiceCalendarPayload, imported: ImportedAcademicYear): CalendarImportResult {
        val latest = termYears(existing).maxOrNull()
        if (latest != null && imported.year <= latest) {
            return CalendarImportResult.NothingToDo("${imported.year}년 페이지가 calendar.json의 마지막 학기 연도($latest)보다 새롭지 않아요.")
        }
        if (imported.terms.isEmpty()) {
            return CalendarImportResult.NothingToDo("${imported.year}년 페이지에 아직 개강 일정이 없어요.")
        }
        val known = existing.academicEvents.toSet()
        val events = (existing.academicEvents + imported.events.filterNot { it in known })
            .sortedBy { it.start } // stable: existing order is kept within a day
        val merged = existing.copy(
            terms = (existing.terms + imported.terms).sortedBy { it.start },
            academicEvents = events,
        )
        serviceCalendarProblem(merged)?.let { throw IllegalStateException("가져온 학사일정이 달력 검증을 통과하지 못했어요: $it") }
        return CalendarImportResult.Candidate(merged, imported)
    }
}

/** Writes `calendar.json` in the hand-kept layout of `data-source/calendar.json`, so a candidate's diff is only its additions. */
internal object CalendarJsonFormat {
    fun format(payload: ServiceCalendarPayload): String = buildString {
        fun q(value: String) = JsonPrimitive(value).toString()
        fun <T> array(key: String, items: List<T>, last: Boolean = false, line: (T) -> String) {
            append("  ${q(key)}: [")
            if (items.isNotEmpty()) {
                append('\n')
                items.forEachIndexed { index, item ->
                    append("    ").append(line(item))
                    append(if (index < items.lastIndex) ",\n" else "\n")
                }
                append("  ")
            }
            append(if (last) "]\n" else "],\n")
        }
        append("{\n")
        append("  \"schemaVersion\": ${payload.schemaVersion},\n")
        array("terms", payload.terms) { term ->
            buildString {
                append("{\n      \"id\": ${q(term.id)}, \"start\": ${q(term.start)}, \"end\": ${q(term.end)}")
                listOfNotNull(term.midterm?.let { "midterm" to it }, term.finalExam?.let { "final" to it }).forEach { (key, period) ->
                    append(",\n      ${q(key)}: { \"start\": ${q(period.start)}, \"end\": ${q(period.end)} }")
                }
                append("\n    }")
            }
        }
        array("holidays", payload.holidays) { "{ \"date\": ${q(it.date)}, \"name\": ${q(it.name)} }" }
        array("campusShuttleSuspensions", payload.campusShuttleSuspensions) {
            "{ \"start\": ${q(it.start)}, \"end\": ${q(it.end)}, \"reason\": ${q(it.reason)} }"
        }
        array("academicEvents", payload.academicEvents, last = true) {
            "{ \"name\": ${q(it.name)}, \"start\": ${q(it.start)}, \"end\": ${q(it.end)} }"
        }
        append("}\n")
    }
}

/** The pull request (or issue) text for a candidate: page text appears only inside inline code. */
internal object CalendarImportSummary {
    fun markdown(candidate: CalendarImportResult.Candidate, sourceUrl: String): String = buildString {
        val imported = candidate.imported
        val year = imported.year
        appendLine("## $year 학사일정 가져오기 후보")
        appendLine()
        appendLine("$sourceUrl 의 ${year}년도 학사일정을 읽어 `data-source/calendar.json`에 ${year}년 학기·시험 기간·학사 일정을 더한 후보예요 (D-131(2)).")
        appendLine("기존 연도, 공휴일, 셔틀 운행 중지는 그대로예요. 검토해 병합하기 전까지 학생에게는 아무것도 가지 않아요.")
        appendLine()
        appendLine("### 학기와 시험 기간")
        appendLine()
        appendLine("| 학기 | 시작 | 끝 | 중간고사 | 기말고사 |")
        appendLine("|---|---|---|---|---|")
        imported.terms.forEach { term ->
            fun period(value: CalendarPeriod?) = value?.let { "${it.start} ~ ${it.end}" } ?: "없음"
            appendLine("| ${term.id} | ${term.start} | ${term.end} | ${period(term.midterm)} | ${period(term.finalExam)} |")
        }
        appendLine()
        appendLine("### 학사 일정 ${imported.events.size}건")
        appendLine()
        imported.events.forEach { appendLine("- ${code(it.name)} ${it.start} ~ ${it.end}") }
        val checks = buildList {
            addAll(imported.notes)
            if (candidate.payload.holidays.none { it.date.startsWith("$year-") }) add("calendar.json에 ${year}년 공휴일이 없어요 (KASI 월력요항으로 따로 넣어야 해요).")
            imported.corrected.forEach { add("연도 표기를 고쳐 읽은 행: ${code(it.dateText)} ${code(it.name)} (${it.yearCorrection})") }
            imported.unreadable.forEach { add("날짜를 읽지 못한 행: ${code(it.dateText)} ${code(it.name)}") }
        }
        if (checks.isNotEmpty()) {
            appendLine()
            appendLine("### 확인할 점")
            appendLine()
            checks.forEach { appendLine("- $it") }
        }
        val unmapped = imported.unused.filterNot { it.excluded }
        appendLine()
        appendLine("### 규칙에 맞지 않아 가져오지 않은 행 ${unmapped.size}건")
        appendLine()
        if (unmapped.isEmpty()) appendLine("없음")
        unmapped.forEach { appendLine("- ${code(it.row.dateText)} ${code(it.row.name)} — ${it.reason}") }
        val excluded = imported.unused.filter { it.excluded }
        if (excluded.isNotEmpty()) {
            appendLine()
            appendLine("<details><summary>제외 규칙으로 뺀 행 ${excluded.size}건</summary>")
            appendLine()
            excluded.forEach { appendLine("- ${code(it.row.dateText)} ${code(it.row.name)} — ${it.reason}") }
            appendLine()
            appendLine("</details>")
        }
        appendLine()
        appendLine("이 브랜치는 다시 덮어쓰지 않아요. 페이지가 바뀌어 새로 만들려면 풀 리퀘스트를 닫고 브랜치를 지운 뒤 워크플로를 다시 실행해 주세요.")
    }

    /** Inline code without backticks or control characters, so page text cannot mention, link or format. */
    private fun code(value: String): String {
        val cleaned = value.filterNot { it == '`' || it.isISOControl() }.take(80)
        return "`${cleaned.ifEmpty { " " }}`"
    }
}

/**
 * `import-calendar <calendar.json> <out.json>` (D-131(2)): reads the public 학사일정 page for the year after the
 * latest term in `calendar.json` and, when that year has terms, writes the merged candidate to `out.json` and its
 * review summary next to it (`<out>.summary.md`). Otherwise nothing is written. Existing years are never changed.
 */
internal class CalendarImportCommand(
    private val fetchPage: (String) -> String = { url -> MealRemoteHttpClient().getText(url, MealRemoteEndpoint.DIMA_ACADEMIC_CALENDAR) },
) {
    fun run(calendarFile: Path, outFile: Path): CalendarImportResult {
        val existing = Json.decodeFromString<ServiceCalendarPayload>(Files.readString(calendarFile))
        serviceCalendarProblem(existing)?.let { throw IllegalArgumentException("calendar.json이 올바르지 않아요: $it") }
        val latest = AcademicCalendarMerger.termYears(existing).maxOrNull()
        val url = sourceUrl(latest?.plus(1))
        val page = CalendarPageParser.parse(fetchPage(url))
        if (latest != null && page.year != latest + 1) {
            return CalendarImportResult.NothingToDo("${latest + 1}년을 요청했지만 ${page.year}년 페이지가 왔어요.")
        }
        val result = AcademicCalendarMerger.merge(existing, AcademicCalendarMapper.map(page))
        if (result is CalendarImportResult.Candidate) {
            outFile.toAbsolutePath().parent?.let(Files::createDirectories)
            Files.writeString(outFile, CalendarJsonFormat.format(result.payload))
            Files.writeString(summaryFile(outFile), CalendarImportSummary.markdown(result, url))
        }
        return result
    }

    companion object {
        fun sourceUrl(year: Int?): String =
            if (year == null) StaticDataPublisher.CALENDAR_SOURCE_URL else "${StaticDataPublisher.CALENDAR_SOURCE_URL}&nyear=$year"

        fun summaryFile(outFile: Path): Path = outFile.resolveSibling(outFile.fileName.toString().removeSuffix(".json") + ".summary.md")
    }
}
