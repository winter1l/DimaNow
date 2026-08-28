package com.example.dimanow.notice

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DimaNoticeParserTest {
    @Test
    fun `official notice board shape yields absolute links and newest-first order`() {
        val html = checkNotNull(javaClass.getResource("/fixtures/notice_official_shape.html")).readText()

        val notices = DimaNoticeParser().parse(html)

        assertEquals(5, notices.size)
        assertEquals("2026-2학기 수강신청 정정 기간 안내", notices[0].title)
        assertEquals(LocalDate.of(2026, 8, 27), notices[0].date)
        assertEquals("2608271512340001", notices[0].id)
        assertEquals(
            "https://www.dima.ac.kr/?p=111&page=1&viewMode=view&reqIdx=2608271512340001",
            notices[0].url,
        )
        assertFalse(notices[0].isPinned)
    }

    @Test
    fun `same-date notices keep page order and pinned badge survives`() {
        val html = checkNotNull(javaClass.getResource("/fixtures/notice_official_shape.html")).readText()

        val notices = DimaNoticeParser().parse(html)

        assertEquals("학내 Wi-Fi 구축 관련 사용 안내", notices[1].title)
        assertEquals("[취창업] 안성일자리센터 MBTI 검사 및 진로 상담 신청 안내", notices[2].title)
        assertTrue(notices[1].isPinned)
        assertTrue(notices[2].isPinned)
        assertEquals("[성과홍보팀] 2026학년도 전문대학 혁신지원사업 서포터즈 2차 추가 모집", notices[3].title)
        assertEquals("2026-2학기 국가장학금 2차 신청 안내", notices[4].title)
    }

    @Test
    fun `page without the notice list yields no rows`() {
        assertTrue(DimaNoticeParser().parse("<html><body><p>점검 중</p></body></html>").isEmpty())
    }
}
