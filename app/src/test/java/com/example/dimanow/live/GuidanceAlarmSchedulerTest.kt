package com.example.dimanow.live

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.TermSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class GuidanceAlarmSchedulerTest {
    @Test
    fun `finished term produces an explicit alarm cancellation plan`() {
        val course = Course(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 50), "조명기초및실습", "덕성관 402", "이용창", CampusZoneId.MAIN)
        val schedule = TermSchedule(
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 8, 28),
            courses = listOf(course),
        )
        val now = ZonedDateTime.of(2026, 8, 29, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"))

        val plan = GuidanceAlarmScheduler.planNext(now, schedule)

        assertEquals(GuidanceAlarmPlan.Cancel, plan)
    }

    @Test
    fun `active class schedules the thirty minute return guidance boundary`() {
        val course = Course(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 50), "조명기초및실습", "덕성관 402", "이용창", CampusZoneId.MAIN)
        val now = ZonedDateTime.of(2026, 8, 31, 10, 10, 0, 0, ZoneId.of("Asia/Seoul"))

        val next = GuidanceAlarmScheduler.calculateNextTrigger(now, listOf(course))

        assertEquals("2026-08-31T10:30+09:00[Asia/Seoul]", next.toString())
    }

    @Test
    fun `next boundary is recalculated in the current time zone`() {
        val course = Course(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 50), "조명기초및실습", "덕성관 402", "이용창", CampusZoneId.MAIN)
        val seoulNow = ZonedDateTime.of(2026, 8, 31, 8, 30, 0, 0, ZoneId.of("Asia/Seoul"))

        val next = GuidanceAlarmScheduler.calculateNextTrigger(seoulNow, listOf(course))

        assertEquals("2026-08-31T09:00+09:00[Asia/Seoul]", next.toString())
    }
}
