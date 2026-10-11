package com.example.dimanow.widget

import com.example.dimanow.domain.MealDay
import com.example.dimanow.domain.MealValidationState
import com.example.dimanow.meal.mealServiceStatus
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealWidgetContentPlannerTest {
    private val meal = MealDay(
        date = LocalDate.of(2026, 10, 12),
        menuLines = listOf("제육볶음", "잡곡밥", "미역국"),
        hours = "11:00 ~ 14:00",
        sourceUrl = "https://www.dima.ac.kr/?p=1",
        sourceImageUrl = "https://instagram.com/sample.jpg",
        validationState = MealValidationState.VALID,
    )

    @Test
    fun `before opening the header shows only the start time but the spoken summary keeps the status`() {
        val content = MealWidgetContentPlanner().plan(meal, mealServiceStatus(meal, LocalTime.of(9, 0)), isWeekend = false, refreshFailed = false)

        assertEquals("11:00부터", content.hoursLabel)
        assertEquals("제육볶음\n잡곡밥\n미역국", content.menuText)
        assertNull(content.stateText)
        assertEquals("학생식당. 운영 전 · 11:00부터. 제육볶음, 잡곡밥, 미역국", content.spokenSummary)
    }

    @Test
    fun `a day without a menu says so once in plain wording`() {
        val content = MealWidgetContentPlanner().plan(null, mealServiceStatus(null, LocalTime.of(12, 0)), isWeekend = true, refreshFailed = true)

        assertEquals("", content.hoursLabel)
        assertEquals("주말에는 학생식당을 쉬어요", content.menuText)
        assertEquals("식단을 새로 받지 못했어요", content.stateText)
        assertEquals("학생식당. 주말에는 학생식당을 쉬어요. 식단을 새로 받지 못했어요", content.spokenSummary)
    }
}
