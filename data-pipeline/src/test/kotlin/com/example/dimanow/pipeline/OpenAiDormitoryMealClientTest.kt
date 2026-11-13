package com.example.dimanow.pipeline

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiDormitoryMealClientTest {
    @Test
    fun `기숙사 사진 검증은 Bearer 인증과 저장하지 않는 구조화 응답을 사용한다`() {
        responseServer { _, _ -> 200 to completed(VALIDATION) }.use { server ->
            val result = server.client().validate(byteArrayOf(1, 2, 3), "image/jpeg")
            assertTrue(result.accepted)
            assertEquals(null, result.reason)
            assertEquals(listOf("Bearer test-secret"), server.authorization)
            assertRequest(server.bodies.single(), "high")
            val schema = server.bodies.single().getValue("text").jsonObject
                .getValue("format").jsonObject.getValue("schema").jsonObject
            val properties = schema.getValue("properties").jsonObject
            assertEquals(properties.keys, schema.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet())
            assertTrue(properties.containsKey("동아방송예술대 기숙사 식단이 맞는가?"))
            assertTrue(properties.containsKey("식단표가 전부 보이는가?"))
        }
    }

    @Test
    fun `검증을 통과한 사진만 별도 OCR 호출로 전사하고 reasoning 항목을 건너뛴다`() {
        responseServer { call, _ -> 200 to completed(if (call == 1) VALIDATION else EXTRACTION) }.use { server ->
            val result = server.client().analyze(byteArrayOf(1, 2, 3), "image/jpeg")
            assertEquals(DormitoryMealAnalysis.Accepted(EXTRACTION), result)
            assertEquals(2, server.bodies.size)
            assertRequest(server.bodies[0], "high")
            assertRequest(server.bodies[1], "low")
            assertTrue(server.bodies[1].toString().contains("sections"))
            val schema = server.bodies[1].getValue("text").jsonObject.getValue("format").jsonObject.getValue("schema")
            assertStrictSchema(schema.jsonObject)
        }
    }

    @Test
    fun `추출 none 설정은 사진 검증 high와 원본 해상도를 유지한다`() {
        responseServer { call, _ -> 200 to completed(if (call == 1) VALIDATION else EXTRACTION) }.use { server ->
            server.client("none").analyze(byteArrayOf(1, 2, 3), "image/jpeg")
            assertRequest(server.bodies[0], "high")
            assertRequest(server.bodies[1], "none")
        }
    }

    @Test
    fun `검증이 거절되면 OCR을 호출하지 않고 사유를 반환한다`() {
        val rejected = """{"동아방송예술대 기숙사 식단이 맞는가?":true,"식단표가 전부 보이는가?":false,"모두 true가 아닌 경우 사용자에게 알려줄 간단 사유":"표 오른쪽이 잘렸어요"}"""
        responseServer { _, _ -> 200 to completed(rejected) }.use { server ->
            assertEquals(DormitoryMealAnalysis.Rejected("표 오른쪽이 잘렸어요"), server.client().analyze(byteArrayOf(1), "image/jpeg"))
            assertEquals(1, server.bodies.size)
        }
    }

    @Test
    fun `429와 503은 같은 본문으로 재시도하고 세 번째 성공을 사용한다`() {
        responseServer { call, _ -> if (call < 3) (if (call == 1) 429 else 503) to "private-error" else 200 to completed(VALIDATION) }.use { server ->
            assertTrue(server.client().validate(byteArrayOf(1), "image/jpeg").accepted)
            assertEquals(3, server.bodies.size)
            assertEquals(1, server.bodies.distinct().size)
        }
    }

    @Test
    fun `계속된 일시 오류는 세 번 뒤 실패하고 공급자 오류와 키를 노출하지 않는다`() {
        responseServer { _, _ -> 503 to "test-secret private-error" }.use { server ->
            val error = assertThrows(IllegalStateException::class.java) { server.client().validate(byteArrayOf(1), "image/jpeg") }
            assertEquals(3, server.bodies.size)
            assertFalse(error.toString().contains("test-secret"))
            assertFalse(error.toString().contains("private-error"))
        }
    }

    @Test
    fun `401은 재시도하지 않고 실패한다`() {
        responseServer { _, _ -> 401 to "test-secret private-error" }.use { server ->
            val error = assertThrows(IllegalStateException::class.java) { server.client().validate(byteArrayOf(1), "image/jpeg") }
            assertEquals(1, server.bodies.size)
            assertFalse(error.toString().contains("test-secret"))
            assertFalse(error.toString().contains("private-error"))
        }
    }

    @Test
    fun `미완료 거절 빈 응답과 잘못된 JSON은 OCR이나 게시 성공으로 처리하지 않는다`() {
        val invalidResponses = listOf(
            """{"status":"incomplete","output":[{"type":"message","content":[{"type":"output_text","text":${Json.encodeToString(VALIDATION)}}]}]}""",
            """{"status":"failed","error":{"message":"private-error"},"output":[]}""",
            """{"status":"completed","output":[{"type":"message","role":"assistant","status":"completed","content":[{"type":"refusal","refusal":"private-error"}]}]}""",
            """{"status":"completed","output":[]}""",
            completed(""),
            "not-json",
            completed("not-json"),
            "{\"private-error\":",
            completed("{\"private-error\":"),
        )
        invalidResponses.forEach { response ->
            responseServer { _, _ -> 200 to response }.use { server ->
                val error = assertThrows("response should fail: $response", Exception::class.java) { server.client().analyze(byteArrayOf(1), "image/jpeg") }
                assertFalse(error.toString().contains("private-error"))
                assertEquals(1, server.bodies.size)
            }
        }
    }

    @Test
    fun `모델 비교에서도 지정한 모델을 검증과 전사 양쪽에 사용한다`() {
        for (model in listOf("gpt-6.1-terra", "gpt-6.1-sol")) {
            responseServer { call, _ -> 200 to completed(if (call == 1) VALIDATION else EXTRACTION) }.use { server ->
                OpenAiDormitoryMealClient("test-secret", "http://127.0.0.1:${server.addressPort}", model = model)
                    .analyze(byteArrayOf(1, 2, 3), "image/jpeg")
                assertEquals(listOf(model, model), server.bodies.map { it.getValue("model").jsonPrimitive.content })
            }
        }
    }

    private fun assertRequest(body: JsonObject, effort: String) {
        assertEquals("gpt-6-luna", body.getValue("model").jsonPrimitive.content)
        assertFalse(body.getValue("store").jsonPrimitive.boolean)
        assertEquals(effort, body.getValue("reasoning").jsonObject.getValue("effort").jsonPrimitive.content)
        assertFalse(body.toString().contains("test-secret"))
        val format = body.getValue("text").jsonObject.getValue("format").jsonObject
        assertEquals("json_schema", format.getValue("type").jsonPrimitive.content)
        assertTrue(format.getValue("strict").jsonPrimitive.boolean)
        val content = body.getValue("input").jsonArray.single().jsonObject.getValue("content").jsonArray
        val image = content.single { it.jsonObject["type"]?.jsonPrimitive?.content == "input_image" }.jsonObject
        assertEquals("original", image.getValue("detail").jsonPrimitive.content)
        assertEquals("data:image/jpeg;base64,AQID", image.getValue("image_url").jsonPrimitive.content)
        assertTrue(content.any { it.jsonObject["type"]?.jsonPrimitive?.content == "input_text" })
    }

    private fun assertStrictSchema(schema: JsonObject) {
        if ((schema["type"] as? JsonPrimitive)?.content == "object") {
            assertFalse(schema.getValue("additionalProperties").jsonPrimitive.boolean)
            val properties = schema.getValue("properties").jsonObject
            assertEquals(properties.keys, schema.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet())
            properties.values.forEach { if (it is JsonObject) assertStrictSchema(it) }
        }
        (schema["items"] as? JsonObject)?.let(::assertStrictSchema)
    }

    private fun responseServer(response: (Int, JsonObject) -> Pair<Int, String>) = ResponseServer(response)

    private class ResponseServer(response: (Int, JsonObject) -> Pair<Int, String>) : AutoCloseable {
        val bodies = mutableListOf<JsonObject>()
        val authorization = mutableListOf<String?>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/responses") { exchange ->
                val body = Json.parseToJsonElement(exchange.requestBody.readBytes().toString(Charsets.UTF_8)).jsonObject
                bodies += body
                authorization += exchange.requestHeaders.getFirst("Authorization")
                val (status, payload) = response(bodies.size, body)
                val bytes = payload.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val addressPort: Int get() = server.address.port
        fun client(effort: String? = null) = if (effort == null) {
            OpenAiDormitoryMealClient("test-secret", "http://127.0.0.1:${server.address.port}")
        } else {
            OpenAiDormitoryMealClient("test-secret", "http://127.0.0.1:${server.address.port}", extractionReasoningEffort = effort)
        }
        override fun close() = server.stop(0)
    }

    private companion object {
        const val VALIDATION = """{"동아방송예술대 기숙사 식단이 맞는가?":true,"식단표가 전부 보이는가?":true,"모두 true가 아닌 경우 사용자에게 알려줄 간단 사유":null}"""
        const val EXTRACTION = """{"days":[{"month":8,"day":24,"sections":[{"name":"조식","hours":null,"menuLines":["떡국"]}]}]}"""
        fun completed(text: String) = """{"status":"completed","output":[{"type":"reasoning","summary":[]},{"type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":${Json.encodeToString(text)}}]}]}"""
    }
}
