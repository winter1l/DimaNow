package com.example.dimanow.pipeline

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Stateless image-to-JSON transport shared by the two meal sources. Never logs provider content or credentials. */
internal class OpenAiMealResponsesClient(
    private val apiKey: String,
    private val endpointRoot: String,
    private val model: String = MODEL,
) {
    fun extract(
        imageBytes: ByteArray,
        mimeType: String,
        prompt: String,
        schema: JsonObject,
        schemaName: String,
        reasoningEffort: String,
    ): String {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY가 비어 있습니다." }
        require(imageBytes.isNotEmpty()) { "식단 이미지가 비어 있습니다." }
        require(mimeType in setOf("image/jpeg", "image/png", "image/webp")) { "지원하지 않는 식단 이미지 형식입니다." }
        val body = buildJsonObject {
            put("model", model)
            put("store", false)
            put("reasoning", buildJsonObject { put("effort", reasoningEffort) })
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject { put("type", "input_text"); put("text", prompt) })
                        add(buildJsonObject {
                            put("type", "input_image")
                            put("image_url", "data:$mimeType;base64,${Base64.getEncoder().encodeToString(imageBytes)}")
                            put("detail", "original")
                        })
                    })
                })
            })
            put("text", buildJsonObject {
                put("format", buildJsonObject {
                    put("type", "json_schema")
                    put("name", schemaName)
                    put("strict", true)
                    put("schema", schema)
                })
            })
        }.toString().toByteArray(Charsets.UTF_8)
        repeat(3) { attempt ->
            try {
                return requestOnce(body)
            } catch (error: OpenAiHttpException) {
                if (!error.retryable || attempt == 2) throw error
                Thread.sleep(if (attempt == 0) 1_000L else 4_000L)
            }
        }
        error("OpenAI 식단 처리를 완료하지 못했습니다.")
    }

    private fun requestOnce(body: ByteArray): String {
        val connection = URL("${endpointRoot.trimEnd('/')}/v1/responses").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode !in 200..299) throw OpenAiHttpException(connection.responseCode)
            val envelope = parseJsonObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                as? JsonObject ?: error("OpenAI API JSON 응답이 아닙니다.")
            check(envelope["status"]?.jsonPrimitive?.contentOrNull == "completed" && envelope["error"] !is JsonObject) {
                "OpenAI 식단 응답이 완료되지 않았습니다."
            }
            val messages = (envelope["output"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .filter { it["type"]?.jsonPrimitive?.contentOrNull == "message" }
            check(messages.isNotEmpty() && messages.all {
                it["status"]?.jsonPrimitive?.contentOrNull == "completed" &&
                    it["role"]?.jsonPrimitive?.contentOrNull == "assistant"
            }) { "OpenAI 식단 메시지가 완료되지 않았습니다." }
            val content = messages.flatMap { (it["content"] as? JsonArray).orEmpty() }.map { it.jsonObject }
            check(content.none { it["type"]?.jsonPrimitive?.contentOrNull == "refusal" }) {
                "OpenAI가 식단 이미지 처리를 거절했습니다."
            }
            val text = content.filter { it["type"]?.jsonPrimitive?.contentOrNull == "output_text" }
                .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            check(text.isNotBlank()) { "OpenAI 식단 JSON이 비어 있습니다." }
            parseJsonObject(text)
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun parseJsonObject(text: String): JsonObject =
        runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: error("OpenAI 식단 JSON 객체가 아닙니다.")

    private class OpenAiHttpException(status: Int) : IllegalStateException("OpenAI API 응답 $status") {
        val retryable = status == 429 || status in 500..599
    }

    companion object {
        const val MODEL = "gpt-6-luna"
    }
}
