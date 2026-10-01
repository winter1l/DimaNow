package com.example.dimanow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DIMANowTheme
import com.example.dimanow.ui.motion.LocalReducedMotion
import com.example.dimanow.ui.motion.expressiveBounceClick
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** User interactions affected by replacing custom controls and restructuring the app shell. */
@OptIn(ExperimentalTestApi::class)
class MaterialInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun daySelectionRemainsReachableInANarrowPaneAndAnnouncesDateAndToday() {
        var selected by mutableStateOf(DayOfWeek.MONDAY)
        val selections = mutableListOf<DayOfWeek>()
        composeRule.setContent {
            DIMANowTheme {
                Box(Modifier.requiredWidth(240.dp)) {
                    DimaDaySelector(
                        days = DayOfWeek.entries.take(5),
                        selected = selected,
                        onSelect = { selected = it; selections += it },
                        itemTag = { "day_${it.name}" },
                        today = DayOfWeek.WEDNESDAY,
                        supportingLabel = { "9/${14 + it.ordinal}" },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("day_MONDAY").assertIsSelected()
        // D-094(11): "오늘" is the cell's state, not part of its name.
        composeRule.onNodeWithTag("day_WEDNESDAY")
            .assertContentDescriptionEquals("수요일 9/16")
            .assert(hasStateDescription("오늘"))
        DayOfWeek.entries.take(5).forEach { day ->
            composeRule.onNodeWithTag("day_${day.name}")
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        composeRule.onNodeWithTag("day_FRIDAY").performClick().assertIsSelected()
        composeRule.onNodeWithTag("day_MONDAY").assertIsNotSelected()
        composeRule.runOnIdle { assertEquals(listOf(DayOfWeek.FRIDAY), selections) }
    }

    @Test
    fun allSevenDaysStayVisibleAndSelectableInANarrowDialog() =
        verifyVisibleDays(width = 272.dp, fontScale = 1f, dayCount = 7, expectedRows = 2)

    @Test
    fun phoneWidthFitsSevenFullTouchTargetsInOneRow() =
        verifyVisibleDays(width = 360.dp, fontScale = 1f, dayCount = 7, expectedRows = 1)

    @Test
    fun largeTextKeepsWeekendsVisibleWithoutHorizontalScrolling() =
        verifyVisibleDays(width = 360.dp, fontScale = 1.5f, dayCount = 7, expectedRows = 2)

    @Test
    fun narrowDialogWithLargeTextKeepsAllSevenDaysInTwoRows() =
        verifyVisibleDays(width = 272.dp, fontScale = 1.5f, dayCount = 7, expectedRows = 2)

    @Test
    fun fiveMealDaysShareOneEqualWidthRow() =
        verifyVisibleDays(width = 272.dp, fontScale = 1f, dayCount = 5, expectedRows = 1)

    private fun verifyVisibleDays(width: Dp, fontScale: Float, dayCount: Int, expectedRows: Int) {
        var selected by mutableStateOf(DayOfWeek.MONDAY)
        val selections = mutableListOf<DayOfWeek>()
        val days = DayOfWeek.entries.take(dayCount)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                DIMANowTheme {
                    Box(Modifier.requiredWidth(width)) {
                        DimaDaySelector(
                            days = days, selected = selected,
                            onSelect = { selected = it; selections += it },
                            today = DayOfWeek.WEDNESDAY,
                            supportingLabel = { (14 + it.ordinal).toString() },
                            itemTag = { "visible_day_${it.name}" },
                        )
                    }
                }
            }
        }
        val bounds = days.map { day ->
            val target = composeRule.onNodeWithTag("visible_day_${day.name}")
            target.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            target.fetchSemanticsNode().boundsInRoot
        }
        assertEquals("days should wrap into deliberate visible rows", expectedRows, bounds.map { it.top.toInt() }.distinct().size)
        assertTrue("day targets should have equal widths even in the last row", bounds.maxOf { it.width } - bounds.minOf { it.width } <= 1f)
        composeRule.onNodeWithTag("visible_day_WEDNESDAY").assertContentDescriptionEquals("수요일 16")
            .assert(hasStateDescription("오늘"))
        composeRule.onNodeWithText("오늘").assertDoesNotExist()
        // Tap every option without scrolling, including both weekend choices in the seven-day cases.
        days.forEach { day ->
            composeRule.onNodeWithTag("visible_day_${day.name}")
                .performTouchInput { click() }.assertIsSelected()
        }
        composeRule.runOnIdle { assertEquals(days, selections) }
    }

    @Test
    fun expressiveClickSupportsKeyboardAndDoesNotAlsoInvokeTheParentForAChildTap() {
        var parentClicks = 0
        var childClicks = 0
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            DIMANowTheme {
                inputModeManager = LocalInputModeManager.current
                Column(
                    Modifier.size(240.dp).testTag("parent")
                        .expressiveBounceClick { parentClicks++ },
                ) {
                    Text("카드 열기", Modifier.padding(16.dp))
                    TextButton(onClick = { childClicks++ }, modifier = Modifier.testTag("child")) {
                        Text("별도 작업")
                    }
                }
            }
        }
        composeRule.onNodeWithTag("child").performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(1, childClicks)
            assertEquals(0, parentClicks)
            inputModeManager.requestInputMode(InputMode.Keyboard)
        }
        composeRule.onNodeWithTag("parent").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        composeRule.onNodeWithTag("parent").performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        composeRule.runOnIdle {
            assertEquals(1, parentClicks)
            assertEquals(1, childClicks)
        }
    }

