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
    fun backFromAnyTabReturnsDirectlyHomeThenBackFromHomeFinishesTheActivity() {
        prepareCompletedOnboarding()

        composeRule.onNodeWithTag("nav_SHUTTLE").performClick()
        composeRule.onNodeWithTag("nav_MEAL").performClick()
        composeRule.onNodeWithTag("nav_SETTINGS").performClick()
        composeRule.onNodeWithTag("nav_SETTINGS").assertIsSelected()

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

        composeRule.onNodeWithTag("nav_TIMETABLE").performClick()
        composeRule.onNodeWithText("수업 추가").performClick()
        composeRule.onNodeWithText("수업명").assertExists()

        systemBack()

        composeRule.onNodeWithText("수업명").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_TIMETABLE").assertIsSelected()

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
