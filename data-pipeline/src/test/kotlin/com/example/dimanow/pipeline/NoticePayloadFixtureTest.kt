package com.example.dimanow.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticePayloadFixtureTest {
    @Test
    fun `official notice fixture remains newest first and preserves pinned rows`() {
        val html = checkNotNull(javaClass.getResource("/fixtures/notice_official_shape.html")).readText()

        val notices = NoticePayloadBuilder().build(html).notices

        assertEquals(5, notices.size)
        assertEquals("2026-2학기 수강신청 정정 기간 안내", notices[0].title)
        assertEquals("2026-08-27", notices[0].date)
        assertEquals("2608271512340001", notices[0].id)
        assertTrue(notices[1].pinned)
        assertTrue(notices[2].pinned)
    }
}
