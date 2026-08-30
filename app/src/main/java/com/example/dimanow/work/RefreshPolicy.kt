package com.example.dimanow.work

import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.meal.MealData
import com.example.dimanow.meal.DormitoryMealData
import com.example.dimanow.notice.NoticeData
import com.example.dimanow.domain.MealValidationState
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object RefreshPolicy {
    fun shouldRefreshDormitory(data: DormitoryMealData, now: ZonedDateTime): Boolean =
        !data.hasCurrentWeek(now.toLocalDate())

    fun shouldRefreshShuttle(data: ShuttleData, now: ZonedDateTime): Boolean {
        if (data.departures.isEmpty()) return true
        val lastSuccess = data.lastSuccess ?: return true
        val weekStart = now.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(now.zone)
            .toInstant()
        return lastSuccess.isBefore(weekStart)
    }

    fun shouldRefreshNotices(data: NoticeData, now: ZonedDateTime): Boolean {
        if (data.notices.isEmpty()) return true
        val lastSuccess = data.lastSuccess ?: return true
        val todayStart = now.toLocalDate().atStartOfDay(now.zone).toInstant()
        return lastSuccess.isBefore(todayStart)
    }

    fun shouldRefreshMeal(data: MealData, now: ZonedDateTime): Boolean {
        val lastValidatedDate = data.days
            .asSequence()
            .filter { it.validationState == MealValidationState.VALID }
            .maxOfOrNull { it.date }
            ?: return true
        return lastValidatedDate.isBefore(now.toLocalDate())
    }
}
