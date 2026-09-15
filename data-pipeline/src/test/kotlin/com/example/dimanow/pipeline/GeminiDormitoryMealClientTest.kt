package com.example.dimanow.pipeline

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiDormitoryMealClientTest {
    @Test
    fun `일시적인 503 이후 같은 요청을 재시도해 검증을 완료한다`() {
        var calls = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent") { exchange ->
                calls++
                exchange.requestBody.readBytes()
                if (calls == 1) {
                    val response = """{"error":{"code":503,"message":"The service is temporarily unavailable."}}""".toByteArray()
                    exchange.sendResponseHeaders(503, response.size.toLong())
                    exchange.responseBody.use { it.write(response) }
                } else {
                    val response = """
                        {"candidates":[{"content":{"parts":[{"text":"{\"동아방송예술대 기숙사 식단이 맞는가?\":true,\"식단표가 전부 보이는가?\":true}"}]}}]}
                    """.trimIndent().toByteArray()
                    exchange.sendResponseHeaders(200, response.size.toLong())
                    exchange.responseBody.use { it.write(response) }
                }
            }
            start()
        }

        try {
            val result = GeminiDormitoryMealClient(
                apiKey = "test-secret",
                endpointRoot = "http://127.0.0.1:${server.address.port}",
            ).validate(byteArrayOf(1, 2, 3), "image/jpeg")

            assertEquals(2, calls)
            assertTrue(result.accepted)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `기숙사 식단 검증은 high 추론과 승인된 한국어 JSON 계약을 사용한다`() {
        var receivedKey: String? = null
        var receivedBody = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent") { exchange ->
                receivedKey = exchange.requestHeaders.getFirst("x-goog-api-key")
                receivedBody = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                val response = """
                    {"candidates":[{"content":{"parts":[{"text":"{\"동아방송예술대 기숙사 식단이 맞는가?\":true,\"식단표가 전부 보이는가?\":true}"}]}}]}
                """.trimIndent().toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val result = GeminiDormitoryMealClient(
                apiKey = "test-secret",
                endpointRoot = "http://127.0.0.1:${server.address.port}",
            ).validate(byteArrayOf(1, 2, 3), "image/jpeg")

            assertEquals("test-secret", receivedKey)
            assertFalse(receivedBody.contains("test-secret"))
            assertTrue(receivedBody.contains("\"thinkingLevel\":\"HIGH\""))
            assertTrue(receivedBody.contains("동아방송예술대 기숙사 식단이 맞는가?"))
            assertTrue(receivedBody.contains("식단표가 전부 보이는가?"))
            assertTrue(result.isDormitoryMeal)
            assertTrue(result.isComplete)
            assertEquals(null, result.reason)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `검증을 통과한 사진만 별도 minimal OCR 호출로 전사한다`() {
        val bodies = mutableListOf<String>()
        var call = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent") { exchange ->
                bodies += exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                val text = if (call++ == 0) {
                    """{"동아방송예술대 기숙사 식단이 맞는가?":true,"식단표가 전부 보이는가?":true}"""
                } else {
                    """{"days":[{"month":8,"day":24,"sections":[{"name":"조식","menuLines":["떡국"]}]}]}"""
                }
                val response = """{"candidates":[{"content":{"parts":[{"text":${jsonString(text)}}]}}]}""".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val result = GeminiDormitoryMealClient(
                apiKey = "test-secret",
                endpointRoot = "http://127.0.0.1:${server.address.port}",
            ).analyze(byteArrayOf(9, 8, 7), "image/jpeg")

            assertEquals(2, bodies.size)
            bodies.forEach { body ->
                val generationConfig = Json.parseToJsonElement(body).jsonObject.getValue("generationConfig").jsonObject
                assertEquals(
                    "MEDIA_RESOLUTION_HIGH",
                    generationConfig["mediaResolution"]?.jsonPrimitive?.content,
                )
            }
            assertTrue(bodies[0].contains("\"thinkingLevel\":\"HIGH\""))
            assertTrue(bodies[1].contains("\"thinkingLevel\":\"minimal\""))
            assertTrue(bodies[1].contains("sections"))
            assertEquals(
                """{"days":[{"month":8,"day":24,"sections":[{"name":"조식","menuLines":["떡국"]}]}]}""",
                (result as DormitoryMealAnalysis.Accepted).responseJson,
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `검증이 거절되면 OCR을 호출하지 않고 간단 사유를 반환한다`() {
        var calls = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent") { exchange ->
                calls++
                exchange.requestBody.readBytes()
                val text = """{"동아방송예술대 기숙사 식단이 맞는가?":true,"식단표가 전부 보이는가?":false,"모두 true가 아닌 경우 사용자에게 알려줄 간단 사유":"표 오른쪽이 잘렸어요"}"""
                val response = """{"candidates":[{"content":{"parts":[{"text":${jsonString(text)}}]}}]}""".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val result = GeminiDormitoryMealClient(
                apiKey = "test-secret",
                endpointRoot = "http://127.0.0.1:${server.address.port}",
            ).analyze(byteArrayOf(4, 5, 6), "image/jpeg")

            assertEquals(1, calls)
            assertEquals("표 오른쪽이 잘렸어요", (result as DormitoryMealAnalysis.Rejected).reason)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `low 추출 비교는 사진 검증 high와 양쪽 이미지 해상도 high를 유지한다`() {
        val bodies = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent") { exchange ->
                bodies += exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                val text = if (bodies.size == 1) {
                    """{"동아방송예술대 기숙사 식단이 맞는가?":true,"식단표가 전부 보이는가?":true}"""
                } else {
                    """{"days":[]}"""
                }
                val response = """{"candidates":[{"content":{"parts":[{"text":${jsonString(text)}}]}}]}""".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        try {
            val result = GeminiDormitoryMealClient(
                apiKey = "test-secret",
                endpointRoot = "http://127.0.0.1:${server.address.port}",
                extractionThinkingLevel = "LOW",
            ).analyze(byteArrayOf(9, 8, 7), "image/jpeg")
            assertTrue(result is DormitoryMealAnalysis.Accepted)
            assertEquals(2, bodies.size)
            val configs = bodies.map { Json.parseToJsonElement(it).jsonObject.getValue("generationConfig").jsonObject }
            assertEquals(listOf("MEDIA_RESOLUTION_HIGH", "MEDIA_RESOLUTION_HIGH"), configs.map { it.getValue("mediaResolution").jsonPrimitive.content })
            assertEquals(listOf("HIGH", "LOW"), configs.map { it.getValue("thinkingConfig").jsonObject.getValue("thinkingLevel").jsonPrimitive.content })
        } finally {
            server.stop(0)
        }
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(char)
            }
        }
        append('"')
    }
}
