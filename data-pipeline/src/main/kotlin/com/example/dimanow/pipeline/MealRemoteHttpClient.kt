package com.example.dimanow.pipeline

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL

internal enum class MealRemoteEndpoint {
    DIMA_DISCOVERY,
    INSTAGRAM_PROFILE,
    INSTAGRAM_POST,
    INSTAGRAM_EMBED,
    INSTAGRAM_IMAGE,
}

internal data class DownloadedImage(val bytes: ByteArray, val mimeType: String)

internal object MealRemoteUrlPolicy {
    fun canonicalInstagramPostOrNull(value: String): String? = runCatching {
        canonical(value, MealRemoteEndpoint.INSTAGRAM_POST).toASCIIString()
    }.getOrNull()

    fun requireAllowed(
        value: String,
        endpoint: MealRemoteEndpoint,
        resolver: (String) -> Array<InetAddress>,
    ): URI {
        val uri = canonical(value, endpoint)
        val addresses = resolver(checkNotNull(uri.host))
        require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) {
            "허용되지 않은 원격 주소입니다."
        }
        return uri
    }

    private fun canonical(value: String, endpoint: MealRemoteEndpoint): URI {
        val uri = URI.create(value)
        require(uri.scheme.equals("https", ignoreCase = true)) { "HTTPS 주소만 허용됩니다." }
        require(uri.userInfo == null && uri.port in setOf(-1, 443) && uri.rawFragment == null) {
            "허용되지 않은 URL 권한입니다."
        }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("URL host가 없습니다.")
        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery
        val allowed = when (endpoint) {
            MealRemoteEndpoint.DIMA_DISCOVERY ->
                host in setOf("www.dima.ac.kr", "dima.ac.kr") && path in setOf("", "/") && query == "p=1"
            MealRemoteEndpoint.INSTAGRAM_PROFILE ->
                host == "www.instagram.com" && path == "/api/v1/feed/user/30891067635/" && query == "count=12"
            MealRemoteEndpoint.INSTAGRAM_POST ->
                host == "www.instagram.com" && POST_PATH.matches(path) && query == null
            MealRemoteEndpoint.INSTAGRAM_EMBED ->
                host == "www.instagram.com" && EMBED_PATH.matches(path) && query == null
            MealRemoteEndpoint.INSTAGRAM_IMAGE ->
                (host.endsWith(".cdninstagram.com") || host.endsWith(".fbcdn.net")) && path.startsWith('/') && path.length > 1
        }
        require(allowed) { "허용되지 않은 식단 원격 URL입니다." }
        return URI.create(buildString {
            append("https://")
            append(host)
            append(path)
            if (query != null) {
                append('?')
                append(query)
            }
        })
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val bytes = address.address.map(Byte::toInt).map { it and 0xff }
        if (bytes.size == 4) {
            val first = bytes[0]
            val second = bytes[1]
            val third = bytes[2]
            return when {
                first == 0 || first == 10 || first == 127 || first >= 224 -> false
                first == 100 && second in 64..127 -> false
                first == 169 && second == 254 -> false
                first == 172 && second in 16..31 -> false
                first == 192 && second == 168 -> false
                first == 192 && second == 0 && third in setOf(0, 2) -> false
                first == 192 && second == 88 && third == 99 -> false
                first == 198 && second in 18..19 -> false
                first == 198 && second == 51 && third == 100 -> false
                first == 203 && second == 0 && third == 113 -> false
                else -> true
            }
        }
        if (bytes.size != 16 || bytes[0] !in 0x20..0x3f) return false
        val isIpv6Documentation = bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8
        val isIpv6TransitionOrOrchid = bytes[0] == 0x20 && (
            (bytes[1] == 0x01 && bytes[2] == 0x00 && (bytes[3] == 0x00 || bytes[3] in 0x10..0x2f)) ||
                bytes[1] == 0x02
            )
        return !isIpv6Documentation && !isIpv6TransitionOrOrchid
    }

    private val POST_PATH = Regex("/p/[A-Za-z0-9_-]{1,32}/")
    private val EMBED_PATH = Regex("/p/[A-Za-z0-9_-]{1,32}/embed/captioned/")
}

internal class MealRemoteHttpClient(
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val resolver: (String) -> Array<InetAddress> = InetAddress::getAllByName,
) {
    fun getText(
        url: String,
        endpoint: MealRemoteEndpoint,
        headers: Map<String, String> = emptyMap(),
    ): String = request(url, endpoint, headers, MAX_TEXT_BYTES, requireImage = false).bytes.toString(Charsets.UTF_8)

    fun getImage(url: String): DownloadedImage = request(
        url,
        MealRemoteEndpoint.INSTAGRAM_IMAGE,
        emptyMap(),
        MAX_IMAGE_BYTES,
        requireImage = true,
    )

    private fun request(
        initialUrl: String,
        endpoint: MealRemoteEndpoint,
        headers: Map<String, String>,
        maxBytes: Int,
        requireImage: Boolean,
    ): DownloadedImage {
        var current = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val uri = MealRemoteUrlPolicy.requireAllowed(current, endpoint, resolver)
            val connection = connectionFactory(uri.toURL())
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", USER_AGENT)
                headers.forEach(connection::setRequestProperty)
                val status = connection.responseCode
                if (status in 300..399) {
                    require(redirectCount < MAX_REDIRECTS) { "원격 URL redirect가 너무 많습니다." }
                    val location = connection.getHeaderField("Location")?.takeIf(String::isNotBlank)
                        ?: error("원격 URL redirect 위치가 없습니다.")
                    current = uri.resolve(location).toASCIIString()
                    return@repeat
                }
                require(status == HttpURLConnection.HTTP_OK) { "식단 원격 응답 $status" }
                require(connection.contentLengthLong in -1L..maxBytes.toLong()) { "식단 원격 응답이 너무 큽니다." }
                val bytes = connection.inputStream.use { it.readNBytes(maxBytes + 1) }
                require(bytes.size <= maxBytes) { "식단 원격 응답이 너무 큽니다." }
                val mimeType = connection.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                if (requireImage) require(mimeType.startsWith("image/")) { "식단 파일이 이미지가 아닙니다." }
                return DownloadedImage(bytes, mimeType)
            } finally {
                connection.disconnect()
            }
        }
        error("원격 URL redirect를 완료하지 못했습니다.")
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_TEXT_BYTES = 4 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 15 * 1024 * 1024
        const val USER_AGENT = "DIMA-Now/1.4 GitHub data pipeline"
    }
}
