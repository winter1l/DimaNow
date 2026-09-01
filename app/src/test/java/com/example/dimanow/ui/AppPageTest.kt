package com.example.dimanow.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPageTest {
    @Test
    fun `only time-sensitive visible pages subscribe to minute ticks`() {
        assertTrue(AppPage.DASHBOARD.usesMinuteTicker)
        assertTrue(AppPage.SHUTTLE.usesMinuteTicker)
        assertTrue(AppPage.MEAL.usesMinuteTicker)
        assertFalse(AppPage.TIMETABLE.usesMinuteTicker)
        assertTrue(AppPage.COURSES.usesMinuteTicker)
        assertFalse(AppPage.SETTINGS.usesMinuteTicker)
    }

    @Test
    fun `bottom navigation contains five primary pages and settings is a separate destination`() {
        // D-044: 기존 4탭 순서를 보존하기 위해 수업은 맨 뒤
        assertEquals(listOf("홈", "시간표", "셔틀", "식단", "수업"), primaryAppPages.map { it.title })
        assertFalse(AppPage.SETTINGS in primaryAppPages)
    }
}
