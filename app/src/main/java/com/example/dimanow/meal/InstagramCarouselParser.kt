package com.example.dimanow.meal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup

data class InstagramCarousel(
    val imageUrls: List<String>,
) {
    val mealTableImageUrl: String? get() = imageUrls.getOrNull(1)
}

class InstagramCarouselParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(embedHtml: String): InstagramCarousel {
        Jsoup.parse(embedHtml).select("script").forEach { script ->
            val data = script.data().takeIf { it.contains("edge_sidecar_to_children") } ?: return@forEach
            val candidates = buildList {
                add(data)
                addAll(extractServerJsObjects(data))
            }
            candidates.forEach { candidate ->
                val root = runCatching { json.parseToJsonElement(candidate) }.getOrNull() ?: return@forEach
                findCarousel(root)?.let { return InstagramCarousel(it) }
            }
        }
        return InstagramCarousel(emptyList())
    }

    private fun extractServerJsObjects(script: String): List<String> {
        val results = mutableListOf<String>()
        var searchFrom = 0
        while (true) {
            val handle = script.indexOf("s.handle(", searchFrom)
            if (handle < 0) break
            val start = script.indexOf('{', handle + "s.handle(".length)
            if (start < 0) break
            extractBalancedObject(script, start)?.let(results::add)
            searchFrom = start + 1
        }
        return results
    }

    private fun extractBalancedObject(text: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val character = text[index]
            if (inString) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') inString = false
                continue
            }
            when (character) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun findCarousel(element: JsonElement): List<String>? = when (element) {
        is JsonObject -> {
            element["edge_sidecar_to_children"]?.let(::imagesFromSidecar)
                ?: element.values.firstNotNullOfOrNull(::findCarousel)
        }
        is JsonArray -> element.firstNotNullOfOrNull(::findCarousel)
        is JsonPrimitive -> if (element.isString && element.content.contains("edge_sidecar_to_children")) {
            runCatching { json.parseToJsonElement(element.content) }.getOrNull()?.let(::findCarousel)
        } else null
        else -> null
    }

    private fun imagesFromSidecar(element: JsonElement): List<String>? {
        val edges = (element as? JsonObject)?.get("edges") as? JsonArray ?: return null
        val urls = edges.mapNotNull { edge ->
            (((edge as? JsonObject)?.get("node") as? JsonObject)?.get("display_url") as? JsonPrimitive)?.contentOrNull
        }
        return urls.takeIf { it.isNotEmpty() }
    }
}
