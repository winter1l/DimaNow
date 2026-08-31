package com.example.dimanow.pipeline

import com.example.dimanow.sync.MealPayload
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup

data class MealPost(val title: String, val sourceUrl: String, val hours: String) {
    val embedUrl: String get() = sourceUrl.substringBefore('?').trimEnd('/') + "/embed/captioned/"
}

enum class MealPublicationDecision { PUBLISH, WAITING }

class MealPublicationPolicy {
    fun decide(postFound: Boolean, payload: MealPayload?, today: LocalDate): MealPublicationDecision {
        if (!postFound) return MealPublicationDecision.WAITING
        val lastDate = payload?.days?.maxOfOrNull { LocalDate.parse(it.date) } ?: return MealPublicationDecision.WAITING
        return if (lastDate.isBefore(today)) MealPublicationDecision.WAITING else MealPublicationDecision.PUBLISH
    }
}

sealed interface MealPublicationResult {
    data class Published(val payload: MealPayload) : MealPublicationResult
    data object Waiting : MealPublicationResult
}

class MealRemotePipeline(
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Seoul")),
    private val geminiClient: GeminiMealOcrClient = GeminiMealOcrClient(System.getenv("GEMINI_API_KEY").orEmpty()),
) {
    fun fetch(): MealPayload = when (val result = fetchPublication()) {
        is MealPublicationResult.Published -> result.payload
        MealPublicationResult.Waiting -> error(NOT_PUBLISHED_MESSAGE)
    }

    fun fetchPublication(): MealPublicationResult {
        val discoveryHtml = Jsoup.connect(DISCOVERY_URL).userAgent(USER_AGENT).get().outerHtml()
        val post = MealDiscoveryParser().parse(discoveryHtml)
            ?: profilePost()
            ?: return MealPublicationResult.Waiting
        val embedHtml = Jsoup.connect(post.embedUrl).userAgent(USER_AGENT).get().outerHtml()
        val imageUrl = InstagramCarouselParser().parse(embedHtml).imageUrls.getOrNull(1)
            ?: error("식단표 carousel 이미지를 찾지 못했습니다.")
        val image = download(imageUrl)
        val responseJson = geminiClient.extract(image.bytes, image.mimeType)
        val payload = GeminiMealPayloadBuilder().build(
            responseJson,
            LocalDate.now(clock),
            post.hours,
            post.sourceUrl,
            imageUrl,
        )
        return when (MealPublicationPolicy().decide(true, payload, LocalDate.now(clock))) {
            MealPublicationDecision.PUBLISH -> MealPublicationResult.Published(payload)
            MealPublicationDecision.WAITING -> MealPublicationResult.Waiting
        }
    }

    private fun profilePost(): MealPost? {
        val response = Jsoup.connect(PROFILE_FEED_URL)
            .ignoreContentType(true)
            .userAgent(USER_AGENT)
            .header("X-IG-App-ID", INSTAGRAM_PUBLIC_APP_ID)
            .execute()
        return MealProfileFeedParser().parse(response.body())
    }

    private fun download(url: String): DownloadedImage {
        require(url.startsWith("https://")) { "식단 이미지가 HTTPS가 아닙니다." }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            require(connection.responseCode == HttpURLConnection.HTTP_OK) { "식단 이미지 응답 ${connection.responseCode}" }
            require(connection.contentLengthLong in -1L..MAX_IMAGE_BYTES) { "식단 이미지가 너무 큽니다." }
            val bytes = connection.inputStream.use { input ->
                val bytes = input.readNBytes(MAX_IMAGE_BYTES.toInt() + 1)
                require(bytes.size <= MAX_IMAGE_BYTES) { "식단 이미지가 너무 큽니다." }
                bytes
            }
            val mimeType = connection.contentType?.substringBefore(';')?.trim().orEmpty()
            require(mimeType.startsWith("image/")) { "식단 파일이 이미지가 아닙니다." }
            return DownloadedImage(bytes, mimeType)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val DISCOVERY_URL = "https://www.dima.ac.kr/?p=1"
        const val PROFILE_FEED_URL = "https://www.instagram.com/api/v1/feed/user/30891067635/?count=12"
        const val INSTAGRAM_PUBLIC_APP_ID = "936619743392459"
        const val USER_AGENT = "DIMA-Now/1.4 GitHub data pipeline"
        const val MAX_IMAGE_BYTES = 15L * 1024 * 1024
        const val NOT_PUBLISHED_MESSAGE = "아직 새 식단이 올라오지 않았어요"
    }

    private data class DownloadedImage(val bytes: ByteArray, val mimeType: String)
}

