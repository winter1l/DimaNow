package com.example.dimanow.lms

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
        composeRule.onNodeWithText("새 항목").assertExists()
        composeRule.onNodeWithText("미완료").assertExists()
        composeRule.onNodeWithText("완료한 학습").assertExists()
        composeRule.onNodeWithText("완료한 콘텐츠").assertDoesNotExist()
        composeRule.onNodeWithText("완료한 학습").performClick()
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

        // D-058: 전체도 오늘과 똑같이 과목 묶음에는 미완료만 두고 완료는 아래로 접어 둔다
        composeRule.onNodeWithText("음향기초실습 · 1").assertExists()
        composeRule.onNodeWithText("사운드디자인 기초(2)").assertExists()
        composeRule.onNodeWithText("미수강").assertExists()
        composeRule.onNodeWithText("사운드디자인 기초(1)").assertDoesNotExist()

        composeRule.onNodeWithTag("lms_completed_toggle").performClick()
        composeRule.onNodeWithText("사운드디자인 기초(1)").assertExists()
        composeRule.onNodeWithText("수강 완료").assertExists()

        // D-058: 카드에서 읽음/안읽음 배지는 완전히 사라졌다. 읽지 않은 항목도 표시되지 않는다.
        composeRule.onAllNodesWithText("읽음").assertCountEquals(0)
        composeRule.onAllNodesWithText("안읽음").assertCountEquals(0)
    }
}
