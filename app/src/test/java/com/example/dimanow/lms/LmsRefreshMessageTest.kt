package com.example.dimanow.lms

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LmsRefreshMessageTest {
    private val due = Instant.parse("2026-10-30T14:59:00Z")
    private fun item(id: String, title: String = "과제 $id", read: Boolean = false, complete: Boolean = false) = LmsItem(
        id = id,
        courseId = "c1",
        courseName = "음향기초실습",
        kind = LmsItemKind.ASSIGNMENT,
        title = title,
        dueAt = due,
        detailUrl = "https://lms.example/$id",
        isRead = read,
        completionState = if (complete) LmsCompletionState.COMPLETE else LmsCompletionState.INCOMPLETE,
    )

    @Test
    fun `the same list says it is already up to date even when only read marks moved`() {
        assertEquals("이미 최신 수업 정보예요", lmsRefreshMessage(listOf(item("1"), item("2")), listOf(item("2", read = true), item("1"))))
    }

    @Test
    fun `new items are counted`() {
        assertEquals("새 항목 2개를 받았어요", lmsRefreshMessage(listOf(item("1")), listOf(item("1"), item("2"), item("3"))))
    }

    @Test
    fun `a changed item without new ones still says it was refreshed`() {
        assertEquals("수업 정보를 새로 받았어요", lmsRefreshMessage(listOf(item("1")), listOf(item("1", complete = true))))
    }
}
