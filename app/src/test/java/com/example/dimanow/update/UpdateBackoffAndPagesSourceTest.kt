package com.example.dimanow.update

import com.example.dimanow.sync.StaticDataTransport
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBackoffAndPagesSourceTest {
    private val now = Instant.parse("2026-10-17T03:00:00Z")

    @Test
    fun `a failed automatic check waits six hours but a manual check always runs`() {
        val policy = AppUpdatePolicy()
        val failed = now.minus(Duration.ofHours(2))
        assertFalse(policy.shouldCheck(lastCheckedAt = null, now = now, manual = false, lastFailedAt = failed))
        assertTrue(policy.shouldCheck(lastCheckedAt = null, now = now, manual = true, lastFailedAt = failed))
        assertTrue(policy.shouldCheck(lastCheckedAt = null, now = now, manual = false, lastFailedAt = now.minus(Duration.ofHours(7))))
        assertFalse(policy.shouldCheck(lastCheckedAt = now.minus(Duration.ofHours(3)), now = now, manual = false))
    }

    private val release = (
        "{\"tag_name\":\"v2.0\",\"draft\":false,\"prerelease\":false," +
            "\"html_url\":\"https://github.com/winter1l/DimaNow/releases/tag/v2.0\"," +
            "\"assets\":[{\"name\":\"DIMA-Now-v2.0-optimized.apk\",\"size\":5854213," +
            "\"browser_download_url\":\"https://github.com/winter1l/DimaNow/releases/download/v2.0/DIMA-Now-v2.0-optimized.apk\"," +
            "\"digest\":\"sha256:" + "a".repeat(64) + "\"}]}"
        ).toByteArray()

    private fun manifest(sha: String) = (
        "{\"schemaVersion\":1,\"generatedAt\":\"2026-10-17T00:00:00Z\",\"datasets\":{\"app_release\":{\"revision\":1," +
            "\"state\":\"READY\",\"publishedAt\":\"2026-10-17T00:00:00Z\",\"lastAttemptAt\":\"2026-10-17T00:00:00Z\"," +
            "\"url\":\"app-release/$sha.json\",\"sha256\":\"$sha\"," +
            "\"sourceUrl\":\"https://github.com/winter1l/DimaNow/releases\",\"futureField\":true}}}"
        ).toByteArray()

    @Test
    fun `the latest release is read from Pages without calling the GitHub API`() = runBlocking {
        val sha = MessageDigest.getInstance("SHA-256").digest(release).joinToString("") { "%02x".format(it) }
        val requested = mutableListOf<String>()
        val transport = StaticDataTransport { url ->
            requested += url
            if (url.endsWith("manifest.json")) manifest(sha) else release
        }
        val fallback = object : AppUpdateSource {
            override suspend fun latestStableRelease(): AppUpdateRelease = error("GitHub API must not be called")
        }

        val result = PagesAppUpdateSource(transport, fallback).latestStableRelease()

        assertEquals("2.0", result.versionName)
        assertEquals("a".repeat(64), result.sha256)
        assertTrue(requested.none { it.contains("api.github.com") })
    }

    @Test
    fun `a tampered Pages payload falls back to the GitHub API`() = runBlocking {
        val transport = StaticDataTransport { url -> if (url.endsWith("manifest.json")) manifest("b".repeat(64)) else release }
        val expected = AppUpdateRelease("2.0", "https://github.com/winter1l/DimaNow/releases/tag/v2.0", "https://github.com/x", 1, "c".repeat(64))
        val fallback = object : AppUpdateSource {
            override suspend fun latestStableRelease(): AppUpdateRelease = expected
        }

        assertEquals(expected, PagesAppUpdateSource(transport, fallback).latestStableRelease())
    }
}
