package com.example.dimanow.school

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryPublicTest {
    @Test
    fun theHoursApiAnswerBecomesPlacesAndTimes() {
        // The front page's /LibtechApi/GetSpongeManagement answer, as observed on 2026-10-26.
        val body = "\"\\u003cdiv class=\\\"time-guide\\\"\\u003e\\n\\u003cdl\\u003e\\n\\t\\u003cdt\\u003e리더스스테이션\\u003c/dt\\u003e\\n\\t\\u003cdd\\u003e09:00 ~ 20:00\\u003c/dd\\u003e\\n\\u003c/dl\\u003e\\n\\n\\u003cdl\\u003e\\n\\t\\u003cdt\\u003e러닝라운지\\u003c/dt\\u003e\\n\\t\\u003cdd\\u003e09:00 ~ 20:00\\u003c/dd\\u003e\\n\\u003c/dl\\u003e\\n\\n\\u003cdl\\u003e\\n\\t\\u003cdt\\u003e자유열람실\\u003c/dt\\u003e\\n\\t\\u003cdd\\u003e24시간 운영중\\u003c/dd\\u003e\\n\\u003c/dl\\u003e\\n\\n\\u003cdiv class=\\\"clear\\\"\\u003e\\u0026nbsp;\\u003c/div\\u003e\\n\\u003c/div\\u003e\""
        assertEquals(
            listOf(
                LibraryHours("리더스스테이션", "09:00 ~ 20:00"),
                LibraryHours("러닝라운지", "09:00 ~ 20:00"),
                LibraryHours("자유열람실", "24시간 운영중"),
            ),
            parseLibraryHoursApi(body),
        )
    }

    @Test
    fun theSavedHoursAndNoticesComeBackAsTheyWere() {
        // D-136: kept on the device so 도서관 shows its hours at once.
        val info = LibraryPublicInfo(
            hours = listOf(LibraryHours("리더스스테이션", "09:00 ~ 20:00"), LibraryHours("자유열람실", "24시간 운영중")),
            notices = listOf(
                LibraryNotice("입시기간 도서관 이용 안내", "https://lib.dima.ac.kr/Board/Detail/20260929101500000?n=notice", LocalDate.of(2026, 9, 29)),
                LibraryNotice("휴관 안내", "https://lib.dima.ac.kr/Board/Detail/1?n=notice", null),
            ),
            readAt = Instant.ofEpochMilli(1_792_000_000_000),
        )
        assertEquals(info, decodeLibraryPublicInfo(encodeLibraryPublicInfo(info)))
        // A saved link off the library's site is dropped.
        val elsewhere = info.copy(notices = listOf(LibraryNotice("x", "https://evil.example/", null)))
        assertEquals(emptyList<LibraryNotice>(), decodeLibraryPublicInfo(encodeLibraryPublicInfo(elsewhere))?.notices)
        assertNull(decodeLibraryPublicInfo("""{"version":2,"readAt":1}"""))
    }

    @Test
    fun onlyRecentNoticesAboutOpeningHoursAreKept() {
        val html = """
            <dd><a href="/Board/Detail/20260709163937426?n=notice&p=1">
            도서관 편의 물품 대여 서비스 안내   </a></dd>
            <dd><a href="/Board/Detail/20221214151632204?n=notice&p=1">안성-평택 연합대학 도서관 특별 회원 신청 안내</a></dd>
            <dd><a href="/Board/Detail/20261007002123417?n=notice&p=1">DIMA Festa 2026 도서관 이용 안내 : 10/6(화)~10/8(목)</a></dd>
            <dd><a href="/Board/Detail/20260929172201963?n=notice&p=1">입시기간 도서관 이용 안내 : 10/12(월)~10/16(금)</a></dd>
            <dd><a href="/Board/Detail/20260918123048943?n=notice&p=1">추석 연휴 도서관 운영시간 안내</a></dd>
            <dd><a href="/Board/Detail/20260825145901211?n=notice&p=1">[8/26(수)] 도서관 WiFi 이용 제한 안내</a></dd>
            <dd><a href="/Board/Detail/20260824132310688?n=notice&p=1">개강 후 이용안내 : 8/24(월)부터</a></dd>
            <dd><a href="/Board/Detail/20251219090000000?n=notice&p=1">겨울방학 도서관 이용시간 안내</a></dd>
        """
        val notices = parseLibraryNotices(html)
        assertEquals(8, notices.size)
        assertEquals("https://lib.dima.ac.kr/Board/Detail/20260918123048943?n=notice", notices[4].url)
        assertEquals(LocalDate.of(2026, 9, 18), notices[4].postedOn)
        val kept = hoursNotices(notices, LocalDate.of(2026, 10, 26))
        assertEquals(
            listOf(
                "DIMA Festa 2026 도서관 이용 안내 : 10/6(화)~10/8(목)",
                "입시기간 도서관 이용 안내 : 10/12(월)~10/16(금)",
                "추석 연휴 도서관 운영시간 안내",
                "개강 후 이용안내 : 8/24(월)부터",
            ),
            kept.map { it.title },
        )
        // A vacation notice counts while it is recent.
        assertEquals("겨울방학 도서관 이용시간 안내", hoursNotices(notices.filter { it.postedOn!! <= LocalDate.of(2026, 1, 10) }, LocalDate.of(2026, 1, 10)).first().title)
    }
}
