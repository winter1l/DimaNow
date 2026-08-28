package com.example.dimanow.meal

import org.jsoup.Jsoup

data class MealPost(
    val title: String,
    val sourceUrl: String,
    val hours: String,
) {
    val embedUrl: String
        get() = sourceUrl.substringBefore('?').trimEnd('/') + "/embed/captioned/"
}

class MealDiscoveryParser {
    fun parse(html: String, baseUrl: String = "https://www.dima.ac.kr/?p=1"): MealPost? {
        return Jsoup.parse(html, baseUrl).select("a[href]")
            .mapNotNull { anchor ->
                val text = anchor.text().replace(Regex("\\s+"), " ").trim()
                if (!text.startsWith("[DIMA 학생식당]")) return@mapNotNull null
                val hours = HOURS.find(text)?.groupValues?.let { "${it[1]} ~ ${it[2]}" } ?: return@mapNotNull null
                MealPost(text, anchor.absUrl("href"), hours)
            }
            .lastOrNull()
    }

    private companion object {
        val HOURS = Regex("운영시간\\s*[:：]\\s*(\\d{1,2}:\\d{2})\\s*[~\\-–]\\s*(\\d{1,2}:\\d{2})")
    }
}
