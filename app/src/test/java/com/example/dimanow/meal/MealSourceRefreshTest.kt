package com.example.dimanow.meal

import com.example.dimanow.domain.MealDay
import com.example.dimanow.domain.MealValidationState
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealSourceRefreshTest {
    @Test
    fun `missing Monday meal is checked after thirty minutes instead of waiting twelve hours`() = runTest {
        val source = MemoryMealSource("2026-09-07T00:07:00Z") // Monday 09:07 KST
        assertEquals(Instant.parse("2026-09-07T00:37:00Z"), source.nextBackgroundCheckAt(Instant.parse("2026-09-07T00:08:00Z")))
        assertNull(source.refreshIfDue(MealRefreshTrigger.PUBLICATION_WATCH, Instant.parse("2026-09-07T00:36:59Z")))
        source.refreshIfDue(MealRefreshTrigger.PUBLICATION_WATCH, Instant.parse("2026-09-07T00:37:00Z"))
        assertEquals(1, source.requests)
    }

    private class MemoryMealSource(lastAttempt: String) : MealSource {
        override val data = MutableStateFlow(MealData(emptyList(), null, Instant.parse(lastAttempt), null, OFFICIAL_MEAL_SOURCE_URL, null, null))
        var requests = 0
        override suspend fun refresh(): MealRefreshResult {
            requests++
            return MealRefreshResult.NotPublishedYet
        }
    }

    @Test
    fun `afternoon and other days wait two hours but Monday morning starts at nine`() = runTest {
        val afternoon = MemoryMealSource("2026-09-07T05:07:00Z")
        assertEquals(Instant.parse("2026-09-07T07:07:00Z"), afternoon.nextBackgroundCheckAt(Instant.parse("2026-09-07T05:08:00Z")))
        val tuesday = MemoryMealSource("2026-09-08T00:07:00Z")
        assertEquals(Instant.parse("2026-09-08T02:07:00Z"), tuesday.nextBackgroundCheckAt(Instant.parse("2026-09-08T00:08:00Z")))
        val beforeOpening = MemoryMealSource("2026-09-06T23:45:00Z")
        assertEquals(Instant.parse("2026-09-07T00:00:00Z"), beforeOpening.nextBackgroundCheckAt(Instant.parse("2026-09-06T23:50:00Z")))
    }

    @Test
    fun `complete current week stops publication polling until next Monday but permits foreground corrections`() = runTest {
        val source = MemoryMealSource("2026-09-07T00:07:00Z")
        source.data.value = source.data.value.copy(days = (0L..4L).map { offset ->
            MealDay(LocalDate.parse("2026-09-07").plusDays(offset), listOf("밥", "국"), "11:00 ~ 14:00", OFFICIAL_MEAL_SOURCE_URL, "https://example.com/menu.jpg", MealValidationState.VALID)
        })
        assertEquals(Instant.parse("2026-09-14T00:00:00Z"), source.nextBackgroundCheckAt(Instant.parse("2026-09-07T00:40:00Z")))
        assertNull(source.refreshIfDue(MealRefreshTrigger.PUBLICATION_WATCH, Instant.parse("2026-09-07T00:40:00Z")))
        source.refreshIfDue(MealRefreshTrigger.FOREGROUND, Instant.parse("2026-09-07T00:40:00Z"))
        assertEquals(1, source.requests)
        // KST has entered the next week even though UTC still says Sunday.
        source.refreshIfDue(MealRefreshTrigger.PUBLICATION_WATCH, Instant.parse("2026-09-13T15:01:00Z"))
        assertEquals(2, source.requests)
    }

    @Test
    fun `foreground has a fifteen minute quiet period while manual refresh is immediate`() = runTest {
        val source = MemoryMealSource("2026-09-07T00:07:00Z")
        assertNull(source.refreshIfDue(MealRefreshTrigger.FOREGROUND, Instant.parse("2026-09-07T00:21:59Z")))
        source.refreshIfDue(MealRefreshTrigger.FOREGROUND, Instant.parse("2026-09-07T00:22:00Z"))
        source.refreshIfDue(MealRefreshTrigger.MANUAL, Instant.parse("2026-09-07T00:07:01Z"))
        assertEquals(2, source.requests)
    }
}
