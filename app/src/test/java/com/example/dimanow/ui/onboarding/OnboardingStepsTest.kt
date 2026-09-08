package com.example.dimanow.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepsTest {
    @Test
    fun `only a fresh install sees onboarding and an update never replays it`() {
        // 최초 설치: 두 플래그 모두 비어 있다
        assertTrue(shouldShowOnboarding(onboardingCompleted = false, homeBaseConfirmed = false))
        // 온보딩을 끝낸 뒤
        assertFalse(shouldShowOnboarding(onboardingCompleted = true, homeBaseConfirmed = true))
        // D-056 이전에 설치해 귀가 기준지만 확정된 기존 사용자는 업데이트해도 다시 보지 않는다
        assertFalse(shouldShowOnboarding(onboardingCompleted = false, homeBaseConfirmed = true))
    }

    @Test
    fun `every permission the app actually uses gets its own explained step`() {
        val steps = onboardingSteps(needsNotification = true, needsExactAlarm = true)

        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.NOTIFICATION,
                OnboardingStep.LOCATION,
                OnboardingStep.BACKGROUND_LOCATION,
                OnboardingStep.EXACT_ALARM,
                OnboardingStep.HOME_BASE,
            ),
            steps,
        )
    }

    @Test
    fun `background location always follows precise location so the staged request can succeed`() {
        val steps = onboardingSteps(needsNotification = true, needsExactAlarm = true)

        assertTrue(steps.indexOf(OnboardingStep.LOCATION) < steps.indexOf(OnboardingStep.BACKGROUND_LOCATION))
    }

    @Test
    fun `returning from the system alarm screen with the permission on moves the flow forward`() {
        // D-059: 정확한 알람은 결과 콜백이 없어, 복귀 시 직접 다시 읽지 않으면 화면이 멈춘다
        assertTrue(shouldAdvanceOnResume(OnboardingStep.EXACT_ALARM, wasGranted = false, isGranted = true))
    }

    @Test
    fun `returning without granting, or on another step, never skips anything`() {
        assertFalse(shouldAdvanceOnResume(OnboardingStep.EXACT_ALARM, wasGranted = false, isGranted = false))
        // 이미 켜진 상태로 잠깐 앱을 벗어났다 돌아온 것만으로 단계를 건너뛰지 않는다
        assertFalse(shouldAdvanceOnResume(OnboardingStep.EXACT_ALARM, wasGranted = true, isGranted = true))
        // 다른 단계는 각자의 권한 콜백이 진행을 맡는다
        assertFalse(shouldAdvanceOnResume(OnboardingStep.NOTIFICATION, wasGranted = false, isGranted = true))
        assertFalse(shouldAdvanceOnResume(OnboardingStep.HOME_BASE, wasGranted = false, isGranted = true))
    }

    @Test
    fun `steps the platform does not need are dropped but home base always closes the flow`() {
        val steps = onboardingSteps(needsNotification = false, needsExactAlarm = false)

        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.LOCATION, OnboardingStep.BACKGROUND_LOCATION, OnboardingStep.HOME_BASE),
            steps,
        )
        assertEquals(OnboardingStep.HOME_BASE, steps.last())
    }
}
