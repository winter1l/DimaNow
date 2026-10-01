package com.example.dimanow.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.MainActivity
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.time.MinuteTicker
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** D-094(12): switching bottom tabs keeps each tab's scroll position and screen-local choices. */
class TabStateRestorationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeListScrollPositionSurvivesATripToShuttleAndBack() {
        prepareCompletedOnboarding()
        waitForHomeData()

        val homeList = composeRule.onNodeWithTag("home_list")
        homeList.performScrollToIndex(1)
        // A slow drag (no fling) leaves the list part-way into an item, so the offset is checked too.
        homeList.performTouchInput {
            swipeUp(startY = centerY, endY = centerY - 120f, durationMillis = 600)
        }
        composeRule.waitForIdle()
        val before = homeScrollPosition()
        assertTrue("the Home list did not scroll: $before", before > 0f)
        saveScreenshotIfRequested("before")

        composeRule.onNodeWithTag("nav_SHUTTLE").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shuttle_list").assertExists()
        composeRule.onNodeWithTag("home_list").assertDoesNotExist()

        composeRule.onNodeWithTag("nav_DASHBOARD").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_DASHBOARD").assertIsSelected()
        assertEquals("Home first visible item index/offset after the round trip", before, homeScrollPosition())
        saveScreenshotIfRequested("after")
    }

    @Test
    fun selectedShuttleDaySurvivesATripToHomeAndBack() {
        prepareCompletedOnboarding()

        composeRule.onNodeWithTag("nav_SHUTTLE").performClick()
        composeRule.waitForIdle()
        val today = LocalDate.now(MinuteTicker.CAMPUS_ZONE).dayOfWeek
        val other = if (today == DayOfWeek.FRIDAY) DayOfWeek.TUESDAY else DayOfWeek.FRIDAY
        composeRule.onNodeWithTag("shuttle_day_${other.name}").performClick().assertIsSelected()
        composeRule.onNodeWithTag("shuttle_day_${today.name}").assertIsNotSelected()

        composeRule.onNodeWithTag("nav_DASHBOARD").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shuttle_list").assertDoesNotExist()

        composeRule.onNodeWithTag("nav_SHUTTLE").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shuttle_day_${other.name}").assertIsSelected()
        composeRule.onNodeWithTag("shuttle_day_${today.name}").assertIsNotSelected()
    }

    /** Lazy-list scroll semantics encode the first visible item index and its offset. */
    private fun homeScrollPosition(): Float =
        composeRule.onNodeWithTag("home_list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun waitForHomeData() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTagCount("dashboard_shuttle_loading") == 0 &&
                composeRule.onAllNodesWithTagCount("dashboard_meal_loading") == 0
        }
        composeRule.waitForIdle()
    }

    private fun saveScreenshotIfRequested(name: String) {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        if (args.getString("tabStateScreenshots") != "true") return
        val output = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/tab_state_$name.png")
        // Reading to the end waits for screencap to finish writing the file.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
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

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String): Int =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
