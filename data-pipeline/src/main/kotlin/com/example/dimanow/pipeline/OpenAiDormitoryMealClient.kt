package com.example.dimanow.pipeline

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

class OpenAiDormitoryMealClient(
    private val apiKey: String,
    private val endpointRoot: String = "https://api.openai.com",
    private val extractionReasoningEffort: String = "low",
    model: String = OpenAiMealResponsesClient.MODEL,
) {
    init {
        require(extractionReasoningEffort in setOf("none", "low", "medium", "high", "xhigh", "max")) { "지원하지 않는 식단 추출 추론 수준입니다." }
    }

    private val transport = OpenAiMealResponsesClient(apiKey, endpointRoot, model)
    private val json = Json { ignoreUnknownKeys = true }

    fun validate(imageBytes: ByteArray, mimeType: String): DormitoryMealValidation {
        val response = validationRequest(imageBytes, mimeType)
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
        val response = extractionRequest(imageBytes, mimeType)
        return DormitoryMealAnalysis.Accepted(response)
    }

    private fun validationRequest(imageBytes: ByteArray, mimeType: String): String = requestBody(
        imageBytes = imageBytes,
        mimeType = mimeType,
        prompt = "사진이 동아방송예술대학교 학생기숙사에서 제공하는 주간 식단표인지, 표의 모든 가장자리와 식단 내용이 잘리지 않고 전부 보이는지 확인하세요. 사진에 명시된 휴일·휴무·미운영 안내로 메뉴가 없는 칸은 잘리거나 누락된 식단으로 판단하지 마세요. 휴무 근거가 보이지 않는 빈칸은 휴일이라고 추측하지 마세요. 두 조건 중 하나라도 거짓이면 사용자가 다시 촬영할 수 있도록 한 문장으로만 이유를 적으세요.",
        schema = VALIDATION_SCHEMA,
        reasoningEffort = "high",
        schemaName = "dormitory_meal_validation",
    )

    private fun extractionRequest(imageBytes: ByteArray, mimeType: String): String = requestBody(
        imageBytes = imageBytes,
        mimeType = mimeType,
        prompt = "동아방송예술대학교 학생기숙사 주간 식단표를 월요일부터 금요일까지 전사하세요. 각 날짜 아래의 조식, 간편식, 라면, 중식, 석식, 샐러드도시락 등 표에 보이는 모든 구분을 위에서 아래 순서대로 sections에 넣으세요. 이름과 운영시간, 메뉴는 보이는 그대로 적고 번역, 보충, 추측하지 마세요. 같은 이름의 구분이 하루에 여러 번 있으면 각각 별도 section으로 유지하세요. 휴일·휴무·미운영 문구도 menuLines에 원문 그대로 한 줄로 기록하고 날짜를 생략하지 마세요. 특정 식사만 휴무이면 그 식사의 이름을 유지하세요. 하루 전체를 덮는 휴무 안내만 있고 식사 구분이 없으면 name은 안내, menuLines는 사진에 보이는 휴무 문구로 기록하며 hours는 null로 적으세요. 여러 날짜에 걸친 병합 칸의 안내는 사진에서 적용 범위가 명확한 날짜에만 각각 기록하세요. 빈칸이나 보이지 않는 날짜·식사·운영시간을 휴무로 채우지 마세요.",
        schema = EXTRACTION_SCHEMA,
        reasoningEffort = extractionReasoningEffort,
        schemaName = "dormitory_meal_extraction",
    )

    private fun requestBody(
        imageBytes: ByteArray,
        mimeType: String,
        prompt: String,
        schema: JsonObject,
        reasoningEffort: String,
        schemaName: String,
    ): String = transport.extract(imageBytes, mimeType, prompt, schema, schemaName, reasoningEffort)

    private companion object {
        const val DORMITORY_KEY = "동아방송예술대 기숙사 식단이 맞는가?"
        const val COMPLETE_KEY = "식단표가 전부 보이는가?"
        const val REASON_KEY = "모두 true가 아닌 경우 사용자에게 알려줄 간단 사유"
        val VALIDATION_SCHEMA = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            put("required", buildJsonArray {
                add(JsonPrimitive(DORMITORY_KEY))
                add(JsonPrimitive(COMPLETE_KEY))
                add(JsonPrimitive(REASON_KEY))
            })
            put("properties", buildJsonObject {
                put(DORMITORY_KEY, buildJsonObject { put("type", "boolean") })
                put(COMPLETE_KEY, buildJsonObject { put("type", "boolean") })
                put(REASON_KEY, buildJsonObject { put("type", buildJsonArray { add(JsonPrimitive("string")); add(JsonPrimitive("null")) }) })
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
                                        add(JsonPrimitive("hours"))
                                        add(JsonPrimitive("menuLines"))
                                    })
                                    put("properties", buildJsonObject {
                                        put("name", buildJsonObject { put("type", "string") })
                                        put("hours", buildJsonObject { put("type", buildJsonArray { add(JsonPrimitive("string")); add(JsonPrimitive("null")) }) })
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
