package com.example.dimanow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DIMANowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** D-121: the imminent departure capsule turns from a 12dp tile into a pill, and back when it is no longer imminent. */
class ImminentDepartureShapeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun imminentCapsuleRoundsItsCornersIntoAPill() {
        var imminent by mutableStateOf(false)
        composeRule.setContent {
            DIMANowTheme(darkTheme = false) {
                Box(Modifier.testTag("capsule_scene").background(Color.Black).padding(8.dp)) {
                    DepartureCapsule(
                        label = "3분 후",
                        clock = "21:30",
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        imminent = imminent,
                        modifier = Modifier.width(160.dp).testTag("capsule"),
                    )
                }
            }
        }
        // 6dp in from the corner lies inside a 12dp corner but outside a pill's (about half the capsule height).
        assertEquals("a tile fills near its corner", Color.White.toArgb(), cornerProbe())
        composeRule.runOnIdle { imminent = true }
        composeRule.waitForIdle()
        assertEquals("a pill leaves its corner empty", Color.Black.toArgb(), cornerProbe())
        composeRule.runOnIdle { imminent = false }
        composeRule.waitForIdle()
        assertEquals("the tile comes back once the departure is no longer imminent", Color.White.toArgb(), cornerProbe())
    }

    private fun cornerProbe(): Int {
        val image = composeRule.onNodeWithTag("capsule_scene").captureToImage()
        val inset = with(composeRule.density) { (8.dp + 6.dp).roundToPx() }
        return image.toPixelMap()[inset, inset].toArgb()
    }
}
