package com.example.dimanow.update

import com.example.dimanow.sync.CampusDataManifest
import com.example.dimanow.sync.StaticDataTransport
import java.security.MessageDigest
import kotlinx.serialization.json.Json

/**
 * Reads the latest stable release from the `app_release` dataset on GitHub Pages (D-099 clause 8), published by the
 * data workflow from the GitHub release. Pages is a CDN without the 60-requests-per-hour limit that unauthenticated
 * GitHub API calls have per address, which many students share on campus. The payload keeps the GitHub release
 * shape and goes through the same [GitHubReleaseParser] checks. When Pages has no usable entry, [fallback] (the
 * GitHub API) is asked instead; the downloaded APK is verified the same way either way.
 */
class PagesAppUpdateSource(
    private val transport: StaticDataTransport,
    private val fallback: AppUpdateSource? = GitHubAppUpdateSource(),
    private val parser: GitHubReleaseParser = GitHubReleaseParser(),
) : AppUpdateSource {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun latestStableRelease(): AppUpdateRelease = runCatching { fromPages() }.getOrElse { error ->
        fallback?.latestStableRelease() ?: throw error
    }

    private suspend fun fromPages(): AppUpdateRelease {
        val manifest = json.decodeFromString<CampusDataManifest>(transport.get(MANIFEST_URL).decodeToString())
        require(manifest.schemaVersion == 1) { "지원하지 않는 동기화 스키마예요." }
        val descriptor = manifest.datasets[DATASET]?.takeIf { it.state == "READY" } ?: error("릴리스 정보가 아직 없어요.")
        require(descriptor.sourceUrl == SOURCE_URL) { "허용되지 않은 릴리스 원문 주소예요." }
        require(descriptor.url.matches(Regex("""app-release/[0-9a-f]{64}\.json"""))) { "잘못된 데이터 경로예요." }
        val bytes = transport.get("$DATA_ROOT/${descriptor.url}")
        require(bytes.size <= 64 * 1024 && bytes.sha256() == descriptor.sha256) { "릴리스 정보 무결성 검사에 실패했어요." }
        return parser.parse(bytes.decodeToString())
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private companion object {
        const val DATA_ROOT = "https://winter1l.github.io/DimaNow/data/v1"
        const val MANIFEST_URL = "$DATA_ROOT/manifest.json"
        const val DATASET = "app_release"
        const val SOURCE_URL = "https://github.com/winter1l/DimaNow/releases"
    }
}
