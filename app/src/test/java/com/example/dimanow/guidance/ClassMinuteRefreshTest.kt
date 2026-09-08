package com.example.dimanow.guidance

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.live.LiveDeliveryPlanner
import com.example.dimanow.live.NotificationGuidanceMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassMinuteRefreshTest {
    @Test
    fun `the last minute before class remains visible between minute boundaries`() {
        val now = ZonedDateTime.parse("2026-09-08T09:59:00.100+09:00[Asia/Seoul]")
        val snapshot = GuidanceEngine().snapshot(
            now, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 18),
            listOf(Course(now.dayOfWeek, LocalTime.of(10, 0), LocalTime.of(11, 50),
                "수업", "덕성관 402", "교수", CampusZoneId.MAIN)),
            emptySet(), CampusZoneId.MAIN, true,
        )

        assertEquals("시작까지 1분", snapshot.classContent?.remainingText)
        assertTrue(snapshot.requiresMinuteUpdates)
    }

    @Test
    fun `class remaining text requests minute updates while the system timer is running`() {
        val now = ZonedDateTime.parse("2026-09-08T09:18:00+09:00[Asia/Seoul]")
        val engine = GuidanceEngine()
        val courses = listOf(
            Course(now.dayOfWeek, LocalTime.of(10, 0), LocalTime.of(11, 50),
                "수업", "덕성관 402", "교수", CampusZoneId.MAIN),
        )
        val before = engine.snapshot(
            now, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 18),
            courses, emptySet(), CampusZoneId.MAIN, true,
        )

        assertEquals("시작까지 42분", before.classContent?.remainingText)
        assertTrue(
            "The displayed class text needs the minute updater even when Android animates the timer",
            LiveDeliveryPlanner.plan(NotificationGuidanceMode.LIVE_UPDATE, before.requiresMinuteUpdates).startMinuteUpdater,
        )

        val after = engine.snapshot(
            now.plusMinutes(1), LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 18),
            courses, emptySet(), CampusZoneId.MAIN, true,
        )
        assertEquals("시작까지 41분", after.classContent?.remainingText)
        assertEquals(before.countdownTarget, after.countdownTarget)
    }
}
