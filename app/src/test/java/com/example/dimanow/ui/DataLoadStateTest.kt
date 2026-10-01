package com.example.dimanow.ui

import com.example.dimanow.meal.MealRefreshResult
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.shuttle.ShuttleRefreshResult
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DataLoadStateTest {
    private val at = Instant.parse("2026-09-30T00:00:00Z")
    private val today = LocalDate.parse("2026-09-30") // Wednesday; its week starts 2026-09-28

    @Test
    fun `shuttle pull-to-refresh messages are plain and never expose raw errors`() {
        assertEquals("셔틀 시간표를 새로 받았어요", shuttleRefreshMessage(ShuttleRefreshResult.Success(625, at)))
        assertEquals(
            "셔틀 시간표를 받지 못했어요. 저장된 시간표를 보여 드릴게요",
            shuttleRefreshMessage(ShuttleRefreshResult.Failure("java.net.UnknownHostException: www.dima.ac.kr", 625)),
        )
        assertEquals(
            "셔틀 시간표를 받지 못했어요. 잠시 후 다시 당겨 주세요",
            shuttleRefreshMessage(ShuttleRefreshResult.Failure("HTTP 503", 0)),
        )
    }

    @Test
    fun `meal pull-to-refresh messages describe the week without dates or raw reasons`() {
        assertEquals(
            "이번 주 식단을 새로 받았어요",
            mealRefreshMessage(MealRefreshResult.Success(LocalDate.parse("2026-09-28"), at), MealVenue.MAIN_CAFETERIA, today),
        )
        assertEquals(
            "식단을 새로 받았어요",
            mealRefreshMessage(MealRefreshResult.Success(LocalDate.parse("2026-10-05"), at), MealVenue.MAIN_CAFETERIA, today),
        )
        assertEquals(
            "기숙사 식단을 새로 받았어요",
            mealRefreshMessage(MealRefreshResult.Success(LocalDate.parse("2026-09-28"), at), MealVenue.DORMITORY, today),
        )
        assertEquals("아직 새 식단이 올라오지 않았어요", mealRefreshMessage(MealRefreshResult.NotPublishedYet, MealVenue.MAIN_CAFETERIA, today))
        assertEquals("이미 최신 식단이에요", mealRefreshMessage(null, MealVenue.MAIN_CAFETERIA, today))
        assertEquals(
            "식단을 확인하고 있어요. 잠시 후 다시 확인해 주세요",
            mealRefreshMessage(MealRefreshResult.NeedsReview("OCR confidence 0.41", null), MealVenue.MAIN_CAFETERIA, today),
        )
        val failure = mealRefreshMessage(MealRefreshResult.Failure("SocketTimeoutException: timeout"), MealVenue.DORMITORY, today)
        assertEquals("식단을 받지 못했어요. 잠시 후 다시 당겨 주세요", failure)
        assertFalse(failure.contains("Exception"))
    }

    @Test
    fun `home shuttle empty state points to the shuttle tab instead of asking to refresh here`() {
        val empty = ShuttleData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=97", null)
        assertEquals("셔틀 시간표가 아직 없어요. 셔틀 탭에서 아래로 당겨 받아 보세요", homeShuttleUnavailableMessage(empty))
        assertEquals(
            "셔틀 시간표를 받지 못했어요. 셔틀 탭에서 아래로 당겨 다시 받아 보세요",
            homeShuttleUnavailableMessage(empty.copy(error = "HTTP 503", lastAttempt = at)),
        )
        assertFalse(homeShuttleUnavailableMessage(empty).contains("새로고침"))
    }
}
