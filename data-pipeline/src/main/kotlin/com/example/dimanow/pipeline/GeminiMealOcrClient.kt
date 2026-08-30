package com.example.dimanow.pipeline

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class GeminiMealOcrClient(
    private val apiKey: String,
    private val endpointRoot: String = "https://generativelanguage.googleapis.com",
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun extract(imageBytes: ByteArray, mimeType: String): String {
        require(apiKey.isNotBlank()) { "GEMINI_API_KEY가 비어 있습니다." }
        require(mimeType.startsWith("image/")) { "식단 파일이 이미지가 아닙니다." }
        val requestBytes = requestBody(imageBytes, mimeType).toString().toByteArray()
        val connection = URL("$endpointRoot/v1beta/models/$MODEL:generateContent").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("x-goog-api-key", apiKey)
            connection.setFixedLengthStreamingMode(requestBytes.size)
            connection.outputStream.use { it.write(requestBytes) }
            require(connection.responseCode in 200..299) { "Gemini API 응답 ${connection.responseCode}" }
            val root = json.parseToJsonElement(connection.inputStream.bufferedReader().readText()) as? JsonObject
                ?: error("Gemini API JSON 응답이 아닙니다.")
            return (((((root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.get("content") as? JsonObject)?.get("parts") as? JsonArray)
                ?.firstNotNullOfOrNull { (it as? JsonObject)?.get("text") as? JsonPrimitive })
                ?.contentOrNull
                ?: error("Gemini 식단 JSON이 비어 있습니다.")
        } finally {
            connection.disconnect()
        }
    }

    private fun requestBody(imageBytes: ByteArray, mimeType: String): JsonObject = buildJsonObject {
        put("contents", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("parts", buildJsonArray {
                    add(buildJsonObject {
                        put("inline_data", buildJsonObject {
                            put("mime_type", mimeType)
                            put("data", Base64.getEncoder().encodeToString(imageBytes))
                        })
                    })
                    add(buildJsonObject { put("text", PROMPT) })
                })
            })
        })
        put("generationConfig", buildJsonObject {
            put("responseMimeType", "application/json")
            put("responseJsonSchema", RESPONSE_SCHEMA)
        })
    }

    private companion object {
        const val MODEL = "gemini-3.5-flash-lite"
        const val PROMPT = """이 이미지는 동아방송예술대학교 학생식당의 주간 식단표입니다. 월요일부터 금요일까지 날짜의 월, 일과 각 메뉴를 이미지에 적힌 위에서 아래 순서대로 정확히 전사하세요. 음식명을 번역하거나 보충하거나 추측하지 마세요. 표지, 로고, 운영시간, 안내문은 menuLines에 넣지 마세요. 보이지 않는 항목은 만들지 마세요."""
        val RESPONSE_SCHEMA = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            put("required", buildJsonArray { add(JsonPrimitive("days")) })
            put("properties", buildJsonObject {
                put("days", buildJsonObject {
                    put("type", "array")
                    put("minItems", 5)
                    put("maxItems", 5)
                    put("items", buildJsonObject {
                        put("type", "object")
                        put("additionalProperties", false)
                        put("required", buildJsonArray {
                            add(JsonPrimitive("month"))
                            add(JsonPrimitive("day"))
                            add(JsonPrimitive("menuLines"))
                        })
                        put("properties", buildJsonObject {
                            put("month", buildJsonObject {
                                put("type", "integer")
                                put("minimum", 1)
                                put("maximum", 12)
                            })
                            put("day", buildJsonObject {
                                put("type", "integer")
                                put("minimum", 1)
                                put("maximum", 31)
                            })
                            put("menuLines", buildJsonObject {
                                put("type", "array")
                                put("minItems", 1)
                                put("items", buildJsonObject { put("type", "string") })
                            })
                        })
                    })
                })
            })
        }
    }
}
