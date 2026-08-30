package com.example.dimanow.update

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface ReleaseJsonTransport {
    suspend fun getLatestReleaseJson(): String
}

class GitHubAppUpdateSource(
    private val transport: ReleaseJsonTransport = GitHubReleaseJsonTransport(),
    private val parser: GitHubReleaseParser = GitHubReleaseParser(),
) : AppUpdateSource {
    override suspend fun latestStableRelease(): AppUpdateRelease = parser.parse(transport.getLatestReleaseJson())
}

class GitHubReleaseJsonTransport(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReleaseJsonTransport {
    override suspend fun getLatestReleaseJson(): String = withContext(ioDispatcher) {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "DIMA-Now/1.2")
            require(connection.responseCode == HttpURLConnection.HTTP_OK) { "GitHub 릴리스 응답 ${connection.responseCode}" }
            val length = connection.contentLengthLong
            require(length == -1L || length in 1..MAX_JSON_BYTES) { "GitHub 릴리스 응답이 너무 큽니다." }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_JSON_BYTES) { "GitHub 릴리스 응답이 너무 큽니다." }
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/winter1l/DimaNow/releases/latest"
        const val MAX_JSON_BYTES = 1024 * 1024
    }
}
