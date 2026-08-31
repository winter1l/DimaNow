package com.example.dimanow.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.MainActivity
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.lms.LmsSessionState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class LmsTabTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun classesIsAFirstClassTabAndShowsTheEncryptedCredentialLoginForm() {
        val app = composeRule.activity.application as DimaNowApplication
        runBlocking {
            app.preferences.setHomeBase(HomeBase.YEIN)
            app.preferences.setNowBarSetupCompleted(true)
            app.lmsCredentialStore.delete()
            app.lmsSource.clearPrivateData()
            app.lmsSessionController.transition(LmsSessionState.SIGNED_OUT)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_COURSES").performClick()

        composeRule.onNodeWithText("학번").assertExists()
        composeRule.onNodeWithText("비밀번호").assertExists()
        composeRule.onNodeWithText("이 기기에 암호화해 저장합니다").assertExists()
        composeRule.onNodeWithTag("nav_SETTINGS").assertDoesNotExist()
    }
}
