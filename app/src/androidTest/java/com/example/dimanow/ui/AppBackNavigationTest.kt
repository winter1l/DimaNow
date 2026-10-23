package com.example.dimanow.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.MainActivity
import com.example.dimanow.guidance.HomeBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppBackNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun backFromAPageUnderAllReturnsToAllThenHomeThenFinishesTheActivity() {
        prepareCompletedOnboarding()

        composeRule.onNodeWithTag("nav_SHUTTLE").performClick()
        composeRule.onNodeWithTag("nav_ALL").performClick()
        composeRule.waitForIdle()
        // D-110: Settings opens from 전체; no screen carries a settings gear.
        composeRule.onNodeWithTag("open_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("all_settings").performClick()
        composeRule.onNodeWithTag("nav_ALL").assertDoesNotExist()
        composeRule.onNodeWithTag("close_settings").assertExists()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithTag("nav_ALL").assertIsSelected()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithTag("nav_DASHBOARD").assertIsSelected()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activityRule.scenario.state == Lifecycle.State.DESTROYED
        }
        assertEquals(Lifecycle.State.DESTROYED, composeRule.activityRule.scenario.state)
    }

    @Test
    fun backClosesTheCurrentDialogBeforeReturningTheTabHome() {
        prepareCompletedOnboarding()

        composeRule.onNodeWithTag("nav_ALL").performClick()
        composeRule.onNodeWithTag("all_timetable").performClick()
        // D-102: a fresh timetable offers 수업 추가 in its empty state; with courses it is the FAB.
        composeRule.onNodeWithTag("add_course").performClick()
        composeRule.onNodeWithText("수업명").assertExists()

        systemBack()

        composeRule.onNodeWithText("수업명").assertDoesNotExist()
        composeRule.onNodeWithTag("close_timetable").assertExists()

        systemBack()
        composeRule.onNodeWithTag("nav_ALL").assertIsSelected()

        systemBack()
        composeRule.onNodeWithTag("nav_DASHBOARD").assertIsSelected()
    }

    private fun systemBack() {
        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun prepareCompletedOnboarding() {
        val preferences = (composeRule.activity.application as DimaNowApplication).preferences
        runBlocking {
            preferences.setHomeBase(HomeBase.YEIN)
            preferences.setNowBarSetupCompleted(true)
        }
        composeRule.waitForIdle()
    }
}
