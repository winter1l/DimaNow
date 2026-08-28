package com.example.dimanow.widget

import java.time.Instant
import java.time.ZonedDateTime

data class WidgetMinuteUpdatePlan(
    val shouldSchedule: Boolean,
    val triggerAt: Instant?,
)

data class WidgetMinuteSurface(
    val widgetCount: Int,
    val requiresMinuteUpdate: Boolean,
)

class SharedWidgetMinutePlanner {
    fun plan(
        now: ZonedDateTime,
        shuttle: WidgetMinuteSurface,
        summary: WidgetMinuteSurface,
    ): WidgetMinuteUpdatePlan = WidgetMinuteUpdatePlanner().plan(
        now = now,
        widgetCount = shuttle.widgetCount + summary.widgetCount,
        hasRemainingService =
            (shuttle.widgetCount > 0 && shuttle.requiresMinuteUpdate) ||
                (summary.widgetCount > 0 && summary.requiresMinuteUpdate),
    )
}

class WidgetMinuteUpdatePlanner {
    fun plan(now: ZonedDateTime, widgetCount: Int, hasRemainingService: Boolean): WidgetMinuteUpdatePlan {
        if (widgetCount <= 0 || !hasRemainingService) return WidgetMinuteUpdatePlan(false, null)
        return WidgetMinuteUpdatePlan(
            shouldSchedule = true,
            triggerAt = now.plusMinutes(1).withSecond(0).withNano(0).toInstant(),
        )
    }
}
