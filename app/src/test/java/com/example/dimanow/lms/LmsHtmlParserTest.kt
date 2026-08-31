package com.example.dimanow.lms

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LmsHtmlParserTest {
    private val parser = LmsHtmlParser(ZoneId.of("Asia/Seoul"))

    @Test
    fun dashboardParsesCoursesAndClassifiesPublicItemKindsWithoutPrefetchingDetails() {
        val html = """
            <div class="learn_element_summery_box">
              <div class="learn_element_detail_list">
                <span class="badge">공지</span>
                <a href="/lms/class/boardItem/doViewBoardItem.dunet?board_no=7&boarditem_no=91">[음향기초실습(D반)] 1주차 수업안내 자료</a>
                <span>(등록일 : 2026.08.30 13:30:00)</span>
              </div>
              <div class="learn_element_detail_list">
                <span class="badge">과제</span>
                <a href="/lms/class/report/doViewReportStudent.dunet?report_no=12">[카메라기초및실습(D반)] 자기소개서 작성</a>
                <span>(종료시간 : 2026.09.25 23:59:00)</span>
              </div>
            </div>
            <div class="lecture_info" data-course-id="C101">
              <a href="javascript:goClass('C101')">음향기초실습(D반)</a>
              <span>교수명 : 이화현</span>
            </div>
        """.trimIndent()

        val result = parser.parseDashboard(html, "https://lms.dima.ac.kr")

        assertEquals(listOf("음향기초실습(D반)"), result.courses.map { it.name })
        assertEquals(listOf(LmsItemKind.NOTICE, LmsItemKind.ASSIGNMENT), result.items.map { it.kind })
        assertEquals("2026-09-25T14:59:00Z", result.items[1].dueAt.toString())
        assertTrue(result.items.all { it.detailUrl.startsWith("https://lms.dima.ac.kr/") })
    }

    @Test
    fun detailSanitizesExecutableMarkupAndKeepsAuthenticatedAttachments() {
        val item = LmsItem("91", "C101", "음향기초실습", LmsItemKind.NOTICE, "수업안내", detailUrl = "https://lms.dima.ac.kr/detail")
        val html = """
            <div id="board_contents"><p onclick="steal()">준비물 확인</p><script>alert(1)</script></div>
            <a href="javascript:doDownloadFile('501','7','91','N')">1주차 안내.pdf</a>
        """.trimIndent()

        val detail = parser.parseDetail(item, html, "https://lms.dima.ac.kr")

        assertTrue(detail.sanitizedHtml.contains("준비물 확인"))
        assertFalse(detail.sanitizedHtml.contains("script"))
        assertFalse(detail.sanitizedHtml.contains("onclick"))
        assertEquals("1주차 안내.pdf", detail.attachments.single().fileName)
        assertTrue(detail.attachments.single().downloadUrl.contains("boarditem_attach_file_no=501"))
    }
}
