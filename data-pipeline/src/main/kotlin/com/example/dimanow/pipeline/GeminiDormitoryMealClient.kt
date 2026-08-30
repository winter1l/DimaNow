package com.example.dimanow.pipeline

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class DormitoryMealValidation(
    val isDormitoryMeal: Boolean,
    val isComplete: Boolean,
    val reason: String?,
) {
    val accepted: Boolean get() = isDormitoryMeal && isComplete
}

sealed interface DormitoryMealAnalysis {
    data class Accepted(val responseJson: String) : DormitoryMealAnalysis
    data class Rejected(val reason: String) : DormitoryMealAnalysis
}

class GeminiDormitoryMealClient(
    private val apiKey: String,
    private val endpointRoot: String = "https://generativelanguage.googleapis.com",
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun validate(imageBytes: ByteArray, mimeType: String): DormitoryMealValidation {
        val response = request(imageBytes, mimeType, validationRequest(imageBytes, mimeType))
        val root = json.parseToJsonElement(response) as JsonObject
        return DormitoryMealValidation(
            isDormitoryMeal = (root.getValue(DORMITORY_KEY) as JsonPrimitive).boolean,
            isComplete = (root.getValue(COMPLETE_KEY) as JsonPrimitive).boolean,
            reason = (root[REASON_KEY] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    fun analyze(imageBytes: ByteArray, mimeType: String): DormitoryMealAnalysis {
        val validation = validate(imageBytes, mimeType)
        if (!validation.accepted) {
            return DormitoryMealAnalysis.Rejected(validation.reason ?: "기숙사 식단표 전체가 보이도록 다시 촬영해 주세요")
        }
        val response = request(imageBytes, mimeType, extractionRequest(imageBytes, mimeType))
        return DormitoryMealAnalysis.Accepted(response)
    }

    private fun request(imageBytes: ByteArray, mimeType: String, body: JsonObject): String {
        require(apiKey.isNotBlank()) { "GEMINI_API_KEY가 비어 있습니다." }
        require(imageBytes.isNotEmpty()) { "식단 이미지가 비어 있습니다." }
        require(mimeType.startsWith("image/")) { "식단 파일이 이미지가 아닙니다." }
        val requestBytes = body.toString().toByteArray()
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
            if (connection.responseCode !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.readText().orEmpty().take(800)
                error("Gemini API 응답 ${connection.responseCode}: $detail")
            }
            val envelope = json.parseToJsonElement(connection.inputStream.bufferedReader().readText()) as? JsonObject
                ?: error("Gemini API JSON 응답이 아닙니다.")
            return (((((envelope["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.get("content") as? JsonObject)?.get("parts") as? JsonArray)
                ?.firstNotNullOfOrNull { (it as? JsonObject)?.get("text") as? JsonPrimitive })
                ?.contentOrNull
                ?: error("Gemini 기숙사 식단 JSON이 비어 있습니다.")
        } finally {
            connection.disconnect()
        }
    }

    private fun validationRequest(imageBytes: ByteArray, mimeType: String): JsonObject = requestBody(
        imageBytes = imageBytes,
        mimeType = mimeType,
        prompt = "사진이 동아방송예술대학교 학생기숙사에서 제공하는 주간 식단표인지, 표의 모든 가장자리와 식단 내용이 잘리지 않고 전부 보이는지 확인하세요. 두 조건 중 하나라도 거짓이면 사용자가 다시 촬영할 수 있도록 한 문장으로만 이유를 적으세요.",
        schema = VALIDATION_SCHEMA,
        thinkingLevel = "HIGH",
    )

    private fun extractionRequest(imageBytes: ByteArray, mimeType: String): JsonObject = requestBody(
        imageBytes = imageBytes,
        mimeType = mimeType,
        prompt = "동아방송예술대학교 학생기숙사 주간 식단표를 월요일부터 금요일까지 전사하세요. 각 날짜 아래의 조식, 간편식, 라면, 중식, 석식, 샐러드도시락 등 표에 보이는 모든 구분을 위에서 아래 순서대로 sections에 넣으세요. 이름과 운영시간, 메뉴는 보이는 그대로 적고 번역, 보충, 추측하지 마세요. 같은 이름의 구분이 하루에 여러 번 있으면 각각 별도 section으로 유지하세요.",
        schema = EXTRACTION_SCHEMA,
        thinkingLevel = "minimal",
    )

    private fun requestBody(
        imageBytes: ByteArray,
        mimeType: String,
        prompt: String,
        schema: JsonObject,
        thinkingLevel: String,
    ): JsonObject = buildJsonObject {
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
                    add(buildJsonObject { put("text", prompt) })
                })
            })
        })
        put("generationConfig", buildJsonObject {
            put("thinkingConfig", buildJsonObject { put("thinkingLevel", thinkingLevel) })
            put("responseMimeType", "application/json")
            put("responseJsonSchema", schema)
        })
    }

    private companion object {
        const val MODEL = "gemini-3.5-flash-lite"
        const val DORMITORY_KEY = "동아방송예술대 기숙사 식단이 맞는가?"
        const val COMPLETE_KEY = "식단표가 전부 보이는가?"
        const val REASON_KEY = "모두 true가 아닌 경우 사용자에게 알려줄 간단 사유"
        val VALIDATION_SCHEMA = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            put("required", buildJsonArray {
                add(JsonPrimitive(DORMITORY_KEY))
                add(JsonPrimitive(COMPLETE_KEY))
            })
            put("properties", buildJsonObject {
                put(DORMITORY_KEY, buildJsonObject { put("type", "boolean") })
                put(COMPLETE_KEY, buildJsonObject { put("type", "boolean") })
                put(REASON_KEY, buildJsonObject { put("type", "string") })
            })
        }
        val EXTRACTION_SCHEMA = buildJsonObject {
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
                            add(JsonPrimitive("sections"))
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
                            put("sections", buildJsonObject {
                                put("type", "array")
                                put("minItems", 1)
                                put("items", buildJsonObject {
                                    put("type", "object")
                                    put("additionalProperties", false)
                                    put("required", buildJsonArray {
                                        add(JsonPrimitive("name"))
                                        add(JsonPrimitive("menuLines"))
                                    })
                                    put("properties", buildJsonObject {
                                        put("name", buildJsonObject { put("type", "string") })
                                        put("hours", buildJsonObject { put("type", "string") })
                                        put("menuLines", buildJsonObject {
                                            put("type", "array")
                                            put("minItems", 1)
                                            put("items", buildJsonObject { put("type", "string") })
                                        })
                                    })
                                })
                            })
                        })
                    })
                })
            })
        }
    }
}