internal class MealDiscoveryParser {
    fun parse(html: String): MealPost? = Jsoup.parse(html, "https://www.dima.ac.kr/?p=1").select("a[href]")
        .mapNotNull { anchor ->
            val text = anchor.text().replace(Regex("\\s+"), " ").trim()
            if (!text.startsWith("[DIMA 학생식당]")) return@mapNotNull null
            val hours = HOURS.find(text)?.groupValues?.let { "${it[1]} ~ ${it[2]}" } ?: return@mapNotNull null
            MealPost(text, anchor.absUrl("href"), hours)
        }.lastOrNull()

    private companion object {
        val HOURS = Regex("운영시간\\s*[:：]\\s*(\\d{1,2}:\\d{2})\\s*[~\\-–]\\s*(\\d{1,2}:\\d{2})")
    }
}

internal class MealProfileFeedParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(payload: String): MealPost? {
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        val username = ((root["user"] as? JsonObject)?.get("username") as? JsonPrimitive)?.contentOrNull
        val status = (root["status"] as? JsonPrimitive)?.contentOrNull
        if (username != "dima_people_1997" || status != "ok") return null
        return (root["items"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val caption = (((item["caption"] as? JsonObject)?.get("text")) as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val title = caption.lineSequence().firstOrNull()?.trim().orEmpty()
            if (!title.startsWith("[DIMA 학생식당]")) return@mapNotNull null
            val hours = HOURS.find(caption)?.groupValues?.let { "${it[1]} ~ ${it[2]}" } ?: return@mapNotNull null
            val code = (item["code"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: return@mapNotNull null
            val takenAt = (item["taken_at"] as? JsonPrimitive)?.longOrNull ?: Long.MIN_VALUE
            takenAt to MealPost(title, "https://www.instagram.com/p/$code/", hours)
        }.maxByOrNull { it.first }?.second
    }

    private companion object {
        val HOURS = Regex("운영시간\\s*[:：]\\s*(\\d{1,2}:\\d{2})\\s*[~\\-–]\\s*(\\d{1,2}:\\d{2})")
    }
}

internal data class InstagramCarousel(val imageUrls: List<String>) {
    val mealTableImageUrl: String? get() = imageUrls.getOrNull(1)
}

internal class InstagramCarouselParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(html: String): InstagramCarousel {
        Jsoup.parse(html).select("script").forEach { script ->
            val data = script.data().takeIf { it.contains("edge_sidecar_to_children") } ?: return@forEach
            val candidates = buildList { add(data); addAll(extractObjects(data)) }
            candidates.forEach { candidate ->
                val root = runCatching { json.parseToJsonElement(candidate) }.getOrNull() ?: return@forEach
                findCarousel(root)?.let { return InstagramCarousel(it) }
            }
        }
        return InstagramCarousel(emptyList())
    }

    private fun extractObjects(script: String): List<String> {
        val results = mutableListOf<String>()
        var from = 0
        while (true) {
            val handle = script.indexOf("s.handle(", from)
            if (handle < 0) return results
            val start = script.indexOf('{', handle)
            if (start < 0) return results
            balancedObject(script, start)?.let(results::add)
            from = start + 1
        }
    }

    private fun balancedObject(text: String, start: Int): String? {
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(start, index + 1)
            }
        }
        return null
    }

    private fun findCarousel(element: JsonElement): List<String>? = when (element) {
        is JsonObject -> element["edge_sidecar_to_children"]?.let(::images) ?: element.values.firstNotNullOfOrNull(::findCarousel)
        is JsonArray -> element.firstNotNullOfOrNull(::findCarousel)
        is JsonPrimitive -> if (element.isString && element.content.contains("edge_sidecar_to_children")) {
            runCatching { json.parseToJsonElement(element.content) }.getOrNull()?.let(::findCarousel)
        } else null
    }

    private fun images(element: JsonElement): List<String>? {
        val edges = (element as? JsonObject)?.get("edges") as? JsonArray ?: return null
        return edges.mapNotNull { edge ->
            (((edge as? JsonObject)?.get("node") as? JsonObject)?.get("display_url") as? JsonPrimitive)?.contentOrNull
        }.takeIf { it.isNotEmpty() }
    }
}
