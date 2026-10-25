package com.example.dimanow.work

import com.example.dimanow.lms.LmsCompletionState
import com.example.dimanow.lms.LmsItem
import com.example.dimanow.lms.LmsItemKind
import com.example.dimanow.lms.LmsSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyReminderTest {
    private val morning = LocalTime.of(8, 30)

    @Test
    fun theNextRunIsTodaysTimeUntilItPassesThenTomorrows() {
        assertEquals(Duration.ofMinutes(30), DailyWork.delayUntilNext(ZonedDateTime.parse("2026-10-26T08:00:00+09:00[Asia/Seoul]"), morning))
        assertEquals(Duration.ofHours(24), DailyWork.delayUntilNext(ZonedDateTime.parse("2026-10-26T08:30:00+09:00[Asia/Seoul]"), morning))
        // Q-032 (c): a run held back to 22:05 still books the next morning, not 22:05 tomorrow.
        assertEquals(Duration.ofMinutes(625), DailyWork.delayUntilNext(ZonedDateTime.parse("2026-10-26T22:05:00+09:00[Asia/Seoul]"), morning))
    }

    @Test
    fun theDigestCountsTodaysOpenTasksByKindAndListsThemByTime() {
        val now = ZonedDateTime.parse("2026-10-26T08:30:00+09:00[Asia/Seoul]")
        val snapshot = LmsSnapshot(
            items = listOf(
                task("a", "영상제작", "과제 1", "2026-10-26T23:59:00+09:00"),
                task("b", "사운드", "퀴즈 1", "2026-10-26T18:00:00+09:00", LmsItemKind.QUIZ),
                task("c", "영상제작", "과제 2", "2026-10-26T12:00:00+09:00"),
                task("d", "영상제작", "내일 과제", "2026-10-27T09:00:00+09:00"),
            ),
            lastSuccessAt = Instant.parse("2026-10-25T12:10:00Z"),
        )
        val digest = requireNotNull(deadlineDigest(snapshot, now))
        assertEquals("오늘 마감 과제 2개, 퀴즈 1개", digest.title)
        assertEquals(listOf("12:00 영상제작 · 과제 2", "18:00 사운드 · 퀴즈 1", "23:59 영상제작 · 과제 1"), digest.lines)
        assertEquals("10/25 21:10에 불러온 목록 기준", digest.basis)
    }

    @Test
    fun nothingDueTodayPostsNothing() {
        val now = ZonedDateTime.parse("2026-10-26T08:30:00+09:00[Asia/Seoul]")
        assertNull(deadlineDigest(LmsSnapshot(items = listOf(task("d", "A", "내일", "2026-10-27T09:00:00+09:00"))), now))
    }

    private fun task(id: String, course: String, title: String, due: String, kind: LmsItemKind = LmsItemKind.ASSIGNMENT) = LmsItem(
        id = id, courseId = course, courseName = course, kind = kind, title = title,
        dueAt = ZonedDateTime.parse(due).toInstant(), detailUrl = "https://lms.dima.ac.kr/test",
        completionState = LmsCompletionState.INCOMPLETE,
    )
}
