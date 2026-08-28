package com.example.dimanow.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.dimanow.domain.GuidancePause
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimetablePauseDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun pauseDialogOffersOnlyTheFourApprovedDurations() {
        composeRule.setContent {
            PauseDurationDialog(
                today = LocalDate.of(2026, 8, 27),
                termEnd = LocalDate.of(2026, 12, 18),
                onSelection = { _: GuidancePause -> },
                onRangeRequested = {},
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("오늘만").assertExists()
        composeRule.onNodeWithText("기간 지정").assertExists()
        composeRule.onNodeWithText("학기 종료일까지").assertExists()
        composeRule.onNodeWithText("휴강모드를 다시 끌 때까지").assertExists()
        composeRule.onAllNodesWithText("날짜 선택").assertCountEquals(0)
    }

    @Test
    fun untilDisabledSelectionHasNoCalendarEndDate() {
        var selected: GuidancePause? = null
        composeRule.setContent {
            PauseDurationDialog(
                today = LocalDate.of(2026, 8, 27),
                termEnd = LocalDate.of(2026, 12, 18),
                onSelection = { selected = it },
                onRangeRequested = {},
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("휴강모드를 다시 끌 때까지").performClick()

        assertEquals(LocalDate.of(2026, 8, 27), selected?.startDate)
        assertEquals(true, selected?.isUntilDisabled)
    }
}
