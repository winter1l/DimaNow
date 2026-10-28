package com.example.dimanow.school

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * D-131 library reader, on made-up page data (no real loan row has been seen: the student had none on 2026-10-25/26).
 * [SCRIPT_ANSWER] is what [LIBRARY_LOANS_SCRIPT] returned in a browser for a synthetic `/MyLibrary` page.
 */
class LibraryTest {
    private val today = LocalDate.of(2026, 10, 26)

    @Test
    fun `the loan table is read by its header names, nearest due first`() {
        val status = parseLibraryStatus(SCRIPT_ANSWER)
        assertEquals(1, status.overdueCount)
        assertEquals(2, status.loanCount)
        assertEquals(
            listOf(
                LibraryBook("영상 문법의 이해", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 24), renewable = false, markedOverdue = true),
                LibraryBook("사운드 디자인 입문", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 3), renewable = true),
            ),
            status.books,
        )
        assertEquals(
            listOf(
                LibraryHours("리더스스테이션", "09:00 ~ 20:00"),
                LibraryHours("러닝라운지", "09:00 ~ 20:00"),
                LibraryHours("자유열람실", "24시간 운영중"),
            ),
            status.hours,
        )
        assertEquals(2, status.loans)
        assertFalse(status.incomplete)
    }

    @Test
    fun `columns are found wherever they stand and whatever they are called`() {
        val json = """
            {"overdue":0,"loans":2,"noData":false,"hours":[],
             "table":{"headers":["상태","반납 예정일","자료명","대출일자","선택"],
              "rows":[
                {"cells":["대출중","20261105","소리의 과학\n김저자 · 2024","2026/10/22",""],"actions":[false,false,true,false,true]},
                {"cells":["대출중","2026년 10월 30일","촬영 기초","2026년 10월 16일",""],"actions":[false,false,true,false,false]}
              ]}}
        """.trimIndent()
        val books = parseLibraryStatus(json).books
        assertEquals(listOf("촬영 기초", "소리의 과학"), books.map { it.title })
        assertEquals(listOf(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 5)), books.map { it.dueOn })
        assertEquals(LocalDate.of(2026, 10, 22), books[1].loanedOn)
        // No 연기 column: whether a renewal is open is not known.
        assertTrue(books.all { it.renewable == null })
    }

    @Test
    fun `an empty table, a no-data row or a zero count is no loans`() {
        val noDataRow = """
            {"overdue":0,"loans":0,"noData":true,"hours":[],
             "table":{"headers":["번호","서명","대출일","반납예정일","연기","상태"],
              "rows":[{"cells":["대출중인 자료가 없습니다."],"actions":[false]}]}}
        """.trimIndent()
        assertEquals(emptyList<LibraryBook>(), parseLibraryStatus(noDataRow).books)
        val noTable = """{"overdue":0,"loans":0,"noData":false,"hours":[],"table":null}"""
        assertEquals(0, parseLibraryStatus(noTable).loans)
        val noCountButNotice = """{"overdue":null,"loans":null,"noData":true,"hours":[],"table":null}"""
        assertEquals(emptyList<LibraryBook>(), parseLibraryStatus(noCountButNotice).books)
    }

    @Test
    fun `loans without a readable list keep the header counts and say the list was not read`() {
        val missing = parseLibraryStatus("""{"overdue":1,"loans":2,"noData":false,"hours":[],"table":null}""")
        assertEquals(2, missing.loans)
        assertEquals(1, missing.overdue(today))
        assertTrue(missing.incomplete)
        assertEquals("대출 중인 책 2권의 목록을 읽지 못했어요. 대출·연기에서 볼 수 있어요.", missing.incompleteNote)
        val unknownTable = parseLibraryStatus("""{"loans":1,"table":{"headers":["번호","등록번호"],"rows":[{"cells":["1","EM1"],"actions":[]}]}}""")
        assertTrue(unknownTable.incomplete)
        assertNull(parseLibraryStatus(SCRIPT_ANSWER).incompleteNote)
    }

    @Test
    fun `a page with neither counts nor a list is not understood`() {
        try {
            parseLibraryStatus("""{"overdue":null,"loans":null,"noData":false,"hours":[],"table":null}""")
            fail("nothing known must not read as no loans")
        } catch (_: IllegalArgumentException) {
        }
        val unknownTable = """{"loans":null,"table":{"headers":["번호","등록번호"],"rows":[{"cells":["1","EM1"],"actions":[]}]}}"""
        assertTrue(runCatching { parseLibraryStatus(unknownTable) }.isFailure)
    }

    @Test
    fun `fewer rows than the header count is flagged, returned rows are left out`() {
        val json = """
            {"overdue":0,"loans":3,"noData":false,"hours":[],
             "table":{"headers":["서명","대출일","반납예정일","상태"],
              "rows":[
                {"cells":["책 하나","2026-10-01","2026-10-29","대출"],"actions":[]},
                {"cells":["돌려준 책","2026-09-01","2026-09-15","반납완료"],"actions":[]},
                {"cells":["책 둘","2026-10-02","2026-10-30","대출"],"actions":[]}
              ]}}
        """.trimIndent()
        val status = parseLibraryStatus(json)
        assertEquals(listOf("책 하나", "책 둘"), status.books.map { it.title })
        assertTrue(status.incomplete)
        assertEquals(3, status.loans)
        assertEquals("대출 중인 책 3권 중 2권만 읽었어요. 대출·연기에서 모두 볼 수 있어요.", status.incompleteNote)
    }

    @Test
    fun `overdue comes from the date or the row's own word, and the header count is kept when larger`() {
        val late = LibraryBook("늦은 책", null, LocalDate.of(2026, 10, 24), renewable = null)
        val marked = LibraryBook("표시된 책", null, LocalDate.of(2026, 10, 28), renewable = null, markedOverdue = true)
        val dueToday = LibraryBook("오늘 책", null, today, renewable = true)
        val later = LibraryBook("나중 책", null, LocalDate.of(2026, 11, 3), renewable = true)
        assertTrue(late.isOverdue(today))
        assertTrue(marked.isOverdue(today))
        assertFalse(dueToday.isOverdue(today))
        assertTrue(dueToday.needsReturn(today))
        assertFalse(later.needsReturn(today))
        val status = LibraryStatus(overdueCount = 0, loanCount = 4, books = listOf(late, marked, dueToday, later), hours = emptyList())
        assertEquals(2, status.overdue(today))
        assertEquals(3, LibraryStatus(overdueCount = 3, loanCount = null, books = listOf(late), hours = emptyList()).overdue(today))
    }

    @Test
    fun `due labels count down, name the day, and count overdue days`() {
        assertEquals("D-8 · 11/3 반납", libraryDueLabel(LocalDate.of(2026, 11, 3), today))
        assertEquals("D-day · 10/26 반납", libraryDueLabel(today, today))
        assertEquals("연체 2일 · 10/24 반납", libraryDueLabel(LocalDate.of(2026, 10, 24), today))
        assertEquals("반납일을 읽지 못했어요", libraryDueLabel(null, today))
    }

    @Test
    fun `dates come in the library's usual forms`() {
        assertEquals(LocalDate.of(2026, 10, 27), parseLibraryDate("2026-10-27"))
        assertEquals(LocalDate.of(2026, 10, 27), parseLibraryDate("2026.10.27 (화)"))
        assertEquals(LocalDate.of(2026, 1, 5), parseLibraryDate("2026/1/5"))
        assertEquals(LocalDate.of(2026, 10, 27), parseLibraryDate("20261027"))
        assertEquals(LocalDate.of(2026, 10, 20), parseLibraryDate("2026-10-20 (연체 6일)"))
        assertNull(parseLibraryDate("-"))
        assertNull(parseLibraryDate("2026-13-40"))
    }

    @Test
    fun `opening hours pair however the block splits its text`() {
        val hours = pairLibraryHours(
            listOf("열람실 안내", "리더스스테이션", "09:00", "~", "20:00", "러닝라운지 09:00~20:00", "자유열람실", "24시간 운영중", "토요일: 휴관", "더보기"),
        )
        assertEquals(
            listOf(
                LibraryHours("리더스스테이션", "09:00 ~ 20:00"),
                LibraryHours("러닝라운지", "09:00 ~ 20:00"),
                LibraryHours("자유열람실", "24시간 운영중"),
                LibraryHours("토요일", "휴관"),
            ),
            hours,
        )
        assertEquals(emptyList<LibraryHours>(), pairLibraryHours(listOf("09:00 ~ 20:00", "더보기")))
    }

    @Test
    fun `the saved due list keeps titles and due dates only, and comes back as it was`() {
        val status = parseLibraryStatus(SCRIPT_ANSWER)
        val list = status.dueList(Instant.ofEpochMilli(1_792_000_000_000))
        assertEquals(
            listOf(LibraryDueBook("영상 문법의 이해", LocalDate.of(2026, 10, 24)), LibraryDueBook("사운드 디자인 입문", LocalDate.of(2026, 11, 3))),
            list.books,
        )
        val saved = encodeLibraryDue(list)
        assertFalse(saved.contains("2026-10-10"))
        assertFalse(saved.contains("리더스스테이션"))
        assertEquals(list, decodeLibraryDue(saved))
        assertNull(decodeLibraryDue("""{"version":2,"readAt":1,"books":[]}"""))
        assertNull(runCatching { decodeLibraryDue("not json") }.getOrNull())
    }

    @Test
    fun `the saved read comes back as it was, so the page shows it at once after a restart`() {
        // D-136: the whole read is kept beside the due list.
        val read = SchoolReadCache.LibraryRead(parseLibraryStatus(SCRIPT_ANSWER), Instant.ofEpochMilli(1_792_000_000_000))
        assertEquals(read, decodeLibraryRead(encodeLibraryRead(read)))
        val empty = SchoolReadCache.LibraryRead(LibraryStatus(null, 0, emptyList(), emptyList()), Instant.ofEpochMilli(1))
        assertEquals(empty, decodeLibraryRead(encodeLibraryRead(empty)))
        assertNull(decodeLibraryRead("""{"version":2,"readAt":1,"books":[],"hours":[]}"""))
        assertNull(runCatching { decodeLibraryRead("not json") }.getOrNull())
    }

    @Test
    fun `opening the page reads again only after half an hour`() {
        val now = Instant.parse("2026-10-26T01:00:00Z")
        assertTrue(libraryAutoReadDue(null, now))
        assertFalse(libraryAutoReadDue(now.minus(Duration.ofMinutes(29)), now))
        assertTrue(libraryAutoReadDue(now.minus(Duration.ofMinutes(30)), now))
    }

    @Test
    fun `the library signs in through the portal and its own SSO entry`() {
        val library = SchoolService.LIBRARY
        assertEquals("lib.dima.ac.kr", library.host)
        assertEquals("도서관", library.label)
        assertTrue(SchoolUrlPolicy.isPortalCredentialPage(library.portalEntry))
        assertTrue(library.portalEntry.endsWith("?r=https://lib.dima.ac.kr/sso/main.aspx"))
        assertEquals("/sso/main.aspx", library.selfForwardingSsoPath)
        assertTrue(SchoolUrlPolicy.isAllowedNavigation(LIBRARY_LOANS_URL))
        assertTrue(SchoolUrlPolicy.isAllowedNavigation(LIBRARY_MY_LIBRARY_URL))
        assertTrue(SchoolUrlPolicy.isAllowedNavigation(LIBRARY_SEARCH_URL))
        assertTrue(SchoolUrlPolicy.isAllowedNavigation("https://lib.dima.ac.kr/sso/main.aspx"))
        assertFalse(SchoolUrlPolicy.isAllowedNavigation("http://lib.dima.ac.kr/MyLibrary"))
        assertFalse(SchoolUrlPolicy.isAllowedNavigation("https://lib.dima.ac.kr.evil.example/MyLibrary"))
        assertEquals("대출 현황을", withObjectParticle("대출 현황"))
    }

    @Test
    fun `the page script takes the header counts, never the name beside them`() {
        assertTrue(LIBRARY_LOANS_SCRIPT.contains("#mylibrary_tab_01"))
        assertFalse(LIBRARY_LOANS_SCRIPT.contains("님"))
        assertFalse(SCRIPT_ANSWER.contains("홍길동"))
    }

    private companion object {
        /** The script's `data` on a synthetic page whose header read `연체중 1건, 대출중 2건 홍길동님!`. */
        const val SCRIPT_ANSWER = """{"overdue":1,"loans":2,"table":{"headers":["번호","서명 / 저자","등록번호","대출일","반납예정일","연기신청","상태"],"rows":[{"cells":["1","영상 문법의 이해 / 김작가\nDIMA 출판","EM0001","2026.10.10","2026.10.24","연기불가","연체"],"actions":[false,true,false,false,false,false,false]},{"cells":["2","사운드 디자인 입문 / 이작가","EM0002","2026-10-20","2026-11-03","연기신청","대출중"],"actions":[false,true,false,false,false,true,false]}]},"noData":false,"hours":["리더스스테이션","09:00 ~ 20:00","러닝라운지 09:00 ~ 20:00","자유열람실","24시간 운영중","더보기"]}"""
    }
}
