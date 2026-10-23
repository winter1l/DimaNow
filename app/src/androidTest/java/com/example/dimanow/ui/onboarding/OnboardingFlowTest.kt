package com.example.dimanow.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.dimanow.data.OnboardingDraft
import com.example.dimanow.data.OnboardingStage
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.ui.GuidanceSetupAction
import com.example.dimanow.ui.GuidanceSetupContent
import com.example.dimanow.ui.GuidanceSetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class OnboardingFlowTest {
    @get:Rule val composeRule = createComposeRule()

    private val essentialsGranted = GuidanceSetupState(notifications = true, preciseLocation = true, approximateLocation = true)

    @Test
    fun choosingDirectionKeepsOnboardingOpenUntilSetupIsFinished() {
        var completed: HomeBase? = null
        var essentialRequests = 0
        val actions = mutableListOf<GuidanceSetupAction>()
        composeRule.setContent {
            var draft by remember { mutableStateOf(OnboardingDraft()) }
            MaterialTheme {
                OnboardingScreen(draft, { draft = it }, { completed = it }, onRequestEssentials = { essentialRequests++ }) {
                    GuidanceSetupContent(GuidanceSetupState(), { actions += it })
                }
            }
        }
        composeRule.onNodeWithTag("onboarding_primary").performClick()
        composeRule.onNodeWithText("주로 어느 방향으로 돌아가나요?").assertExists()
        composeRule.onNodeWithTag("onboarding_primary").assertIsNotEnabled()
        composeRule.onNodeWithTag("onboarding_home_base_one_room").performClick()
        composeRule.onNodeWithTag("onboarding_home_base_one_room").assertIsSelected()
        composeRule.runOnIdle { assertNull(completed) }
        composeRule.onNodeWithTag("onboarding_primary").performClick()
        composeRule.onNodeWithText("알림과 위치를 허용해 주세요").assertExists()
        // D-102: the optional settings-page items wait until the two essential permissions were asked for.
        composeRule.onNodeWithTag("guidance_setup_EXACT_ALARM").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(emptyList<GuidanceSetupAction>(), actions) }
        composeRule.onNodeWithTag("onboarding_back").performClick()
        composeRule.onNodeWithTag("onboarding_home_base_one_room").assertIsSelected()
        composeRule.onNodeWithTag("onboarding_primary").performClick()

        // One 계속 lets Android ask for both permissions; it never finishes onboarding by itself.
        composeRule.onNodeWithTag("onboarding_primary").assertTextEquals("계속").performClick()
        composeRule.runOnIdle {
            assertEquals(1, essentialRequests)
            assertNull(completed)
        }
        composeRule.onNodeWithTag("guidance_setup_EXACT_ALARM").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("onboarding_primary").assertTextEquals("완료").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(HomeBase.ONE_ROOM, completed) }
    }

    @Test
    fun everyStepShowsTheDecorativeGraphicWithoutSpokenContent() {
        composeRule.setContent {
            var draft by remember { mutableStateOf(OnboardingDraft(homeBase = HomeBase.YEIN)) }
            MaterialTheme {
                OnboardingScreen(draft, { draft = it }, {})
            }
        }
        repeat(OnboardingStage.entries.size) { step ->
            // Decorative: one node with no text, description or click for screen readers.
            composeRule.onNodeWithTag("onboarding_hero").assertExists()
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            if (step < OnboardingStage.entries.size - 1) composeRule.onNodeWithTag("onboarding_primary").performClick()
        }
        composeRule.onNodeWithText("알림과 위치를 허용해 주세요").assertExists()
    }

    @Test
    fun laterFinishesWithoutAskingForAnyPermission() {
        var completed: HomeBase? = null
        var essentialRequests = 0
        composeRule.setContent {
            MaterialTheme {
                OnboardingScreen(
                    OnboardingDraft(OnboardingStage.SETUP, HomeBase.YEIN), {}, { completed = it },
                    onRequestEssentials = { essentialRequests++ },
                )
            }
        }
        composeRule.onNodeWithTag("onboarding_skip").performClick()
        composeRule.runOnIdle {
            assertEquals(HomeBase.YEIN, completed)
            assertEquals(0, essentialRequests)
        }
    }

    @Test
    fun restoredSetupKeepsDirectionAndDoesNotTreatOpeningSettingsAsADeviceGrant() {
        val requests = mutableListOf<GuidanceSetupAction>()
        composeRule.setContent {
            MaterialTheme {
                OnboardingScreen(OnboardingDraft(OnboardingStage.SETUP, HomeBase.YEIN), {}, {}, setup = essentialsGranted) {
                    GuidanceSetupContent(GuidanceSetupState(), { requests += it })
                }
            }
        }
        composeRule.onNodeWithText("알림과 위치를 허용해 주세요").assertExists()
        composeRule.onNodeWithTag("onboarding_skip").assertDoesNotExist()
        composeRule.onNodeWithTag("guidance_setup_BACKGROUND_LOCATION").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("guidance_setup_EXACT_ALARM").performScrollTo().performClick()
        composeRule.onNodeWithTag("guidance_setup_EXACT_ALARM").assertIsEnabled()
        composeRule.runOnIdle { assertEquals(listOf(GuidanceSetupAction.EXACT_ALARM), requests) }
        composeRule.onNodeWithTag("onboarding_primary").assertTextEquals("완료").assertIsEnabled()
        composeRule.onNodeWithText("실시간 알림").assertDoesNotExist()
    }

    @Test
    fun changedSystemSnapshotRefreshesChecklistAndApproximateLocationRemainsClear() {
        var state by mutableStateOf(GuidanceSetupState(approximateLocation = true, liveSupported = true))
        composeRule.setContent {
            MaterialTheme {
                OnboardingScreen(OnboardingDraft(OnboardingStage.SETUP, HomeBase.YEIN), {}, {}, setup = essentialsGranted) {
                    GuidanceSetupContent(state, {})
                }
            }
        }
        composeRule.onNodeWithText("대략적 위치 허용됨 · 정확한 위치가 필요해요").performScrollTo().assertExists()
        composeRule.onNodeWithTag("guidance_setup_LOCATION").assertIsEnabled()
        composeRule.onNodeWithTag("guidance_setup_BACKGROUND_LOCATION").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("guidance_setup_LIVE").performScrollTo().assertIsNotEnabled()
        composeRule.runOnIdle {
            state = GuidanceSetupState(true, true, true, true, true, true, true)
        }
        GuidanceSetupAction.entries.forEach { action ->
            composeRule.onNodeWithTag("guidance_setup_${action.name}").assertDoesNotExist()
        }
        composeRule.onNodeWithTag("onboarding_primary").assertIsEnabled()
    }
}
