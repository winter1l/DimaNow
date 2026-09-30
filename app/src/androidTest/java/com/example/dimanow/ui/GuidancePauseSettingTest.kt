package com.example.dimanow.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.dimanow.domain.GuidancePause
import com.example.dimanow.theme.DIMANowTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class GuidancePauseSettingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun futurePauseCanBeChangedOrClearedBeforeItStarts() {
        val today = LocalDate.parse("2026-09-16")
        val scheduled = GuidancePause(today.plusDays(2), today.plusDays(4))
        val pause = mutableStateOf<GuidancePause?>(scheduled)
        var configurationRequests = 0
        compose.setContent {
            DIMANowTheme {
                GuidancePauseSetting(
                    pause = pause.value,
                    today = today,
                    onConfigure = { configurationRequests++ },
                    onClear = { pause.value = null },
                )
            }
        }

        compose.onNodeWithText("9월 18일부터 9월 20일까지 휴강").assertExists()
        compose.onNodeWithText("변경").performClick()
        compose.runOnIdle {
            assertEquals(1, configurationRequests)
            assertEquals(scheduled, pause.value)
        }
        compose.onNodeWithTag("clear_guidance_pause").performClick()
        compose.runOnIdle { assertNull(pause.value) }
        compose.onNodeWithTag("clear_guidance_pause").assertDoesNotExist()
        compose.onNodeWithText("설정").performClick()
        compose.runOnIdle {
            assertEquals(2, configurationRequests)
            assertNull(pause.value)
        }
    }
}
