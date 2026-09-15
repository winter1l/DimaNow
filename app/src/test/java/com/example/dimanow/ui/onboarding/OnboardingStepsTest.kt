package com.example.dimanow.ui.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepsTest {
    @Test
    fun `only a fresh install sees onboarding and an update never replays it`() {
        assertTrue(shouldShowOnboarding(onboardingCompleted = false, homeBaseConfirmed = false))
        assertFalse(shouldShowOnboarding(onboardingCompleted = true, homeBaseConfirmed = true))
        assertFalse(shouldShowOnboarding(onboardingCompleted = false, homeBaseConfirmed = true))
        assertFalse(shouldShowOnboarding(onboardingCompleted = true, homeBaseConfirmed = false))
    }
}
