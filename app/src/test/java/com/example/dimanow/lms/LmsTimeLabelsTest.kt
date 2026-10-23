package com.example.dimanow.lms

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LmsTimeLabelsTest {
    // 2026-10-23 (금) 22:10 in Seoul.
    private val now = Instant.parse("2026-10-23T13:10:00Z")

    @Test
    fun `a deadline says how much time is left and only the next two days are urgent`() {
        assertEquals(LmsDueLabel("20분 남음", urgent = true), lmsDueLabel(Instant.parse("2026-10-23T13:30:00Z"), now))
        assertEquals(LmsDueLabel("오늘 23:59", urgent = true), lmsDueLabel(Instant.parse("2026-10-23T14:59:00Z"), now))
        assertEquals(LmsDueLabel("내일 23:59", urgent = true), lmsDueLabel(Instant.parse("2026-10-24T14:59:00Z"), now))
        assertEquals(LmsDueLabel("3일 남음", urgent = false), lmsDueLabel(Instant.parse("2026-10-26T14:59:00Z"), now))
        assertEquals(LmsDueLabel("56일 남음", urgent = false), lmsDueLabel(Instant.parse("2026-12-18T14:59:00Z"), now))
    }

    @Test
    fun `a missed deadline counts the days since`() {
        assertEquals(LmsDueLabel("오늘 지남", urgent = false), lmsDueLabel(Instant.parse("2026-10-23T09:00:00Z"), now))
        assertEquals(LmsDueLabel("4일 지남", urgent = false), lmsDueLabel(Instant.parse("2026-10-19T14:59:00Z"), now))
    }

    @Test
    fun `rows give the exact time and the list says when it was read`() {
        val due = LmsItem("1", "c", "과목", LmsItemKind.DISCUSSION, "색온도", dueAt = Instant.parse("2026-10-19T14:59:00Z"), detailUrl = "x")
        assertEquals("10월 19일 (월) 23:59 마감", lmsWhenLine(due))
        val posted = due.copy(dueAt = null, registeredAt = Instant.parse("2026-10-03T01:00:00Z"))
        assertEquals("10월 3일 등록", lmsWhenLine(posted))
        assertEquals("오늘 22:05에 불러왔어요", lmsUpdatedLabel(Instant.parse("2026-10-23T13:05:00Z"), now))
        assertEquals("어제 18:20에 불러왔어요", lmsUpdatedLabel(Instant.parse("2026-10-22T09:20:00Z"), now))
    }
}
