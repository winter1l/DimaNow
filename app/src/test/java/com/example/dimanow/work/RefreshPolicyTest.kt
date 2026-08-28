package com.example.dimanow.work

import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.meal.MealData
import com.example.dimanow.notice.NoticeData
import com.example.dimanow.domain.CampusNotice
import java.time.LocalDate
import com.example.dimanow.domain.MealDay
import com.example.dimanow.domain.MealValidationState
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.ShuttleDeparture
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Instant
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {
    @Test
    fun `shuttle cache refreshed this KST week is not downloaded again`() {
        val data = ShuttleData(
            departures = listOf(
                ShuttleDeparture(
                    sourceRouteId = "A",
                    sourceStopId = "headquarters",
                    direction = "TO_YEIN",
                    serviceDay = DayOfWeek.THURSDAY,
                    time = LocalTime.of(10, 30),
                    originZone = CampusZoneId.MAIN,
                    destinationZone = CampusZoneId.YEIN,
                ),
            ),
            lastSuccess = Instant.parse("2026-08-24T00:00:00Z"),
            lastAttempt = Instant.parse("2026-08-24T00:00:00Z"),
            error = null,
            sourceUrl = "https://www.dima.ac.kr/?p=97",
            noticeUrl = null,
        )

        assertFalse(
            RefreshPolicy.shouldRefreshShuttle(
                data = data,
                now = ZonedDateTime.parse("2026-08-27T10:00:00+09:00[Asia/Seoul]"),
            ),
        )
    }

    @Test
    fun `shuttle cache from the previous KST week is refreshed`() {
        val data = ShuttleData(
            departures = listOf(
                ShuttleDeparture(
                    sourceRouteId = "A",
                    sourceStopId = "headquarters",
                    direction = "TO_YEIN",
                    serviceDay = DayOfWeek.MONDAY,
                    time = LocalTime.of(8, 30),
                    originZone = CampusZoneId.MAIN,
                    destinationZone = CampusZoneId.YEIN,
                ),
            ),
            lastSuccess = Instant.parse("2026-08-23T14:59:59Z"),
            lastAttempt = Instant.parse("2026-08-23T14:59:59Z"),
            error = null,
            sourceUrl = "https://www.dima.ac.kr/?p=97",
            noticeUrl = null,
        )

        assertTrue(
            RefreshPolicy.shouldRefreshShuttle(
                data = data,
                now = ZonedDateTime.parse("2026-08-24T00:00:00+09:00[Asia/Seoul]"),
            ),
        )
    }

    @Test
    fun `meal refresh starts after every validated menu date has passed`() {
        val data = MealData(
            days = listOf(
                MealDay(
                    date = java.time.LocalDate.parse("2026-08-28"),
                    menuLines = listOf("제육볶음"),
                    hours = "11:30~14:00",
                    sourceUrl = "https://www.dima.ac.kr/?p=1",
                    sourceImageUrl = "https://example.invalid/menu.jpg",
                    validationState = MealValidationState.VALID,
                ),
            ),
            lastSuccess = Instant.parse("2026-08-28T05:00:00Z"),
            lastAttempt = Instant.parse("2026-08-28T05:00:00Z"),
            error = null,
            sourceUrl = "https://www.dima.ac.kr/?p=1",
            sourceImageUrl = null,
            hours = "11:30~14:00",
        )

        assertTrue(
            RefreshPolicy.shouldRefreshMeal(
                data = data,
                now = ZonedDateTime.parse("2026-08-29T08:00:00+09:00[Asia/Seoul]"),
            ),
        )
    }

    @Test
    fun `meal refresh stays idle while a validated menu date remains`() {
        val data = MealData(
            days = listOf(
                MealDay(
                    date = java.time.LocalDate.parse("2026-08-28"),
                    menuLines = listOf("제육볶음"),
                    hours = "11:30~14:00",
                    sourceUrl = "https://www.dima.ac.kr/?p=1",
                    sourceImageUrl = "https://example.invalid/menu.jpg",
                    validationState = MealValidationState.VALID,
                ),
            ),
            lastSuccess = Instant.parse("2026-08-24T05:00:00Z"),
            lastAttempt = Instant.parse("2026-08-24T05:00:00Z"),
            error = null,
            sourceUrl = "https://www.dima.ac.kr/?p=1",
            sourceImageUrl = null,
            hours = "11:30~14:00",
        )

        assertFalse(
            RefreshPolicy.shouldRefreshMeal(
                data = data,
                now = ZonedDateTime.parse("2026-08-27T23:59:00+09:00[Asia/Seoul]"),
            ),
        )
    }

    @Test
    fun `notice cache refreshed today KST is not downloaded again`() {
        val data = NoticeData(
            notices = listOf(
                CampusNotice(
                    id = "2608271512340001",
                    title = "2026-2학기 수강신청 정정 기간 안내",
                    url = "https://www.dima.ac.kr/?p=111&page=1&viewMode=view&reqIdx=2608271512340001",
                    date = LocalDate.of(2026, 8, 27),
                    isPinned = false,
                ),
            ),
            lastSuccess = Instant.parse("2026-08-28T00:30:00Z"),
            lastAttempt = Instant.parse("2026-08-28T00:30:00Z"),
            error = null,
            sourceUrl = "https://www.dima.ac.kr/?p=111",
        )

        assertFalse(
            RefreshPolicy.shouldRefreshNotices(
                data = data,
                now = ZonedDateTime.parse("2026-08-28T21:00:00+09:00[Asia/Seoul]"),
            ),
        )
    }

    @Test
    fun `empty or yesterday-success notice cache is refreshed`() {
        val empty = NoticeData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=111")
        assertTrue(
            RefreshPolicy.shouldRefreshNotices(
                data = empty,
                now = ZonedDateTime.parse("2026-08-28T09:00:00+09:00[Asia/Seoul]"),
            ),
        )

        val stale = empty.copy(
            notices = listOf(
                CampusNotice(
                    id = "2608261751206706",
                    title = "학내 Wi-Fi 구축 관련 사용 안내",
                    url = "https://www.dima.ac.kr/?p=111&page=1&viewMode=view&reqIdx=2608261751206706",
                    date = LocalDate.of(2026, 8, 26),
                    isPinned = true,
                ),
            ),
            lastSuccess = Instant.parse("2026-08-27T10:00:00Z"),
            lastAttempt = Instant.parse("2026-08-27T10:00:00Z"),
        )
        assertTrue(
            RefreshPolicy.shouldRefreshNotices(
                data = stale,
                now = ZonedDateTime.parse("2026-08-28T09:00:00+09:00[Asia/Seoul]"),
            ),
        )
    }
}
