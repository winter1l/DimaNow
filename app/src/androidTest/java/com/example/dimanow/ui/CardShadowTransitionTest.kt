package com.example.dimanow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.dimanow.theme.DIMANowTheme
import com.example.dimanow.ui.motion.staggeredEntrance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CardShadowTransitionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun cardSurfaceRemainsOpaqueDuringItsEntranceMotion() {
        var expected = Color.Unspecified
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            DIMANowTheme(darkTheme = false) {
                expected = MaterialTheme.colorScheme.surfaceContainerLow
                Box(Modifier.padding(24.dp)) {
                    ElevatedCard(
                        modifier = Modifier.size(240.dp, 120.dp).testTag("moving_card").staggeredEntrance(0),
                        colors = CardDefaults.elevatedCardColors(containerColor = expected),
                    ) { Text("셔틀 카드", Modifier.padding(24.dp)) }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(120)

        val image = composeRule.onNodeWithTag("moving_card").captureToImage()
        val pixels = image.toPixelMap()
        val expectedArgb = expected.toArgb()
        var matches = 0
        for (x in 0 until image.width) for (y in 0 until image.height) {
            if (pixels[x, y].toArgb() == expectedArgb) matches++
        }
        assertTrue("card surface was translucent at the transition midpoint: $matches/${image.width * image.height}", matches >= image.width * image.height * 4 / 5)
    }

    @Test
    fun incomingAndOutgoingTabsBothFadeBetweenOpaqueDestinationStates() {
        composeRule.mainClock.autoAdvance = false
        var page by mutableIntStateOf(0)
        composeRule.setContent {
            Box(Modifier.size(200.dp).background(Color.Black).testTag("transition_scene")) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { dimaTabContentTransform(targetState > initialState) },
                    label = "tab_transition_test",
                ) { target ->
                    Box(
                        Modifier
                            .size(200.dp)
                            .background(if (target == 0) Color.Red else Color.Blue)
                            .testTag("tab_$target"),
                    )
                }
            }
        }

        assertEquals("initial destination must be opaque", Color.Red.toArgb(), sceneCenter())
        composeRule.runOnIdle { page = 1 }
        var blendedFrame: Int? = null
        // Sample a bounded number of actual frames, without assuming a duration-based midpoint.
        // Both panels cover the center throughout this quarter-width slide. Over the black parent,
        // red + blue < 255 proves the outgoing layer fades too, rather than only the incoming one.
        for (frame in 0 until 20) {
            composeRule.mainClock.advanceTimeByFrame()
            val center = sceneCenter()
            val red = (center ushr 16) and 0xff
            val blue = center and 0xff
            if (red in 1..254 && blue in 1..254 && red + blue < 250) {
                blendedFrame = center
                break
            }
        }
        assertTrue("no rendered frame showed both destination layers fading", blendedFrame != null)
        // This is a completion bound, not an expectation about spring duration or its midpoint.
        composeRule.mainClock.advanceTimeBy(2_000)
        assertEquals("final destination must be opaque", Color.Blue.toArgb(), sceneCenter())
        composeRule.onNodeWithTag("tab_0").assertDoesNotExist()
    }

    private fun sceneCenter(): Int {
        val image = composeRule.onNodeWithTag("transition_scene").captureToImage()
        return image.toPixelMap()[image.width / 2, image.height / 2].toArgb()
    }
}
