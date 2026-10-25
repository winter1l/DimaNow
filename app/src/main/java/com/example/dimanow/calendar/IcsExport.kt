package com.example.dimanow.calendar

import com.example.dimanow.domain.Course
import com.example.dimanow.domain.CourseOverrideKind
import com.example.dimanow.domain.TermSchedule
import com.example.dimanow.sync.ServiceCalendar
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Builds the `.ics` file that the 시간표 page shares (D-128(4)). The app never writes to a calendar itself
 * (D-125(6)): the student opens the file in a calendar app of their choice. Pure, so it is unit-tested on the JVM.
 *
 * RFC 5545: CRLF line ends, lines folded at 75 octets on UTF-8 boundaries, TEXT values escaped. Classes are
 * weekly events in Asia/Seoul until the term's last day, without no-class days, the guidance pause and cancelled
 * single classes; 학사 일정 periods are all-day events.
 */
object IcsExport {
    private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    private val LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val UTC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private const val TZID = "Asia/Seoul"
    private const val CRLF = "\r\n"
    private const val MAX_OCTETS = 75

    /**
     * The whole calendar: one weekly event per class of [schedule] (null leaves classes out) and one all-day
     * event per entry of [academicEvents]. [now] stamps every event (DTSTAMP).
     */
    fun build(
        schedule: TermSchedule?,
        academicEvents: List<ServiceCalendar.AcademicEvent>,
        now: Instant,
    ): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "PRODID:-//DIMA Now//DIMA Now//KO",
            "VERSION:2.0",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:DIMA Now",
            "X-WR-TIMEZONE:$TZID",
            "BEGIN:VTIMEZONE",
            "TZID:$TZID",
            "BEGIN:STANDARD",
            "DTSTART:19700101T000000",
            "TZOFFSETFROM:+0900",
            "TZOFFSETTO:+0900",
            "TZNAME:KST",
            "END:STANDARD",
            "END:VTIMEZONE",
        )
        val stamp = UTC_DATE_TIME.format(now)
        schedule?.let { term ->
            term.courses
                .sortedWith(compareBy<Course>({ it.weekday.value }, { it.start }, { it.name }))
                .forEach { course -> lines += courseEvent(term, course, stamp) }
        }
        academicEvents.forEach { event -> lines += academicEvent(event, stamp) }
        lines += "END:VCALENDAR"
        return lines.joinToString(separator = "") { fold(it) + CRLF }
    }

    /** The 학사 일정 periods, exam weeks included, that have not ended before [today]. */
    fun upcomingAcademicEvents(calendar: ServiceCalendar, today: LocalDate): List<ServiceCalendar.AcademicEvent> =
        calendar.academicAgenda(today, lookAheadDays = 3_650)

    /** Classes whose weekday occurs at least once in the term; the others would make an event outside the term. */
    fun exportableCourses(schedule: TermSchedule): List<Course> =
        schedule.courses.filter { firstOccurrence(schedule, it) != null }

    private fun firstOccurrence(schedule: TermSchedule, course: Course): LocalDate? =
        schedule.termStart.with(TemporalAdjusters.nextOrSame(course.weekday)).takeUnless { it.isAfter(schedule.termEnd) }

    private fun courseEvent(schedule: TermSchedule, course: Course, stamp: String): List<String> {
        val first = firstOccurrence(schedule, course) ?: return emptyList()
        // The last instant of the term's last day in Seoul, in UTC as RFC 5545 requires with a TZID DTSTART.
        val until = LocalDateTime.of(schedule.termEnd, LocalTime.of(23, 59, 59)).atZone(SEOUL).toInstant()
        val skipped = skippedDates(schedule, course, first)
        return buildList {
            add("BEGIN:VEVENT")
            add("UID:${uid("course", schedule.termStart.toString(), course.key())}@dimanow")
            add("DTSTAMP:$stamp")
            add("DTSTART;TZID=$TZID:${LOCAL_DATE_TIME.format(first.atTime(course.start))}")
            add("DTEND;TZID=$TZID:${LOCAL_DATE_TIME.format(first.atTime(course.end))}")
            add("RRULE:FREQ=WEEKLY;UNTIL=${UTC_DATE_TIME.format(until)}")
            if (skipped.isNotEmpty()) {
                add("EXDATE;TZID=$TZID:" + skipped.joinToString(",") { LOCAL_DATE_TIME.format(it.atTime(course.start)) })
            }
            add("SUMMARY:${escape(course.name)}")
            if (course.room.isNotBlank()) add("LOCATION:${escape(course.room)}")
            if (course.professor.isNotBlank()) add("DESCRIPTION:${escape(course.professor)}")
            add("TRANSP:OPAQUE")
            add("END:VEVENT")
        }
    }

    /** No-class days, the guidance pause and this class's cancelled dates that fall on its weekday within the term. */
    private fun skippedDates(schedule: TermSchedule, course: Course, first: LocalDate): List<LocalDate> {
        val cancelled = schedule.courseOverrides
            .filter { it.courseId == course.id && course.id != 0L && it.kind == CourseOverrideKind.CANCELLED }
            .map { it.date }
        val paused = schedule.guidancePause?.let { pause ->
            generateSequence(first) { it.plusWeeks(1) }
                .takeWhile { !it.isAfter(schedule.termEnd) }
                .filter { pause.contains(it) }
                .toList()
        }.orEmpty()
        return (schedule.noClassDates + cancelled + paused)
            .filter { it.dayOfWeek == course.weekday && !it.isBefore(first) && !it.isAfter(schedule.termEnd) }
            .distinct()
            .sorted()
    }

    private fun academicEvent(event: ServiceCalendar.AcademicEvent, stamp: String): List<String> = listOf(
        "BEGIN:VEVENT",
        "UID:${uid("academic", event.name, event.start.toString(), event.end.toString())}@dimanow",
        "DTSTAMP:$stamp",
        "DTSTART;VALUE=DATE:${DATE.format(event.start)}",
        // All-day DTEND is exclusive: the day after the period's last day.
        "DTEND;VALUE=DATE:${DATE.format(event.end.plusDays(1))}",
        "SUMMARY:${escape(event.name)}",
        "TRANSP:TRANSPARENT",
        "END:VEVENT",
    )

    /** A saved class keeps its database id across edits, so a re-export updates the same calendar event. */
    private fun Course.key(): String =
        if (id != 0L) "id:$id" else "draft:${weekday}|$start|$name"

    private fun uid(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    /** RFC 5545 §3.3.11 TEXT escaping. */
    internal fun escape(value: String): String = buildString {
        value.replace("\r\n", "\n").replace('\r', '\n').forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                else -> append(char)
            }
        }
    }

    /**
     * RFC 5545 §3.1 folding: no line longer than 75 octets; each continuation starts with one space, which
     * counts toward its 75. Never splits a UTF-8 sequence (or a surrogate pair).
     */
    internal fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= MAX_OCTETS) return line
        val out = StringBuilder()
        var used = 0
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            val chars = Character.charCount(codePoint)
            val octets = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (used + octets > MAX_OCTETS) {
                out.append(CRLF).append(' ')
                used = 1
            }
            out.append(line, index, index + chars)
            used += octets
            index += chars
        }
        return out.toString()
    }
}
