package com.example.dimanow.lms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.charset.Charset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLmsSourceTest {
    @Test
    fun anEmptyTodoStillRequestsTheRenderedCourseCatalog() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val source = RoomLmsSource(
            database,
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            RecordingLmsTransport(rawDashboardOmitsCourses = true),
        )

        assertEquals(LmsRefreshResult.CourseCatalogRequired, source.refresh(force = true))

        database.close()
    }

    @Test
    fun todoWithoutAStoredCourseCatalogRequestsOneRenderedLoginPass() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val source = RoomLmsSource(
            database,
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            RecordingLmsTransport(rawDashboardOmitsCourses = true, todoWithoutCourse = true),
        )

        assertEquals(LmsRefreshResult.CourseCatalogRequired, source.refresh(force = true))
        assertEquals(LmsSyncState.ERROR, source.snapshot.first().syncState)

        database.close()
    }

    @Test
    fun renderedCatalogIsUsedWhenSubsequentDashboardHtmlOmitsCourseCards() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val transport = RecordingLmsTransport(rawDashboardOmitsCourses = true)
        val source = RoomLmsSource(
            database,
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            transport,
        )

        source.storeRenderedCourses(
            listOf(LmsCourse("202620UN00025451401401D", "음향기초실습(D반)", "이화현", "D")),
        )
        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        val snapshot = source.snapshot.first()
        assertEquals(listOf("음향기초실습(D반)"), snapshot.courses.map { it.name })
        assertEquals(listOf("1주차 안내"), snapshot.items.map { it.title })

        database.close()
    }

    @Test
    fun allLearningRowsAreCachedWithoutEnumeratingSeparateCourseBoards() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val transport = RecordingLmsTransport(allLearningKinds = true)
        val source = RoomLmsSource(
            database,
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            transport,
        )

        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        assertEquals(
            setOf(
                LmsItemKind.ASSIGNMENT,
                LmsItemKind.NOTICE,
                LmsItemKind.CONTENT,
                LmsItemKind.MATERIAL,
            ),
            source.snapshot.first().items.map { it.kind }.toSet(),
        )
        assertEquals(
            listOf("https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=all"),
            transport.requestedUrls,
        )
        assertTrue(transport.sessionFields.isEmpty())

        database.close()
    }

    @Test
    fun completionStatusPagesAreMergedIntoTheAuthoritativeAllLearningRows() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val transport = RecordingLmsTransport(allLearningKinds = true, includeStatusLinks = true)
        val source = RoomLmsSource(
            database,
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            transport,
        )

        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        assertEquals(
            mapOf(
                "91" to LmsCompletionState.COMPLETE,
                "301" to LmsCompletionState.INCOMPLETE,
                "801" to LmsCompletionState.NOT_TRACKED,
                "901" to LmsCompletionState.NOT_TRACKED,
            ),
            source.snapshot.first().items.associate { it.id to it.completionState },
        )
        assertEquals(
            listOf(
                "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=complete",
                "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=incomplete",
            ),
            transport.requestedUrls.filter { "to_do_type=all" !in it },
        )

        database.close()
    }

    @Test
    fun optionalStatusFailureKeepsThePreviousStatesAndStillRefreshesTheMainList() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val transport = RecordingLmsTransport(allLearningKinds = true, includeStatusLinks = true)
        val source = RoomLmsSource(database, MutableLmsSessionController(LmsSessionState.ACTIVE), transport)

        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        transport.failIncompleteStatus = true
        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))

        assertEquals(
            mapOf(
                "91" to LmsCompletionState.COMPLETE,
                "301" to LmsCompletionState.INCOMPLETE,
                "801" to LmsCompletionState.NOT_TRACKED,
                "901" to LmsCompletionState.NOT_TRACKED,
            ),
            source.snapshot.first().items.associate { it.id to it.completionState },
        )
        database.close()
    }

    @Test
    fun selectedTermIsAppliedBeforeCompleteCourseAndBoardHistoryIsLoaded() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val transport = RecordingLmsTransport(requiresTermSelection = true)
        val source = RoomLmsSource(
            database,
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            transport,
        )

        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        assertEquals(
            mapOf("term_year" to "2026", "term_cd" to "20", "term_nm" to "2학기"),
            transport.termFields.single(),
        )
        assertEquals(1, transport.dashboardRequests)
        assertEquals(1, transport.dashboardPosts)
        assertEquals(listOf("음향기초실습(D반)"), source.snapshot.first().courses.map { it.name })

        database.close()
    }

    @Test
    fun failedRefreshPreservesTheLastGoodCourseItems() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val transport = RecordingLmsTransport()
        val session = MutableLmsSessionController(LmsSessionState.ACTIVE)
        val source = RoomLmsSource(database, session, transport)

        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        assertTrue(transport.sessionFields.isEmpty())
        assertEquals("1주차 안내", source.snapshot.first { it.items.isNotEmpty() }.items.single().title)

        transport.failDashboard = true
        assertTrue(source.refresh(force = true) is LmsRefreshResult.Failure)
        assertEquals("1주차 안내", source.snapshot.first().items.single().title)

        database.close()
    }

    @Test
    fun openingAnItemMarksItReadAndRefreshKeepsThatState() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val transport = RecordingLmsTransport()
        val source = RoomLmsSource(
            database,
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            transport,
        )

        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        val unread = source.snapshot.first { it.items.isNotEmpty() }.items.single()
        assertEquals(false, unread.isRead)

        assertTrue(source.loadDetail(unread) is LmsDetailLoadResult.Fresh)
        assertEquals(true, source.snapshot.first().items.single().isRead)

        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        assertEquals(true, source.snapshot.first().items.single().isRead)

        database.close()
    }

    @Test
    fun laterRefreshMarksNewAndChangedRowsUntilTheUserOpensThem() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val transport = RecordingLmsTransport()
        val source = RoomLmsSource(
            database,
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            transport,
        )

        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        assertEquals(LmsChangeState.NONE, source.snapshot.first().items.single().changeState)

        transport.useRevisedDashboard = true
        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        val changed = source.snapshot.first().items.associateBy { it.id }
        assertEquals(LmsChangeState.UPDATED, changed.getValue("91").changeState)
        assertEquals(LmsChangeState.NEW, changed.getValue("902").changeState)

        assertTrue(source.loadDetail(changed.getValue("91")) is LmsDetailLoadResult.Fresh)
        val opened = source.snapshot.first().items.single { it.id == "91" }
        assertEquals(true, opened.isRead)
        assertEquals(LmsChangeState.NONE, opened.changeState)

        database.close()
    }

    @Test
    fun listBackedItemOpensItsMatchingArticleInsideTheSourceAndKeepsAttachments() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val transport = RecordingLmsTransport(allLearningKinds = true)
        val source = RoomLmsSource(
            database,
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            transport,
        )

        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        val assignment = source.snapshot.first().items.single { it.kind == LmsItemKind.ASSIGNMENT }

        val result = source.loadDetail(assignment)
        assertTrue(result is LmsDetailLoadResult.Fresh)
        val detail = (result as LmsDetailLoadResult.Fresh).detail

        assertTrue(detail.sanitizedHtml.contains("제출 안내"))
        assertEquals("과제 양식.pdf", detail.attachments.single().fileName)
        assertTrue(transport.requestedUrls.any { it.contains("doViewReportStudent") })
        assertEquals(
            mapOf("course_id" to "202620UN00025451401401D", "class_no" to "D"),
            transport.sessionFields.single(),
        )

        database.close()
    }

    @Test
    fun openingRevalidatesAttachmentsAndFallsBackToTheCachedDetailOffline() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val transport = RecordingLmsTransport(allLearningKinds = true)
        val source = RoomLmsSource(
            database,
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            transport,
        )
        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        val assignment = source.snapshot.first().items.single { it.kind == LmsItemKind.ASSIGNMENT }

        val first = source.loadDetail(assignment)
        assertTrue(first is LmsDetailLoadResult.Fresh && !first.attachmentsChanged)

        transport.assignmentAttachmentName = "과제 양식 수정.pdf"
        val changed = source.loadDetail(assignment)
        assertTrue(changed is LmsDetailLoadResult.Fresh && changed.attachmentsChanged)
        assertEquals("과제 양식 수정.pdf", (changed as LmsDetailLoadResult.Fresh).detail.attachments.single().fileName)

        transport.failDetailRequests = true
        val cached = source.loadDetail(assignment)
        assertTrue(cached is LmsDetailLoadResult.Cached)
        assertEquals("과제 양식 수정.pdf", (cached as LmsDetailLoadResult.Cached).detail.attachments.single().fileName)

        database.close()
    }
}

