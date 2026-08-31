package com.example.dimanow.lms

import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

data class ParsedLmsDashboard(
    val courses: List<LmsCourse>,
    val items: List<LmsItem>,
)

class LmsHtmlParser(private val zoneId: ZoneId = ZoneId.of("Asia/Seoul")) {
    fun parseDashboard(html: String, origin: String): ParsedLmsDashboard {
        val document = Jsoup.parse(html, origin)
        val courses = document.select("[data-course-id], .lecture_info, .lecture-card, .course-card")
            .mapNotNull(::parseCourse)
            .distinctBy { it.id }
        val items = document.select(".learn_element_detail_list, .todo-item, [data-lms-item]")
            .mapNotNull { parseItem(it, origin, courses) }
            .distinctBy { it.kind to it.id }
        return ParsedLmsDashboard(courses, items)
    }

    fun parseBoardItems(
        html: String,
        origin: String,
        course: LmsCourse,
        kind: LmsItemKind,
    ): List<LmsItem> {
        val document = Jsoup.parse(html, origin)
        return document.select("tr, .board-list li, .list-item")
            .mapNotNull { row ->
                val link = row.selectFirst("a[href]") ?: return@mapNotNull null
                val href = link.absUrl("href").ifBlank { resolve(origin, link.attr("href")) }
                if (!LmsUrlPolicy.isAllowed(href)) return@mapNotNull null
                val title = link.text().trim().ifBlank { return@mapNotNull null }
                LmsItem(
                    id = queryValue(href, "boarditem_no")
                        ?: queryValue(href, "report_no")
                        ?: stableId(course.id, kind.name, title, href),
                    courseId = course.id,
                    courseName = course.name,
                    kind = kind,
                    title = title,
                    registeredAt = extractInstant(row.text(), "등록일"),
                    dueAt = extractInstant(row.text(), "종료시간") ?: extractInstant(row.text(), "마감"),
                    detailUrl = href,
                )
            }
            .distinctBy { it.id }
    }

    fun parseDetail(item: LmsItem, html: String, origin: String): LmsItemDetail {
        val document = Jsoup.parse(html, origin)
        val body = document.selectFirst("#board_contents, .board_contents, .view_content, .report-content")
            ?: document.body()
        body.select("script, style, iframe, object, embed, form").remove()
        body.allElements.forEach { element ->
            element.attributes().asList()
                .filter { it.key.startsWith("on", ignoreCase = true) }
                .forEach { element.removeAttr(it.key) }
        }
        val clean = Jsoup.clean(
            body.html(),
            origin,
            Safelist.relaxed()
                .addTags("table", "thead", "tbody", "tr", "th", "td")
                .addAttributes(":all", "class")
                .removeAttributes(":all", "style"),
        )
        val attachments = document.select("a")
            .mapNotNull { parseAttachment(it, origin) }
            .distinctBy { it.id }
        return LmsItemDetail(item, clean, attachments)
    }

    fun isLoginPage(html: String, url: String): Boolean {
        if (URI.create(url).path.startsWith("/login/")) return true
        val document = Jsoup.parse(html, url)
        return document.selectFirst("#id") != null && document.selectFirst("#pass") != null
    }

    private fun parseCourse(element: Element): LmsCourse? {
        val name = element.selectFirst("a, .title, .lecture_title")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val raw = element.attr("data-course-id").ifBlank {
            COURSE_ID.find(element.html())?.groupValues?.get(1).orEmpty()
        }
        val id = raw.ifBlank { "local-${stableId(name)}" }
        val professor = PROFESSOR.find(element.text())?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        return LmsCourse(id, name, professor)
    }

    private fun parseItem(element: Element, origin: String, courses: List<LmsCourse>): LmsItem? {
        val link = element.selectFirst("a[href]") ?: return null
        val title = link.text().trim().ifBlank { return null }
        val href = link.absUrl("href").ifBlank { resolve(origin, link.attr("href")) }
        if (!LmsUrlPolicy.isAllowed(href)) return null
        val badge = element.selectFirst(".badge, .label, [class*=badge]")?.text().orEmpty()
        val kind = classifyKind(badge.ifBlank { element.text().substringBefore(' ') })
        val titleCourse = COURSE_NAME.find(title)?.groupValues?.get(1)?.trim()
        val course = courses.firstOrNull { it.name == titleCourse || title.startsWith("[${it.name}]") }
        val courseName = course?.name ?: titleCourse.orEmpty().ifBlank { "전체" }
        val courseId = course?.id ?: stableId(courseName)
        return LmsItem(
            id = queryValue(href, "boarditem_no")
                ?: queryValue(href, "report_no")
                ?: stableId(kind.name, title, href),
            courseId = courseId,
            courseName = courseName,
            kind = kind,
            title = title.removePrefix("[$courseName]").trim(),
            registeredAt = extractInstant(element.text(), "등록일"),
            dueAt = extractInstant(element.text(), "종료시간") ?: extractInstant(element.text(), "마감"),
            detailUrl = href,
        )
    }

    private fun parseAttachment(link: Element, origin: String): LmsAttachment? {
        val action = link.attr("href") + " " + link.attr("onclick")
        val values = DOWNLOAD.find(action)?.groupValues ?: return null
        val attachNo = values[1]
        val boardNo = values[2]
        val itemNo = values[3]
        val learningDesign = values.getOrNull(4).orEmpty().ifBlank { "N" }
        val url = "$origin/lms/class/boardItem/doDownloadFile.dunet" +
            "?boarditem_attach_file_no=${encode(attachNo)}&board_no=${encode(boardNo)}" +
            "&boarditem_no=${encode(itemNo)}&learning_design_yn=${encode(learningDesign)}&time_flag="
        return LmsAttachment(
            id = "$boardNo:$itemNo:$attachNo",
            fileName = link.text().trim().ifBlank { "첨부파일" },
            downloadUrl = url,
        )
    }

    private fun classifyKind(value: String): LmsItemKind = when {
        "공지" in value -> LmsItemKind.NOTICE
        "과제" in value -> LmsItemKind.ASSIGNMENT
        "자료" in value -> LmsItemKind.MATERIAL
        else -> LmsItemKind.OTHER
    }

    private fun extractInstant(text: String, label: String): Instant? {
        val match = Regex("$label\\s*[:：]?\\s*(\\d{4}\\.\\d{2}\\.\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})")
            .find(text)
            ?: return null
        return runCatching {
            LocalDateTime.parse(match.groupValues[1], DATE_TIME).atZone(zoneId).toInstant()
        }.getOrNull()
    }

    private fun queryValue(url: String, name: String): String? = runCatching {
        URI.create(url).rawQuery.orEmpty().split('&').firstNotNullOfOrNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.firstOrNull() == name) URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8") else null
        }
    }.getOrNull()

    private fun resolve(origin: String, href: String): String = runCatching { URI.create(origin).resolve(href).toString() }.getOrDefault("")

    private fun stableId(vararg values: String): String = values.joinToString("|").hashCode().toUInt().toString(16)

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    companion object {
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
        val COURSE_ID = Regex("(?:goClass|doSetSessionClassRoom)\\(['\"]([^'\"]+)")
        val COURSE_NAME = Regex("^\\[([^]]+)]")
        val PROFESSOR = Regex("교수(?:명)?\\s*[:：]\\s*([^·|\\n]+)")
        val DOWNLOAD = Regex("(?:doDownloadFile|fn_fileDown)\\(['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"](?:\\s*,\\s*['\"]([^'\"]*)['\"])?")
    }
}
