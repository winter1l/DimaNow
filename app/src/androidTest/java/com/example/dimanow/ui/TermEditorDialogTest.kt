package com.example.dimanow.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.dimanow.theme.DIMANowTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class TermEditorDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun invalidOrReversedDatesExplainTheProblemAndCannotSaveUntilCorrected() {
        var saved: Pair<LocalDate, LocalDate>? = null
        compose.setContent {
            DIMANowTheme {
                TermEditorDialog(
                    start = LocalDate.parse("2026-09-01"),
                    end = LocalDate.parse("2026-12-18"),
                    onDismiss = {},
                    onSave = { start, end -> saved = start to end },
                )
            }
        }
        compose.onNodeWithTag("term_start").performTextReplacement("2026-02-30")
        compose.onNodeWithText("2026-09-01 형식으로 입력해 주세요").assertExists()
        compose.onNodeWithText("저장").assertIsNotEnabled()
        compose.runOnIdle { assertNull(saved) }

        compose.onNodeWithTag("term_start").performTextReplacement("2026-12-19")
        compose.onNodeWithText("종료일은 시작일과 같거나 늦어야 해요").assertExists()
        compose.onNodeWithText("저장").assertIsNotEnabled()

        compose.onNodeWithTag("term_end").performTextReplacement("2026-12-20")
        compose.onNodeWithText("저장").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(LocalDate.parse("2026-12-19") to LocalDate.parse("2026-12-20"), saved)
        }
    }
}
