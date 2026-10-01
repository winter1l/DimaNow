package com.example.dimanow.ui

import androidx.compose.ui.test.performScrollToNode
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.MainActivity
import com.example.dimanow.guidance.HomeBase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/** Exercises the real app shell and captures every main destination on a selected test emulator. */
class AppVisualReviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun allDestinationsAndSettingsRemainReachable() {
        runBlocking {
            (compose.activity.application as DimaNowApplication).preferences.setHomeBase(HomeBase.YEIN)
        }
        compose.waitForIdle()
        primaryAppPages.forEach { page ->
            compose.onNodeWithTag("nav_${page.name}").performClick().assertIsSelected()
            capture(page.name.lowercase())
            if (page == AppPage.TIMETABLE) {
                // D-094: 수업 추가 is the Timetable FAB, expanded at the top of the list.
                compose.onNodeWithTag("add_course").assertContentDescriptionEquals("수업 추가").performClick()
                compose.onNodeWithText("수업명").performClick()
                capture("course-editor-keyboard", dialog = true)
                // Large text and the keyboard may require vertical form scrolling, never hidden horizontal days.
                compose.onNodeWithText("일").performScrollTo().performClick().assertIsSelected()
                capture("course-editor-weekend", dialog = true)
                compose.onNodeWithText("취소").performClick()
            }
            if (page == AppPage.SHUTTLE) {
                compose.onNodeWithText("4402").performClick()
                capture("bus-4402")
            }
        }
        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithTag("close_settings").assertExists()
        capture("settings")
        // Settings is a lazy list: bring the section into composition through the list itself.
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("고급 및 진단"))
        compose.onNodeWithText("고급 및 진단").performClick()
        capture("settings-diagnostics")
        // D-094: the back action is pinned in the top app bar, so it stays reachable after scrolling.
        compose.onNodeWithTag("close_settings").assertIsDisplayed().performClick()
        compose.onNodeWithTag("nav_COURSES").assertIsSelected()
    }

    private fun capture(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val variant = InstrumentationRegistry.getArguments().getString("visualVariant") ?: "phone"
        val directory = File(compose.activity.getExternalFilesDir(null), "m3-review/$variant").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            val root = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
            root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
