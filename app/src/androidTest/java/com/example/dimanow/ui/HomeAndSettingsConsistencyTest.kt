package com.example.dimanow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

/** D-094(6), (9): one tap pattern on Home and one single-choice control in Settings. */
class HomeAndSettingsConsistencyTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun homeCardsAreWholeCardTargetsThatAnnounceWhatTheyOpen() {
        val opened = mutableListOf<AppPage>()
        composeRule.setContent {
            DIMANowTheme {
                DashboardScreen(
                    schedule = SampleSchedule.create(),
                    zone = CampusZoneId.MAIN,
                    shuttle = ShuttleData(emptyList(), null, null, null, "https://example.invalid", null),
                    meal = MealData(emptyList(), null, null, null, "https://example.invalid", null, null),
                    onNavigateToPage = { opened += it },
                    now = ZonedDateTime.of(2026, 9, 14, 12, 0, 0, 0, ZoneId.of("Asia/Seoul")),
                )
            }
        }

        // Home is a LazyColumn: scroll to each card first so a short screen still composes it
        // and its centre is on screen for the click.
        val homeList = composeRule.onNodeWithTag("home_list")
        // Signed out with nothing cached: a compact single-row prompt, not a full card.
        homeList.performScrollToNode(hasTestTag("dashboard_learning_prompt"))
        val prompt = composeRule.onNodeWithTag("dashboard_learning_prompt").assert(hasClickLabel("수업 보기"))
        assertTrue("the signed-out prompt stays a single compact row", prompt.getUnclippedBoundsInRoot().height <= 96.dp)
        prompt.performClick()
        composeRule.onNodeWithTag("dashboard_learning_card").assertDoesNotExist()

        homeList.performScrollToNode(hasTestTag("dashboard_shuttle_card"))
        composeRule.onNodeWithTag("dashboard_shuttle_card")
            .assert(hasClickLabel("셔틀 전체 시간표 보기"))
            .performClick()
        homeList.performScrollToNode(hasTestTag("dashboard_meal_card"))
        composeRule.onNodeWithTag("dashboard_meal_card")
            .assert(hasClickLabel("식단 보기"))
            .performClick()
        // The labelled action is the card's own action, not a second one.
        homeList.performScrollToNode(hasTestTag("dashboard_shuttle_card"))
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
                    // A phone-width Settings column (412dp window minus the 16dp page margins),
                    // start-aligned so a narrower test screen clips its end instead of centring
                    // it at a negative offset.
                    Box(
                        Modifier
                            .wrapContentWidth(Alignment.Start, unbounded = true)
                            .requiredWidth(380.dp)
                            .testTag(ZONE_COLUMN_TAG),
                    ) {
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

        // The claim is "all four fit a 380dp column without horizontal scrolling", so bounds are
        // measured against that column, not against the test device's screen width.
        // Unclipped bounds keep the check meaningful on a screen narrower than the column.
        val column = composeRule.onNodeWithTag(ZONE_COLUMN_TAG).getUnclippedBoundsInRoot()
        CampusZoneId.entries.forEach {
            // No option sits inside a horizontally scrolling container that could hide it.
            val option = composeRule.onNode(
                hasTestTag("test_zone_${it.name}") and
                    !hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)),
            ).assertIsDisplayed().assertIsNotEnabled()
            val bounds = option.getUnclippedBoundsInRoot()
            assertTrue(
                "${it.name} must fit inside the 380dp column at font $fontScale",
                bounds.left >= column.left - 0.5.dp && bounds.right <= column.right + 0.5.dp,
            )
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

    private companion object {
        const val ZONE_COLUMN_TAG = "test_zone_column"
    }
}
