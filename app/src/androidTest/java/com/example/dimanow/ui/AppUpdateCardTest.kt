package com.example.dimanow.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.dimanow.update.AppUpdatePhase
import com.example.dimanow.update.AppUpdateRelease
import com.example.dimanow.update.AppUpdateUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppUpdateCardTest {
    @get:Rule val composeRule = createComposeRule()

    private val release = AppUpdateRelease(
        versionName = "1.2",
        releasePageUrl = "https://github.com/winter1l/DimaNow/releases/tag/v1.2",
        downloadUrl = "https://github.com/winter1l/DimaNow/releases/download/v1.2/DIMA-Now-v1.2-optimized.apk",
        sizeBytes = 10,
        sha256 = "a".repeat(64),
    )

    @Test
    fun availableUpdateRemainsActionableFromSettings() {
        var downloadClicked = false
        composeRule.setContent {
            AppUpdateCard(
                state = AppUpdateUiState("1.1", AppUpdatePhase.AVAILABLE, release),
                onCheck = {},
                onDownload = { downloadClicked = true },
                onContinueInstall = {},
                onCancelDownload = {},
            )
        }

        composeRule.onNodeWithText("현재 버전 1.1").assertExists()
        composeRule.onNodeWithText("새 버전 1.2").assertExists()
        composeRule.onNodeWithText("업데이트 확인").assertExists()
        composeRule.onNodeWithText("다운로드 및 설치").performClick()
        composeRule.runOnIdle { assertTrue(downloadClicked) }
    }

    @Test
    fun downloadingStateExposesProgressAndCancel() {
        composeRule.setContent {
            AppUpdateCard(
                state = AppUpdateUiState("1.1", AppUpdatePhase.DOWNLOADING, release, downloadProgress = 42),
                onCheck = {}, onDownload = {}, onContinueInstall = {}, onCancelDownload = {},
            )
        }
        composeRule.onNodeWithText("다운로드 중 42%").assertExists()
        composeRule.onNodeWithText("취소").assertExists()
    }

    @Test
    fun permissionStateExposesContinueInstall() {
        composeRule.setContent {
            AppUpdateCard(
                state = AppUpdateUiState("1.1", AppUpdatePhase.PERMISSION_REQUIRED, release),
                onCheck = {}, onDownload = {}, onContinueInstall = {}, onCancelDownload = {},
            )
        }
        composeRule.onNodeWithText("설치 권한이 필요합니다").assertExists()
        composeRule.onNodeWithText("설치 계속").assertExists()
    }
}
