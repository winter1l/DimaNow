package com.example.dimanow.work

import com.example.dimanow.lms.LmsCompletionState
import com.example.dimanow.lms.LmsItem
import com.example.dimanow.lms.LmsItemKind
import com.example.dimanow.lms.LmsSnapshot
import com.example.dimanow.school.LibraryDueBook
import com.example.dimanow.school.LibraryDueList
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** D-131: library books due back today join the one morning digest (D-126(3)). */
class LibraryDigestTest {
    private val now = ZonedDateTime.parse("2026-10-26T08:30:00+09:00[Asia/Seoul]")
    private val library = LibraryDueList(
        books = listOf(
            LibraryDueBook("영상 문법의 이해", LocalDate.of(2026, 10, 26)),
            LibraryDueBook("사운드 디자인 입문", LocalDate.of(2026, 11, 3)),
            LibraryDueBook("지난 책", LocalDate.of(2026, 10, 24)),
        ),
        readAt = Instant.parse("2026-10-25T00:05:00Z"),
    )

    @Test
    fun booksDueTodayJoinTheTasksInOneNotification() {
        val snapshot = LmsSnapshot(
            items = listOf(
                task("a", "영상제작", "과제 1", "2026-10-26T23:59:00+09:00"),
                task("c", "영상제작", "과제 2", "2026-10-26T12:00:00+09:00"),
            ),
            lastSuccessAt = Instant.parse("2026-10-25T12:10:00Z"),
        )
        val digest = requireNotNull(deadlineDigest(snapshot, now, library))
        assertEquals("오늘 마감 과제 2개, 반납 1권", digest.title)
        assertEquals(listOf("12:00 영상제작 · 과제 2", "23:59 영상제작 · 과제 1", "반납 · 영상 문법의 이해"), digest.lines)
        assertEquals("과제 10/25 21:10 · 도서관 10/25 09:05 기준", digest.basis)
        assertEquals("COURSES", digest.page)
    }

    @Test
    fun onlyBooksDueTodayStillPostAndOpenTheLibrary() {
        val digest = requireNotNull(deadlineDigest(LmsSnapshot(), now, library))
        assertEquals("오늘 반납 1권", digest.title)
        assertEquals(listOf("반납 · 영상 문법의 이해"), digest.lines)
        assertEquals("10/25 09:05에 불러온 도서관 목록 기준", digest.basis)
        assertEquals("LIBRARY", digest.page)
    }

    @Test
    fun booksDueOnOtherDaysAddNothing() {
        val later = library.copy(books = library.books.filter { it.dueOn != LocalDate.of(2026, 10, 26) })
        assertNull(deadlineDigest(LmsSnapshot(), now, later))
        assertNull(deadlineDigest(LmsSnapshot(), now, null))
        val snapshot = LmsSnapshot(items = listOf(task("a", "영상제작", "과제 1", "2026-10-26T23:59:00+09:00")))
        val digest = requireNotNull(deadlineDigest(snapshot, now, later))
        assertEquals("오늘 마감 과제 1개", digest.title)
        assertEquals(listOf("23:59 영상제작 · 과제 1"), digest.lines)
    }

    private fun task(id: String, course: String, title: String, due: String) = LmsItem(
        id = id, courseId = course, courseName = course, kind = LmsItemKind.ASSIGNMENT, title = title,
        dueAt = ZonedDateTime.parse(due).toInstant(), detailUrl = "https://lms.dima.ac.kr/test",
        completionState = LmsCompletionState.INCOMPLETE,
    )
}
