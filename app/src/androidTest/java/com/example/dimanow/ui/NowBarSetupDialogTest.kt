package com.example.dimanow.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class NowBarSetupDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun firstRunGuideUsesOnlyTheTwoRequiredSamsungSetupInstructions() {
        composeRule.setContent {
            NowBarSetupDialog(
                onOpenLockScreenNotifications = {},
                onOpenDeveloperOptions = {},
                onComplete = {},
            )
        }

        composeRule.onNodeWithText("'잠긴 상태에서 알림 내용 표시' 옵션을 항상 표시로 변경해주세요.").assertExists()
        composeRule.onNodeWithText("개발자 옵션에서 ‘모든 앱의 실시간 정보 보기’를 켜세요.").assertExists()
        composeRule.onNodeWithText("잠금화면 알림 설정 열기").assertExists()
        composeRule.onNodeWithText("개발자 옵션 열기").assertExists()
        composeRule.onNodeWithText("완료").assertExists()
    }
}
