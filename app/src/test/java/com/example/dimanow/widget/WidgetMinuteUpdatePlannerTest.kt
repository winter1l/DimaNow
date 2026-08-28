package com.example.dimanow.widget

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMinuteUpdatePlannerTest {
    @Test
    fun `shuttle and summary widgets share one next-minute alarm plan`() {
        val now = ZonedDateTime.of(2026, 8, 27, 18, 50, 41, 0, ZoneId.of("Asia/Seoul"))

        val plan = SharedWidgetMinutePlanner().plan(
            now = now,
            shuttle = WidgetMinuteSurface(widgetCount = 1, requiresMinuteUpdate = true),
            summary = WidgetMinuteSurface(widgetCount = 1, requiresMinuteUpdate = true),
        )

        assertTrue(plan.shouldSchedule)
        assertEquals(ZonedDateTime.of(2026, 8, 27, 18, 51, 0, 0, now.zone).toInstant(), plan.triggerAt)
    }

    @Test
    fun `active widget schedules the exact next minute boundary`() {
        val now = ZonedDateTime.of(2026, 8, 27, 18, 50, 41, 250_000_000, ZoneId.of("Asia/Seoul"))

        val plan = WidgetMinuteUpdatePlanner().plan(now, widgetCount = 1, hasRemainingService = true)

        assertTrue(plan.shouldSchedule)
        assertEquals(ZonedDateTime.of(2026, 8, 27, 18, 51, 0, 0, now.zone).toInstant(), plan.triggerAt)
    }

    @Test
    fun `no widget or no remaining service cancels the minute alarm`() {
        val now = ZonedDateTime.of(2026, 8, 27, 23, 0, 0, 0, ZoneId.of("Asia/Seoul"))

        assertFalse(WidgetMinuteUpdatePlanner().plan(now, 0, true).shouldSchedule)
        assertFalse(WidgetMinuteUpdatePlanner().plan(now, 1, false).shouldSchedule)
    }
}