private class RecordingLmsTransport(
    private val requiresTermSelection: Boolean = false,
    private val allLearningKinds: Boolean = false,
    private val rawDashboardOmitsCourses: Boolean = false,
    private val todoWithoutCourse: Boolean = false,
    private val includeStatusLinks: Boolean = false,
) : LmsHttpTransport {
    var failDashboard = false
    var dashboardRequests = 0
    var dashboardPosts = 0
    var termApplied = false
    var useRevisedDashboard = false
    var assignmentAttachmentName = "과제 양식.pdf"
    var failDetailRequests = false
    var failIncompleteStatus = false
    val sessionFields = mutableListOf<Map<String, String>>()
    val termFields = mutableListOf<Map<String, String>>()
    val requestedUrls = mutableListOf<String>()

    override suspend fun get(url: String, maxBytes: Long): LmsHttpResponse {
        requestedUrls += url
        if (url.contains("myLecture") && failDashboard) error("offline")
        if (url.contains("to_do_type=incomplete") && failIncompleteStatus) error("offline status")
        if (!url.contains("myLecture") && failDetailRequests) error("offline detail")
        val html = if (url.contains("to_do_type=complete")) {
            statusHtml("1", "91", "1주차 안내")
        } else if (url.contains("to_do_type=incomplete")) {
            statusHtml("3", "301", "프로툴 사전진단")
        } else if (url.contains("myLecture")) {
            dashboardRequests += 1
            dashboardHtml(includeCourse = !rawDashboardOmitsCourses && (!requiresTermSelection || termApplied))
        } else if (url.contains("report/stud/doListView")) {
            """<table><tr><td><a href="/lms/class/report/stud/doViewReportStudent.dunet?report_no=301">프로툴 사전진단</a></td></tr></table>"""
        } else if (url.contains("doViewReportStudent")) {
            """<div class="report-content"><p>제출 안내</p></div><a href="javascript:doDownloadFile('501','7','301','N')">$assignmentAttachmentName</a>"""
        } else if (url.contains("boardItem/doViewBoardItem")) {
            """<div id="board_contents"><p>수업 안내 본문</p></div>"""
        } else "<main class=\"sub_content\"><p>학습 상세</p></main>"
        return LmsHttpResponse(
            url,
            200,
            "text/html;charset=KSC5601",
            emptyMap(),
            html.toByteArray(Charset.forName("MS949")),
        )
    }

    override suspend fun postForm(url: String, fields: Map<String, String>, maxBytes: Long): LmsHttpResponse {
        if (url.contains("myLecture")) {
            dashboardPosts += 1
            val html = dashboardHtml(includeCourse = !rawDashboardOmitsCourses && termApplied)
            return LmsHttpResponse(
                url,
                200,
                "text/html;charset=KSC5601",
                emptyMap(),
                html.toByteArray(Charset.forName("MS949")),
            )
        }
        if (url.contains("doChangeCourseYear")) {
            termFields += fields
            termApplied = true
        } else {
            sessionFields += fields
        }
        return LmsHttpResponse(url, 200, "application/json", emptyMap(), "{}".toByteArray())
    }

    private fun dashboardHtml(includeCourse: Boolean): String = if (!includeCourse) {
        """
            <div class="select_learn_term">
              <div class="select_termbox"><a class="title"><strong>2026년</strong></a>
                <a href="javascript:changeYearTerm('2026', '20', '2학기');">2026년</a>
              </div>
              <div class="select_termbox"><a class="title"><strong>2학기</strong></a>
                <a href="javascript:changeYearTerm('2026', '20', '2학기');">2학기</a>
              </div>
            </div>
            <div class="learn_element_detail_list"><span class="badge">공지</span>
              <a href="/lms/class/boardItem/doViewBoardItem.dunet?board_no=7&amp;boarditem_no=91">[음향기초실습(D반)] 1주차 안내</a>
              <span>등록일 : 2026.08.30 13:30:00</span>
            </div>
        """.trimIndent()
    } else {
        """
            ${if (includeStatusLinks) """
              <nav><a href="/lms/myLecture/doListView.dunet?to_do_type=complete">완료한 학습</a>
              <a href="/lms/myLecture/doListView.dunet?to_do_type=incomplete">미완료한 학습</a></nav>
            """.trimIndent() else ""}
            <li class="box"><div class="top offline">
              <a href="javascript:fncGoClassroom('202620UN00025451401401D','D','3');">
                <strong class="title">음향기초실습(D반)</strong><span>교수명 : 이화현</span>
              </a>
            </div></li>
            <div class="learn_element_detail_list"><div class="outline"><ul>
              ${if (allLearningKinds) """
                <li><span class="cata">과제</span><a href="javascript:fnGoContent('3','202620UN00025451401401D','D','301','S');">[2026-2학기)음향기초실습(D반)] 프로툴 사전진단</a><span>종료시한 : 2026.09.08 15:59:59</span></li>
              """.trimIndent() else ""}
              <li><span class="cata">공지</span><a href="javascript:fnGoContent('1','202620UN00025451401401D','D','91','S');">[2026-2학기)음향기초실습(D반)] ${if (useRevisedDashboard) "1주차 안내 수정" else "1주차 안내"}</a><span>등록일 : 2026.08.30 13:30:00</span></li>
              ${if (useRevisedDashboard) """
                <li><span class="cata">자료실</span><a href="javascript:fnGoContent('9','202620UN00025451401401D','D','902','S');">[2026-2학기)음향기초실습(D반)] 새 실습 자료</a><span>등록일 : 2026.09.01 10:00:00</span></li>
              """.trimIndent() else ""}
              ${if (allLearningKinds) """
                <li><span class="cata">콘텐츠</span><a href="javascript:fnGoContent('8','202620UN00025451401401D','D','801','S');">[2026-2학기)음향기초실습(D반)] 사운드디자인 기초</a><span>종료시한 : 2026.09.08 15:59:59</span></li>
                <li><span class="cata">자료실</span><a href="javascript:fnGoContent('9','202620UN00025451401401D','D','901','S');">[2026-2학기)음향기초실습(D반)] 실습 자료</a><span>등록일 : 2026.09.01 09:00:00</span></li>
              """.trimIndent() else ""}
            </ul></div></div>
        """.trimIndent()
    }

    private fun statusHtml(type: String, id: String, title: String): String = """
        <div class="learn_element_detail_list"><div class="outline"><ul>
          <li><span class="cata">${if (type == "1") "공지" else "과제"}</span>
            <a href="javascript:fnGoContent('$type','202620UN00025451401401D','D','$id','S');">[2026-2학기)음향기초실습(D반)] $title</a>
          </li>
        </ul></div></div>
    """.trimIndent()

    override suspend fun download(
        url: String,
        destination: File,
        maxBytes: Long,
        onProgress: (Long, Long?) -> Unit,
    ): LmsHttpResponse = error("not used")
}
