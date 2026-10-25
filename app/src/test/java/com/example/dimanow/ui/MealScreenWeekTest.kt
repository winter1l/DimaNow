package com.example.dimanow.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MealScreenWeekTest {
    @Test
    fun `weekdays show this week from today`() {
        val thursday = LocalDate.parse("2026-10-22")
        assertEquals(LocalDate.parse("2026-10-19") to thursday, mealScreenWeek(thursday))
        val friday = LocalDate.parse("2026-10-23")
        assertEquals(LocalDate.parse("2026-10-19") to friday, mealScreenWeek(friday))
    }

    @Test
    fun `Saturday and Sunday move on to next week's Monday`() {
        val nextMonday = LocalDate.parse("2026-10-26")
        assertEquals(nextMonday to nextMonday, mealScreenWeek(LocalDate.parse("2026-10-24")))
        assertEquals(nextMonday to nextMonday, mealScreenWeek(LocalDate.parse("2026-10-25")))
    }
}
