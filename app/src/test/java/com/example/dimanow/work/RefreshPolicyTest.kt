package com.example.dimanow.work

import com.example.dimanow.meal.DormitoryMealData
import com.example.dimanow.meal.DormitoryMealDay
import com.example.dimanow.meal.DormitoryMealSection
import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {
    @Test
    fun `dormitory sync waits only when the target week is already cached`() {
        val cached = DormitoryMealData(
            days = listOf(
                DormitoryMealDay(
                    LocalDate.parse("2026-08-31"),
                    listOf(DormitoryMealSection("중식", null, listOf("제육볶음"))),
                    "https://example.invalid/dorm.jpg",
                ),
            ),
            lastSuccess = null,
            lastAttempt = null,
            error = null,
        )

        assertFalse(RefreshPolicy.shouldRefreshDormitory(cached, ZonedDateTime.parse("2026-09-02T09:00:00+09:00[Asia/Seoul]")))
        assertTrue(RefreshPolicy.shouldRefreshDormitory(cached.copy(days = emptyList()), ZonedDateTime.parse("2026-09-02T09:00:00+09:00[Asia/Seoul]")))
    }
}
