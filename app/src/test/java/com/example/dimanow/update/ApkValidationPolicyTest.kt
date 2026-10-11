package com.example.dimanow.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ApkValidationPolicyTest {
    private val release = AppUpdateRelease("1.2", "https://github.com/winter1l/DimaNow/releases/tag/v1.2", "https://github.com/winter1l/DimaNow/releases/download/v1.2/DIMA-Now-v1.2-optimized.apk", 10, "a".repeat(64))
    private val current = InstalledAppIdentity("com.example.dimanow", "1.1", 2, setOf("cert-a"))

    @Test fun `matching higher same-signer APK is accepted`() {
        assertEquals(ApkValidationResult.Valid, ApkValidationPolicy().validate(release, current, InstalledAppIdentity("com.example.dimanow", "1.2", 3, setOf("cert-a"))))
    }

    @Test fun `package version code and signer mismatches are rejected`() {
        assertEquals(ApkValidationResult.PackageMismatch, ApkValidationPolicy().validate(release, current, InstalledAppIdentity("evil.app", "1.2", 3, setOf("cert-a"))))
        assertEquals(ApkValidationResult.VersionNameMismatch, ApkValidationPolicy().validate(release, current, InstalledAppIdentity("com.example.dimanow", "9.9", 3, setOf("cert-a"))))
        assertEquals(ApkValidationResult.NotNewer, ApkValidationPolicy().validate(release, current, InstalledAppIdentity("com.example.dimanow", "1.2", 2, setOf("cert-a"))))
        assertEquals(ApkValidationResult.SignerMismatch, ApkValidationPolicy().validate(release, current, InstalledAppIdentity("com.example.dimanow", "1.2", 3, setOf("cert-b"))))
    }

    @Test fun `same signer without lineage is accepted`() {
        val candidate = InstalledAppIdentity("com.example.dimanow", "1.2", 3, setOf("cert-a"), listOf("cert-a"))
        assertEquals(ApkValidationResult.Valid, ApkValidationPolicy().validate(release, current, candidate))
    }

    @Test fun `rotated signer whose lineage contains the installed signer is accepted`() {
        val candidate = InstalledAppIdentity("com.example.dimanow", "1.2", 3, setOf("cert-release"), listOf("cert-a", "cert-release"))
        assertEquals(ApkValidationResult.Valid, ApkValidationPolicy().validate(release, current, candidate))
    }

    @Test fun `already rotated install accepts the next build signed by the same rotated key`() {
        val rotatedInstall = InstalledAppIdentity("com.example.dimanow", "1.1", 2, setOf("cert-release"), listOf("cert-a", "cert-release"))
        val candidate = InstalledAppIdentity("com.example.dimanow", "1.2", 3, setOf("cert-release"), listOf("cert-a", "cert-release"))
        assertEquals(ApkValidationResult.Valid, ApkValidationPolicy().validate(release, rotatedInstall, candidate))
    }

    @Test fun `unrelated signer is rejected`() {
        val candidate = InstalledAppIdentity("com.example.dimanow", "1.2", 3, setOf("cert-evil"), listOf("cert-evil"))
        assertEquals(ApkValidationResult.SignerMismatch, ApkValidationPolicy().validate(release, current, candidate))
    }

    @Test fun `rotated signer whose lineage lacks the installed signer is rejected`() {
        val candidate = InstalledAppIdentity("com.example.dimanow", "1.2", 3, setOf("cert-release"), listOf("cert-other", "cert-release"))
        assertEquals(ApkValidationResult.SignerMismatch, ApkValidationPolicy().validate(release, current, candidate))
    }

    @Test fun `lineage that does not end with the current signer is rejected`() {
        val candidate = InstalledAppIdentity("com.example.dimanow", "1.2", 3, setOf("cert-evil"), listOf("cert-a", "cert-release"))
        assertEquals(ApkValidationResult.SignerMismatch, ApkValidationPolicy().validate(release, current, candidate))
    }

    @Test fun `multiple-signer APK is rejected even when it includes the installed signer`() {
        val candidate = InstalledAppIdentity("com.example.dimanow", "1.2", 3, setOf("cert-a", "cert-b"), listOf("cert-a", "cert-b"))
        assertEquals(ApkValidationResult.SignerMismatch, ApkValidationPolicy().validate(release, current, candidate))
    }

    @Test fun `downgrade from rotated install to the old signer is rejected`() {
        val rotatedInstall = InstalledAppIdentity("com.example.dimanow", "1.1", 2, setOf("cert-release"), listOf("cert-a", "cert-release"))
        val candidate = InstalledAppIdentity("com.example.dimanow", "1.2", 3, setOf("cert-a"), listOf("cert-a"))
        assertEquals(ApkValidationResult.SignerMismatch, ApkValidationPolicy().validate(release, rotatedInstall, candidate))
    }

    @Test fun `only GitHub HTTPS release redirects are allowed`() {
        assertEquals(true, UpdateDownloadPolicy.isAllowedUrl("https://github.com/winter1l/DimaNow/releases/download/v1.2/app.apk"))
        assertEquals(true, UpdateDownloadPolicy.isAllowedUrl("https://release-assets.githubusercontent.com/github-production-release-asset/app.apk"))
        assertEquals(false, UpdateDownloadPolicy.isAllowedUrl("http://github.com/app.apk"))
        assertEquals(false, UpdateDownloadPolicy.isAllowedUrl("https://example.com/app.apk"))
    }
}
