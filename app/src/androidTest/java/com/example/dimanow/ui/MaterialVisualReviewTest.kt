package com.example.dimanow.ui

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.data.OnboardingDraft
import com.example.dimanow.data.OnboardingStage
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.lms.*
import com.example.dimanow.theme.DIMANowTheme
import com.example.dimanow.ui.onboarding.OnboardingScreen
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test

/** Deterministic, synthetic content for the bounded visual review; never uses a school account. */
class MaterialVisualReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun learningListAndRecoveryStates() {
        val now = Instant.parse("2026-09-16T03:00:00Z")
        var snapshot by mutableStateOf(LmsSnapshot(
            items = listOf(
                LmsItem(id = "a", courseId = "audio", courseName = "음향기초실습",
                    kind = LmsItemKind.ASSIGNMENT, title = "현장 녹음 실습 보고서와 장비 연결 과정을 제출해 주세요",
                    detailUrl = "https://lms.dima.ac.kr/example/a", dueAt = now.plusSeconds(3600),
                    completionState = LmsCompletionState.INCOMPLETE),
                LmsItem(id = "b", courseId = "video", courseName = "스튜디오기초실습",
                    kind = LmsItemKind.CONTENT, title = "카메라 연결과 촬영 준비",
                    detailUrl = "https://lms.dima.ac.kr/example/b", dueAt = now.plusSeconds(86400),
                    completionState = LmsCompletionState.INCOMPLETE),
            ), syncState = LmsSyncState.READY,
        ))
        var refreshed = false
        compose.setContent {
            DIMANowTheme {
                Surface {
                    LmsItemsScreen(snapshot, LmsSessionState.ACTIVE, null, null, {}, {}, { refreshed = true }, {}, now)
                }
            }
        }
        compose.onNodeWithText("현장 녹음 실습 보고서와 장비 연결 과정을 제출해 주세요").assertExists()
        capture("learning-list")
        compose.runOnIdle { snapshot = snapshot.copy(syncState = LmsSyncState.ERROR, errorMessage = "synthetic failure") }
        capture("learning-cached-error")
        compose.runOnIdle { snapshot = LmsSnapshot(syncState = LmsSyncState.SYNCING) }
        compose.onNodeWithText("수업 정보를 불러오고 있어요").assertExists()
        capture("learning-loading")
        compose.runOnIdle { snapshot = LmsSnapshot(syncState = LmsSyncState.READY) }
        compose.onNodeWithText("오늘 확인할 학습이 없어요").assertExists()
        capture("learning-empty")
        compose.runOnIdle { snapshot = LmsSnapshot(syncState = LmsSyncState.ERROR) }
        compose.onNodeWithText("다시 시도").performClick()
        compose.runOnIdle { check(refreshed) }
        capture("learning-error")
    }

    @Test fun onboardingChoicesAndCompletionRemainUsable() {
        var draft by mutableStateOf(OnboardingDraft())
        var completed = false
        compose.setContent {
            DIMANowTheme {
                OnboardingScreen(draft, { draft = it }, { completed = true }, setupContent = {
                    GuidanceSetupContent(GuidanceSetupState(), {})
                })
            }
        }
        capture("onboarding-welcome")
        compose.runOnIdle { draft = draft.copy(stage = OnboardingStage.HOME_BASE) }
        capture("onboarding-direction")
        compose.runOnIdle { draft = draft.copy(stage = OnboardingStage.SETUP, homeBase = HomeBase.YEIN) }
        capture("onboarding-setup")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val variant = InstrumentationRegistry.getArguments().getString("visualVariant") ?: "phone"
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "m3-review/$variant").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
