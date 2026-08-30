package com.example.dimanow.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateSourceTest {
    @Test
    fun `latest stable GitHub release selects the exact optimized APK with digest`() {
        val json = """{"tag_name":"v1.2","draft":false,"prerelease":false,"html_url":"https://github.com/winter1l/DimaNow/releases/tag/v1.2","assets":[{"name":"DIMA-Now-v1.2-optimized.apk","browser_download_url":"https://github.com/winter1l/DimaNow/releases/download/v1.2/DIMA-Now-v1.2-optimized.apk","size":4628357,"digest":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]}"""

        val release = GitHubReleaseParser().parse(json)

        assertEquals("1.2", release.versionName)
        assertEquals(4_628_357L, release.sizeBytes)
        assertEquals("a".repeat(64), release.sha256)
    }

    @Test
    fun `draft prerelease missing digest and wrong asset are rejected`() {
        val invalid = listOf(
            """{"tag_name":"v1.2","draft":true,"prerelease":false,"html_url":"https://github.com/x","assets":[]}""",
            """{"tag_name":"v1.2","draft":false,"prerelease":true,"html_url":"https://github.com/x","assets":[]}""",
            """{"tag_name":"v1.2","draft":false,"prerelease":false,"html_url":"https://github.com/x","assets":[{"name":"DIMA-Now-v1.2-optimized.apk","browser_download_url":"https://github.com/x.apk","size":1,"digest":null}]}""",
            """{"tag_name":"v1.2","draft":false,"prerelease":false,"html_url":"https://github.com/x","assets":[{"name":"other.apk","browser_download_url":"https://github.com/x.apk","size":1,"digest":"sha256:${"b".repeat(64)}"}]}""",
        )

        invalid.forEach { payload -> assertTrue(runCatching { GitHubReleaseParser().parse(payload) }.isFailure) }
    }

    @Test
    fun `release page must be the exact trusted repository releases path`() {
        val payload = """{"tag_name":"v1.2","draft":false,"prerelease":false,"html_url":"https://github.com/winter1l/DimaNow/releases.evil/tag/v1.2","assets":[{"name":"DIMA-Now-v1.2-optimized.apk","browser_download_url":"https://github.com/winter1l/DimaNow/releases/download/v1.2/DIMA-Now-v1.2-optimized.apk","size":10,"digest":"sha256:${"a".repeat(64)}"}]}"""

        assertTrue(runCatching { GitHubReleaseParser().parse(payload) }.isFailure)
    }

    @Test
    fun `semantic versions compare by numeric components`() {
        assertTrue(AppVersion("1.2") > AppVersion("1.1"))
        assertTrue(AppVersion("1.10") > AppVersion("1.9"))
        assertEquals(AppVersion("1.2.0"), AppVersion("1.2"))
    }
}
