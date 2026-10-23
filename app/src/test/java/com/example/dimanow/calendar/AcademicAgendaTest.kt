package com.example.dimanow.calendar

import com.example.dimanow.sync.CalendarEvent
import com.example.dimanow.sync.CalendarTerm
import com.example.dimanow.sync.ServiceCalendar
import com.example.dimanow.sync.ServiceCalendarPayload
import com.example.dimanow.sync.serviceCalendarProblem
import com.example.dimanow.ui.academicEventStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-107: Home's 학사 일정 card and the morning reminder read the published 학사일정. */
class AcademicAgendaTest {
    private val calendar = BuiltInServiceCalendar.calendar

    private fun names(today: LocalDate) = calendar.academicAgenda(today).map { it.name }

    @Test
    fun `the agenda holds running periods and those starting within two weeks, exams included`() {
        // 2026-11-26: 지정보강 (12-07) starts in 11 days; 기말고사 (12-14) is 18 days away.
        assertEquals(listOf("지정보강"), names(LocalDate.of(2026, 11, 26)))
        assertEquals(listOf("지정보강", "기말고사", "수업평가"), names(LocalDate.of(2026, 12, 7)))
        // The midterm week shows while it runs.
        assertEquals(listOf("중간고사"), names(LocalDate.of(2026, 10, 21)))
        assertTrue(names(LocalDate.of(2026, 11, 2)).isEmpty())
    }

    @Test
    fun `statuses say how soon a period starts or until when it runs`() {
        val agenda = calendar.academicAgenda(LocalDate.of(2026, 12, 7))
        val makeup = agenda.first { it.name == "지정보강" }
        val finals = agenda.first { it.name == "기말고사" }
        assertEquals("진행 중 · 12월 11일 (금)까지", academicEventStatus(makeup, LocalDate.of(2026, 12, 7)))
        assertEquals("오늘까지", academicEventStatus(makeup, LocalDate.of(2026, 12, 11)))
        assertEquals("D-7", academicEventStatus(finals, LocalDate.of(2026, 12, 7)))
        assertEquals("내일부터", academicEventStatus(finals, LocalDate.of(2026, 12, 13)))
    }

    @Test
    fun `the morning reminder names starts, last days and the eve of an exam week`() {
        fun titles(day: LocalDate) = academicReminders(day, calendar).map { it.title }
        assertEquals(listOf("오늘부터 지정보강"), titles(LocalDate.of(2026, 12, 7)))
        assertEquals(listOf("오늘이 지정보강 마지막 날이에요"), titles(LocalDate.of(2026, 12, 11)))
        assertEquals(listOf("내일부터 기말고사"), titles(LocalDate.of(2026, 12, 13)))
        assertEquals(listOf("오늘부터 기말고사", "오늘부터 수업평가"), titles(LocalDate.of(2026, 12, 14)))
        assertTrue(titles(LocalDate.of(2026, 12, 9)).isEmpty())
    }

    @Test
    fun `a calendar synchronized before exam weeks and events borrows them from the bundled copy`() {
        val older = ServiceCalendar.from(
            ServiceCalendarPayload(terms = listOf(CalendarTerm("2026-2", "2026-08-24", "2026-12-18")), holidays = emptyList()),
        )
        val completed = older.completedFrom(calendar)
        assertEquals(ServiceCalendar.Exam.MIDTERM, completed.examOn(LocalDate.of(2026, 10, 23)))
        assertTrue(completed.academicAgenda(LocalDate.of(2026, 12, 7)).any { it.name == "지정보강" })
        // A term whose dates were changed in the published file keeps the published (empty) exam weeks.
        val moved = ServiceCalendar.from(
            ServiceCalendarPayload(terms = listOf(CalendarTerm("2026-2", "2026-08-31", "2026-12-18")), holidays = emptyList()),
        )
        assertEquals(null, moved.completedFrom(calendar).examOn(LocalDate.of(2026, 10, 23)))
    }

    @Test
    fun `academic events are validated and must be in start order`() {
        val term = CalendarTerm("2026-2", "2026-08-24", "2026-12-18")
        val ok = ServiceCalendarPayload(
            terms = listOf(term),
            holidays = emptyList(),
            academicEvents = listOf(CalendarEvent("수업평가", "2026-12-14", "2026-12-26"), CalendarEvent("성적 열람", "2026-12-28", "2026-12-30")),
        )
        assertEquals(null, serviceCalendarProblem(ok))
        assertNotNull(serviceCalendarProblem(ok.copy(academicEvents = ok.academicEvents.reversed())))
        assertNotNull(serviceCalendarProblem(ok.copy(academicEvents = listOf(CalendarEvent("수업평가", "2026-12-26", "2026-12-14")))))
        assertNotNull(serviceCalendarProblem(ok.copy(academicEvents = listOf(CalendarEvent(" ", "2026-12-14", "2026-12-14")))))
    }
}
