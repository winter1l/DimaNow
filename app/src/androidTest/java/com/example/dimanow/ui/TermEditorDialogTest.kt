package com.example.dimanow.ui

import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.dimanow.theme.DIMANowTheme
import com.example.dimanow.ui.schedule.TermEditorDialog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class TermEditorDialogTest {
    @get:Rule val compose = createComposeRule()

    private var saved: Pair<LocalDate, LocalDate>? = null

    private fun show(displayMode: DisplayMode) {
        compose.setContent {
            DIMANowTheme {
                TermEditorDialog(
                    start = LocalDate.parse("2026-09-01"),
                    end = LocalDate.parse("2026-12-18"),
                    onDismiss = {},
                    onSave = { start, end -> saved = start to end },
                    pickerDisplayMode = displayMode,
                )
            }
        }
    }

    /** Drives the Material date picker through its text-input mode, formatted in Korean (yyyy. MM. dd.). */
    private fun pick(fieldTag: String, digits: String) {
        compose.onNodeWithTag(fieldTag).performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("term_date_picker")))
            .performTextReplacement(digits)
        compose.onNodeWithTag("term_picker_confirm").assertIsEnabled().performClick()
        compose.waitForIdle()
    }

    @Test
    fun termDatesAreShownForPeopleAndOpenAKoreanDatePicker() {
        show(DisplayMode.Picker)
        // D-094(14): dates read as Korean dates, not typed ISO text
        compose.onNodeWithText("2026년 9월 1일 (화)").assertExists()
        compose.onNodeWithText("2026년 12월 18일 (금)").assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()

        compose.onNodeWithTag("term_start").performClick()
        compose.onNodeWithTag("term_date_picker").assertExists()
        // The picker itself is formatted in Korean (month header "2026년 9월").
        val koreanMonth = compose.onAllNodes(hasText("2026년 9월", substring = true) and hasAnyAncestor(hasTestTag("term_date_picker")))
            .fetchSemanticsNodes()
        assertTrue("picker should be formatted in Korean", koreanMonth.isNotEmpty())
        // The headline uses the app's date style instead of the device language ("Sep 1, 2026").
        compose.onNode(hasText("9월 1일 (화)") and hasAnyAncestor(hasTestTag("term_date_picker"))).assertExists()
        compose.onNodeWithTag("term_picker_confirm").assertIsEnabled()
    }

    @Test
    fun pickerSetsDatesAndRejectsAReversedRangeWithAnExplanation() {
        show(DisplayMode.Input)

        pick("term_start", "20261219")
        compose.onNodeWithText("2026년 12월 19일 (토)").assertExists()
        compose.onNodeWithText("종료일은 시작일과 같거나 늦어야 해요").assertExists()
        compose.onNodeWithText("저장").assertIsNotEnabled()
        compose.runOnIdle { assertNull(saved) }

        pick("term_end", "20261220")
        compose.onNodeWithText("종료일은 시작일과 같거나 늦어야 해요").assertDoesNotExist()
        compose.onNodeWithText("저장").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(LocalDate.parse("2026-12-19") to LocalDate.parse("2026-12-20"), saved)
        }
    }
}
