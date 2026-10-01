package com.example.dimanow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DefaultSchedule
import com.example.dimanow.meal.MealData
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.theme.DIMANowTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** D-094(6), (9): one tap pattern on Home and one single-choice control in Settings. */
class HomeAndSettingsConsistencyTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun homeCardsAreWholeCardTargetsThatAnnounceWhatTheyOpen() {
        val opened = mutableListOf<AppPage>()
        composeRule.setContent {
            DIMANowTheme {
                DashboardScreen(
                    schedule = DefaultSchedule.create(),
                    zone = CampusZoneId.MAIN,
                    shuttle = ShuttleData(emptyList(), null, null, null, "https://example.invalid", null),
                    meal = MealData(emptyList(), null, null, null, "https://example.invalid", null, null),
                    onNavigateToPage = { opened += it },
                    now = ZonedDateTime.of(2026, 9, 14, 12, 0, 0, 0, ZoneId.of("Asia/Seoul")),
                )
            }
        }

        // Signed out with nothing cached: a compact single-row prompt, not a full card.
        val prompt = composeRule.onNodeWithTag("dashboard_learning_prompt").assert(hasClickLabel("수업 보기"))
        assertTrue("the signed-out prompt stays a single compact row", prompt.getUnclippedBoundsInRoot().height <= 96.dp)
        prompt.performClick()
        composeRule.onNodeWithTag("dashboard_learning_card").assertDoesNotExist()

        composeRule.onNodeWithTag("dashboard_shuttle_card")
            .assert(hasClickLabel("셔틀 전체 시간표 보기"))
            .performClick()
        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("dashboard_meal_card"))
        composeRule.onNodeWithTag("dashboard_meal_card")
            .assert(hasClickLabel("식단 보기"))
            .performClick()
        // The labelled action is the card's own action, not a second one.
        composeRule.onNodeWithTag("dashboard_shuttle_card").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(listOf(AppPage.COURSES, AppPage.SHUTTLE, AppPage.MEAL, AppPage.SHUTTLE), opened)
        }
        composeRule.onNodeWithText("수업 (LMS)").assertDoesNotExist()
    }

    @Test
    fun locationTestZonesShowAllFourChoicesWithoutHorizontalScrolling() = verifyTestZones(fontScale = 1f)

    @Test
    fun locationTestZonesStayVisibleWithLargerText() = verifyTestZones(fontScale = 1.3f)

    private fun verifyTestZones(fontScale: Float) {
        var testMode by mutableStateOf(false)
        var zone by mutableStateOf(CampusZoneId.MAIN)
        val chosen = mutableListOf<CampusZoneId>()
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                DIMANowTheme {
                    // A phone-width Settings column (412dp window minus the 16dp page margins).
                    Box(Modifier.requiredWidth(380.dp)) {
                        LocationTestCard(
                            testMode = testMode,
                            testZone = zone,
                            onTestModeChange = { testMode = it },
                            onTestZone = { zone = it; chosen += it },
                            testTransitStopNumber = null,
                            onTestTransitStop = {},
                        )
                    }
                }
            }
        }

        val rootRight = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        CampusZoneId.entries.forEach {
            // No option sits inside a horizontally scrolling container that could hide it.
            val option = composeRule.onNode(
                hasTestTag("test_zone_${it.name}") and
                    !hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)),
            ).assertIsDisplayed().assertIsNotEnabled()
            val bounds = option.fetchSemanticsNode().boundsInRoot
            assertTrue("${it.name} must be fully on screen at font $fontScale", bounds.left >= 0f && bounds.right <= rootRight + 0.5f)
        }
        composeRule.onNodeWithText("테스트 모드").performClick()
        composeRule.onNodeWithTag("test_zone_MAIN").assertIsEnabled().assertIsSelected()
        composeRule.onNodeWithTag("test_zone_OUTSIDE").assertHasClickAction().performClick().assertIsSelected()
        composeRule.onNodeWithTag("test_zone_MAIN").assertIsNotSelected()
        composeRule.runOnIdle {
            assertTrue(testMode)
            assertEquals(listOf(CampusZoneId.OUTSIDE), chosen)
        }
    }
}
