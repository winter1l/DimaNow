package com.example.dimanow.calendar

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.CourseOverride
import com.example.dimanow.domain.CourseOverrideKind
import com.example.dimanow.domain.GuidancePause
import com.example.dimanow.domain.TermSchedule
import com.example.dimanow.sync.ServiceCalendar
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-128(4): the `.ics` file shared from the 시간표 page follows RFC 5545. */
class IcsExportTest {
    private val now = Instant.parse("2026-10-26T01:02:03Z")

    private val wednesday = Course(
        weekday = DayOfWeek.WEDNESDAY,
        start = LocalTime.of(10, 0),
        end = LocalTime.of(11, 30),
        name = "디자인 기초",
        room = "본관 301",
        professor = "김교수",
        zone = CampusZoneId.MAIN,
        id = 7,
    )

    private val schedule = TermSchedule(
        termStart = LocalDate.of(2026, 8, 24),
        termEnd = LocalDate.of(2026, 12, 18),
        courses = listOf(wednesday),
        // A Wednesday, a Thursday (another weekday) and a day before the term.
        noClassDates = setOf(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 24), LocalDate.of(2026, 8, 19)),
        guidancePause = GuidancePause(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)),
        courseOverrides = listOf(
            CourseOverride(7, LocalDate.of(2026, 11, 4), CourseOverrideKind.CANCELLED),
            CourseOverride(7, LocalDate.of(2026, 11, 11), CourseOverrideKind.ONLINE),
        ),
    )

    /** Content lines after undoing the folding. */
    private fun unfolded(ics: String): List<String> = ics.replace("\r\n ", "").split("\r\n").dropLast(1)

    @Test
    fun `the calendar has its header, the Seoul time zone and CRLF line ends only`() {
        val ics = IcsExport.build(schedule, emptyList(), now)
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertFalse("a bare LF", ics.replace("\r\n", "").contains('\n'))
        assertFalse("a bare CR", ics.replace("\r\n", "").contains('\r'))
        val lines = unfolded(ics)
        assertEquals("BEGIN:VCALENDAR", lines.first())
        listOf(
            "VERSION:2.0", "CALSCALE:GREGORIAN", "X-WR-TIMEZONE:Asia/Seoul", "BEGIN:VTIMEZONE", "TZID:Asia/Seoul",
            "TZOFFSETFROM:+0900", "TZOFFSETTO:+0900",
        ).forEach { assertTrue(it, it in lines) }
        assertTrue(lines.any { it.startsWith("PRODID:") })
    }

    @Test
    fun `a class repeats weekly from its first day in the term until the term's last day`() {
        val lines = unfolded(IcsExport.build(schedule, emptyList(), now))
        // 2026-08-24 is a Monday; the first Wednesday is 08-26.
        assertTrue("DTSTART;TZID=Asia/Seoul:20260826T100000" in lines)
        assertTrue("DTEND;TZID=Asia/Seoul:20260826T113000" in lines)
        // 2026-12-18 23:59:59 KST is 14:59:59 UTC.
        assertTrue("RRULE:FREQ=WEEKLY;UNTIL=20261218T145959Z" in lines)
        assertTrue("SUMMARY:디자인 기초" in lines)
        assertTrue("LOCATION:본관 301" in lines)
        assertTrue("DESCRIPTION:김교수" in lines)
        assertTrue("DTSTAMP:20261026T010203Z" in lines)
    }

    @Test
    fun `no-class days, the pause and cancelled classes on the class's weekday become EXDATEs`() {
        val lines = unfolded(IcsExport.build(schedule, emptyList(), now))
        assertEquals(
            listOf("EXDATE;TZID=Asia/Seoul:20260923T100000,20261014T100000,20261104T100000"),
            lines.filter { it.startsWith("EXDATE") },
        )
    }

    @Test
    fun `a pause until turned off skips every remaining class of the term`() {
        val paused = schedule.copy(noClassDates = emptySet(), courseOverrides = emptyList(), guidancePause = GuidancePause.untilDisabled(LocalDate.of(2026, 12, 7)))
        val lines = unfolded(IcsExport.build(paused, emptyList(), now))
        assertEquals(
            listOf("EXDATE;TZID=Asia/Seoul:20261209T100000,20261216T100000"),
            lines.filter { it.startsWith("EXDATE") },
        )
    }

    @Test
    fun `a class whose weekday never occurs in the term is left out`() {
        val short = schedule.copy(termStart = LocalDate.of(2026, 8, 24), termEnd = LocalDate.of(2026, 8, 25))
        assertTrue(IcsExport.exportableCourses(short).isEmpty())
        assertFalse(IcsExport.build(short, emptyList(), now).contains("BEGIN:VEVENT"))
    }

    @Test
    fun `academic periods are all-day events whose DTEND is the day after their last day`() {
        val events = listOf(
            ServiceCalendar.AcademicEvent("성적 열람", LocalDate.of(2026, 12, 28), LocalDate.of(2026, 12, 30)),
            ServiceCalendar.AcademicEvent("개교기념일", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31)),
        )
        val lines = unfolded(IcsExport.build(null, events, now))
        assertFalse(lines.any { it.startsWith("RRULE") })
        assertEquals(
            listOf("DTSTART;VALUE=DATE:20261228", "DTSTART;VALUE=DATE:20261231"),
            lines.filter { it.startsWith("DTSTART;") },
        )
        assertEquals(
            listOf("DTEND;VALUE=DATE:20261231", "DTEND;VALUE=DATE:20270101"),
            lines.filter { it.startsWith("DTEND") },
        )
    }

    @Test
    fun `only academic periods that have not ended are exported, exam weeks included`() {
        val calendar = BuiltInServiceCalendar.calendar
        val names = IcsExport.upcomingAcademicEvents(calendar, LocalDate.of(2026, 12, 29)).map { it.name }
        assertEquals(listOf("성적 열람"), names)
        assertTrue("기말고사" in IcsExport.upcomingAcademicEvents(calendar, LocalDate.of(2026, 10, 26)).map { it.name })
    }

    @Test
    fun `UIDs stay the same across exports and differ between events`() {
        val events = listOf(ServiceCalendar.AcademicEvent("수업평가", LocalDate.of(2026, 12, 14), LocalDate.of(2026, 12, 26)))
        val uids = { at: Instant -> unfolded(IcsExport.build(schedule, events, at)).filter { it.startsWith("UID:") } }
        val first = uids(now)
        assertEquals(first, uids(now.plusSeconds(86_400)))
        assertEquals(2, first.toSet().size)
        assertTrue(first.all { it.endsWith("@dimanow") })
        // An edited class (same id) keeps its UID, so a calendar updates it instead of adding a copy.
        val edited = schedule.copy(courses = listOf(wednesday.copy(name = "디자인 기초 II", start = LocalTime.of(13, 0))))
        assertEquals(first.first(), unfolded(IcsExport.build(edited, events, now)).first { it.startsWith("UID:") })
    }

    @Test
    fun `TEXT values escape backslashes, semicolons, commas and newlines`() {
        assertEquals("a\\\\b\\;c\\,d\\ne\\nf", IcsExport.escape("a\\b;c,d\ne\r\nf"))
        val course = wednesday.copy(name = "기초, 실습; A\\B", professor = "김교수\n박교수")
        val lines = unfolded(IcsExport.build(schedule.copy(courses = listOf(course)), emptyList(), now))
        assertTrue("SUMMARY:기초\\, 실습\\; A\\\\B" in lines)
        assertTrue("DESCRIPTION:김교수\\n박교수" in lines)
    }

    @Test
    fun `long lines fold at 75 octets without splitting a character`() {
        val name = "시각디자인과 융합 프로젝트 스튜디오 🎨 " + "가나다라마바사".repeat(8)
        val course = wednesday.copy(name = name)
        val ics = IcsExport.build(schedule.copy(courses = listOf(course)), emptyList(), now)
        val physical = ics.split("\r\n").dropLast(1)
        assertTrue(physical.all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        assertTrue(physical.count { it.startsWith(" ") } >= 2)
        // No unpaired surrogates: every physical line is well-formed on its own.
        physical.forEach { line -> assertEquals(line, String(line.toByteArray(Charsets.UTF_8), Charsets.UTF_8)) }
        assertTrue("SUMMARY:$name" in unfolded(ics))
        // A line of exactly 75 octets stays whole.
        val exact = "X".repeat(75)
        assertEquals(exact, IcsExport.fold(exact))
        assertEquals("X".repeat(75) + "\r\n " + "X", IcsExport.fold("X".repeat(76)))
    }
}
