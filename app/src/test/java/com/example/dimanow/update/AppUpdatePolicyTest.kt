package com.example.dimanow.update

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    private val now = Instant.parse("2026-08-31T00:00:00Z")

    @Test fun `automatic check runs after 24 hours`() {
        assertTrue(AppUpdatePolicy().shouldCheck(now.minus(Duration.ofHours(24)), now, manual = false))
    }

    @Test fun `automatic check is suppressed inside 24 hours but manual ignores ttl`() {
        val recent = now.minus(Duration.ofHours(23))
        assertFalse(AppUpdatePolicy().shouldCheck(recent, now, manual = false))
        assertTrue(AppUpdatePolicy().shouldCheck(recent, now, manual = true))
    }

    @Test fun `popup appears once for each discovered version`() {
        val policy = AppUpdatePolicy()
        assertTrue(policy.shouldPrompt("1.2", dismissedVersion = null))
        assertFalse(policy.shouldPrompt("1.2", dismissedVersion = "1.2"))
        assertTrue(policy.shouldPrompt("1.3", dismissedVersion = "1.2"))
    }

    @Test fun `prepared APK is retained only while it is newer than the installed app`() {
        val policy = AppUpdatePolicy()
        assertTrue(policy.shouldKeepPrepared("1.2", currentVersion = "1.1"))
        assertFalse(policy.shouldKeepPrepared("1.2", currentVersion = "1.2"))
        assertFalse(policy.shouldKeepPrepared("1.2", currentVersion = "1.3"))
    }

    @Test fun `prepared APK is reused only for the unchanged release asset`() {
        val oldRelease = AppUpdateRelease("1.2", "https://github.com/winter1l/DimaNow/releases/tag/v1.2", "https://github.com/winter1l/DimaNow/releases/download/v1.2/DIMA-Now-v1.2-optimized.apk", 10, "a".repeat(64))
        val replacedAsset = oldRelease.copy(sha256 = "b".repeat(64))
        val policy = AppUpdatePolicy()

        assertTrue(policy.shouldReusePrepared("1.2", oldRelease, oldRelease))
        assertFalse(policy.shouldReusePrepared("1.2", oldRelease, replacedAsset))
    }
}
