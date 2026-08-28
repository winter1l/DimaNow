package com.example.dimanow.pipeline

import com.example.dimanow.sync.NoticePayload
import com.example.dimanow.sync.NoticePayloadRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.jsoup.Jsoup

class NoticePayloadBuilder {
    fun build(html: String): NoticePayload {
        val document = Jsoup.parse(html, BASE_URL)
        val records = document.select("div.bbs_box ul li a[href*=viewMode=view]")
            .mapNotNull { row ->
                val href = row.attr("href")
                val id = REQ_IDX.find(href)?.groupValues?.get(1) ?: return@mapNotNull null
                val title = row.selectFirst("p.tit")?.text()?.trim().orEmpty()
                val dateText = row.selectFirst("span.date")?.text()?.substringBefore('|')?.trim()
                val date = runCatching { LocalDate.parse(dateText, DATE_FORMAT) }.getOrNull()
                    ?: return@mapNotNull null
                val url = row.absUrl("href")
                if (title.isEmpty() || !url.startsWith("$BASE_URL/")) return@mapNotNull null
                NoticePayloadRecord(
                    id = id,
                    title = title,
                    url = url,
                    date = date.toString(),
                    pinned = row.parent()?.hasClass("notice") == true || row.selectFirst("span.cate.notice") != null,
                )
            }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<NoticePayloadRecord> { it.date }.thenBy { it.id })
            .take(10)
        require(records.isNotEmpty()) { "공식 공지 목록을 찾지 못했습니다." }
        return NoticePayload(notices = records)
    }

    private companion object {
        const val BASE_URL = "https://www.dima.ac.kr"
        val REQ_IDX = Regex("reqIdx=(\\d+)")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    }
}
