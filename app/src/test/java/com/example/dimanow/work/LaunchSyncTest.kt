package com.example.dimanow.work

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-131 (Q-032 (g)): a process start skips the launch sync only right after a full sync. */
class LaunchSyncTest {
    private val now = Instant.parse("2026-10-26T03:00:00Z")

    @Test
    fun `the first sync is never skipped`() {
        assertTrue(launchSyncDue(now, listOf(null, null, null)))
        // One source that never succeeded is enough, even if the others just did.
        assertTrue(launchSyncDue(now, listOf(now.minusSeconds(60), null, now.minusSeconds(60))))
        assertTrue(launchSyncDue(now, emptyList()))
    }

    @Test
    fun `a start right after every source succeeded skips the sync`() {
        val recent = now.minus(Duration.ofMinutes(10))
        assertFalse(launchSyncDue(now, listOf(recent, recent, recent)))
        assertFalse(launchSyncDue(now, listOf(now, now.minus(Duration.ofMinutes(29)), recent)))
    }

    @Test
    fun `one stale source lets the sync run`() {
        val recent = now.minus(Duration.ofMinutes(5))
        assertTrue(launchSyncDue(now, listOf(recent, now.minus(Duration.ofMinutes(30)), recent)))
        assertTrue(launchSyncDue(now, listOf(now.minus(Duration.ofHours(12)), recent, recent)))
    }

    @Test
    fun `a success in the future after a clock change does not count as recent`() {
        val recent = now.minus(Duration.ofMinutes(5))
        assertTrue(launchSyncDue(now, listOf(recent, recent, now.plus(Duration.ofMinutes(1)))))
    }

    @Test
    fun `the threshold is the data pipeline's shortest publishing interval`() {
        assertEquals(Duration.ofMinutes(30), LAUNCH_SYNC_FRESH_FOR)
    }
}
