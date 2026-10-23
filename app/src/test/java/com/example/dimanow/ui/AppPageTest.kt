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
    fun `the bottom bar ends with 전체 and the occasional pages open from it`() {
        // D-110: the timetable, 기숙사, 장비 대여 and Settings moved under the Toss-style 전체 tab.
        assertEquals(listOf("홈", "셔틀", "식단", "수업", "전체"), primaryAppPages.map { it.title })
        val subPages = listOf(AppPage.TIMETABLE, AppPage.DORM, AppPage.EQUIPMENT, AppPage.SETTINGS)
        assertTrue(subPages.all { it.isSubPage && it !in primaryAppPages })
        assertTrue(primaryAppPages.none { it.isSubPage })
    }
}
