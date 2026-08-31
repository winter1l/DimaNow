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
    fun incomingAndOutgoingTabsBothFadeDuringTheOriginalDirectionalTransition() {
        composeRule.mainClock.autoAdvance = false
        var page by mutableIntStateOf(0)
        composeRule.setContent {
            Box(Modifier.size(200.dp).background(Color.Black)) {
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

        composeRule.runOnIdle { page = 1 }
        composeRule.mainClock.advanceTimeBy(90)

        val center = composeRule.onNodeWithTag("tab_1").captureToImage().toPixelMap()[100, 100].toArgb()
        val red = (center ushr 16) and 0xff
        val blue = center and 0xff
        assertTrue("incoming tab was already opaque at the midpoint: ${center.toUInt().toString(16)}", red in 20..220 && blue in 40..235)
    }
}
