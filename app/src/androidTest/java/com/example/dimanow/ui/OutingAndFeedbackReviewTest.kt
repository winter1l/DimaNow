package com.example.dimanow.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.dorm.OutingWarning
import com.example.dimanow.feedback.FeedbackCategory
import com.example.dimanow.feedback.FeedbackResult
import com.example.dimanow.theme.DIMANowTheme
import java.io.File
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** D-119: the outing warnings and the in-app report form, captured for review under `files/d119-review`. */
class OutingAndFeedbackReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theRollCallWarningAsksBeforeOpeningTheApplication() {
        var opened = false
        compose.setContent {
            DIMANowTheme {
                OutingWarningDialog(OutingWarning.AfterRollCallDeadline("21:00 전까지"), now = LocalTime.of(21, 34), onOpen = { opened = true }, onDismiss = {})
            }
        }
        compose.onNodeWithText("오늘은 신청 시간이 지났어요").assertExists()
        capture("outing-rollcall")
        compose.onNodeWithTag("dorm_outing_warning_open").performClick()
        compose.runOnIdle { assertEquals(true, opened) }
    }

    @Test
    fun theOvernightWarningNamesTheDoorTime() {
        compose.setContent {
            DIMANowTheme { OutingWarningDialog(OutingWarning.OvernightClosed, now = LocalTime.of(0, 44), onOpen = {}, onDismiss = {}) }
        }
        compose.onNodeWithText("외박 신청 시간이 지났어요").assertExists()
        capture("outing-overnight")
    }

    @Test
    fun theReportFormSendsAndLinksTheIssue() {
        var sent: FeedbackDraft? = null
        compose.setContent {
            DIMANowTheme {
                FeedbackDialog(
                    onDismiss = {},
                    send = { draft ->
                        sent = draft
                        FeedbackResult.Posted(42, "https://github.com/winter1l/DimaNow/issues/42")
                    },
                    loadLog = { "10-24 00:44:49.123 W/DimaNow( 4120): <토큰>" },
                )
            }
        }
        compose.onNodeWithTag("feedback_message").performTextInput("시간표 가져오기에서 화면이 멈춰요")
        compose.onNodeWithTag("feedback_log_preview").performClick()
        compose.onNodeWithText("10-24 00:44:49.123 W/DimaNow( 4120): <토큰>").assertExists()
        capture("feedback-form")
        compose.onNodeWithTag("feedback_send").performClick()
        compose.onNodeWithTag("feedback_posted").assertExists()
        compose.onNodeWithTag("feedback_issue_url").assertExists()
        capture("feedback-posted")
        compose.runOnIdle {
            assertEquals(FeedbackCategory.BUG, sent?.category)
            assertEquals("10-24 00:44:49.123 W/DimaNow( 4120): <토큰>", sent?.log)
        }
    }

    @Test
    fun aQuestionSendsNoLog() {
        var sent: FeedbackDraft? = null
        compose.setContent {
            DIMANowTheme {
                FeedbackDialog(onDismiss = {}, send = { sent = it; FeedbackResult.Failed }, loadLog = { "log" })
            }
        }
        compose.onNodeWithTag("feedback_category_question").performClick()
        compose.onNodeWithTag("feedback_add_screenshot").assertDoesNotExist()
        compose.onNodeWithTag("feedback_message").performTextInput("셔틀 시간이 궁금해요")
        compose.onNodeWithTag("feedback_send").performClick()
        compose.runOnIdle { assertEquals(null, sent?.log) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "d119-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
