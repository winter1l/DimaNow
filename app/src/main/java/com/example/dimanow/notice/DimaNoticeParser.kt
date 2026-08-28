package com.example.dimanow.notice

import com.example.dimanow.domain.CampusNotice
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.jsoup.Jsoup

/**
 * 공식 공지사항 목록(`?p=111`)의 `div.bbs_box > ul > li > a` 구조를 파싱한다.
 * 행 링크는 `/?p=111&page=1&viewMode=view&reqIdx=<ID>`, 제목은 `p.tit`,
 * 고정 공지는 `span.cate.notice`, 날짜는 `span.date`의 `yyyy.MM.dd | Hit : n` 형식.
 */
class DimaNoticeParser {
    fun parse(html: String): List<CampusNotice> {
        val document = Jsoup.parse(html, BASE_URL)
        val rows = document.select("div.bbs_box ul li a[href*=viewMode=view]")
        val notices = rows.mapNotNull { row ->
            val href = row.attr("href")
            val id = REQ_IDX_PATTERN.find(href)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = row.selectFirst("p.tit")?.text()?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val dateText = row.selectFirst("span.date")?.text()?.substringBefore("|")?.trim()
            val date = dateText?.let { text ->
                runCatching { LocalDate.parse(text, DATE_FORMAT) }.getOrNull()
            } ?: return@mapNotNull null
            CampusNotice(
                id = id,
                title = title,
                url = row.absUrl("href").ifEmpty { BASE_URL + href },
                date = date,
                isPinned = row.selectFirst("span.cate.notice") != null,
            )
        }
        return notices
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<CampusNotice>> { it.value.date }.thenBy { it.index })
            .map { it.value }
            .distinctBy { it.id }
            .take(MAX_NOTICES)
    }

    companion object {
        private const val BASE_URL = "https://www.dima.ac.kr"
        private val REQ_IDX_PATTERN = Regex("""reqIdx=(\d+)""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        private const val MAX_NOTICES = 10
    }
}
