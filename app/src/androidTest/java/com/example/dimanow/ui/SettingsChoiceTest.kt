package com.example.dimanow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.dimanow.live.GuidanceKind
import com.example.dimanow.live.NotificationGuidanceMode
import com.example.dimanow.live.NotificationGuidancePolicy
import com.example.dimanow.theme.DIMANowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsChoiceTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun changingNotificationModePreservesOtherGuidanceChoices() {
        var policy by mutableStateOf(NotificationGuidancePolicy())
        val changes = mutableListOf<Pair<GuidanceKind, NotificationGuidanceMode>>()
        composeRule.setContent {
            DIMANowTheme {
                Box(Modifier.requiredWidth(380.dp)) {
                    NotificationGuidanceSettings(
                        policy = policy,
                        onModeChange = { kind, mode ->
                            changes += kind to mode
                            policy = policy.copy(classGuidance = mode)
                        },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("notification_mode_CLASS_OFF").performClick().assertIsSelected()
        composeRule.onNodeWithTag("notification_mode_CLASS_LIVE_UPDATE").assertIsNotSelected()
        composeRule.onNodeWithTag("notification_mode_CAMPUS_SHUTTLE_${policy.campusShuttle.name}").assertIsSelected()
        composeRule.runOnIdle { assertEquals(listOf(GuidanceKind.CLASS to NotificationGuidanceMode.OFF), changes) }
    }

    @Test
    fun narrowLargeTextChoicesRemainVisibleAndSelectable() {
        var selected by mutableStateOf<String?>(null)
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                DIMANowTheme {
                    Box(Modifier.requiredWidth(240.dp)) {
                        SettingsChoice(
                            options = listOf(null to "선택 안 함", "34710" to "대학 셔틀 정류장", "33243" to "원룸촌 앞"),
                            selected = selected,
                            onSelect = { selected = it },
                            itemTag = { "stop_$it" },
                        )
                    }
                }
            }
        }
        listOf("null", "34710", "33243").forEach {
            composeRule.onNodeWithTag("stop_$it").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
        composeRule.onNodeWithTag("stop_34710").performClick().assertIsSelected()
        composeRule.onNodeWithTag("stop_null").assertIsNotSelected()
        composeRule.runOnIdle { assertEquals("34710", selected) }
    }
}
