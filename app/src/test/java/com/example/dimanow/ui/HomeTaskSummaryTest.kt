package com.example.dimanow.ui

import com.example.dimanow.lms.LmsCompletionState
import com.example.dimanow.lms.LmsItem
import com.example.dimanow.lms.LmsItemKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTaskSummaryTest {
    private val now = Instant.parse("2026-09-14T14:50:00Z")

    @Test
    fun countsIncludeAllCoursesWhileTitlesAreLimitedToThreeGloballyNearestDeadlines() {
        val overview = upcomingHomeTasks(listOf(
            task("A-late", "A", 600), task("D", "D", 400),
            task("C", "C", 300), task("A-third", "A", 700),
            task("B", "B", 100), task("A-first", "A", 200),
        ), now)

        assertEquals(listOf("B", "A", "C", "D"), overview.courses.map { it.courseName })
        assertEquals(listOf(1, 3, 1, 1), overview.courses.map { it.totalCount })
        assertEquals(listOf("B", "A-first", "C"), overview.items.map { it.id })
    }

    @Test
    fun threeNearestTitlesCanAllBelongToTheSameCourse() {
        val overview = upcomingHomeTasks(listOf(
            task("B-later", "B", 400), task("A-third", "A", 300),
            task("A-first", "A", 100), task("A-second", "A", 200),
        ), now)

        assertEquals(listOf(3, 1), overview.courses.map { it.totalCount })
        assertEquals(listOf("A-first", "A-second", "A-third"), overview.items.map { it.id })
    }

    @Test
    fun includesDistantAndUnknownTasksButNotCompleteElapsedUndatedOrNonActionItems() {
        val items = listOf(
            task("elapsed", "A", -1),
            task("complete", "A", 1).copy(completionState = LmsCompletionState.COMPLETE),
            task("undated", "A", 1).copy(dueAt = null),
            task("notice", "A", 1).copy(kind = LmsItemKind.NOTICE),
            task("material", "A", 1).copy(kind = LmsItemKind.MATERIAL, completionState = LmsCompletionState.NOT_TRACKED),
            task("unknown", "A", 0).copy(completionState = LmsCompletionState.UNKNOWN),
            task("distant", "A", 30 * 86400),
        )
        val overview = upcomingHomeTasks(items, now)
        assertEquals(listOf("unknown", "distant"), overview.items.map { it.id })
        assertEquals(2, overview.courses.single().totalCount)
    }

    @Test
    fun aMaterialCountsOnlyWhileLmsReportsItUnopened() {
        val overview = upcomingHomeTasks(listOf(
            task("unopened", "A", 10).copy(kind = LmsItemKind.MATERIAL),
            task("untracked", "A", 20).copy(kind = LmsItemKind.MATERIAL, completionState = LmsCompletionState.NOT_TRACKED),
            task("opened", "A", 30).copy(kind = LmsItemKind.MATERIAL, completionState = LmsCompletionState.COMPLETE),
        ), now)
        assertEquals(listOf("unopened"), overview.items.map { it.id })
    }

    @Test
    fun nearnessIncludesTheWholeSecondKoreanCalendarDayButNotTheThird() {
        assertEquals(true, homeTaskIsNear(Instant.parse("2026-09-16T14:59:59Z"), now))
        assertEquals(false, homeTaskIsNear(Instant.parse("2026-09-16T15:00:00Z"), now))
    }

    @Test
    fun distantOnlyCoursesStillShowTheirNearestTitle() {
        val overview = upcomingHomeTasks(listOf(task("distant", "A", 30 * 86400)), now)
        assertEquals(1, overview.courses.single().totalCount)
        assertEquals(listOf("distant"), overview.items.map { it.id })
    }

    @Test
    fun todayListKeepsOnlyOpenTasksDueOnTheKoreanDate() {
        val today = todayHomeTasks(listOf(
            task("tonight", "A", 9 * 60).copy(kind = LmsItemKind.QUIZ),
            task("tomorrow", "A", 11 * 60),
            task("done", "A", 60).copy(completionState = LmsCompletionState.COMPLETE),
            task("gone", "A", -60),
        ), now)
        assertEquals(listOf("tonight"), today.map { it.id })
    }

    @Test
    fun sameNamedDistinctCoursesRemainSeparate() {
        val overview = upcomingHomeTasks(listOf(
            task("one", "a", 1).copy(courseName = "실습"),
            task("two", "b", 2).copy(courseName = "실습"),
        ), now)
        assertEquals(2, overview.courses.size)
        assertEquals(listOf(1, 1), overview.courses.map { it.totalCount })
    }

    @Test
    fun dDayUsesKoreanCalendarBoundaryRatherThanRemainingFullDays() {
        assertEquals("D-day · 9/14 23:59 마감", homeTaskDeadlineLabel(Instant.parse("2026-09-14T14:59:00Z"), now))
        assertEquals("D-1 · 9/15 00:00 마감", homeTaskDeadlineLabel(Instant.parse("2026-09-14T15:00:00Z"), now))
        assertEquals("D-8 · 9/22 마감", homeTaskDeadlineLabel(Instant.parse("2026-09-21T15:00:00Z"), now))
    }

    private fun task(id: String, course: String, seconds: Long) = LmsItem(
        id = id, courseId = course, courseName = course,
        kind = LmsItemKind.ASSIGNMENT, title = id,
        dueAt = now.plusSeconds(seconds), detailUrl = "https://lms.dima.ac.kr/test",
        completionState = LmsCompletionState.INCOMPLETE,
    )
}
