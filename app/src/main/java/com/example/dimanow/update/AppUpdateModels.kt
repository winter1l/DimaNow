package com.example.dimanow.update

import java.net.URI
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.long

data class AppUpdateRelease(
    val versionName: String,
    val releasePageUrl: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)

interface AppUpdateSource {
    suspend fun latestStableRelease(): AppUpdateRelease
}

interface AppUpdateInstaller {
    suspend fun downloadAndVerify(release: AppUpdateRelease, onProgress: (Int) -> Unit): PreparedUpdate
    fun requestInstall(update: PreparedUpdate): AppInstallRequestResult
    fun cleanupIncompleteDownloads()
}

data class PreparedUpdate(val release: AppUpdateRelease, val absolutePath: String)

enum class AppInstallRequestResult { INSTALLER_OPENED, PERMISSION_REQUIRED, UNAVAILABLE }

class AppVersion(value: String) : Comparable<AppVersion> {
    private val components = value.split('.').map { part -> part.toIntOrNull() ?: throw IllegalArgumentException("잘못된 버전입니다: $value") }
        .dropLastWhile { it == 0 }

    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(components.size, other.components.size)
        for (index in 0 until size) {
            val comparison = (components.getOrElse(index) { 0 }).compareTo(other.components.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0
    override fun hashCode(): Int = components.hashCode()
    override fun toString(): String = if (components.isEmpty()) "0" else components.joinToString(".")
}

class AppUpdatePolicy {
    fun shouldCheck(lastCheckedAt: Instant?, now: Instant, manual: Boolean): Boolean = manual || lastCheckedAt == null ||
        !now.isBefore(lastCheckedAt.plus(Duration.ofHours(24)))

    fun shouldPrompt(latestVersion: String, dismissedVersion: String?): Boolean = latestVersion != dismissedVersion

    fun shouldKeepPrepared(preparedVersion: String, currentVersion: String): Boolean =
        AppVersion(preparedVersion) > AppVersion(currentVersion)
}

data class InstalledAppIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signerSha256: Set<String>,
)

sealed interface ApkValidationResult {
    data object Valid : ApkValidationResult
    data object PackageMismatch : ApkValidationResult
    data object VersionNameMismatch : ApkValidationResult
    data object NotNewer : ApkValidationResult
    data object SignerMismatch : ApkValidationResult
}

class ApkValidationPolicy {
    fun validate(release: AppUpdateRelease, current: InstalledAppIdentity, candidate: InstalledAppIdentity): ApkValidationResult = when {
        candidate.packageName != current.packageName -> ApkValidationResult.PackageMismatch
        candidate.versionName != release.versionName -> ApkValidationResult.VersionNameMismatch
        candidate.versionCode <= current.versionCode -> ApkValidationResult.NotNewer
        candidate.signerSha256.isEmpty() || current.signerSha256.isEmpty() || candidate.signerSha256 != current.signerSha256 -> ApkValidationResult.SignerMismatch
        else -> ApkValidationResult.Valid
    }
}

object UpdateDownloadPolicy {
    const val MAX_APK_BYTES = 128L * 1024 * 1024
    private val allowedHosts = setOf(
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )

    fun isAllowedUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host?.lowercase() in allowedHosts && uri.userInfo == null
    }.getOrDefault(false)
}

class GitHubReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): AppUpdateRelease {
        val root = json.parseToJsonElement(payload) as? JsonObject ?: error("GitHub 릴리스 응답이 객체가 아닙니다.")
        val tagName = root.string("tag_name")
        require(!root.boolean("draft") && !root.boolean("prerelease")) { "안정 릴리스가 아닙니다." }
        require(tagName.matches(Regex("v[0-9]+\\.[0-9]+(?:\\.[0-9]+)?"))) { "지원하지 않는 릴리스 태그입니다." }
        val version = tagName.removePrefix("v")
        val expectedAsset = "DIMA-Now-$tagName-optimized.apk"
        val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { it.string("name") == expectedAsset }
        require(assets.size == 1) { "optimized APK asset을 하나만 선택할 수 있어야 합니다." }
        val asset = assets.single()
        val size = (asset["size"] as? JsonPrimitive)?.long ?: error("APK 크기가 없습니다.")
        val downloadUrl = asset.string("browser_download_url")
        val htmlUrl = root.string("html_url")
        require(size in 1..UpdateDownloadPolicy.MAX_APK_BYTES) { "APK 크기가 올바르지 않습니다." }
        require(UpdateDownloadPolicy.isAllowedUrl(downloadUrl)) { "허용되지 않은 APK 주소입니다." }
        require(htmlUrl.startsWith("https://github.com/winter1l/DimaNow/releases/")) { "허용되지 않은 릴리스 주소입니다." }
        val digest = (asset["digest"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.matches(Regex("sha256:[0-9a-fA-F]{64}")) }
            ?: throw IllegalArgumentException("GitHub asset SHA-256이 없습니다.")
        return AppUpdateRelease(version, htmlUrl, downloadUrl, size, digest.substringAfter(':').lowercase())
    }

    private fun JsonObject.string(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull
        ?: throw IllegalArgumentException("GitHub 릴리스 응답에 $key 값이 없습니다.")

    private fun JsonObject.boolean(key: String): Boolean = (get(key) as? JsonPrimitive)?.boolean
        ?: throw IllegalArgumentException("GitHub 릴리스 응답에 $key 값이 없습니다.")
}
