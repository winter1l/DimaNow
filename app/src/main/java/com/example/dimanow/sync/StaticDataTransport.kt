package com.example.dimanow.sync

import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface StaticDataTransport {
    suspend fun get(url: String): ByteArray
}

class CachingStaticDataTransport(
    private val delegate: StaticDataTransport,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val manifestTtlMillis: Long = 60_000L,
) : StaticDataTransport {
    private val mutex = Mutex()
    private var cachedManifest: ByteArray? = null
    private var cachedAtMillis: Long = Long.MIN_VALUE

    override suspend fun get(url: String): ByteArray {
        if (!url.endsWith("/manifest.json")) return delegate.get(url)
        return mutex.withLock {
            val now = nowMillis()
            cachedManifest?.takeIf { now - cachedAtMillis in 0 until manifestTtlMillis }?.copyOf()
                ?: delegate.get(url).also { bytes ->
                    cachedManifest = bytes.copyOf()
                    cachedAtMillis = now
                }
        }
    }
}

class UrlConnectionStaticDataTransport : StaticDataTransport {
    override suspend fun get(url: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "DIMA-Now/1.2")
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "동기화 서버 응답 ${connection.responseCode}"
            }
            val length = connection.contentLengthLong
            require(length <= MAX_RESPONSE_BYTES || length == -1L) { "동기화 응답이 너무 큽니다." }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(if (length in 1..MAX_RESPONSE_BYTES) length.toInt() else 8_192)
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_RESPONSE_BYTES) { "동기화 응답이 너무 큽니다." }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 5 * 1024 * 1024
    }
}
