package com.example.dimanow.meal

import com.example.dimanow.domain.MealValidationState
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class MealRefreshTrigger { FOREGROUND, PUBLICATION_WATCH, MANUAL }

fun MealData.hasCurrentStudentWeek(today: LocalDate): Boolean {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val validDates = days.filter { it.validationState == MealValidationState.VALID }.map { it.date }.toSet()
    return (0L..4L).all { monday.plusDays(it) in validDates }
}

internal object MealRefreshPolicy {
    private val korea = ZoneId.of("Asia/Seoul")

    fun nextBackgroundCheckAt(data: MealData, now: Instant): Instant {
        val local = now.atZone(korea)
        val nextMonday = local.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            .atTime(9, 0).atZone(korea).toInstant()
        if (data.hasCurrentStudentWeek(local.toLocalDate())) return nextMonday
        val interval = if (local.dayOfWeek == DayOfWeek.MONDAY && local.hour in 9..13) 30L else 120L
        val due = data.lastAttempt?.plus(Duration.ofMinutes(interval)) ?: now
        val mondayOpening = if (local.dayOfWeek == DayOfWeek.MONDAY && local.hour < 9) {
            local.toLocalDate().atTime(9, 0).atZone(korea).toInstant()
        } else nextMonday
        return minOf(due, mondayOpening).coerceAtLeast(now)
    }

    fun shouldRefresh(data: MealData, trigger: MealRefreshTrigger, now: Instant): Boolean = when (trigger) {
        MealRefreshTrigger.MANUAL -> true
        MealRefreshTrigger.FOREGROUND -> data.lastAttempt?.let { Duration.between(it, now) >= Duration.ofMinutes(15) || it > now } ?: true
        MealRefreshTrigger.PUBLICATION_WATCH -> !data.hasCurrentStudentWeek(now.atZone(korea).toLocalDate()) && nextBackgroundCheckAt(data, now) <= now
    }
}
