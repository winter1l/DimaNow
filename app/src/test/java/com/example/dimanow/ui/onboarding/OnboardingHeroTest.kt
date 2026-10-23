package com.example.dimanow.ui.onboarding

import com.example.dimanow.data.OnboardingStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OnboardingHeroTest {
    @Test
    fun `each step has its own main shape and icon so a step change visibly morphs`() {
        val arts = OnboardingStage.entries.map(::onboardingHeroArt)
        assertEquals(arts.size, arts.map { it.main }.distinct().size)
        assertEquals(arts.size, arts.map { it.mainGlyph }.distinct().size)
        // Neighbouring steps also change the small shapes, so all three layers move together.
        arts.zipWithNext().forEach { (a, b) ->
            assertNotEquals(a.accent, b.accent)
            assertNotEquals(a.dot, b.dot)
        }
    }

    @Test
    fun `the graphic names the step's subject`() {
        assertEquals(OnboardingHeroGlyph.FORWARD, onboardingHeroArt(OnboardingStage.WELCOME).mainGlyph)
        assertEquals(OnboardingHeroGlyph.BUS, onboardingHeroArt(OnboardingStage.HOME_BASE).mainGlyph)
        assertEquals(OnboardingHeroGlyph.HOME, onboardingHeroArt(OnboardingStage.HOME_BASE).accentGlyph)
        assertEquals(OnboardingHeroGlyph.NOTIFICATIONS, onboardingHeroArt(OnboardingStage.SETUP).mainGlyph)
        assertEquals(OnboardingHeroGlyph.LOCATION, onboardingHeroArt(OnboardingStage.SETUP).accentGlyph)
    }

    @Test
    fun `going on turns clockwise one step at a time and going back turns back`() {
        assertEquals(ONBOARDING_HERO_TURN_DEGREES, onboardingHeroTurn(OnboardingStage.WELCOME, OnboardingStage.HOME_BASE), 0f)
        assertEquals(-ONBOARDING_HERO_TURN_DEGREES, onboardingHeroTurn(OnboardingStage.SETUP, OnboardingStage.HOME_BASE), 0f)
        assertEquals(0f, onboardingHeroTurn(OnboardingStage.SETUP, OnboardingStage.SETUP), 0f)
        // A forward-and-back round trip ends where it started, so the mark never drifts.
        val there = onboardingHeroTurn(OnboardingStage.WELCOME, OnboardingStage.SETUP)
        assertEquals(0f, there + onboardingHeroTurn(OnboardingStage.SETUP, OnboardingStage.WELCOME), 0f)
    }
}
