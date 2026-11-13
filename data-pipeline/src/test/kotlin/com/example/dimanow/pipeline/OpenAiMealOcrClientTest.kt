package com.example.dimanow.pipeline

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiMealOcrClientTest {
    @Test
    fun `학생식당 이미지도 OpenAI Responses의 구조화 JSON으로 읽는다`() {
        var receivedKey: String? = null
        var receivedBody = ""
        val text = """{"days":[{"month":8,"day":24,"menuLines":["유부장국"]}]}"""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/responses") { exchange ->
                receivedKey = exchange.requestHeaders.getFirst("Authorization")
                receivedBody = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                val response = """{"status":"completed","output":[{"type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":${Json.encodeToString(text)}}]}]}""".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }
        try {
            val result = OpenAiMealOcrClient("test-secret", "http://127.0.0.1:${server.address.port}")
                .extract(byteArrayOf(1, 2, 3), "image/jpeg")
            assertEquals(text, result)
            assertEquals("Bearer test-secret", receivedKey)
            assertFalse(receivedBody.contains("test-secret"))
            val body = Json.parseToJsonElement(receivedBody).jsonObject
            assertEquals("gpt-6-luna", body.getValue("model").jsonPrimitive.content)
            assertFalse(body.getValue("store").jsonPrimitive.boolean)
            val format = body.getValue("text").jsonObject.getValue("format").jsonObject
            assertEquals("json_schema", format.getValue("type").jsonPrimitive.content)
            assertTrue(format.getValue("strict").jsonPrimitive.boolean)
            assertTrue(receivedBody.contains("data:image/jpeg;base64,AQID"))
        } finally {
            server.stop(0)
        }
    }
}
