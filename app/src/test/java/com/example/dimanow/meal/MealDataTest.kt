package com.example.dimanow.meal

import com.example.dimanow.domain.MealDay
import com.example.dimanow.domain.MealValidationState
import java.time.LocalDate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MealDataTest {
    @Test
    fun `meal refresh reference date always follows Korea time`() {
        val clock = Clock.fixed(Instant.parse("2026-08-27T15:30:00Z"), ZoneOffset.UTC)

        assertEquals(LocalDate.parse("2026-08-28"), MealRefreshClock.today(clock))
    }

    @Test
    fun `meal before opening explains when service starts`() {
        val data = MealData(
            days = listOf(mealDay("2026-08-27")),
            lastSuccess = null,
            lastAttempt = null,
            error = null,
            sourceUrl = "https://www.dima.ac.kr/?p=1",
            sourceImageUrl = null,
            hours = "11:30~14:00",
        )

        val status = data.serviceStatusAt(ZonedDateTime.parse("2026-08-27T11:29:00+09:00[Asia/Seoul]"))

        assertEquals(MealServiceState.BEFORE_OPEN, status.state)
        assertEquals("운영 전 · 11:30부터", status.label)
    }

    @Test
    fun `meal at opening time explains when service ends`() {
        val data = MealData(
            days = listOf(mealDay("2026-08-27")),
            lastSuccess = null,
            lastAttempt = null,
            error = null,
            sourceUrl = "https://www.dima.ac.kr/?p=1",
            sourceImageUrl = null,
            hours = "11:30 ~ 14:00",
        )

        val status = data.serviceStatusAt(ZonedDateTime.parse("2026-08-27T11:30:00+09:00[Asia/Seoul]"))

        assertEquals(MealServiceState.OPEN, status.state)
        assertEquals("운영 중 · 14:00까지", status.label)
    }

    @Test
    fun `meal at closing time says todays service is over`() {
        val data = MealData(
            days = listOf(mealDay("2026-08-27")),
            lastSuccess = null,
            lastAttempt = null,
            error = null,
            sourceUrl = "https://www.dima.ac.kr/?p=1",
            sourceImageUrl = null,
            hours = "11:30~14:00",
        )

        val status = data.serviceStatusAt(ZonedDateTime.parse("2026-08-27T14:00:00+09:00[Asia/Seoul]"))

        assertEquals(MealServiceState.CLOSED, status.state)
        assertEquals("운영 종료", status.label)
    }

    @Test
    fun `unrecognized service hours stay truthful without an invented state`() {
        val day = mealDay("2026-08-27").copy(hours = "운영시간은 원문 확인")

        val status = mealServiceStatus(day, java.time.LocalTime.NOON)

        assertEquals(MealServiceState.UNKNOWN_HOURS, status.state)
        assertEquals("운영시간은 원문 확인", status.label)
    }

    @Test
    fun `validated meal cache is grouped by Monday based week ranges`() {
        val data = MealData(
            days = listOf(
                mealDay("2026-08-24"),
                mealDay("2026-08-25"),
                mealDay("2026-08-31"),
            ),
            lastSuccess = null,
            lastAttempt = null,
            error = null,
            sourceUrl = "https://www.dima.ac.kr/?p=1",
            sourceImageUrl = null,
            hours = "11:30~14:00",
        )

        assertEquals(
            listOf(
                MealCachedWeek(LocalDate.parse("2026-08-24"), LocalDate.parse("2026-08-30"), 2),
                MealCachedWeek(LocalDate.parse("2026-08-31"), LocalDate.parse("2026-09-06"), 1),
            ),
            data.cachedWeeks,
        )
    }

    private fun mealDay(date: String) = MealDay(
        date = LocalDate.parse(date),
        menuLines = listOf("김치볶음밥", "미역국"),
        hours = "11:30~14:00",
        sourceUrl = "https://www.dima.ac.kr/?p=1",
        sourceImageUrl = "https://example.invalid/menu.jpg",
        validationState = MealValidationState.VALID,
    )
}
