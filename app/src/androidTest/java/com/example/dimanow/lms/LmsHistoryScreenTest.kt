package com.example.dimanow.lms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.dimanow.theme.DIMANowTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class LmsHistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedOnlyTodayExplainsThereIsNothingToReviewAndStillOpensCompletedLearning() {
        val completed = LmsItem(
            id = "completed", courseId = "audio", courseName = "음향기초실습",
            kind = LmsItemKind.CONTENT, title = "완료한 실습 영상",
            detailUrl = "https://lms.dima.ac.kr/item/completed",
            completionState = LmsCompletionState.COMPLETE,
        )
        composeRule.setContent {
            DIMANowTheme {
                LmsItemsScreen(
                    snapshot = LmsSnapshot(items = listOf(completed), syncState = LmsSyncState.READY),
                    sessionState = LmsSessionState.ACTIVE,
                    selectedCourse = null, selectedKind = null,
                    onCourseChange = {}, onKindChange = {}, onRefresh = {}, onOpenItem = {},
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithText("남은 할 일이 없어요").assertExists()
        composeRule.onNodeWithTag("lms_completed_toggle").assertExists()
        composeRule.onNodeWithText("완료한 실습 영상").assertDoesNotExist()
        composeRule.onNodeWithTag("lms_completed_toggle").performClick()
        composeRule.onNodeWithText("완료한 실습 영상").assertExists()
        composeRule.onNodeWithText("콘텐츠 · 음향기초실습").assertExists()
    }

    @Test
    fun aSignInElsewhereKeepsTheSavedListAndSignsInOnlyFromTheCard() {
        // D-135: no automatic sign-in while another device most likely holds the LMS session.
        val pending = LmsItem(
            id = "pending", courseId = "audio", courseName = "음향기초실습",
            kind = LmsItemKind.ASSIGNMENT, title = "믹싱 과제",
            detailUrl = "https://lms.dima.ac.kr/item/pending",
            dueAt = Instant.parse("2026-09-02T14:59:00Z"),
        )
        var signInHere = 0
        var refreshes = 0
        composeRule.setContent {
            DIMANowTheme {
                LmsItemsScreen(
                    snapshot = LmsSnapshot(
                        items = listOf(pending),
                        syncState = LmsSyncState.READY,
                        lastSuccessAt = Instant.parse("2026-09-01T02:40:00Z"),
                    ),
                    sessionState = LmsSessionState.EXPIRED,
                    selectedCourse = null, selectedKind = null,
                    onCourseChange = {}, onKindChange = {}, onRefresh = { refreshes++ }, onOpenItem = {},
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                    signedInElsewhere = true,
                    onSignInHere = { signInHere++ },
                )
            }
        }

        composeRule.onNodeWithText("다른 곳에서 LMS에 로그인한 것 같아요").assertExists()
        composeRule.onNodeWithText("믹싱 과제").assertExists()
        composeRule.onNodeWithTag("lms_refresh_error_banner").assertDoesNotExist()
        composeRule.onNodeWithTag("lms_sign_in_here").performClick()
        assertEquals(1, signInHere)
        assertEquals(0, refreshes)
    }

    @Test
    fun anEmptyListDistinguishesUnfetchedLoadingFailureSignedOutAndSuccessfulFetch() {
        var snapshot by mutableStateOf(LmsSnapshot())
        var session by mutableStateOf(LmsSessionState.ACTIVE)
        var retries = 0
        composeRule.setContent {
            DIMANowTheme {
                LmsItemsScreen(
                    snapshot = snapshot, sessionState = session,
                    selectedCourse = null, selectedKind = null,
                    onCourseChange = {}, onKindChange = {}, onRefresh = { retries++ }, onOpenItem = {},
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithText("아직 불러온 수업 정보가 없어요").assertExists()
        composeRule.onNodeWithText("남은 할 일이 없어요").assertDoesNotExist()
        composeRule.onNodeWithText("수업 정보 불러오기").performClick()
        assertEquals(1, retries)

        composeRule.runOnIdle { snapshot = LmsSnapshot(syncState = LmsSyncState.SYNCING) }
        composeRule.onNodeWithText("수업 정보를 불러오고 있어요").assertExists()
        composeRule.onNodeWithText("남은 할 일이 없어요").assertDoesNotExist()

        composeRule.runOnIdle {
            snapshot = LmsSnapshot(syncState = LmsSyncState.ERROR, errorMessage = "HTTP 503")
        }
        composeRule.onNodeWithText("수업 정보를 불러오지 못했어요").assertExists()
        composeRule.onNodeWithText("남은 할 일이 없어요").assertDoesNotExist()
        composeRule.onNodeWithText("HTTP 503").assertDoesNotExist()
        composeRule.onNodeWithText("다시 시도").performClick()
        assertEquals(2, retries)

        composeRule.runOnIdle {
            snapshot = LmsSnapshot(syncState = LmsSyncState.READY)
            session = LmsSessionState.SIGNED_OUT
        }
        composeRule.onNodeWithText("수업 정보를 확인하려면 로그인해 주세요").assertExists()
        composeRule.onNodeWithText("남은 할 일이 없어요").assertDoesNotExist()

        composeRule.runOnIdle { session = LmsSessionState.ACTIVE }
        composeRule.onNodeWithText("남은 할 일이 없어요").assertExists()
    }

    @Test
    fun todayShowsAnActiveFilterAndClearingItRestoresTheLearning() {
        var selectedKind by mutableStateOf<LmsItemKind?>(LmsItemKind.ASSIGNMENT)
        var selectedCourse by mutableStateOf<String?>("audio")
        val news = LmsItem(
            id = "news", courseId = "audio", courseName = "음향기초실습",
            kind = LmsItemKind.NOTICE, title = "이번 주 실습 안내",
            detailUrl = "https://lms.dima.ac.kr/item/news", changeState = LmsChangeState.NEW,
        )
        composeRule.setContent {
            DIMANowTheme {
                LmsItemsScreen(
                    snapshot = LmsSnapshot(
                        courses = listOf(LmsCourse("audio", "음향기초실습")),
                        items = listOf(news), syncState = LmsSyncState.READY,
                    ),
                    sessionState = LmsSessionState.ACTIVE,
                    selectedCourse = selectedCourse, selectedKind = selectedKind,
                    onCourseChange = { selectedCourse = it }, onKindChange = { selectedKind = it },
                    onRefresh = {}, onOpenItem = {}, now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithTag("lms_kind_filter").assertExists()
        composeRule.onNodeWithTag("lms_course_filter").assertExists()
        composeRule.onNodeWithText("선택한 조건에 맞는 학습이 없어요").assertExists()
        composeRule.onNodeWithText("남은 할 일이 없어요").assertDoesNotExist()
        composeRule.onNodeWithText("필터 초기화").performClick()
        composeRule.onNodeWithText("이번 주 실습 안내").assertExists()
        composeRule.runOnIdle {
            assertEquals(null, selectedKind)
            assertEquals(null, selectedCourse)
        }
    }

    @Test
    fun freshOverdueAndThreeDayDeadlineLearningEachPreventTheEmptyNotice() {
        val news = LmsItem(
            id = "news", courseId = "audio", courseName = "음향기초실습",
            kind = LmsItemKind.NOTICE, title = "새 실습 안내",
            detailUrl = "https://lms.dima.ac.kr/item/news", changeState = LmsChangeState.NEW,
        )
        val overdue = news.copy(id = "overdue", title = "기한 지난 과제", dueAt = Instant.parse("2026-08-31T14:59:00Z"))
        val future = news.copy(id = "future", title = "3일 뒤 마감 과제", dueAt = Instant.parse("2026-09-04T14:59:00Z"))
        var items by mutableStateOf(listOf(news))
        composeRule.setContent {
            DIMANowTheme {
                LmsItemsScreen(
                    snapshot = LmsSnapshot(items = items, syncState = LmsSyncState.READY),
                    sessionState = LmsSessionState.ACTIVE, selectedCourse = null, selectedKind = null,
                    onCourseChange = {}, onKindChange = {}, onRefresh = {}, onOpenItem = {},
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }
        listOf(news, overdue, future).forEach { learning ->
            composeRule.runOnIdle { items = listOf(learning) }
            composeRule.onNodeWithText(learning.title).assertExists()
            composeRule.onNodeWithText("남은 할 일이 없어요").assertDoesNotExist()
        }
    }

    @Test
    fun tappingAnItemUsesThePublicOpenAction() {
        var openedId: String? = null
        composeRule.setContent {
            DIMANowTheme {
                LmsItemsScreen(
                    snapshot = LmsSnapshot(
                        courses = listOf(LmsCourse("audio", "음향기초실습")),
                        items = listOf(
                            LmsItem(
                                id = "assignment-301",
                                courseId = "audio",
                                courseName = "음향기초실습",
                                kind = LmsItemKind.ASSIGNMENT,
                                title = "프로툴 사전진단",
                                dueAt = Instant.parse("2026-09-01T14:59:00Z"),
                                detailUrl = "https://lms.dima.ac.kr/item/assignment-301",
                            ),
                        ),
                        syncState = LmsSyncState.READY,
                    ),
                    sessionState = LmsSessionState.ACTIVE,
                    selectedCourse = null,
                    selectedKind = null,
                    onCourseChange = {},
                    onKindChange = {},
                    onRefresh = {},
                    onOpenItem = { openedId = it.id },
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNode(hasText("프로툴 사전진단") and hasClickAction()).performClick()

        assertEquals("assignment-301", openedId)
    }

    @Test
    fun theAllModeGroupsByCourseAndKeepsADeepHistoryItemReachable() {
        val history = (0 until 80).map { index ->
            LmsItem(
                id = index.toString(),
                courseId = "audio",
                courseName = "음향기초실습",
                kind = LmsItemKind.NOTICE,
                title = "지난 공지 $index",
                detailUrl = "https://lms.dima.ac.kr/item/$index",
                isRead = index % 2 == 0,
            )
        } + LmsItem(
            id = "content-1",
            courseId = "audio",
            courseName = "음향기초실습",
            kind = LmsItemKind.CONTENT,
            title = "사운드디자인 기초",
            detailUrl = "https://lms.dima.ac.kr/lms/class/courseSchedule/doListView.dunet",
        )
        composeRule.setContent {
            DIMANowTheme {
                LmsItemsScreen(
                    snapshot = LmsSnapshot(
                        courses = listOf(LmsCourse("audio", "음향기초실습")),
                        items = history,
                        syncState = LmsSyncState.READY,
                    ),
                    sessionState = LmsSessionState.ACTIVE,
                    selectedCourse = null,
                    selectedKind = null,
                    onCourseChange = {},
                    onKindChange = {},
                    onRefresh = {},
                    onOpenItem = {},
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithTag("lms_mode_all").performClick()

        // D-058: 읽음/안읽음 칩은 사라지고, 종류·과목은 짧은 드롭다운 칩 두 개로 접혔다
        composeRule.onAllNodesWithText("안읽음").assertCountEquals(0)
        composeRule.onNodeWithTag("lms_course_filter").assertExists()
        composeRule.onNodeWithTag("lms_kind_filter").performClick()
        composeRule.onNodeWithTag("lms_kind_CONTENT").assertExists()
        androidx.test.espresso.Espresso.pressBack()

        // 과목 머리글 아래로 항목이 묶이고, 목록 끝까지 스크롤로 닿는다
        composeRule.onNodeWithTag("lms_history").performScrollToNode(hasText("지난 공지 79"))
        composeRule.onNodeWithText("지난 공지 79").assertExists()
    }

    @Test
    fun eachCourseShowsWhatIsLeftThenFoldedMissedDeadlines() {
        val course = LmsCourse("studio", "스튜디오기초실습")
        val left = LmsItem(
            id = "left", courseId = "studio", courseName = "스튜디오기초실습",
            kind = LmsItemKind.ASSIGNMENT, title = "다음 주 과제",
            dueAt = Instant.parse("2026-09-08T14:59:00Z"),
            detailUrl = "https://lms.dima.ac.kr/item/left",
        )
        val missed = left.copy(id = "missed", kind = LmsItemKind.DISCUSSION, title = "지난 토론", dueAt = Instant.parse("2026-08-20T14:59:00Z"))
        composeRule.setContent {
            DIMANowTheme {
                LmsItemsScreen(
                    snapshot = LmsSnapshot(courses = listOf(course), items = listOf(missed, left), syncState = LmsSyncState.READY),
                    sessionState = LmsSessionState.ACTIVE, selectedCourse = null, selectedKind = null,
                    onCourseChange = {}, onKindChange = {}, onRefresh = {}, onOpenItem = {},
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithTag("lms_mode_all").performClick()
        composeRule.onNodeWithText("다음 주 과제").assertExists()
        composeRule.onNodeWithText("지난 토론").assertDoesNotExist()
        composeRule.onNodeWithTag("lms_overdue_toggle_studio").performClick()
        composeRule.onNodeWithText("지난 토론").assertExists()
        composeRule.onNodeWithText("12일 지남").assertExists()
        composeRule.onAllNodesWithText("남은 학습이 없어요").assertCountEquals(0)
    }

    @Test
    fun aCourseWithOnlyFoldedItemsSaysNothingIsLeft() {
        val done = LmsItem(
            id = "done", courseId = "light", courseName = "조명기초실습",
            kind = LmsItemKind.MATERIAL, title = "빛의 특성",
            detailUrl = "https://lms.dima.ac.kr/item/done",
            completionState = LmsCompletionState.COMPLETE,
        )
        composeRule.setContent {
            DIMANowTheme {
                LmsItemsScreen(
                    snapshot = LmsSnapshot(courses = listOf(LmsCourse("light", "조명기초실습")), items = listOf(done), syncState = LmsSyncState.READY),
                    sessionState = LmsSessionState.ACTIVE, selectedCourse = null, selectedKind = null,
                    onCourseChange = {}, onKindChange = {}, onRefresh = {}, onOpenItem = {},
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithTag("lms_mode_all").performClick()
        composeRule.onNodeWithText("조명기초실습").assertExists()
        composeRule.onNodeWithTag("lms_course_empty_light").assertExists()
        composeRule.onNodeWithText("남은 학습이 없어요").assertExists()
        composeRule.onNodeWithText("빛의 특성").assertDoesNotExist()
    }

    @Test
    fun todayIsDefaultAndShowsChangeAndAuthoritativeCompletionBadges() {
        composeRule.setContent {
            DIMANowTheme {
                LmsItemsScreen(
                    snapshot = LmsSnapshot(
                        courses = listOf(LmsCourse("audio", "음향기초실습")),
                        items = listOf(
                            LmsItem(
                                id = "due",
                                courseId = "audio",
                                courseName = "음향기초실습",
                                kind = LmsItemKind.ASSIGNMENT,
                                title = "오늘 과제",
                                dueAt = Instant.parse("2026-09-01T14:59:00Z"),
                                detailUrl = "https://lms.dima.ac.kr/item/due",
                                completionState = LmsCompletionState.INCOMPLETE,
                                changeState = LmsChangeState.NEW,
                            ),
                            LmsItem(
                                id = "done",
                                courseId = "audio",
                                courseName = "음향기초실습",
                                kind = LmsItemKind.CONTENT,
                                title = "완료한 콘텐츠",
                                dueAt = Instant.parse("2026-08-31T14:59:00Z"),
                                detailUrl = "https://lms.dima.ac.kr/item/done",
                                completionState = LmsCompletionState.COMPLETE,
                            ),
                        ),
                        syncState = LmsSyncState.READY,
                    ),
                    sessionState = LmsSessionState.ACTIVE,
                    selectedCourse = null,
                    selectedKind = null,
                    onCourseChange = {},
                    onKindChange = {},
                    onRefresh = {},
                    onOpenItem = {},
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithTag("lms_mode_today").assertExists()
        composeRule.onNodeWithText("오늘 과제").assertExists()
        // D-117: 새 항목은 종류·과목 줄 앞에, 남은 시간은 행 오른쪽에 쓰고 미완료는 더 쓰지 않는다
        composeRule.onNodeWithText("새 항목 · 과제 · 음향기초실습").assertExists()
        composeRule.onNodeWithText("오늘 23:59").assertExists()
        composeRule.onNodeWithText("9월 1일 (화) 23:59 마감").assertExists()
        composeRule.onAllNodesWithText("미완료").assertCountEquals(0)
        composeRule.onNodeWithTag("lms_completed_toggle").assertExists()
        composeRule.onNodeWithText("완료한 콘텐츠").assertDoesNotExist()
        composeRule.onNodeWithTag("lms_completed_toggle").performClick()
        composeRule.onNodeWithText("완료한 콘텐츠").assertExists()
    }

    @Test
    fun theAllModeKeepsCompletedVideosOutOfTheCourseGroupUntilExpanded() {
        composeRule.setContent {
            DIMANowTheme {
                LmsItemsScreen(
                    snapshot = LmsSnapshot(
                        courses = listOf(LmsCourse("audio", "음향기초실습")),
                        items = listOf(
                            LmsItem(
                                id = "video-complete",
                                courseId = "audio",
                                courseName = "음향기초실습",
                                kind = LmsItemKind.CONTENT,
                                title = "사운드디자인 기초(1)",
                                detailUrl = "https://lms.dima.ac.kr/item/video-complete",
                                isRead = false,
                                completionState = LmsCompletionState.COMPLETE,
                            ),
                            LmsItem(
                                id = "video-incomplete",
                                courseId = "audio",
                                courseName = "음향기초실습",
                                kind = LmsItemKind.CONTENT,
                                title = "사운드디자인 기초(2)",
                                detailUrl = "https://lms.dima.ac.kr/item/video-incomplete",
                                isRead = true,
                                completionState = LmsCompletionState.INCOMPLETE,
                            ),
                        ),
                        syncState = LmsSyncState.READY,
                    ),
                    sessionState = LmsSessionState.ACTIVE,
                    selectedCourse = null,
                    selectedKind = null,
                    onCourseChange = {},
                    onKindChange = {},
                    onRefresh = {},
                    onOpenItem = {},
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithTag("lms_mode_all").performClick()

        // D-118: 과목마다 남은 항목을 먼저 두고, 완료한 학습은 그 과목 아래에 접어 둔다.
        // D-117: 과목 머리글이 과목을 말하므로 행에는 종류만 쓰고, 수강 상태는 섹션이 말한다.
        // The heading reads as one node: the course and how many items are left in it.
        composeRule.onNode(hasText("음향기초실습") and hasText("1개")).assertExists()
        composeRule.onNodeWithText("사운드디자인 기초(2)").assertExists()
        composeRule.onNodeWithText("콘텐츠").assertExists()
        composeRule.onAllNodesWithText("미수강").assertCountEquals(0)
        composeRule.onNodeWithText("사운드디자인 기초(1)").assertDoesNotExist()

        composeRule.onNodeWithTag("lms_completed_toggle_audio").performClick()
        composeRule.onNodeWithText("사운드디자인 기초(1)").assertExists()

        // D-058: 카드에서 읽음/안읽음 배지는 완전히 사라졌다. 읽지 않은 항목도 표시되지 않는다.
        composeRule.onAllNodesWithText("읽음").assertCountEquals(0)
        composeRule.onAllNodesWithText("안읽음").assertCountEquals(0)
    }
}
