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

/** D-124: Home's nearest departure is always a round pill in primary; later departures are 12dp tiles. */
class ImminentDepartureShapeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun nearestCapsuleIsRoundAndLaterOnesAreTiles() {
        var isNext by mutableStateOf(true)
        composeRule.setContent {
            DIMANowTheme(darkTheme = false) {
                Box(Modifier.testTag("capsule_scene").background(Color.Black).padding(8.dp)) {
                    DepartureCapsule(
                        countdown = "3분 후",
                        clock = "21:30",
                        markers = emptyList(),
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        isNext = isNext,
                        modifier = Modifier.width(160.dp).testTag("capsule"),
                    )
                }
            }
        }
        // 6dp in from the corner lies inside a 12dp corner but outside a pill's (about half the capsule height).
        assertEquals("a pill leaves its corner empty", Color.Black.toArgb(), cornerProbe())
        composeRule.runOnIdle { isNext = false }
        composeRule.waitForIdle()
        assertEquals("a later departure is a tile that fills near its corner", Color.White.toArgb(), cornerProbe())
    }

    private fun cornerProbe(): Int {
        val image = composeRule.onNodeWithTag("capsule_scene").captureToImage()
        val inset = with(composeRule.density) { (8.dp + 6.dp).roundToPx() }
        return image.toPixelMap()[inset, inset].toArgb()
    }
}
