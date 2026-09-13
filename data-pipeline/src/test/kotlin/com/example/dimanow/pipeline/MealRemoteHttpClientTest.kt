package com.example.dimanow.pipeline

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MealRemoteHttpClientTest {
    @Test
    fun `redirect destination is revalidated before a second connection`() {
        val connected = mutableListOf<String>()
        val client = MealRemoteHttpClient(
            connectionFactory = { url ->
                connected += url.toString()
                StubConnection(
                    url,
                    status = 302,
                    location = "https://169.254.169.254/latest/meta-data/",
                )
            },
            resolver = { arrayOf(InetAddress.getByName("8.8.8.8")) },
        )

        assertThrows(IllegalArgumentException::class.java) {
            client.getText(
                "https://www.instagram.com/p/fixture/embed/captioned/",
                MealRemoteEndpoint.INSTAGRAM_EMBED,
            )
        }
        assertEquals(listOf("https://www.instagram.com/p/fixture/embed/captioned/"), connected)
    }

    @Test
    fun `approved CDN authority resolving to a private address is rejected`() {
        var connections = 0
        val client = MealRemoteHttpClient(
            connectionFactory = { url -> connections += 1; StubConnection(url, 200, body = byteArrayOf(1)) },
            resolver = { arrayOf(InetAddress.getByName("127.0.0.1")) },
        )

        assertThrows(IllegalArgumentException::class.java) {
            client.getImage("https://scontent.cdninstagram.com/meal.jpg")
        }
        assertEquals(0, connections)
    }

    @Test
    fun `approved authority resolving to non-global reserved addresses is rejected`() {
        listOf(
            "100.64.0.1",
            "192.0.2.1",
            "198.51.100.1",
            "203.0.113.1",
            "2001:db8::1",
        ).forEach { address ->
            var connections = 0
            val client = MealRemoteHttpClient(
                connectionFactory = { url -> connections += 1; StubConnection(url, 200, body = byteArrayOf(1)) },
                resolver = { arrayOf(InetAddress.getByName(address)) },
            )

            assertThrows(IllegalArgumentException::class.java) {
                client.getImage("https://scontent.cdninstagram.com/meal.jpg")
            }
            assertEquals(address, 0, connections)
        }
    }

    @Test
    fun `an allowed public redirect hop can return a bounded image`() {
        val connected = mutableListOf<String>()
        val responses = ArrayDeque(
            listOf(
                StubSpec(302, "https://scontent-lax3-2.cdninstagram.com/meal.jpg"),
                StubSpec(200, body = byteArrayOf(1, 2, 3), contentType = "image/jpeg"),
            ),
        )
        val client = MealRemoteHttpClient(
            connectionFactory = { url ->
                connected += url.toString()
                responses.removeFirst().toConnection(url)
            },
            resolver = { arrayOf(InetAddress.getByName("8.8.8.8")) },
        )

        val image = client.getImage("https://scontent.cdninstagram.com/start.jpg")

        assertEquals(listOf<Byte>(1, 2, 3), image.bytes.toList())
        assertEquals("image/jpeg", image.mimeType)
        assertEquals(2, connected.size)
    }

    @Test
    fun `approved CDN percent escapes and signed query are not double encoded`() {
        val connected = mutableListOf<String>()
        val client = MealRemoteHttpClient(
            connectionFactory = { url ->
                connected += url.toString()
                StubConnection(url, 200, body = byteArrayOf(1), mimeType = "image/jpeg")
            },
            resolver = { arrayOf(InetAddress.getByName("8.8.8.8")) },
        )
        val signedUrl = "https://scontent.cdninstagram.com/media%2Fmeal.jpg?token=abc%2Fdef%2Bghi"

        client.getImage(signedUrl)

        assertEquals(listOf(signedUrl), connected)
    }

    private data class StubSpec(
        val status: Int,
        val location: String? = null,
        val body: ByteArray = byteArrayOf(),
        val contentType: String = "text/html; charset=utf-8",
    ) {
        fun toConnection(url: URL) = StubConnection(url, status, location, body, contentType)
    }

    private class StubConnection(
        url: URL,
        private val status: Int,
        private val location: String? = null,
        private val body: ByteArray = byteArrayOf(),
        private val mimeType: String = "text/html; charset=utf-8",
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getHeaderField(name: String?): String? = if (name.equals("Location", ignoreCase = true)) location else null
        override fun getInputStream() = ByteArrayInputStream(body)
        override fun getContentType(): String = mimeType
        override fun getContentLengthLong(): Long = body.size.toLong()
    }
}
