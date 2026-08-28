package com.example.dimanow.meal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Parses the public feed payload for DIMA's official Instagram profile. */
class MealProfileFeedParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): MealPost? {
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        val username = ((root["user"] as? JsonObject)?.get("username") as? JsonPrimitive)?.contentOrNull
        val status = (root["status"] as? JsonPrimitive)?.contentOrNull
        if (username != OFFICIAL_USERNAME || status != "ok") return null

        return (root["items"] as? JsonArray)
            .orEmpty()
            .mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val caption = (((item["caption"] as? JsonObject)?.get("text")) as? JsonPrimitive)
                    ?.contentOrNull
                    ?.trim()
                    ?: return@mapNotNull null
                val titleLine = caption.lineSequence().firstOrNull()?.trim().orEmpty()
                if (!titleLine.startsWith("[DIMA 학생식당]")) return@mapNotNull null
                val hours = HOURS.find(caption)?.groupValues?.let { "${it[1]} ~ ${it[2]}" }
                    ?: return@mapNotNull null
                val shortcode = (item["code"] as? JsonPrimitive)?.contentOrNull
                    ?.takeIf(SHORTCODE::matches)
                    ?: return@mapNotNull null
                val takenAt = (item["taken_at"] as? JsonPrimitive)?.longOrNull ?: Long.MIN_VALUE
                takenAt to MealPost(
                    title = "$titleLine 운영시간: $hours",
                    sourceUrl = "https://www.instagram.com/p/$shortcode/",
                    hours = hours,
                )
            }
            .maxByOrNull { it.first }
            ?.second
    }

    private companion object {
        const val OFFICIAL_USERNAME = "dima_people_1997"
        val HOURS = Regex("운영시간\\s*[:：]\\s*(\\d{1,2}:\\d{2})\\s*[~\\-–]\\s*(\\d{1,2}:\\d{2})")
        val SHORTCODE = Regex("[A-Za-z0-9_-]+")
    }
}
