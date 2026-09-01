package com.example.dimanow.lms

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.dimanow.theme.DIMANowTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class LmsHistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allReadFiltersAreVisibleAndADeepHistoryItemCanBeReached() {
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
                    selectedRead = null,
                    onCourseChange = {},
                    onKindChange = {},
                    onReadChange = {},
                    onRefresh = {},
                    onOpenItem = {},
                    now = Instant.parse("2026-09-01T03:00:00Z"),
                )
            }
        }

        composeRule.onNodeWithTag("lms_mode_all").performClick()
        composeRule.onNodeWithTag("lms_read_all").assertExists()
        composeRule.onNodeWithTag("lms_read_unread").assertExists()
        composeRule.onNodeWithTag("lms_read_read").assertExists()
        composeRule.onNodeWithTag("lms_kind_CONTENT").assertExists()
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
                    selectedRead = null,
                    onCourseChange = {},
                    onKindChange = {},
                    onReadChange = {},
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
}
