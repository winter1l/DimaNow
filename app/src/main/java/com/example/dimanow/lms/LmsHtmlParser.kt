package com.example.dimanow.lms

import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

data class ParsedLmsDashboard(
    val courses: List<LmsCourse>,
    val items: List<LmsItem>,
    val statusPageRequests: List<LmsStatusPageRequest> = emptyList(),
)

data class ParsedLmsBoardPage(
    val items: List<LmsItem>,
    val nextPages: List<LmsBoardPageRequest>,
)

class LmsHtmlParser(private val zoneId: ZoneId = ZoneId.of("Asia/Seoul")) {
    fun parseRenderedCourses(value: String): List<LmsCourse> = runCatching {
        Json.parseToJsonElement(value).jsonArray.mapNotNull { element ->
            val fields = element.jsonObject
            val id = fields["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val name = fields["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val classNo = fields["classNo"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val professor = fields["professor"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf(String::isNotBlank)
            if (id.isBlank() || id.length > 128 || name.isBlank() || name.length > 200 || classNo.length > 50) {
                null
            } else {
                LmsCourse(id, name, professor?.take(100), classNo)
            }
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    fun parseSelectedTermSelection(html: String): LmsTermSelection? {
        val document = Jsoup.parse(html)
        val titles = document.select(".select_learn_term .title strong")
            .map { it.text().trim() }
        val year = titles.firstNotNullOfOrNull { YEAR.find(it)?.groupValues?.get(1) } ?: return null
        val termName = titles.firstOrNull { it != titles.firstOrNull() && it.isNotBlank() }
            ?: titles.firstOrNull { !YEAR.matches(it) }
            ?: return null
        return document.select(".select_learn_term a[href*=changeYearTerm]")
            .asSequence()
            .mapNotNull { CHANGE_YEAR_TERM.find(it.attr("href")) }
            .firstOrNull { match -> match.groupValues[1] == year && match.groupValues[3] == termName }
            ?.let { match -> LmsTermSelection(match.groupValues[1], match.groupValues[2], match.groupValues[3]) }
    }

    fun parseDashboard(html: String, origin: String): ParsedLmsDashboard {
        val document = Jsoup.parse(html, origin)
        val courses = document.select(
            "[data-course-id], .lecture_info, .lecture-card, .course-card, a[href*=fncGoClassroom]",
        )
            .mapNotNull(::parseCourse)
            .distinctBy { it.id }
        val verifiedRows = document.select(
            ".learn_element_detail_list li, .learn_element_detail_list .todo-item, [data-lms-item]",
        )
        val itemRows = if (verifiedRows.isNotEmpty()) {
            verifiedRows
        } else {
            // Older/fixture pages expose one item directly in each container.
            document.select(".learn_element_detail_list, .todo-item")
        }
        val items = itemRows
            .mapNotNull { parseItem(it, origin, courses) }
            .distinctBy { it.kind to it.id }
        val statusPageRequests = parseStatusPageRequests(document, origin)
        return ParsedLmsDashboard(courses, items, statusPageRequests)
    }

    private fun parseStatusPageRequests(
        document: org.jsoup.nodes.Document,
        origin: String,
    ): List<LmsStatusPageRequest> = document.select("a, button, input[type=submit]")
        .mapNotNull { control ->
            val label = control.text().trim().ifBlank { control.attr("value").trim() }
            val completionState = when (label) {
                "완료한 학습" -> LmsCompletionState.COMPLETE
                "미완료한 학습" -> LmsCompletionState.INCOMPLETE
                else -> return@mapNotNull null
            }
            val rawHref = control.attr("href").trim()
            if (rawHref.isNotBlank() && !rawHref.startsWith("javascript:", ignoreCase = true)) {
                val url = control.absUrl("href").ifBlank { resolve(origin, rawHref) }
                return@mapNotNull url.takeIf(LmsUrlPolicy::isAllowed)
                    ?.let { LmsStatusPageRequest(completionState, it) }
            }
            val form = control.closest("form") ?: return@mapNotNull null
            val url = form.absUrl("action").ifBlank { resolve(origin, form.attr("action")) }
            if (!LmsUrlPolicy.isAllowed(url)) return@mapNotNull null
            val fields = linkedMapOf<String, String>()
            form.select("input[name]").forEach { input -> fields[input.attr("name")] = input.attr("value") }
            val controlName = control.attr("name").trim()
            val controlValue = control.attr("value").trim()
            if (controlName.isNotBlank() && controlValue.isNotBlank()) {
                fields[controlName] = controlValue
            } else {
                val renderedArgument = STATUS_ARGUMENT.find(control.attr("onclick") + " " + rawHref)
                    ?.groupValues?.get(1) ?: return@mapNotNull null
                val renderedStatusField = form.select("input[name]").firstOrNull { input ->
                    input.attr("value").equals("all", ignoreCase = true) ||
                        input.attr("name").contains("todo", ignoreCase = true) ||
                        input.attr("name").contains("to_do", ignoreCase = true)
                }?.attr("name") ?: return@mapNotNull null
                fields[renderedStatusField] = renderedArgument
            }
            LmsStatusPageRequest(completionState, url, fields)
        }
        .distinctBy { it.completionState }

    fun parseBoardItems(
        html: String,
        origin: String,
        course: LmsCourse,
        kind: LmsItemKind,
    ): List<LmsItem> = parseBoardPage(html, origin, course, kind).items

    fun parseBoardPage(
        html: String,
        origin: String,
        course: LmsCourse,
        kind: LmsItemKind,
    ): ParsedLmsBoardPage {
        val document = Jsoup.parse(html, origin)
        val items = document.select("tr, .board-list li, .list-item")
            .mapNotNull { row ->
                val link = row.selectFirst("a[href]") ?: return@mapNotNull null
                val rawHref = link.attr("href")
                val href = javascriptBoardDetailUrl(rawHref, origin, course, kind)
                    ?: link.absUrl("href").ifBlank { resolve(origin, rawHref) }
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
        val currentPage = document.selectFirst("input[name=current_page], input[name=page], input[name=pageIndex], input[name=pageNo]")
            ?.attr("value")
            ?.toIntOrNull()
            ?: 1
        val nextPages = document.select(".paging a[href], .pagination a[href], a[href*=fncPage], a[href*=goPage]")
            .mapNotNull { link -> parsePageRequest(link, origin, currentPage) }
            .distinctBy { it.url to it.formFields }
        return ParsedLmsBoardPage(items, nextPages)
    }

    fun parseDetail(item: LmsItem, html: String, origin: String): LmsItemDetail {
        val document = Jsoup.parse(html, origin)
        val matchingRow = findItemLink(document, item)
            ?.closest("tr, li, .list-item, .content-item, .lecture-item")
        val articleBody = document.selectFirst("#board_contents, .board_contents, .view_content, .report-content")
        val body = articleBody
            ?: matchingRow
            ?: document.selectFirst(".sub_content, main, [role=main]")
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
        val attachmentScope = if (articleBody == null && matchingRow != null) matchingRow else document
        val attachments = attachmentScope.select("a")
            .mapNotNull { parseAttachment(it, origin) }
            .distinctBy { it.id }
        return LmsItemDetail(item, clean, attachments)
    }

    fun resolveLinkedDetailUrl(item: LmsItem, html: String, origin: String): String? {
        val document = Jsoup.parse(html, origin)
        val link = findItemLink(document, item) ?: return null
        val rawHref = link.attr("href").trim()
        if (rawHref.isBlank() || rawHref.startsWith("javascript:", ignoreCase = true)) return null
        val resolved = link.absUrl("href").ifBlank { resolve(origin, rawHref) }
        return resolved.takeIf(LmsUrlPolicy::isAllowed)
    }

    fun isLoginPage(html: String, url: String): Boolean {
        if (URI.create(url).path.startsWith("/login/")) return true
        val document = Jsoup.parse(html, url)
        if (document.selectFirst("#id") != null && document.selectFirst("#pass") != null) return true
        val scripts = document.select("script").joinToString("\n") { it.data() }
        return document.body().text().isBlank() &&
            LOGIN_REDIRECT_LOCATION.containsMatchIn(scripts) &&
            scripts.contains("MainView.dunet", ignoreCase = true)
    }

    private fun parseCourse(element: Element): LmsCourse? {
        val link = if (element.tagName() == "a") element else element.selectFirst("a")
        val name = link?.selectFirst(".title, .lecture_title")?.text()?.trim()
            ?: link?.text()?.trim()
            ?: element.selectFirst(".title, .lecture_title")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val action = listOfNotNull(link?.attr("href"), link?.attr("onclick"), element.html()).joinToString(" ")
        val currentMatch = FNC_GO_CLASSROOM.find(action)
        val raw = element.attr("data-course-id").ifBlank {
            currentMatch?.groupValues?.get(1).orEmpty().ifBlank {
                COURSE_ID.find(action)?.groupValues?.get(1).orEmpty()
            }
        }
        val id = raw.ifBlank { "local-${stableId(name)}" }
        val professor = PROFESSOR.find(element.text())?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val classNo = element.attr("data-class-no").ifBlank { currentMatch?.groupValues?.get(2).orEmpty() }
        return LmsCourse(id, name, professor, classNo)
    }

    private fun findItemLink(document: org.jsoup.nodes.Document, item: LmsItem): Element? {
        val idPattern = Regex("(?:^|\\D)${Regex.escape(item.id)}(?:\\D|$)")
        return document.select("a[href], a[onclick]").firstOrNull { link ->
            val action = link.attr("href") + " " + link.attr("onclick")
            idPattern.containsMatchIn(action) ||
                link.text().trim().contains(item.title, ignoreCase = true)
        }
    }

    private fun parseItem(element: Element, origin: String, courses: List<LmsCourse>): LmsItem? {
        val link = element.selectFirst("a[href]") ?: return null
        val title = link.text().trim().ifBlank { return null }
        val rawHref = link.attr("href")
        val contentMatch = FN_GO_CONTENT.find(rawHref)
        val href = resolveContentUrl(origin, contentMatch)
            ?: link.absUrl("href").ifBlank { resolve(origin, rawHref) }
        if (!LmsUrlPolicy.isAllowed(href)) return null
        val badge = element.selectFirst(".cata, .badge, .label, [class*=badge]")?.text().orEmpty()
        val kind = classifyKind(badge.ifBlank { element.text().substringBefore(' ') })
        val titleCourse = COURSE_NAME.find(title)?.groupValues?.get(1)?.trim()
        val contentCourseId = contentMatch?.groupValues?.get(2).orEmpty()
        val course = courses.firstOrNull { it.id == contentCourseId }
            ?: courses.firstOrNull { it.name == titleCourse || title.startsWith("[${it.name}]") }
        val courseName = course?.name ?: titleCourse.orEmpty().ifBlank { "전체" }
        val courseId = course?.id ?: contentCourseId.ifBlank { stableId(courseName) }
        return LmsItem(
            id = queryValue(href, "boarditem_no")
                ?: queryValue(href, "report_no")
                ?: contentMatch?.groupValues?.get(4)?.takeIf { it.isNotBlank() }
                ?: stableId(kind.name, title, href),
            courseId = courseId,
            courseName = courseName,
            kind = kind,
            title = title.replaceFirst(COURSE_PREFIX, "").trim(),
            registeredAt = extractInstant(element.text(), "등록일"),
            dueAt = extractInstant(element.text(), "종료시간")
                ?: extractInstant(element.text(), "종료시한")
                ?: extractInstant(element.text(), "마감"),
            detailUrl = href,
        )
    }

    private fun resolveContentUrl(origin: String, match: MatchResult?): String? {
        match ?: return null
        val type = match.groupValues[1]
        val courseId = match.groupValues[2]
        val classNo = match.groupValues[3]
        val itemId = match.groupValues[4]
        val pathAndFields = when (type) {
            "1" -> "/lms/class/boardItem/doViewBoardItem.dunet" to listOf(
                "mnid" to "201008945595", "course_id" to courseId, "class_no" to classNo,
                "boarditem_no" to itemId, "board_no" to "7", "dataType" to "C",
            )
            "2" -> "/lms/class/boardItem/doViewBoardItem.dunet" to listOf(
                "mnid" to "201008604579", "course_id" to courseId, "class_no" to classNo,
                "boarditem_no" to itemId, "board_no" to "5", "dataType" to "C",
            )
            "3" -> "/lms/class/report/stud/doListView.dunet" to listOf(
                "mnid" to "201008840336", "course_id" to courseId, "class_no" to classNo,
                "dataType" to "C",
            )
            "4" -> "/lms/class/discuss/stud/doListView.dunet" to listOf(
                "mnid" to "201008136286", "course_id" to courseId, "class_no" to classNo,
                "dataType" to "C",
            )
            "5" -> "/lms/class/teamproject/stud/doGetTeamProjInfo.dunet" to listOf(
                "mnid" to "201008646182", "course_id" to courseId, "class_no" to classNo,
                "dataType" to "C",
            )
            "6", "7" -> "/lms/class/exam/apply/doListView.dunet" to listOf(
                "mnid" to "201103822144", "course_id" to courseId, "class_no" to classNo,
                "dataType" to "C",
            )
            "8" -> "/lms/class/courseSchedule/doListView.dunet" to listOf(
                "mnid" to "201008103161", "course_id" to courseId, "class_no" to classNo,
                "dataType" to "C",
            )
            "9" -> "/lms/class/boardItem/doViewBoardItem.dunet" to listOf(
                "mnid" to "20100863099", "course_id" to courseId, "class_no" to classNo,
                "boarditem_no" to itemId, "board_no" to "6", "dataType" to "C",
            )
            else -> return null
        }
        val query = pathAndFields.second.joinToString("&") { (name, value) -> "$name=${encode(value)}" }
        return resolve(origin, "${pathAndFields.first}?$query")
    }

    private fun parseAttachment(link: Element, origin: String): LmsAttachment? {
        val action = link.attr("href") + " " + link.attr("onclick")
        DOWNLOAD.find(action)?.groupValues?.let { values ->
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
                sizeBytes = parseAttachmentSize(link.parent()?.text().orEmpty()),
            )
        }
        val rawHref = link.attr("href").trim()
        val directUrl = link.absUrl("href").ifBlank { resolve(origin, rawHref) }
        val path = runCatching { URI.create(directUrl).path.orEmpty() }.getOrDefault("")
        if (!LmsUrlPolicy.isAllowed(directUrl) || !DOWNLOAD_PATH.containsMatchIn(path)) return null
        return LmsAttachment(
            id = stableId(directUrl),
            fileName = link.text().trim().ifBlank { "첨부파일" },
            downloadUrl = directUrl,
            sizeBytes = parseAttachmentSize(link.parent()?.text().orEmpty()),
        )
    }

    private fun parseAttachmentSize(text: String): Long? {
        val match = FILE_SIZE.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].uppercase()) {
            "B" -> 1L
            "KB" -> 1_024L
            "MB" -> 1_024L * 1_024L
            "GB" -> 1_024L * 1_024L * 1_024L
            else -> return null
        }
        return (value * multiplier).toLong()
    }

    private fun javascriptBoardDetailUrl(
        rawHref: String,
        origin: String,
        course: LmsCourse,
        kind: LmsItemKind,
    ): String? {
        val itemId = VIEW_BOARD_ITEM.find(rawHref)?.groupValues?.get(1) ?: return null
        val boardNo = when (kind) {
            LmsItemKind.NOTICE -> "7"
            LmsItemKind.MATERIAL -> "6"
            else -> return null
        }
        val fields = listOf(
            "course_id" to course.id,
            "class_no" to course.classNo,
            "boarditem_no" to itemId,
            "board_no" to boardNo,
            "dataType" to "C",
        )
        return resolve(
            origin,
            "/lms/class/boardItem/doViewBoardItem.dunet?" +
                fields.joinToString("&") { (name, value) -> "$name=${encode(value)}" },
        )
    }

    private fun parsePageRequest(link: Element, origin: String, currentPage: Int): LmsBoardPageRequest? {
        val page = PAGE_ACTION.find(link.attr("href"))?.groupValues?.get(1) ?: return null
        if ((page.toIntOrNull() ?: return null) <= currentPage) return null
        val form = link.closest("form") ?: link.ownerDocument()?.selectFirst("form") ?: return null
        val action = form.absUrl("action").ifBlank { resolve(origin, form.attr("action")) }
        if (!LmsUrlPolicy.isAllowed(action)) return null
        val fields = linkedMapOf<String, String>()
        form.select("input[name]").forEach { input -> fields[input.attr("name")] = input.attr("value") }
        val pageField = fields.keys.firstOrNull { it in PAGE_FIELD_NAMES } ?: return null
        fields[pageField] = page
        return LmsBoardPageRequest(action, fields)
    }

    private fun classifyKind(value: String): LmsItemKind = when {
        "공지" in value -> LmsItemKind.NOTICE
        "과제" in value -> LmsItemKind.ASSIGNMENT
        "콘텐츠" in value -> LmsItemKind.CONTENT
        "자료" in value -> LmsItemKind.MATERIAL
        "질문" in value || "Q&A" in value -> LmsItemKind.QUESTION
        "토론" in value -> LmsItemKind.DISCUSSION
        "팀프로젝트" in value || "팀 프로젝트" in value -> LmsItemKind.TEAM_PROJECT
        "퀴즈" in value -> LmsItemKind.QUIZ
        "시험" in value -> LmsItemKind.EXAM
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
        val FNC_GO_CLASSROOM = Regex("fncGoClassroom\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]")
        val FN_GO_CONTENT = Regex(
            "fnGoContent\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*," +
                "\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]",
        )
        val COURSE_ID = Regex("(?:goClass|doSetSessionClassRoom)\\(['\"]([^'\"]+)")
        val COURSE_NAME = Regex("^\\[([^]]+)]")
        val COURSE_PREFIX = Regex("^\\[[^]]+]\\s*")
        val LOGIN_REDIRECT_LOCATION = Regex("(?:top\\.)?(?:window\\.)?location(?:\\.href)?\\s*=", RegexOption.IGNORE_CASE)
        val PROFESSOR = Regex("교수(?:명)?\\s*[:：]\\s*([^·|\\n]+)")
        val DOWNLOAD = Regex("(?:doDownloadFile|fn_fileDown)\\(['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"](?:\\s*,\\s*['\"]([^'\"]*)['\"])?")
        val DOWNLOAD_PATH = Regex("(?:download|filedown)", RegexOption.IGNORE_CASE)
        val YEAR = Regex("(\\d{4})년?")
        val CHANGE_YEAR_TERM = Regex("changeYearTerm\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]")
        val VIEW_BOARD_ITEM = Regex("(?:fncViewBoardItem|fnViewBoardItem|doViewBoardItem)\\(\\s*['\"]([^'\"]+)['\"]")
        val PAGE_ACTION = Regex("(?:fncPage|goPage|fnPage)\\(\\s*['\"]?(\\d+)['\"]?")
        val STATUS_ARGUMENT = Regex("['\"]([A-Za-z0-9_-]{1,64})['\"]")
        val FILE_SIZE = Regex("(\\d+(?:\\.\\d+)?)\\s*(B|KB|MB|GB)\\b", RegexOption.IGNORE_CASE)
        val PAGE_FIELD_NAMES = setOf("current_page", "page", "pageIndex", "pageNo")
    }
}
