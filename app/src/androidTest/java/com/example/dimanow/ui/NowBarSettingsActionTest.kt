package com.example.dimanow.ui

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.MainActivity
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.live.AndroidLiveSurfaceController
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class NowBarSettingsActionTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun lockScreenNotificationButtonKeepsAppAliveWhileOpeningSystemSettings() {
        prepareCompletedOnboarding()

        openNowBarSetupGuide()
        composeRule.onNodeWithText("잠금화면 알림 설정 열기").performClick()

        assertNotEquals(Lifecycle.State.DESTROYED, composeRule.activityRule.scenario.state)
    }

    @Test
    fun developerOptionsButtonKeepsAppAliveWhileOpeningSystemSettings() {
        prepareCompletedOnboarding()

        openNowBarSetupGuide()
        composeRule.onNodeWithText("개발자 옵션 열기").performClick()

        assertNotEquals(Lifecycle.State.DESTROYED, composeRule.activityRule.scenario.state)
    }

    @Test
    fun lockScreenButtonOpensTheSameSystemDestinationAsLiveSettings() {
        // 직전 테스트가 설정 앱 태스크를 다른 화면(예: 개발자 옵션)에 남겨두면
        // topResumedActivity 판독이 오염되므로 설정 앱 태스크를 초기화한다.
        shell("am force-stop com.android.settings")
        SystemClock.sleep(400)
        prepareCompletedOnboarding()
        openNowBarSetupGuide()

        composeRule.onNodeWithText("잠금화면 알림 설정 열기").performClick()
        SystemClock.sleep(800)
        val lockScreenDestination = topResumedComponent()

        shell("input keyevent 4")
        SystemClock.sleep(800)
        AndroidLiveSurfaceController(composeRule.activity.application).openPromotionSettings()
        SystemClock.sleep(800)

        assertEquals(topResumedComponent(), lockScreenDestination)
    }

    private fun prepareCompletedOnboarding() {
        val preferences = (composeRule.activity.application as DimaNowApplication).preferences
        runBlocking {
            preferences.setHomeBase(HomeBase.YEIN)
            preferences.setNowBarSetupCompleted(true)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("귀가 기준지 선택").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun openNowBarSetupGuide() {
        composeRule.onNodeWithText("설정").performClick()
        composeRule.onNodeWithText("나우바 설정 안내").performClick()
    }

    private fun topResumedComponent(): String {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            shell("dumpsys activity activities")
                .lineSequence()
                .firstOrNull { it.contains("topResumedActivity=") }
                ?.let { return it.substringAfter(" u0 ").substringBefore(" t") }
            SystemClock.sleep(100)
        }
        throw AssertionError("5초 안에 재개된 시스템 설정 Activity를 확인하지 못했습니다")
    }

    private fun shell(command: String): String {
        val descriptor = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return descriptor.use {
            FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
    }
}
