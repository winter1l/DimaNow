package com.example.dimanow.lms

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LmsHtmlParserTest {
    private val parser = LmsHtmlParser(ZoneId.of("Asia/Seoul"))

    @Test
    fun dashboardParsesServerProvidedCompletionStatusRequestsWithoutGuessingValues() {
        val html = """
            <nav class="todo-tabs">
              <a href="/lms/myLecture/doListView.dunet?to_do_type=complete">완료한 학습</a>
              <a href="/lms/myLecture/doListView.dunet?to_do_type=incomplete">미완료한 학습</a>
            </nav>
        """.trimIndent()

        assertEquals(
            listOf(
                LmsStatusPageRequest(
                    completionState = LmsCompletionState.COMPLETE,
                    url = "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=complete",
                ),
                LmsStatusPageRequest(
                    completionState = LmsCompletionState.INCOMPLETE,
                    url = "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=incomplete",
                ),
            ),
            parser.parseDashboard(html, "https://lms.dima.ac.kr").statusPageRequests,
        )
    }

    @Test
    fun completionStatusRequestUsesTheRenderedFormFieldAndJavascriptArgument() {
        val html = """
            <form method="post" action="/lms/myLecture/doListView.dunet">
              <input type="hidden" name="term_year" value="2026">
              <input type="hidden" name="to_do_type" value="all">
              <button type="button" onclick="changeTodoType('finished')">완료한 학습</button>
              <button type="button" onclick="changeTodoType('unfinished')">미완료한 학습</button>
            </form>
        """.trimIndent()

        assertEquals(
            listOf(
                LmsStatusPageRequest(
                    LmsCompletionState.COMPLETE,
                    "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet",
                    mapOf("term_year" to "2026", "to_do_type" to "finished"),
                ),
                LmsStatusPageRequest(
                    LmsCompletionState.INCOMPLETE,
                    "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet",
                    mapOf("term_year" to "2026", "to_do_type" to "unfinished"),
                ),
            ),
            parser.parseDashboard(html, "https://lms.dima.ac.kr").statusPageRequests,
        )
    }

    @Test
    fun allLearningListParsesEveryRowAndKeepsTheVerifiedServerOrder() {
        val html = """
            <div class="learn_element_detail_list">
              <div class="outline"><ul>
                <li class="tab tab5"><span class="cata cata_task">과제</span>
                  <a href="javascript:fnGoContent('3','COURSE-A','D','301','S');">[2026-2학기)음향기초실습(D반)] 프로툴 사전진단</a>
                  <span>(종료시한 : 2026.08.28 23:59:59)</span></li>
                <li class="tab tab9"><span class="cata cata_notice">공지</span>
                  <a href="javascript:fnGoContent('1','COURSE-A','D','101','S');">[2026-2학기)음향기초실습(D반)] 1주차 수업안내</a>
                  <span>(등록일 : 2026.08.30 13:30:00)</span></li>
                <li class="tab tab2"><span class="cata cata_contents">콘텐츠</span>
                  <a href="javascript:fnGoContent('8','COURSE-A','D','801','S');">[2026-2학기)음향기초실습(D반)] 사운드디자인 기초(1)</a>
                  <span>(종료시한 : 2026.09.08 15:59:59)</span></li>
                <li class="tab tab4"><span class="cata cata_data">자료실</span>
                  <a href="javascript:fnGoContent('9','COURSE-A','D','901','S');">[2026-2학기)음향기초실습(D반)] 실습 자료</a>
                  <span>(등록일 : 2026.09.01 09:00:00)</span></li>
              </ul></div>
            </div>
            <a href="javascript:fncGoClassroom('COURSE-A','D','3');"><strong class="title">음향기초실습(D반)</strong></a>
        """.trimIndent()

        val items = parser.parseDashboard(html, "https://lms.dima.ac.kr").items

        assertEquals(4, items.size)
        assertEquals(
            listOf(
                LmsItemKind.ASSIGNMENT,
                LmsItemKind.NOTICE,
                LmsItemKind.CONTENT,
                LmsItemKind.MATERIAL,
            ),
            items.map { it.kind },
        )
        assertEquals(listOf("301", "101", "801", "901"), items.map { it.id })
        assertTrue(items.all { it.detailUrl.startsWith("https://lms.dima.ac.kr/") })
        assertTrue(items[2].detailUrl.contains("/lms/class/courseSchedule/doListView.dunet"))
    }

    @Test
    fun allLearningTypeCodesMapToInternalLmsRoutes() {
        val labels = listOf(
            "1" to "공지",
            "2" to "질문",
            "3" to "과제",
            "4" to "토론",
            "5" to "팀프로젝트",
            "6" to "퀴즈",
            "7" to "시험",
            "8" to "콘텐츠",
            "9" to "자료실",
        )
        val html = labels.joinToString(prefix = "<div class='learn_element_detail_list'><ul>", postfix = "</ul></div>") { (type, label) ->
            "<li><span class='cata'>$label</span><a href=\"javascript:fnGoContent('$type','COURSE-A','D','ID-$type','S');\">[과목] 항목 $type</a></li>"
        }

        val items = parser.parseDashboard(html, "https://lms.dima.ac.kr").items

        assertEquals(
            listOf(
                LmsItemKind.NOTICE,
                LmsItemKind.QUESTION,
                LmsItemKind.ASSIGNMENT,
                LmsItemKind.DISCUSSION,
                LmsItemKind.TEAM_PROJECT,
                LmsItemKind.QUIZ,
                LmsItemKind.EXAM,
                LmsItemKind.CONTENT,
                LmsItemKind.MATERIAL,
            ),
            items.map { it.kind },
        )
        assertTrue(items.all { LmsUrlPolicy.isAllowed(it.detailUrl) })
    }

    @Test
    fun selectedTermCanBeAppliedBeforeLoadingTheCompleteCourseList() {
        val html = """
            <div class="select_learn_term">
              <div class="select_termbox"><a class="title"><strong>2026년</strong></a>
                <a href="javascript:changeYearTerm('2026', '20', '2학기');">2026년</a>
              </div>
              <div class="select_termbox"><a class="title"><strong>2학기</strong></a>
                <a href="javascript:changeYearTerm('2026', '20', '2학기');">2학기</a>
              </div>
            </div>
        """.trimIndent()

        assertEquals(
            LmsTermSelection("2026", "20", "2학기"),
            parser.parseSelectedTermSelection(html),
        )
    }

    @Test
    fun dashboardParsesCurrentFncGoClassroomCourseCards() {
        val html = """
            <li class="box">
              <div class="top offline">
                <a href="javascript:fncGoClassroom('202620UN00025451401401D','D','3');">
                  <strong class="title">음향기초실습(D반)</strong>
                  <span class="info">· 교수명 : 이화현</span>
                </a>
              </div>
            </li>
        """.trimIndent()

        val result = parser.parseDashboard(html, "https://lms.dima.ac.kr")

        assertEquals(1, result.courses.size)
        assertEquals("202620UN00025451401401D", result.courses.single().id)
        assertEquals("D", result.courses.single().classNo)
        assertEquals("음향기초실습(D반)", result.courses.single().name)
        assertEquals("이화현", result.courses.single().professor)
    }

    @Test
    fun renderedDashboardCourseCatalogKeepsOnlyStructuredCourseFields() {
        val json = """
            [
              {"id":"202620UN00025451401401D","classNo":"D","name":"음향기초실습(D반)","professor":"이화현"},
              {"id":"202620UN00017391401401D","classNo":"D","name":"카메라기초및실습(D반)","professor":"김재호"}
            ]
        """.trimIndent()

        assertEquals(
            listOf(
                LmsCourse("202620UN00025451401401D", "음향기초실습(D반)", "이화현", "D"),
                LmsCourse("202620UN00017391401401D", "카메라기초및실습(D반)", "김재호", "D"),
            ),
            parser.parseRenderedCourses(json),
        )
    }

    @Test
    fun dashboardParsesCurrentJavascriptTodoAssignmentIntoAllowedStudentUrl() {
        val html = """
            <li class="box"><div class="top offline">
              <a href="javascript:fncGoClassroom('202620UN00017391401401D','D','3');">
                <strong class="title">카메라기초및실습(D반)</strong><span>교수명 : 김재호</span>
              </a>
            </div></li>
            <div class="learn_element_detail_list">
              <span class="label">과제</span>
              <a href="javascript:fnGoContent('3','202620UN00017391401401D','D','5553','S');">
                [2026-2학기)카메라기초및실습(D반)] 자기소개서 작성
              </a>
              <span>(종료시한 : 2026.09.25 23:59:00)</span>
            </div>
        """.trimIndent()

        val item = parser.parseDashboard(html, "https://lms.dima.ac.kr").items.single()

        assertEquals(LmsItemKind.ASSIGNMENT, item.kind)
        assertEquals("202620UN00017391401401D", item.courseId)
        assertEquals("카메라기초및실습(D반)", item.courseName)
        assertEquals("자기소개서 작성", item.title)
        assertEquals("2026-09-25T14:59:00Z", item.dueAt.toString())
        assertEquals(
            "https://lms.dima.ac.kr/lms/class/report/stud/doListView.dunet" +
                "?mnid=201008840336&course_id=202620UN00017391401401D&class_no=D&dataType=C",
            item.detailUrl,
        )
    }

    @Test
    fun shortJavascriptLoginRedirectIsRecognizedAsExpiredSession() {
        val html = """
            <html><head><script>
              alert('로그인이 필요합니다.');
              top.location.href = '/main/MainView.dunet';
            </script></head><body></body></html>
        """.trimIndent()

        assertTrue(
            parser.isLoginPage(
                html,
                "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?mnid=201008840728",
            ),
        )
    }

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
    fun courseBoardParsesJavascriptHistoryRowsAndTheNextPageRequest() {
        val course = LmsCourse(
            id = "202620UN00025451401401D",
            name = "음향기초실습(D반)",
            classNo = "D",
        )
        val html = """
            <form id="frm_board" method="post" action="/lms/class/boardItem/doListView.dunet?board_no=7">
              <input type="hidden" name="current_page" value="1">
              <input type="hidden" name="search_type" value="">
              <table>
                <tr>
                  <td><a href="javascript:fncViewBoardItem('91');">지난 안내</a></td>
                  <td>등록일 : 2026.08.20 10:00:00</td>
                </tr>
                <tr>
                  <td><a href="javascript:fncViewBoardItem('92');">새 안내</a></td>
                  <td>등록일 : 2026.08.30 10:00:00</td>
                </tr>
              </table>
              <div class="paging"><a href="javascript:fncPage('2')">2</a></div>
            </form>
        """.trimIndent()

        val page = parser.parseBoardPage(
            html = html,
            origin = "https://lms.dima.ac.kr",
            course = course,
            kind = LmsItemKind.NOTICE,
        )

        assertEquals(listOf("91", "92"), page.items.map { it.id })
        assertEquals(listOf("지난 안내", "새 안내"), page.items.map { it.title })
        assertEquals(false, page.items[0].isRead)
        assertEquals(
            "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet" +
                "?course_id=202620UN00025451401401D&class_no=D&boarditem_no=91&board_no=7&dataType=C",
            page.items[0].detailUrl,
        )
        assertEquals(
            LmsBoardPageRequest(
                url = "https://lms.dima.ac.kr/lms/class/boardItem/doListView.dunet?board_no=7",
                formFields = mapOf("current_page" to "2", "search_type" to ""),
            ),
            page.nextPages.single(),
        )
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

    @Test
    fun listBackedLearningItemResolvesItsMatchingInternalDetailLink() {
        val item = LmsItem(
            id = "301",
            courseId = "COURSE-A",
            courseName = "음향기초실습(D반)",
            kind = LmsItemKind.ASSIGNMENT,
            title = "프로툴 사전진단",
            detailUrl = "https://lms.dima.ac.kr/lms/class/report/stud/doListView.dunet",
        )
        val html = """
            <table>
              <tr><td><a href="/lms/class/report/stud/doViewReportStudent.dunet?report_no=300">다른 과제</a></td></tr>
              <tr><td><a href="/lms/class/report/stud/doViewReportStudent.dunet?report_no=301">프로툴 사전진단</a></td></tr>
            </table>
        """.trimIndent()

        assertEquals(
            "https://lms.dima.ac.kr/lms/class/report/stud/doViewReportStudent.dunet?report_no=301",
            parser.resolveLinkedDetailUrl(item, html, "https://lms.dima.ac.kr"),
        )
    }

    @Test
    fun listBackedLearningItemFallbackShowsOnlyTheMatchingRow() {
        val item = LmsItem(
            id = "801",
            courseId = "COURSE-A",
            courseName = "음향기초실습(D반)",
            kind = LmsItemKind.CONTENT,
            title = "사운드디자인 기초",
            detailUrl = "https://lms.dima.ac.kr/lms/class/courseSchedule/doListView.dunet",
        )
        val html = """
            <main class="sub_content"><ul>
              <li><a href="#other">다른 콘텐츠</a><a href="/lms/files/doDownloadFile.dunet?id=other">다른 파일.pdf</a></li>
              <li><a href="javascript:void(0)">사운드디자인 기초</a><p>9월 8일까지 학습</p>
                <a href="/lms/files/doDownloadFile.dunet?id=mine">내 자료.pdf</a></li>
            </ul></main>
        """.trimIndent()

        val detail = parser.parseDetail(item, html, "https://lms.dima.ac.kr")

        assertTrue(detail.sanitizedHtml.contains("사운드디자인 기초"))
        assertTrue(detail.sanitizedHtml.contains("9월 8일까지 학습"))
        assertFalse(detail.sanitizedHtml.contains("다른 콘텐츠"))
        assertEquals(listOf("내 자료.pdf"), detail.attachments.map { it.fileName })
    }

    @Test
    fun detailKeepsOfficialDirectDownloadLinksAsAttachments() {
        val item = LmsItem(
            id = "301",
            courseId = "COURSE-A",
            courseName = "음향기초실습(D반)",
            kind = LmsItemKind.ASSIGNMENT,
            title = "프로툴 사전진단",
            detailUrl = "https://lms.dima.ac.kr/detail",
        )
        val html = """
            <div class="report-content"><p>제출 안내</p></div>
            <a href="/lms/class/report/stud/doDownloadFile.dunet?report_attach_no=77">진단 양식.pdf</a>
        """.trimIndent()

        val detail = parser.parseDetail(item, html, "https://lms.dima.ac.kr")

        assertEquals("진단 양식.pdf", detail.attachments.single().fileName)
        assertEquals(
            "https://lms.dima.ac.kr/lms/class/report/stud/doDownloadFile.dunet?report_attach_no=77",
            detail.attachments.single().downloadUrl,
        )
    }

    @Test
    fun detailParsesRenderedAttachmentSizeForLaterChangeDetection() {
        val item = LmsItem(
            id = "91",
            courseId = "COURSE-A",
            courseName = "음향기초실습",
            kind = LmsItemKind.NOTICE,
            title = "수업안내",
            detailUrl = "https://lms.dima.ac.kr/detail",
        )
        val html = """
            <div id="board_contents"><p>안내</p></div>
            <div class="attachment"><a href="javascript:doDownloadFile('501','7','91','N')">1주차 안내.pdf</a><span>2 KB</span></div>
        """.trimIndent()

        assertEquals(2_048L, parser.parseDetail(item, html, "https://lms.dima.ac.kr").attachments.single().sizeBytes)
    }
}
