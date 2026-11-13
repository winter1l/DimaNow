package com.example.dimanow.pipeline

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OpenAiMealOcrClient(
    private val apiKey: String,
    private val endpointRoot: String = "https://api.openai.com",
) {
    private val transport = OpenAiMealResponsesClient(apiKey, endpointRoot)

    fun extract(imageBytes: ByteArray, mimeType: String): String = transport.extract(
        imageBytes, mimeType, PROMPT, RESPONSE_SCHEMA, "student_meal_extraction", "low",
    )

    private companion object {
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
