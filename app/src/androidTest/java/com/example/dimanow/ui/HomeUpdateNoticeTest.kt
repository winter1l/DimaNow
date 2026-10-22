package com.example.dimanow.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.dimanow.theme.DIMANowTheme
import com.example.dimanow.update.AppUpdatePhase
import com.example.dimanow.update.AppUpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** D-102: a new version waits on Home instead of a dialog over the app at launch. */
class HomeUpdateNoticeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun onlyActionableUpdateStatesReachHome() {
        assertTrue(AppUpdateUiState("2.1", promptVersion = "2.2").showsHomeNotice())
        assertTrue(AppUpdateUiState("2.1", phase = AppUpdatePhase.DOWNLOADING).showsHomeNotice())
        assertTrue(AppUpdateUiState("2.1", phase = AppUpdatePhase.PERMISSION_REQUIRED).showsHomeNotice())
        assertFalse(AppUpdateUiState("2.1", phase = AppUpdatePhase.ERROR, message = "확인하지 못했어요").showsHomeNotice())
        assertFalse(AppUpdateUiState("2.1", phase = AppUpdatePhase.UP_TO_DATE).showsHomeNotice())
    }

    @Test
    fun theOfferInstallsOrPutsTheVersionOff() {
        val calls = mutableListOf<String>()
        val state = mutableStateOf(AppUpdateUiState("2.1", phase = AppUpdatePhase.AVAILABLE, promptVersion = "2.2"))
        compose.setContent {
            DIMANowTheme {
                HomeUpdateNotice(
                    state = state.value,
                    onInstall = { calls += "install" },
                    onLater = { calls += "later" },
                    onCancel = { calls += "cancel" },
                    onContinueInstall = { calls += "continue" },
                )
            }
        }

        compose.onNodeWithText("DIMA Now 2.2 버전이 나왔어요").assertExists()
        compose.onNodeWithTag("home_update_later").performClick()
        compose.onNodeWithTag("home_update_install").performClick()
        compose.runOnIdle {
            assertEquals(listOf("later", "install"), calls)
            state.value = AppUpdateUiState("2.1", phase = AppUpdatePhase.DOWNLOADING, downloadProgress = 40)
        }
        compose.onNodeWithText("새 버전을 내려받고 있어요 · 40%").assertExists()
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle {
            assertEquals("cancel", calls.last())
            state.value = AppUpdateUiState("2.1", phase = AppUpdatePhase.PERMISSION_REQUIRED)
        }
        compose.onNodeWithTag("home_update_continue").performClick()
        compose.runOnIdle { assertEquals("continue", calls.last()) }
    }
}
