package com.example.dimanow.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.dimanow.theme.DIMANowTheme
import org.junit.Rule
import org.junit.Test

/** D-134: a school read that takes long says so under its loading line. */
class LoadingLineSlowHintTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theHintAppearsOnlyAfterTheWaitGetsLong() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DIMANowTheme { LoadingLine("도서관에서 대출 현황을 불러오는 중이에요", slowHint = SCHOOL_READ_SLOW_HINT) }
        }
        compose.mainClock.advanceTimeBy(SLOW_LOADING_MILLIS - 1_000)
        compose.onNodeWithText("도서관에서 대출 현황을 불러오는 중이에요").assertIsDisplayed()
        compose.onNodeWithTag("loading_slow_hint").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText(SCHOOL_READ_SLOW_HINT).assertIsDisplayed()
    }

    @Test
    fun aLineWithoutAHintNeverShowsOne() {
        compose.mainClock.autoAdvance = false
        compose.setContent { DIMANowTheme { LoadingLine("식단을 불러오고 있어요") } }
        compose.mainClock.advanceTimeBy(SLOW_LOADING_MILLIS * 2)
        compose.onNodeWithTag("loading_slow_hint").assertDoesNotExist()
    }
}
