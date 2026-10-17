package com.example.dimanow.pipeline

import com.example.dimanow.sync.CampusDataManifest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-099 datasets: service calendar, app release metadata and the owner's dormitory take-down. */
class LaunchDatasetsPublisherTest {
    private val now = Instant.parse("2026-10-17T03:00:00Z")
    private val json = Json { ignoreUnknownKeys = true }

    private fun manifest(site: Path) = json.decodeFromString<CampusDataManifest>(Files.readString(site.resolve("data/v1/manifest.json")))

    private fun projectRoot(): Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("data-source/calendar.json")) }

    @Test
    fun `the admin calendar file publishes as the calendar dataset`() {
        val site = Files.createTempDirectory("calendar-publish")
        StaticDataPublisher(site).publishCalendar(Files.readString(projectRoot().resolve("data-source/calendar.json")), 1, now)

        val descriptor = manifest(site).datasets.getValue("calendar")
        assertEquals("READY", descriptor.state)
        assertEquals("https://www.dima.ac.kr/?p=78", descriptor.sourceUrl)
        assertTrue(descriptor.url.matches(Regex("calendar/[0-9a-f]{64}\\.json")))
    }

    @Test
    fun `an unsorted or duplicated calendar is refused`() {
        val site = Files.createTempDirectory("calendar-invalid")
        val duplicated = """{"schemaVersion":1,"terms":[],"holidays":[{"date":"2027-01-01","name":"1월 1일"},{"date":"2027-01-01","name":"1월 1일"}]}"""
        assertThrows(IllegalArgumentException::class.java) { StaticDataPublisher(site).publishCalendar(duplicated, 1, now) }
        assertFalse(Files.exists(site.resolve("data/v1/manifest.json")))
    }

    private val release = """
        {"tag_name":"v2.0","draft":false,"prerelease":false,"html_url":"https://github.com/winter1l/DimaNow/releases/tag/v2.0",
         "published_at":"2026-10-17T00:00:00Z","author":{"login":"someone"},"body":"notes",
         "assets":[{"name":"DIMA-Now-v2.0-optimized.apk","size":5854213,"digest":"sha256:${"A".repeat(64)}",
           "browser_download_url":"https://github.com/winter1l/DimaNow/releases/download/v2.0/DIMA-Now-v2.0-optimized.apk"},
           {"name":"other.zip","size":1,"browser_download_url":"https://example.com/x"}]}
    """.trimIndent()

    @Test
    fun `the latest release is trimmed to the fields the app checks`() {
        val site = Files.createTempDirectory("release-publish")
        StaticDataPublisher(site).publishAppRelease(release, 1, now)

        val descriptor = manifest(site).datasets.getValue("app_release")
        val payload = Files.readString(site.resolve("data/v1").resolve(descriptor.url))
        assertTrue(payload.contains("DIMA-Now-v2.0-optimized.apk"))
        assertTrue(payload.contains("sha256:" + "a".repeat(64)))
        assertFalse(payload.contains("other.zip"))
        assertFalse(payload.contains("someone"))
    }

    @Test
    fun `an old com-example v1 release is never offered as the public download`() {
        val site = Files.createTempDirectory("release-old")
        StaticDataPublisher(site).publishAppRelease(release.replace("v2.0", "v1.6"), 1, now)

        val descriptor = manifest(site).datasets.getValue("app_release")
        assertEquals("WAITING", descriptor.state)
    }

    @Test
    fun `a release from another repository or without a digest is refused`() {
        val site = Files.createTempDirectory("release-invalid")
        assertThrows(IllegalArgumentException::class.java) {
            StaticDataPublisher(site).publishAppRelease(release.replace("winter1l/DimaNow/releases/download", "evil/DimaNow/releases/download"), 1, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StaticDataPublisher(site).publishAppRelease(release.replace("sha256:", "md5:"), 1, now)
        }
    }

    @Test
    fun `taking down a dormitory week marks it revoked and needs a published week`() {
        val site = Files.createTempDirectory("dorm-revoke")
        val publisher = StaticDataPublisher(site)
        assertThrows(IllegalArgumentException::class.java) { publisher.revokeDormitoryMeal(now) }

        publisher.recordFailure("dorm_meal", "WAITING", "등록된 식단 없음", now)
        val manifestPath = site.resolve("data/v1/manifest.json")
        Files.writeString(manifestPath, Files.readString(manifestPath).replace("\"WAITING\"", "\"READY\""))
        publisher.revokeDormitoryMeal(now)

        val descriptor = manifest(site).datasets.getValue("dorm_meal")
        assertEquals("REVOKED", descriptor.state)
        assertEquals(StaticDataPublisher.REVOKED_MESSAGE, descriptor.message)
    }
}
