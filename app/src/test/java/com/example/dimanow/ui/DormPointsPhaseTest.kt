package com.example.dimanow.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DormPointsPhaseTest {
    @Test
    fun `a saved read stays on screen through a refresh`() {
        assertEquals(DormPointsPhase.SUMMARY, dormPointsPhase(hasRead = true, loading = false))
        // Pull-to-refresh updates the numbers in place instead of swapping the card for a loading line.
        assertEquals(DormPointsPhase.SUMMARY, dormPointsPhase(hasRead = true, loading = true))
    }

    @Test
    fun `without a read the card asks to read, then shows the read in progress`() {
        assertEquals(DormPointsPhase.PROMPT, dormPointsPhase(hasRead = false, loading = false))
        assertEquals(DormPointsPhase.LOADING, dormPointsPhase(hasRead = false, loading = true))
    }
}
