package com.example.dimanow.calendar

import com.example.dimanow.sync.CalendarPeriod
import com.example.dimanow.sync.CalendarTerm
import com.example.dimanow.sync.ServiceCalendar
import com.example.dimanow.sync.ServiceCalendarPayload
import com.example.dimanow.sync.serviceCalendarProblem
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** D-106: Home's title shows the semester week, exam weeks and the vacation countdown. */
class TermHeadlineTest {
    private val calendar = BuiltInServiceCalendar.calendar
    private val fallStart = LocalDate.of(2026, 8, 24)
    private val fallEnd = LocalDate.of(2026, 12, 18)

    private fun label(today: LocalDate, start: LocalDate = fallStart, end: LocalDate = fallEnd) =
        termHeadline(today, start, end, calendar).label

    @Test
    fun `weeks count from the Monday of the first week`() {
        assertEquals("1주차", label(LocalDate.of(2026, 8, 24)))
        assertEquals("1주차", label(LocalDate.of(2026, 8, 30)))
        assertEquals("2주차", label(LocalDate.of(2026, 8, 31)))
        assertEquals("9주차", label(LocalDate.of(2026, 10, 22)).substringBefore(" ·"))
        // A term starting mid-week still has that week as week 1.
        assertEquals("2주차", label(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 3), LocalDate.of(2026, 6, 22)))
    }

    @Test
    fun `exam weeks from the intranet calendar are named`() {
        assertEquals("9주차 · 중간고사", label(LocalDate.of(2026, 10, 19)))
        assertEquals("9주차 · 중간고사", label(LocalDate.of(2026, 10, 23)))
        assertEquals("9주차", label(LocalDate.of(2026, 10, 24)))
        assertEquals("17주차 · 기말고사", label(LocalDate.of(2026, 12, 14)))
        assertEquals("17주차 · 기말고사", label(LocalDate.of(2026, 12, 18)))
        assertEquals(
            "8주차 · 중간고사",
            label(LocalDate.of(2026, 4, 21), LocalDate.of(2026, 3, 3), LocalDate.of(2026, 6, 22)),
        )
    }

    @Test
    fun `between terms Home counts down to the next start`() {
        assertEquals("방학 · 개강까지 3일", label(LocalDate.of(2026, 8, 21)))
        assertEquals("방학 · 내일 개강", label(LocalDate.of(2026, 8, 23)))
        // After the user's term ends, the published calendar gives the next start; none is published after 2026-2.
        assertEquals("방학", label(LocalDate.of(2026, 12, 21)))
        assertEquals(
            "방학 · 개강까지 62일",
            label(LocalDate.of(2026, 6, 22).plusDays(1), LocalDate.of(2026, 3, 3), LocalDate.of(2026, 6, 22)),
        )
    }

    @Test
    fun `exam periods are optional and must sit inside their term`() {
        val term = CalendarTerm("2026-2", "2026-08-24", "2026-12-18")
        val payload = ServiceCalendarPayload(terms = listOf(term), holidays = emptyList())
        assertNull(serviceCalendarProblem(payload))
        val outside = payload.copy(terms = listOf(term.copy(finalExam = CalendarPeriod("2026-12-14", "2026-12-21"))))
        assertNotNull(serviceCalendarProblem(outside))
        val tooLong = payload.copy(terms = listOf(term.copy(midterm = CalendarPeriod("2026-09-01", "2026-10-23"))))
        assertNotNull(serviceCalendarProblem(tooLong))
        // The JSON key is `final`, and an app that predates exam weeks reads the file unchanged.
        val decoded = Json.decodeFromString<ServiceCalendarPayload>(
            """{"terms":[{"id":"2026-2","start":"2026-08-24","end":"2026-12-18","final":{"start":"2026-12-14","end":"2026-12-18"}}],"holidays":[]}""",
        )
        assertEquals(ServiceCalendar.Exam.FINAL, ServiceCalendar.from(decoded).examOn(LocalDate.of(2026, 12, 15)))
    }
}
