package com.example.dimanow.live

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MinuteRefreshGateTest {
    @Test
    fun `the ticker and the alarm refresh each minute once`() {
        // 2026-10-23 12:00:00 UTC, far from any time another test claims.
        val boundary = 1_792_756_800_000L
        assertEquals(boundary, MinuteRefreshGate.claim(boundary + 5))
        assertNull(MinuteRefreshGate.claim(boundary + 40_000))
        // A tick slightly early counts for the coming minute, and that minute is then taken.
        assertEquals(boundary + 60_000, MinuteRefreshGate.claim(boundary + 60_000 - 800))
        assertNull(MinuteRefreshGate.claim(boundary + 60_000 + 3))
        assertEquals(boundary + 120_000, MinuteRefreshGate.claim(boundary + 120_000))
    }

    @Test
    fun `an early tick refreshes only once the wall clock reaches its minute`() = runTest {
        val minuteStart = 1_792_756_860_000L
        // The wall clock starts 800 ms before the boundary and follows virtual time.
        val wallClock = { minuteStart - 800 + currentTime }
        MinuteRefreshGate.awaitStart(minuteStart, wallClock)
        assertEquals(800L, currentTime)
        // On or after the boundary there is no wait.
        MinuteRefreshGate.awaitStart(minuteStart, wallClock)
        assertEquals(800L, currentTime)
    }

    @Test
    fun `a clock set back during the wait does not hold the refresh`() = runTest {
        val minuteStart = 1_792_756_860_000L
        MinuteRefreshGate.awaitStart(minuteStart) { minuteStart - 3_600_000 }
        assertEquals(3_000L, currentTime)
    }
}
