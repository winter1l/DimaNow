package com.example.dimanow.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.ui.motion.PREDICTIVE_BACK_MIN_SCALE
import com.example.dimanow.ui.motion.PageMotion
import com.example.dimanow.ui.motion.dimaFadeThrough
import com.example.dimanow.ui.motion.dimaPageTransform
import com.example.dimanow.ui.motion.predictiveBackScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-121: the pure parts of the expressive motion pass. */
class ExpressiveMotionTest {
    @Test
    fun `opening a 전체 page is forward and Back to where it came from is backward`() {
        assertEquals(PageMotion.FORWARD, pageMotion(AppPage.ALL, AppPage.DORM, backTarget = AppPage.ALL))
        assertEquals(PageMotion.BACKWARD, pageMotion(AppPage.DORM, AppPage.ALL, backTarget = AppPage.ALL))
        // Home's 수업 추가하기 opens the timetable; Back returns to Home.
        assertEquals(PageMotion.FORWARD, pageMotion(AppPage.DASHBOARD, AppPage.TIMETABLE, backTarget = AppPage.DASHBOARD))
        assertEquals(PageMotion.BACKWARD, pageMotion(AppPage.TIMETABLE, AppPage.DASHBOARD, backTarget = AppPage.DASHBOARD))
    }

    @Test
    fun `navigation bar destinations and links to another tab keep the fade-through`() {
        assertEquals(PageMotion.TOP_LEVEL, pageMotion(AppPage.SHUTTLE, AppPage.MEAL, backTarget = AppPage.ALL))
        assertEquals(PageMotion.TOP_LEVEL, pageMotion(AppPage.ALL, AppPage.DASHBOARD, backTarget = AppPage.ALL))
        // 기숙사's 식단 보기 jumps to the meal tab: not a step back up the hierarchy.
        assertEquals(PageMotion.TOP_LEVEL, pageMotion(AppPage.DORM, AppPage.MEAL, backTarget = AppPage.ALL))
    }

    @Test
    fun `reduced motion turns every page change into the plain fade-through`() {
        val fade = dimaFadeThrough()
        for (motion in PageMotion.entries) {
            val reduced = dimaPageTransform(motion, slidePx = 84, reducedMotion = true)
            assertEquals(fade.targetContentEnter, reduced.targetContentEnter)
            assertEquals(fade.initialContentExit, reduced.initialContentExit)
        }
        val forward = dimaPageTransform(PageMotion.FORWARD, slidePx = 84, reducedMotion = false)
        assertNotEquals("forward pages slide as they fade", fade.targetContentEnter, forward.targetContentEnter)
    }

    @Test
    fun `the predictive back preview shrinks to nine tenths and no further`() {
        assertEquals(1f, predictiveBackScale(0f), 0f)
        assertEquals(0.95f, predictiveBackScale(0.5f), 1e-6f)
        assertEquals(PREDICTIVE_BACK_MIN_SCALE, predictiveBackScale(1f), 1e-6f)
        assertEquals(PREDICTIVE_BACK_MIN_SCALE, predictiveBackScale(1.4f), 1e-6f)
        assertEquals(1f, predictiveBackScale(-0.2f), 0f)
    }

    @Test
    fun `the capsule grows from its tile corners to a full pill and never past it`() {
        val size = Size(300f, 60f)
        assertEquals(12f, cornerRadius(DimaShapes.tileToPill(0f), size), 1e-3f)
        assertEquals(21f, cornerRadius(DimaShapes.tileToPill(0.5f), size), 1e-3f)
        assertEquals(30f, cornerRadius(DimaShapes.tileToPill(1f), size), 1e-3f)
        // An overshooting spring past 1 still stops at the pill.
        assertEquals(30f, cornerRadius(DimaShapes.tileToPill(1.08f), size), 1e-3f)
    }

    @Test
    fun `the briefing mood follows the next class`() {
        assertEquals(HeroMood.FREE, heroMood(hasNextCourse = false, ongoing = false))
        assertEquals(HeroMood.UPCOMING, heroMood(hasNextCourse = true, ongoing = false))
        assertEquals(HeroMood.ONGOING, heroMood(hasNextCourse = true, ongoing = true))
    }

    private fun cornerRadius(shape: androidx.compose.ui.graphics.Shape, size: Size): Float {
        val outline = shape.createOutline(size, LayoutDirection.Ltr, Density(1f))
        return (outline as Outline.Rounded).roundRect.topLeftCornerRadius.x
    }
}
