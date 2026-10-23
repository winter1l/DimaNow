package com.example.dimanow.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinuteRefreshGateTest {
    @Test
    fun `the ticker and the alarm refresh each minute once`() {
        // 2026-10-23 12:00:00 UTC, far from any time another test claims.
        val boundary = 1_792_756_800_000L
        assertTrue(MinuteRefreshGate.claim(boundary + 5))
        assertFalse(MinuteRefreshGate.claim(boundary + 40_000))
        // A tick slightly early counts for the coming minute, and that minute is then taken.
        assertTrue(MinuteRefreshGate.claim(boundary + 60_000 - 800))
        assertFalse(MinuteRefreshGate.claim(boundary + 60_000 + 3))
        assertTrue(MinuteRefreshGate.claim(boundary + 120_000))
    }
}
