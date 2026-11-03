package com.example.dimanow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.dimanow.live.NotificationGuidancePolicy
import com.example.dimanow.live.NowBarSupport
import com.example.dimanow.theme.DIMANowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** D-143: the developer-option steps are a fallback reached from 설정, not a first-run item. */
class NowBarGuideTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun firstRunChecklistHasNoNowBarRowOnSamsung() {
        composeRule.setContent {
            DIMANowTheme {
                GuidanceSetupContent(
                    state = GuidanceSetupState(samsungNowBar = true),
                    onAction = {},
                    onlyIncomplete = true,
                )
            }
        }
        composeRule.onNodeWithTag("guidance_setup_NOW_BAR").assertDoesNotExist()
    }

    @Test
    fun settingsOfferTheFallbackGuideOnSamsungWithNowBarStillSelectable() {
        var guides = 0
        composeRule.setContent {
            DIMANowTheme {
                Box(Modifier.verticalScroll(rememberScrollState())) {
                    NotificationGuidanceSettings(
                        policy = NotificationGuidancePolicy(),
                        onModeChange = { _, _ -> },
                        showNowBarHelp = true,
                        onShowNowBarGuide = { guides++ },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("notification_mode_CLASS_LIVE_UPDATE").assertIsDisplayed()
        composeRule.onNodeWithTag("now_bar_unavailable_note").assertDoesNotExist()
        composeRule.onNodeWithTag("now_bar_show_guide").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, guides) }
    }

    @Test
    fun fallbackGuideNamesBothStepsAndOpensTheDeveloperOptions() {
        composeRule.setContent {
            DIMANowTheme { NowBarGuideDialog(unsupported = false, onDismiss = {}) }
        }
        composeRule.onNodeWithText("나우바가 안 보이면").assertIsDisplayed()
        composeRule.onNodeWithText("개발자 옵션 켜기").assertIsDisplayed()
        composeRule.onNodeWithText("모든 앱의 실시간 정보 보기 켜기").assertIsDisplayed()
    }

    @Test
    fun unsupportedPhoneOnlyGetsTheReason() {
        composeRule.setContent {
            DIMANowTheme { NowBarGuideDialog(unsupported = true, onDismiss = {}) }
        }
        composeRule.onNodeWithText("이 휴대폰은 나우바를 쓸 수 없어요").assertIsDisplayed()
        composeRule.onNodeWithText("개발자 옵션 켜기").assertDoesNotExist()
    }

    @Test
    fun dimmedNowBarBeforeAndroid16OpensTheGuide() {
        var guides = 0
        composeRule.setContent {
            DIMANowTheme {
                Box(Modifier.verticalScroll(rememberScrollState())) {
                    NotificationGuidanceSettings(
                        policy = NotificationGuidancePolicy(),
                        onModeChange = { _, _ -> },
                        nowBar = NowBarSupport.UNSUPPORTED,
                        onShowNowBarGuide = { guides++ },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("now_bar_unavailable_note").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notification_mode_CLASS_LIVE_UPDATE").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, guides) }
    }
}
