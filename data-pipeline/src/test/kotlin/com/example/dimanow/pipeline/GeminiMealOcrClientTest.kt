package com.example.dimanow.pipeline

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiMealOcrClientTest {
    @Test
    fun `API 키는 헤더에 두고 구조화 JSON 식단을 요청한다`() {
        var receivedKey: String? = null
        var receivedBody = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent") { exchange ->
                receivedKey = exchange.requestHeaders.getFirst("x-goog-api-key")
                receivedBody = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                val response = """
                    {"modelVersion":"gemini-3.5-flash-lite","candidates":[{"content":{"parts":[{"text":"{\"days\":[{\"month\":8,\"day\":24,\"menuLines\":[\"유부장국\"]}]}"}]}}]}
                """.trimIndent().toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val result = GeminiMealOcrClient(
                apiKey = "test-secret",
                endpointRoot = "http://127.0.0.1:${server.address.port}",
            ).extract(byteArrayOf(1, 2, 3), "image/jpeg")

            assertEquals("test-secret", receivedKey)
            assertFalse(receivedBody.contains("test-secret"))
            assertTrue(receivedBody.contains("\"responseMimeType\":\"application/json\""))
            assertTrue(receivedBody.contains("\"responseJsonSchema\""))
            assertTrue(receivedBody.contains("AQID"))
            assertEquals("""{"days":[{"month":8,"day":24,"menuLines":["유부장국"]}]}""", result)
        } finally {
            server.stop(0)
        }
    }
}