    @Test
    fun pressScalesTheTargetDown() {
        assertTrue("pressed content should shrink", pressedContentWidthRatio(reducedMotion = false) < 0.99f)
    }

    @Test
    fun reducedMotionSkipsTheDecorativePressScale() {
        assertEquals(1f, pressedContentWidthRatio(reducedMotion = true), 0.001f)
    }

    /** Width of the pressed content relative to its resting width, after the press settles. */
    private fun pressedContentWidthRatio(reducedMotion: Boolean): Float {
        var clicks = 0
        composeRule.setContent {
            DIMANowTheme {
                CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                    Box(Modifier.size(200.dp).testTag("press_target").expressiveBounceClick { clicks++ }) {
                        Box(Modifier.size(200.dp).testTag("press_content"))
                    }
                }
            }
        }
        val content = composeRule.onNodeWithTag("press_content", useUnmergedTree = true)
        val resting = content.fetchSemanticsNode().boundsInRoot.width
        composeRule.onNodeWithTag("press_target").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1_000)
        val pressed = content.fetchSemanticsNode().boundsInRoot.width
        composeRule.onNodeWithTag("press_target").performTouchInput { up() }
        composeRule.runOnIdle { assertEquals(1, clicks) }
        return pressed / resting
    }

    @Test
    fun decorativeBounceWrapperDoesNotCreateAnEmptyClickOrBlockChildInput() {
        var clicks = 0
        composeRule.setContent {
            DIMANowTheme {
                Box(Modifier.testTag("wrapper").expressiveBounceClick()) {
                    TextButton(onClick = { clicks++ }, modifier = Modifier.testTag("action")) { Text("열기") }
                }
            }
        }
        composeRule.onNodeWithTag("wrapper").assertHasNoClickAction()
        composeRule.onNodeWithTag("action").performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun screenTitleAndSettingsRemainReachableAfterScrollingToTheEnd() {
        var settingsClicks = 0
        composeRule.setContent {
            DIMANowTheme {
                CompositionLocalProvider(LocalOpenSettings provides { settingsClicks++ }) {
                    Box(Modifier.height(480.dp)) {
                        ScreenScaffold(title = "시간표", listTag = "schedule_list") {
                            items(40) { index -> Text("수업 $index", Modifier.fillMaxWidth().height(64.dp)) }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("schedule_list").performScrollToIndex(39)
        composeRule.onNodeWithText("수업 39").assertIsDisplayed()
        composeRule.onNodeWithText("시간표").assertIsDisplayed()
        val settings = composeRule.onNodeWithTag("open_settings").assertIsDisplayed()
        // Material's 40dp visual IconButton expands its touch target to at least 48dp.
        val touchBounds = settings.fetchSemanticsNode().touchBoundsInRoot
        val minimumTouchSize = with(composeRule.density) { 48.dp.toPx() }
        assertTrue("settings touch height is below 48dp", touchBounds.height >= minimumTouchSize - 0.5f)
        assertTrue("settings touch width is below 48dp", touchBounds.width >= minimumTouchSize - 0.5f)
        settings.performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, settingsClicks) }
    }
}
