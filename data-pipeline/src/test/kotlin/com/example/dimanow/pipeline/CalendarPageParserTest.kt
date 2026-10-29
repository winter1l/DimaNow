package com.example.dimanow.pipeline

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarPageParserTest {
    @Test
    fun `the 2026 page reads its year from the heading and every row from the month lists`() {
        val page = CalendarPageParser.parse(fixture("academic_calendar_2026_page.html"))

        assertEquals(2026, page.year)
        assertEquals(emptyList<UnreadableCalendarRow>(), page.unreadable)
        assertEquals(75, page.rows.size)
        assertEquals((1..12).toList(), page.rows.mapNotNull { it.month }.distinct())
        assertEquals(
            CalendarPageRow(1, "01.06 10시 ~ 01.06 24시", "계절학기 등록", null, LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 6)),
            page.rows.first(),
        )
        // A row's note sits on its own line under the name.
        val seasonal = page.rows.single { it.dateText == "01.08 ~ 01.23" }
        assertEquals("계절학기 수업", seasonal.name)
        assertEquals("12일간 수업", seasonal.note)
        // A time without a range is a single day.
        val ceremony = page.rows.first { it.name == "2026학년도 입학식" }
        assertEquals(LocalDate.of(2026, 2, 27), ceremony.start)
        assertEquals(ceremony.start, ceremony.end)
        // The 2026 page prints one end date with 2025; it is read as the row's own year and reported.
        val grades = page.rows.single { it.name == "1학기 성적 열람 및 성적 정정" }
        assertEquals("07.01 10시 ~ 2025.07.03 12시", grades.dateText)
        assertEquals(LocalDate.of(2026, 7, 1), grades.start)
        assertEquals(LocalDate.of(2026, 7, 3), grades.end)
        assertEquals("2025 → 2026", grades.yearCorrection)
        assertEquals(listOf(grades), page.rows.filter { it.yearCorrection != null })
        // Rows listed after the month's dated rows (the June 보강 days) keep their own dates.
        val makeUp = page.rows.single { it.name == "5. 5(화) 어린이날 보강" }
        assertEquals(6, makeUp.month)
        assertEquals(LocalDate.of(2026, 6, 9), makeUp.start)
    }

    @Test
    fun `the 2027 view already exists with only seasonal rows`() {
        val page = CalendarPageParser.parse(fixture("academic_calendar_2027_partial_page.html"))

        assertEquals(2027, page.year)
        assertEquals(
            listOf("계절학기 수강 신청", "계절학기 등록", "계절학기 수업", "계절학기 성적 열람 및 성적 정정", "2026학년도 전기 학위수여식(제29회)"),
            page.rows.map { it.name },
        )
        assertEquals(LocalDate.of(2027, 1, 4), page.rows.first().start)
        assertEquals(LocalDate.of(2027, 1, 5), page.rows.first().end)
    }

    @Test
    fun `several ranges, wrapped names, year crossings and unreadable dates`() {
        val page = CalendarPageParser.parse(
            page(
                2027,
                row("02.02 ~ 02.13<br>02.23&nbsp;10시 ~ 02.27&nbsp;17시", "<span>1학기\n  일반 휴학</span><span>(1차)</span>", "<b>  두 번\n 받아요 </b>"),
                row("12.28 ~ 01.05", "<span>동계 계절학기</span>"),
                row("2052.03.04", "<span>오타 연도</span>"),
                row("2026.12.30 ~ 01.02", "<span>지난해부터</span>"),
                row("추후 공지", "<span>날짜 없음</span>"),
                row("02.30", "<span>없는 날짜</span>"),
                row("03.10 ~ 03.02", "<span>거꾸로</span>"),
            ),
        )

        assertEquals(2027, page.year)
        val (first, second) = page.rows.filter { it.name == "1학기 일반 휴학 (1차)" }
        assertEquals("두 번 받아요", first.note)
        assertEquals(LocalDate.of(2027, 2, 2) to LocalDate.of(2027, 2, 13), first.start to first.end)
        assertEquals(LocalDate.of(2027, 2, 23) to LocalDate.of(2027, 2, 27), second.start to second.end)
        val winter = page.rows.single { it.name == "동계 계절학기" }
        assertEquals(LocalDate.of(2027, 12, 28) to LocalDate.of(2028, 1, 5), winter.start to winter.end)
        assertNull(winter.yearCorrection)
        val typo = page.rows.single { it.name == "오타 연도" }
        assertEquals(LocalDate.of(2027, 3, 4), typo.start)
        assertEquals("2052 → 2027", typo.yearCorrection)
        val previous = page.rows.single { it.name == "지난해부터" }
        assertEquals(LocalDate.of(2026, 12, 30) to LocalDate.of(2027, 1, 2), previous.start to previous.end)
        assertEquals(
            listOf(
                UnreadableCalendarRow("추후 공지", "날짜 없음"),
                UnreadableCalendarRow("02.30", "없는 날짜"),
                UnreadableCalendarRow("03.10 ~ 03.02", "거꾸로"),
            ),
            page.unreadable,
        )
    }

    @Test
    fun `a page without the schedule or its year heading fails instead of reading nothing`() {
        assertThrows(IllegalArgumentException::class.java) { CalendarPageParser.parse("<html><body><h3>학사일정</h3></body></html>") }
        assertThrows(IllegalArgumentException::class.java) {
            CalendarPageParser.parse("""<div class="schedule"><div class="year_select"><p>학사일정</p></div></div>""")
        }
        assertTrue(CalendarPageParser.parse(page(2028)).rows.isEmpty())
    }

    private fun page(year: Int, vararg rows: String) = """
        <html><head><title>DIMA | 대학생활 | 학사일정</title></head><body>
        <div class="schedule">
          <div class="year_select"><p>${year}년도 학사일정</p></div>
          <div class="month_cont"><div class="left"><p><a name="01">1월</a></p></div>
            <div class="right"><div class="schedule_cont"><ul class="txt">${rows.joinToString("")}</ul></div></div>
          </div>
        </div></body></html>
    """.trimIndent()

    private fun row(date: String, name: String, note: String = "") = "<li><p class=\"date\">$date</p><div>$name$note</div></li>"

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/fixtures/$name")).readText()
}
