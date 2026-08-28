package com.example.dimanow.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPageTest {
    @Test
    fun `only time-sensitive visible pages subscribe to minute ticks`() {
        assertTrue(AppPage.DASHBOARD.usesMinuteTicker)
        assertTrue(AppPage.SHUTTLE.usesMinuteTicker)
        assertTrue(AppPage.MEAL.usesMinuteTicker)
        assertFalse(AppPage.TIMETABLE.usesMinuteTicker)
        assertFalse(AppPage.SETTINGS.usesMinuteTicker)
    }
}
