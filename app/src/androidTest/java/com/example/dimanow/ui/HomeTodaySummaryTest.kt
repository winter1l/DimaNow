package com.example.dimanow.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.dimanow.lms.LmsCompletionState
import com.example.dimanow.lms.LmsItem
import com.example.dimanow.lms.LmsItemKind
import com.example.dimanow.lms.LmsSnapshot
import com.example.dimanow.lms.LmsSyncState
import java.time.Instant
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeTodaySummaryTest {
    @get:Rule val composeRule = createComposeRule()
    private val now = ZonedDateTime.parse("2026-09-14T12:00:00+09:00[Asia/Seoul]")

    @Test
    fun homeShowsUpcomingCourseDeadlinesAndExcludesElapsedTasks() {
        composeRule.setContent {
            HomeTodaySummary(
                snapshot = LmsSnapshot(
                    items = listOf(
                        task("지난 마감", "2026-09-13T14:59:00Z"),
                        task("오전 과제", "2026-09-14T02:00:00Z"),
                        task("오늘 영상", "2026-09-14T14:59:00Z").copy(kind = LmsItemKind.CONTENT),
                        task("완료 과제", "2026-09-14T13:00:00Z").copy(completionState = LmsCompletionState.COMPLETE),
                        task("내일 과제", "2026-09-14T15:00:00Z"),
                        task("공지사항", "2026-09-14T13:00:00Z").copy(kind = LmsItemKind.NOTICE),
                    ),
                    lastSuccessAt = now.toInstant(),
                ),
                now = now,
                onOpenCourses = {},
            )
        }
        composeRule.onNodeWithText("오늘 마감").assertDoesNotExist()
        composeRule.onNodeWithText("영상제작 · 2개").assertIsDisplayed()
        composeRule.onNodeWithText("오늘 영상").assertIsDisplayed()
        composeRule.onNodeWithText("D-day · 9/14 23:59 마감").assertIsDisplayed()
        composeRule.onNodeWithText("내일 과제").assertIsDisplayed()
        composeRule.onNodeWithText("D-1 · 9/15 00:00 마감").assertIsDisplayed()
        listOf("지난 마감", "오전 과제", "완료 과제", "공지사항").forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun courseCountsIncludeTasksBeyondTheThreeVisibleTitles() {
        composeRule.setContent {
            HomeTodaySummary(
                snapshot = LmsSnapshot(
                    items = listOf(
                        task("네 번째 영상", "2026-09-17T14:59:00Z"),
                        task("두 번째 영상", "2026-09-15T14:59:00Z"),
                        task("음향 과제", "2026-09-18T14:59:00Z").copy(courseId = "audio", courseName = "음향실습"),
                        task("첫 번째 영상", "2026-09-14T14:59:00Z"),
                        task("세 번째 영상", "2026-09-16T14:59:00Z"),
                    ),
                    lastSuccessAt = now.toInstant(),
                ),
                now = now,
                onOpenCourses = {},
            )
        }
        composeRule.onNodeWithText("영상제작 · 4개").assertIsDisplayed()
        composeRule.onNodeWithText("음향실습 · 1개").assertIsDisplayed()
        listOf("첫 번째 영상", "두 번째 영상", "세 번째 영상").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        listOf("네 번째 영상", "음향 과제").forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun unavailableLmsDoesNotClaimThereAreNoTasksAndOffersCourses() {
        var coursesOpened = 0
        composeRule.setContent {
            HomeTodaySummary(
                snapshot = LmsSnapshot(syncState = LmsSyncState.ERROR),
                now = now,
                onOpenCourses = { coursesOpened++ },
            )
        }
        composeRule.onNodeWithText("수업 정보를 불러오지 못했어요. 다시 시도해 주세요").assertIsDisplayed()
        composeRule.onNodeWithText("다가오는 마감이 없어요").assertDoesNotExist()
        // D-094(9): the whole card is the target and announces "수업 보기".
        composeRule.onNode(hasClickLabel("수업 보기")).performClick()
        assertEquals(1, coursesOpened)
    }

    @Test
    fun unknownCompletionOmitsDisclosureButFailedRefreshKeepsActionableError() {
        composeRule.setContent {
            HomeTodaySummary(
                snapshot = LmsSnapshot(
                    items = listOf(task("확인할 과제", "2026-09-14T13:00:00Z").copy(completionState = LmsCompletionState.UNKNOWN)),
                    lastSuccessAt = now.minusDays(1).toInstant(),
                    syncState = LmsSyncState.ERROR,
                ),
                now = now,
                onOpenCourses = {},
            )
        }
        composeRule.onNodeWithText("확인할 과제").assertIsDisplayed()
        composeRule.onNodeWithText("완료 여부 확인 필요").assertDoesNotExist()
        composeRule.onNodeWithText("저장된", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("9/13 12:00 확인", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("수업 정보를 불러오지 못했어요. 다시 시도해 주세요").assertIsDisplayed()
    }

    @Test
    fun distantCachedTasksShowCountsWithoutTitlesOrMisleadingEmptyMessage() {
        composeRule.setContent {
            HomeTodaySummary(
                snapshot = LmsSnapshot(
                    items = listOf(task("사흘 뒤 과제", "2026-09-16T15:00:00Z")),
                    lastSuccessAt = now.minusDays(1).toInstant(),
                ),
                now = now,
                onOpenCourses = {},
            )
        }
        composeRule.onNodeWithText("수업").assertIsDisplayed()
        composeRule.onNode(hasClickLabel("수업 보기")).assertIsDisplayed()
        composeRule.onNodeWithText("영상제작 · 1개").assertIsDisplayed()
        composeRule.onNodeWithText("사흘 뒤 과제").assertDoesNotExist()
        composeRule.onNodeWithText("다가오는 마감이 없어요").assertDoesNotExist()
        composeRule.onNodeWithText("저장된", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("9/13 12:00 확인", substring = true).assertDoesNotExist()
    }

    private fun task(title: String, dueAt: String) = LmsItem(
        id = title,
        courseId = "video",
        courseName = "영상제작",
        kind = LmsItemKind.ASSIGNMENT,
        title = title,
        dueAt = Instant.parse(dueAt),
        detailUrl = "https://lms.dima.ac.kr/test",
        completionState = LmsCompletionState.INCOMPLETE,
    )
}

