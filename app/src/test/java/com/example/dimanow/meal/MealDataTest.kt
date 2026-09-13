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
    fun `home never revives ended meals or guesses missing service hours`() {
        val day = DormitoryMealDay(LocalDate.parse("2026-09-09"), listOf(
            DormitoryMealSection("조식", "08:00~09:30", listOf("떡국")),
            DormitoryMealSection("중식", "시간 확인 필요", listOf("불고기")),
            DormitoryMealSection("석식", "25:00~26:00", listOf("미역국")),
        ), "")
        val data = DormitoryMealData(listOf(day), null, null, null)

        assertEquals(null, data.homeServiceAt(ZonedDateTime.parse("2026-09-09T09:30:00+09:00")))
        assertEquals(null, data.copy(days = emptyList()).homeServiceAt(ZonedDateTime.parse("2026-09-09T08:30:00+09:00")))
    }

    @Test
    fun `home retains simultaneous services and chooses using campus local time`() {
        val breakfast = DormitoryMealSection("조식", "08:00~09:30", listOf("떡국"))
        val alternative = DormitoryMealSection("간편식", "08:00~10:00", listOf("우유"))
        val lunch = DormitoryMealSection("중식", "12:00~14:00", listOf("미역국"))
        val day = DormitoryMealDay(LocalDate.parse("2026-09-10"), listOf(breakfast, alternative, lunch), "")
        val data = DormitoryMealData(listOf(day), null, null, null)

        assertEquals(listOf(breakfast, alternative), data.homeServiceAt(ZonedDateTime.parse("2026-09-09T23:30:00Z"))?.sections)
        assertEquals(listOf(breakfast, alternative), data.homeServiceAt(ZonedDateTime.parse("2026-09-10T07:59:00+09:00"))?.sections)
        assertEquals(listOf(alternative), data.homeServiceAt(ZonedDateTime.parse("2026-09-10T09:30:00+09:00"))?.sections)
    }

    @Test
    fun `home advances to the next scheduled meal at closing and then to the next date`() {
        val lunch = DormitoryMealSection("중식", "12:00~14:00", listOf("미역국"))
        val dinner = DormitoryMealSection("석식", "18:00~19:30", listOf("불고기"))
        val breakfast = DormitoryMealSection("조식", "08:00~09:30", listOf("떡국"))
        val today = DormitoryMealDay(LocalDate.parse("2026-09-09"), listOf(dinner, lunch), "")
        val tomorrow = DormitoryMealDay(today.date.plusDays(1), listOf(breakfast), "")
        val data = DormitoryMealData(listOf(tomorrow, today), null, null, null)

        assertEquals(listOf(dinner), data.homeServiceAt(ZonedDateTime.parse("2026-09-09T14:00:00+09:00"))?.sections)
        assertEquals(tomorrow, data.homeServiceAt(ZonedDateTime.parse("2026-09-09T19:30:00+09:00")))
    }

    @Test
    fun `home shows only the open meal and its accompanying corners`() {
        val breakfast = DormitoryMealSection("조식", "08:00~09:30", listOf("아침국"))
        val lunch = DormitoryMealSection("중식", "12:00~14:00", listOf("미역국"))
        val ramen = DormitoryMealSection("라면", null, listOf("라면과 계란"))
        val dinner = DormitoryMealSection("석식", "18:00~19:30", listOf("불고기"))
        val day = DormitoryMealDay(LocalDate.parse("2026-09-09"), listOf(breakfast, lunch, ramen, dinner), "")
        val data = DormitoryMealData(listOf(day), null, null, null)

        assertEquals(listOf(lunch, ramen), data.homeServiceAt(ZonedDateTime.parse("2026-09-09T12:30:00+09:00"))?.sections)
    }

    @Test
    fun `dormitory service distinguishes opening closing and other dates in campus time`() {
        val lunch = DormitoryMealSection("중식", "12:00~14:00", listOf("미역국"))
        val date = LocalDate.parse("2026-09-08")
        assertEquals(MealServiceState.BEFORE_OPEN, lunch.serviceStatusAt(date, ZonedDateTime.parse("2026-09-08T11:59:00+09:00")).state)
        assertEquals(MealServiceState.OPEN, lunch.serviceStatusAt(date, ZonedDateTime.parse("2026-09-08T03:00:00Z")).state)
        assertEquals(MealServiceState.CLOSED, lunch.serviceStatusAt(date, ZonedDateTime.parse("2026-09-08T14:00:00+09:00")).state)
        assertEquals(MealServiceState.BEFORE_OPEN, lunch.serviceStatusAt(date.plusDays(1), ZonedDateTime.parse("2026-09-08T13:00:00+09:00")).state)
        assertEquals(MealServiceState.CLOSED, lunch.serviceStatusAt(date.minusDays(1), ZonedDateTime.parse("2026-09-08T13:00:00+09:00")).state)
    }

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
