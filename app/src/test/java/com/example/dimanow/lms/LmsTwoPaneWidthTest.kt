package com.example.dimanow.lms

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-131 (Q-025): list and detail side by side from the M3 expanded width only. */
class LmsTwoPaneWidthTest {
    @Test
    fun `phones and the rail's medium width keep one pane`() {
        assertFalse(lmsUsesTwoPane(411.dp))
        assertFalse(lmsUsesTwoPane(600.dp))
        assertFalse(lmsUsesTwoPane(839.dp))
    }

    @Test
    fun `expanded windows show list and detail together`() {
        assertTrue(lmsUsesTwoPane(840.dp))
        assertTrue(lmsUsesTwoPane(1280.dp))
    }
}
