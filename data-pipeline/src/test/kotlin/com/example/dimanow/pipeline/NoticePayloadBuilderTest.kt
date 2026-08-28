package com.example.dimanow.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticePayloadBuilderTest {
    @Test
    fun `공식 공지 행만 안전한 절대 URL로 게시한다`() {
        val html = """
            <div class="bbs_box"><ul>
              <li class="notice"><a href="/?p=111&page=1&viewMode=view&reqIdx=2608280001">
                <p class="tit">개강 안내</p><span class="date">2026.08.28</span>
              </a></li>
            </ul></div>
        """.trimIndent()

        val notice = NoticePayloadBuilder().build(html).notices.single()

        assertEquals("2608280001", notice.id)
        assertEquals("개강 안내", notice.title)
        assertEquals("2026-08-28", notice.date)
        assertEquals("https://www.dima.ac.kr/?p=111&page=1&viewMode=view&reqIdx=2608280001", notice.url)
        assertEquals(true, notice.pinned)
    }
}
