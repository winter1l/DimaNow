package com.example.dimanow.lms

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    fun completionStatusRequestsFollowTheActualRenderedJavascriptLinks() {
        val html = """
            <nav class="todo-tabs">
              <a href="javascript:changeToDoList('complete')">완료한 학습</a>
              <a href="javascript:changeToDoList('incomplete')">미완료한 학습</a>
            </nav>
            <script>
              function changeToDoList(type) {
                location.href = '/lms/myLecture/doListView.dunet?to_do_type=' + type;
              }
            </script>
        """.trimIndent()

        assertEquals(
            listOf(
                LmsStatusPageRequest(
                    LmsCompletionState.COMPLETE,
                    "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=complete",
                ),
                LmsStatusPageRequest(
                    LmsCompletionState.INCOMPLETE,
                    "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=incomplete",
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
    fun videoRowsSharingTheOfficialCourseContentIdRemainSeparateItems() {
        val html = """
            <div class="learn_element_detail_list"><ul>
              <li><span class="cata">콘텐츠</span>
                <a href="javascript:fnGoContent('8','COURSE-A','D','COURSE-A_V','S');">[과목] 방송 프로그램 제작(0분/26분)</a></li>
              <li><span class="cata">콘텐츠</span>
                <a href="javascript:fnGoContent('8','COURSE-A','D','COURSE-A_V','S');">[과목] 사운드디자인 기초(1)(0분/28분)</a></li>
              <li><span class="cata">콘텐츠</span>
                <a href="javascript:fnGoContent('8','COURSE-A','D','COURSE-A_V','S');">[과목] 사운드디자인 기초(2)(0분/26분)</a></li>
            </ul></div>
        """.trimIndent()

        val items = parser.parseDashboard(html, "https://lms.dima.ac.kr").items

        assertEquals(
            listOf("방송 프로그램 제작(0분/26분)", "사운드디자인 기초(1)(0분/28분)", "사운드디자인 기초(2)(0분/26분)"),
            items.map { it.title },
        )
        assertEquals(3, items.map { it.id }.distinct().size)
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
    fun dashboardPreservesTheOfficialAssignmentListActionBeforeExactRowSelection() {
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
                "?mnid=201008840336&course_id=202620UN00017391401401D&class_no=D" +
                "&dataType=C",
            item.detailUrl,
        )
    }

    @Test
    fun assignmentListResolvesOnlyTheUniqueExactOfficialModifyAction() {
        val item = LmsItem(
            id = "42",
            courseId = "course-alpha",
            courseName = "실습 과목",
            kind = LmsItemKind.ASSIGNMENT,
            title = "2주차 분석 과제",
            detailUrl = "https://lms.dima.ac.kr/lms/class/report/stud/doListView.dunet",
        )
        val html = """
            <form name="list_frm_form" method="post" action="/lms/class/report/stud/doFormReport.dunet">
              <input type="hidden" name="mode" value="">
              <input type="hidden" name="report_no" value="">
              <a class="subject" href="#"
                 onclick="fncModifyReport('report-other', 'N', '1', 'Y', 'Y')">2주차 분석 과제</a>
              <a class="subject" href="#"
                 onclick="fncModifyReport(42, 'N', '1', 'Y', 'Y')">
                <span class="ellipsis">2주차 분석 과제</span>
                <span class="learning-meta">2주차 · 1회 · 2026.09.01 ~ 2026.09.08</span>
              </a>
            </form>
        """.trimIndent()

        assertEquals(
            "fncModifyReport('42','N','1','Y','Y')",
            parser.resolveAssignmentListOnClick(item, html),
        )
    }

    @Test
    fun assignmentListUsesOnlyTheFirstDirectTextSegmentAsTheExactTitle() {
        val item = LmsItem(
            id = "42",
            courseId = "course-alpha",
            courseName = "실습 과목",
            kind = LmsItemKind.ASSIGNMENT,
            title = "2주차 분석 과제",
            detailUrl = "https://lms.dima.ac.kr/lms/class/report/stud/doListView.dunet",
        )
        val html = """
            <form name="list_frm_form">
              <a class="subject" href="#" onclick="fncModifyReport('42', 'N', '1', 'Y','Y')">2주차 분석 과제<br><br>
                2주 3회차 | 인정시간 : 30분<br>
                26/09/01 16:00 ~ 26/09/08 15:59
              </a>
            </form>
        """.trimIndent()

        assertEquals(
            "fncModifyReport('42','N','1','Y','Y')",
            parser.resolveAssignmentListOnClick(item, html),
        )
    }

    @Test
    fun assignmentListDoesNotGuessWhenTheExactOfficialActionIsDuplicatedOrMissing() {
        val item = LmsItem(
            id = "report-alpha",
            courseId = "course-alpha",
            courseName = "실습 과목",
            kind = LmsItemKind.ASSIGNMENT,
            title = "2주차 분석 과제",
            detailUrl = "https://lms.dima.ac.kr/lms/class/report/stud/doListView.dunet",
        )
        val duplicate = """
            <form name="list_frm_form">
              <a class="subject" href="#" onclick="fncModifyReport('report-alpha','N','1','Y','Y')">2주차 분석 과제</a>
              <a class="subject" href="#" onclick="fncModifyReport('report-alpha','N','1','Y','Y')">2주차 분석 과제</a>
            </form>
        """.trimIndent()
        val partialOnly = """
            <form name="list_frm_form">
              <a class="subject" href="#" onclick="fncModifyReport('report-alpha','N','1','Y','Y')">2주차 분석 과제 보충</a>
            </form>
        """.trimIndent()

        assertNull(parser.resolveAssignmentListOnClick(item, duplicate))
        assertNull(parser.resolveAssignmentListOnClick(item, partialOnly))
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
    fun officialPortalCredentialPageIsRecognizedAsExpiredLmsSession() {
        val html = """
            <form>
              <input id="txtID" name="txtID">
              <input id="txtPwd" name="txtPwd" type="password">
            </form>
        """.trimIndent()

        assertTrue(
            parser.isLoginPage(
                html,
                "https://portal.dima.ac.kr/?r=https://lms.dima.ac.kr/sso/index.jsp",
            ),
        )
        assertFalse(
            parser.isLoginPage(
                html,
                "https://portal.dima.ac.kr.evil.example/",
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
    fun noticeListSummaryRowIsNotAcceptedAsNativeDetail() {
        val item = LmsItem(
            id = "91",
            courseId = "COURSE-A",
            courseName = "음향기초실습(D반)",
            kind = LmsItemKind.NOTICE,
            title = "1주차 수업안내 자료",
            detailUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
        )
        val listHtml = """
            <table class="table_list_basic"><tbody>
              <tr>
                <td>1주차</td>
                <td><a href="/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91">1주차 수업안내 자료</a></td>
                <td>비공개</td>
                <td>2026.08.30</td>
              </tr>
            </tbody></table>
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            parser.parseDetail(item, listHtml, item.detailUrl)
        }

        assertEquals("정확한 게시글 내용을 찾지 못했습니다", error.message)
    }

    @Test
    fun materialListSummaryRowIsNotAcceptedAsNativeDetail() {
        val item = LmsItem(
            id = "32258",
            courseId = "COURSE-A",
            courseName = "음향기초실습(D반)",
            kind = LmsItemKind.MATERIAL,
            title = "1주차 수업안내 자료",
            detailUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=32258",
        )
        val listHtml = """
            <table class="table_list_basic"><tbody>
              <tr>
                <td>1주차</td>
                <td><a href="/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=32258">1주차 수업안내 자료</a></td>
                <td>비공개</td>
                <td>2026.08.30</td>
              </tr>
            </tbody></table>
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            parser.parseDetail(item, listHtml, item.detailUrl)
        }

        assertEquals("정확한 게시글 내용을 찾지 못했습니다", error.message)
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
        val finalUrl = "https://lms.dima.ac.kr/lms/class/report/stud/detail/view.dunet?report_no=301"
        val item = LmsItem(
            id = "301",
            courseId = "COURSE-A",
            courseName = "음향기초실습(D반)",
            kind = LmsItemKind.ASSIGNMENT,
            title = "프로툴 사전진단",
            detailUrl = finalUrl,
        )
        val html = """
            <div class="report-content"><p>제출 안내</p></div>
            <a href="../doDownloadFile.dunet?report_attach_no=77">진단 양식.pdf</a>
        """.trimIndent()

        val detail = parser.parseDetail(item, html, finalUrl)

        assertEquals("진단 양식.pdf", detail.attachments.single().fileName)
        assertEquals(
            "https://lms.dima.ac.kr/lms/class/report/stud/doDownloadFile.dunet?report_attach_no=77",
            detail.attachments.single().downloadUrl,
        )
        assertEquals(finalUrl, detail.attachments.single().request?.refererUrl)
    }

    @Test
    fun officialBoardViewTableBecomesNativeBodyAndAuthenticatedAttachment() {
        val item = LmsItem(
            id = "32258",
            courseId = "COURSE-A",
            courseName = "음향기초실습(D반)",
            kind = LmsItemKind.MATERIAL,
            title = "방송제작 수업 자료",
            detailUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet",
        )
        val html = """
            <input type="hidden" id="board_no" name="board_no" value="6">
            <input type="hidden" id="boarditem_no" name="boarditem_no" value="32258">
            <input type="hidden" id="learning_design_yn" name="learning_design_yn" value="N">
            <table class="table_view_basic">
              <thead><tr><th class="ta_l pd_l10 end">방송제작 수업 자료</th></tr></thead>
              <tbody>
                <tr><td class="ta_l end">작성자 : 담당교수 | 등록일 : 2026-08-30 15:12 | 조회수 : 8</td></tr>
                <tr><td class="end ta_l"><p>방송제작 강의자료입니다</p></td></tr>
                <tr><td class="end">첨부파일 : <a href="javascript:fncFileDown('30379', '')">2주자료.pdf</a></td></tr>
              </tbody>
            </table>
            <table class="table_comment"><tr><td>댓글 입력창</td></tr></table>
        """.trimIndent()

        val detail = parser.parseDetail(item, html, "https://lms.dima.ac.kr")

        assertEquals("방송제작 강의자료입니다", org.jsoup.Jsoup.parse(detail.sanitizedHtml).text())
        assertEquals(listOf("2주자료.pdf"), detail.attachments.map { it.fileName })
        assertEquals(
            "https://lms.dima.ac.kr/lms/class/boardItem/doDownloadFile.dunet?" +
                "boarditem_attach_file_no=30379&board_no=6&boarditem_no=32258&learning_design_yn=N&time_flag=",
            detail.attachments.single().downloadUrl,
        )
    }

    @Test
    fun officialBoardDetailParsesAuthorAndDetailedRegistrationTime() {
        val item = LmsItem(
            id = "32258",
            courseId = "COURSE-A",
            courseName = "음향기초실습(D반)",
            kind = LmsItemKind.MATERIAL,
            title = "방송제작 수업 자료",
            detailUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet",
        )
        val html = """
            <table class="table_view_basic">
              <thead><tr><th class="ta_l">방송제작 수업 자료</th></tr></thead>
              <tbody>
                <tr><td class="ta_l">작성자 : 담당교수 | 등록일 : 2026.08.30 15:12 | 조회수 : 8</td></tr>
                <tr><td class="ta_l"><p>방송제작 강의자료입니다</p></td></tr>
              </tbody>
            </table>
        """.trimIndent()

        val detail = parser.parseDetail(
            item,
            html,
            "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=32258",
        )

        assertEquals("담당교수", detail.metadata.author)
        assertEquals("2026-08-30T06:12:00Z", detail.metadata.registeredAt.toString())
    }

    @Test
    fun officialBoardAttachmentPreservesRenderedRequestAndDetailReferer() {
        val finalUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=32258"
        val item = LmsItem(
            id = "32258",
            courseId = "COURSE-A",
            courseName = "음향기초실습(D반)",
            kind = LmsItemKind.MATERIAL,
            title = "방송제작 수업 자료",
            detailUrl = finalUrl,
        )
        val html = """
            <input type="hidden" id="board_no" name="board_no" value="6">
            <input type="hidden" id="boarditem_no" name="boarditem_no" value="32258">
            <input type="hidden" id="learning_design_yn" name="learning_design_yn" value="Y">
            <table class="table_view_basic"><tbody>
              <tr><td class="ta_l"><p>강의자료입니다.</p></td></tr>
              <tr><td>첨부파일 : <a href="javascript:fncFileDown('30379', '')">2주자료.pdf</a></td></tr>
            </tbody></table>
        """.trimIndent()

        val attachment = parser.parseDetail(item, html, finalUrl).attachments.single()

        assertEquals(
            LmsAttachmentRequest(
                method = LmsHttpMethod.GET,
                url = "https://lms.dima.ac.kr/lms/class/boardItem/doDownloadFile.dunet",
                fields = linkedMapOf(
                    "boarditem_attach_file_no" to "30379",
                    "board_no" to "6",
                    "boarditem_no" to "32258",
                    "learning_design_yn" to "Y",
                    "time_flag" to "",
                ),
                refererUrl = finalUrl,
            ),
            attachment.request,
        )
    }

    @Test
    fun officialAssignmentDetailParsesSubmissionWindowAndMaximumScore() {
        val item = LmsItem(
            id = "5553",
            courseId = "COURSE-A",
            courseName = "카메라기초및실습(D반)",
            kind = LmsItemKind.ASSIGNMENT,
            title = "자기소개서 작성",
            dueAt = java.time.Instant.parse("2026-09-25T14:59:00Z"),
            detailUrl = "https://lms.dima.ac.kr/lms/class/report/stud/doViewReportStudent.dunet?report_no=5553",
        )
        val html = """
            <table class="table_view_basic">
              <tbody>
                <tr><th>과제명</th><td colspan="3">자기소개서 작성</td></tr>
                <tr><th>추가 제출기간</th><td colspan="3">2026.09.01 10:00:00 ~ 2026.09.25 23:59:00</td></tr>
                <tr><th>만점</th><td colspan="3">100점</td></tr>
                <tr><th>과제내용</th><td class="ta_l" colspan="3"><p>자기소개서를 PDF로 제출하세요.</p></td></tr>
              </tbody>
            </table>
        """.trimIndent()

        val detail = parser.parseDetail(item, html, item.detailUrl)

        assertEquals("2026-09-01T01:00:00Z", detail.metadata.submissionStartsAt.toString())
        assertEquals("2026-09-25T14:59:00Z", detail.metadata.submissionEndsAt.toString())
        assertEquals("100점", detail.metadata.maxScore)
        assertEquals("자기소개서를 PDF로 제출하세요.", org.jsoup.Jsoup.parse(detail.sanitizedHtml).text())
    }

    @Test
    fun assignmentOverviewWithoutAnAssignmentBodyIsNotAcceptedAsTheExactReport() {
        val item = LmsItem(
            id = "5553",
            courseId = "COURSE-A",
            courseName = "음향기초실습(D반)",
            kind = LmsItemKind.ASSIGNMENT,
            title = "2주차 과제 - 영상사운드 구성 분석",
            detailUrl = "https://lms.dima.ac.kr/lms/class/report/stud/doFormReport.dunet",
        )
        val intermediateHtml = """
            <table class="table_view_basic"><tbody>
              <tr><th>과제</th><td class="ta_l">2주차 과제 - 영상사운드 구성 분석 · 10 점</td></tr>
              <tr><th>제출기간</th><td>2026.09.01 09:00:00 ~ 2026.09.08 15:59:00</td></tr>
            </tbody></table>
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            parser.parseDetail(item, intermediateHtml, item.detailUrl)
        }
    }

    @Test
    fun officialAssignmentAttachmentPreservesExactDownloadFieldsAndDetailReferer() {
        val finalUrl = "https://lms.dima.ac.kr/lms/class/report/stud/doViewReportStudent.dunet?report_no=5553"
        val item = LmsItem(
            id = "5553",
            courseId = "COURSE-A",
            courseName = "카메라기초및실습(D반)",
            kind = LmsItemKind.ASSIGNMENT,
            title = "자기소개서 작성",
            detailUrl = finalUrl,
        )
        val html = """
            <input type="hidden" id="report_no" name="report_no" value="5553">
            <input type="hidden" id="report_seq" name="report_seq" value="2">
            <table class="table_view_basic"><tbody>
              <tr><th>과제내용</th><td class="ta_l"><p>PDF로 제출하세요.</p></td></tr>
              <tr><th>첨부파일</th><td><a href="javascript:fncDownAttachFile('1')">자기소개서 양식.pdf</a></td></tr>
            </tbody></table>
        """.trimIndent()

        val attachment = parser.parseDetail(item, html, finalUrl).attachments.single()

        assertEquals(
            LmsAttachmentRequest(
                method = LmsHttpMethod.GET,
                url = "https://lms.dima.ac.kr/lms/class/report/stud/doDownloadAttachFile.dunet",
                fields = linkedMapOf(
                    "report_attach_file_no" to "1",
                    "report_no" to "5553",
                ),
                refererUrl = finalUrl,
            ),
            attachment.request,
        )
        assertEquals("자기소개서 양식.pdf", attachment.fileName)
    }

    @Test
    fun attachmentFormPreservesPostMethodFieldsAndFinalDetailUrlAsReferer() {
        val finalUrl = "https://lms.dima.ac.kr/lms/class/report/stud/view/detail.dunet?report_no=5553"
        val item = LmsItem(
            id = "5553",
            courseId = "COURSE-A",
            courseName = "카메라기초및실습(D반)",
            kind = LmsItemKind.ASSIGNMENT,
            title = "자기소개서 작성",
            detailUrl = finalUrl,
        )
        val html = """
            <table class="table_view_basic"><tbody>
              <tr><th>과제내용</th><td class="ta_l"><p>PDF로 제출하세요.</p></td></tr>
              <tr><th>첨부파일</th><td>
                <form method="post" action="../doDownloadAttachFile.dunet">
                  <input type="hidden" name="report_attach_file_no" value="1">
                  <input type="hidden" name="report_no" value="5553">
                  <input type="hidden" name="SAMLResponse" value="must-not-be-cached">
                  <a href="javascript:this.closest('form').submit()">자기소개서 양식.pdf</a>
                </form>
              </td></tr>
            </tbody></table>
        """.trimIndent()

        val attachment = parser.parseDetail(item, html, finalUrl).attachments.single()

        assertEquals(
            LmsAttachmentRequest(
                method = LmsHttpMethod.POST,
                url = "https://lms.dima.ac.kr/lms/class/report/stud/doDownloadAttachFile.dunet",
                fields = linkedMapOf(
                    "report_attach_file_no" to "1",
                    "report_no" to "5553",
                ),
                refererUrl = finalUrl,
            ),
            attachment.request,
        )
    }

    @Test
    fun enclosingDownloadFormWinsOverJavascriptArgumentsAndSurvivesBodySanitizing() {
        val finalUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91"
        val item = LmsItem(
            id = "91",
            courseId = "COURSE-A",
            courseName = "음향기초실습(D반)",
            kind = LmsItemKind.NOTICE,
            title = "수업안내",
            detailUrl = finalUrl,
        )
        val html = """
            <div id="board_contents">
              <p>준비물을 확인하세요.</p>
              <form method="post" action="/lms/class/boardItem/doDownloadFile.dunet">
                <input type="hidden" name="boarditem_attach_file_no" value="700">
                <input type="hidden" name="board_no" value="7">
                <input type="hidden" name="boarditem_no" value="91">
                <input type="hidden" name="learning_design_yn" value="N">
                <a href="javascript:fncFileDown('999', 'guessed')">준비물.pdf</a>
              </form>
            </div>
        """.trimIndent()

        val detail = parser.parseDetail(item, html, finalUrl)

        assertEquals(
            LmsAttachmentRequest(
                method = LmsHttpMethod.POST,
                url = "https://lms.dima.ac.kr/lms/class/boardItem/doDownloadFile.dunet",
                fields = linkedMapOf(
                    "boarditem_attach_file_no" to "700",
                    "board_no" to "7",
                    "boarditem_no" to "91",
                    "learning_design_yn" to "N",
                ),
                refererUrl = finalUrl,
            ),
            detail.attachments.single().request,
        )
        assertEquals("준비물.pdf", detail.attachments.single().fileName)
        assertFalse(detail.sanitizedHtml.contains("form"))
    }

    @Test
    fun javascriptBoardDownloadUsesOnlyItsEnclosingFormFieldsAndMethod() {
        val finalUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91"
        val item = LmsItem(
            id = "91",
            courseId = "COURSE-A",
            courseName = "음향기초실습(D반)",
            kind = LmsItemKind.NOTICE,
            title = "수업안내",
            detailUrl = finalUrl,
        )
        val html = """
            <form method="post" action="/lms/class/boardItem/doViewBoardItem.dunet">
              <input type="hidden" name="boarditem_attach_file_no" value="unrelated">
              <input type="hidden" name="board_no" value="99">
              <input type="hidden" name="boarditem_no" value="999">
            </form>
            <div id="board_contents"><p>준비물을 확인하세요.</p></div>
            <form method="post" action="/lms/class/boardItem/doViewBoardItem.dunet">
              <input type="hidden" name="boarditem_attach_file_no" value="700">
              <input type="hidden" name="board_no" value="7">
              <input type="hidden" name="boarditem_no" value="91">
              <input type="hidden" name="learning_design_yn" value="Y">
              <a href="javascript:fncFileDown('guessed')">준비물.pdf</a>
            </form>
        """.trimIndent()

        val attachment = parser.parseDetail(item, html, finalUrl).attachments.single()

        assertEquals(
            LmsAttachmentRequest(
                method = LmsHttpMethod.POST,
                url = "https://lms.dima.ac.kr/lms/class/boardItem/doDownloadFile.dunet",
                fields = linkedMapOf(
                    "boarditem_attach_file_no" to "700",
                    "board_no" to "7",
                    "boarditem_no" to "91",
                    "learning_design_yn" to "Y",
                ),
                refererUrl = finalUrl,
            ),
            attachment.request,
        )
    }

    @Test
    fun javascriptAssignmentDownloadUsesItsEnclosingFormInsteadOfGuessedArguments() {
        val finalUrl = "https://lms.dima.ac.kr/lms/class/report/stud/doViewReportStudent.dunet?report_no=5553"
        val item = LmsItem(
            id = "5553",
            courseId = "COURSE-A",
            courseName = "카메라기초및실습(D반)",
            kind = LmsItemKind.ASSIGNMENT,
            title = "자기소개서 작성",
            detailUrl = finalUrl,
        )
        val html = """
            <div class="report-content"><p>PDF로 제출하세요.</p></div>
            <form method="post" action="/lms/class/report/stud/doViewReportStudent.dunet">
              <input type="hidden" name="report_attach_file_no" value="8">
              <input type="hidden" name="report_no" value="5553">
              <a href="javascript:fncDownAttachFile('1')">제출 양식.pdf</a>
            </form>
        """.trimIndent()

        val attachment = parser.parseDetail(item, html, finalUrl).attachments.single()

        assertEquals(
            LmsAttachmentRequest(
                method = LmsHttpMethod.POST,
                url = "https://lms.dima.ac.kr/lms/class/report/stud/doDownloadAttachFile.dunet",
                fields = linkedMapOf(
                    "report_attach_file_no" to "8",
                    "report_no" to "5553",
                ),
                refererUrl = finalUrl,
            ),
            attachment.request,
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

    @Test
    fun authenticatedLandingPageIsNotAcceptedAsAnArticleDetail() {
        val item = LmsItem(
            id = "91",
            courseId = "COURSE-A",
            courseName = "음향기초실습",
            kind = LmsItemKind.NOTICE,
            title = "수업안내",
            detailUrl = "https://lms.dima.ac.kr/detail",
        )
        val html = """
            <html><body>
              <header>나의 강의실 입장</header>
              <nav><a href="/main/MainView.dunet">마이페이지</a></nav>
              <main><p>데이터 로딩 중입니다.</p></main>
            </body></html>
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            parser.parseDetail(item, html, "https://lms.dima.ac.kr")
        }
    }
}
