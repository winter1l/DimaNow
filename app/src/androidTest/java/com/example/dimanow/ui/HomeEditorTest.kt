package com.example.dimanow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.SampleSchedule
import com.example.dimanow.meal.MealData
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.theme.DIMANowTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** D-128(1): Home follows the saved card order, hides cards, shows the favourites row and saves edits. */
class HomeEditorTest {
    @get:Rule val compose = createComposeRule()

    private val now = ZonedDateTime.of(2026, 10, 26, 12, 0, 0, 0, ZoneId.of("Asia/Seoul"))
    private val shuttle = ShuttleData(emptyList(), null, null, null, "https://example.invalid", null)
    private val meal = MealData(emptyList(), null, null, null, "https://example.invalid", null, null)

    @Test
    fun hiddenCardsLeaveHomeAndFavouritesOpenTheirTarget() {
        var opened: HomeShortcut? = null
        compose.setContent {
            DIMANowTheme(darkTheme = false) {
                DashboardScreen(
                    schedule = SampleSchedule.create(), zone = CampusZoneId.MAIN, shuttle = shuttle, meal = meal, now = now,
                    hiddenCards = setOf("meal"),
                    favorites = listOf(HomeShortcut.TIMETABLE, HomeShortcut.LIBRARY_CARD),
                    onOpenShortcut = { opened = it },
                    onEditHome = {},
                )
            }
        }
        compose.onNodeWithTag("home_favorite_library_card").performClick()
        assertEquals(HomeShortcut.LIBRARY_CARD, opened)
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_edit"))
        compose.onNodeWithTag("dashboard_meal_card").assertDoesNotExist()
        compose.onNodeWithTag("home_edit").assertIsDisplayed()
    }

    @Test
    fun theEditorMovesHidesAndPicksThenSaves() {
        var saved: Triple<List<HomeCard>, Set<String>, List<HomeShortcut>>? = null
        compose.setContent {
            DIMANowTheme(darkTheme = false) {
                HomeEditorDialog(
                    order = HomeCard.entries,
                    hidden = emptySet(),
                    favorites = listOf(HomeShortcut.TIMETABLE),
                    onSave = { order, hidden, picked -> saved = Triple(order, hidden, picked) },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithContentDescription("셔틀 위로").performClick()
        compose.onNodeWithTag("home_editor_show_meal").performClick()
        compose.onNodeWithTag("home_editor_favorite_heyoung").performScrollTo().performClick()
        compose.onNodeWithTag("home_editor_save").performClick()

        val (order, hidden, picked) = requireNotNull(saved)
        assertEquals(listOf(HomeCard.LEARNING, HomeCard.SHUTTLE, HomeCard.ACADEMIC), order.take(3))
        assertTrue("meal" in hidden)
        assertEquals(listOf(HomeShortcut.TIMETABLE, HomeShortcut.HEYOUNG), picked)
    }
}
