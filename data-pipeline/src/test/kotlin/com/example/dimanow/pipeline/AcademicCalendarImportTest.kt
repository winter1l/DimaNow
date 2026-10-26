package com.example.dimanow.pipeline

import com.example.dimanow.sync.CalendarEvent
import com.example.dimanow.sync.CalendarPeriod
import com.example.dimanow.sync.CalendarTerm
import com.example.dimanow.sync.ServiceCalendarPayload
import com.example.dimanow.sync.serviceCalendarProblem
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicCalendarImportTest {
    private val calendarText = Files.readString(projectRoot().resolve("data-source/calendar.json"))
    private val calendar = Json.decodeFromString<ServiceCalendarPayload>(calendarText)
    private val page2026 = fixture("academic_calendar_2026_page.html")

    @Test
    fun `the 2026 page reproduces the published 2026 terms and exam weeks`() {
        val imported = AcademicCalendarMapper.map(CalendarPageParser.parse(page2026))

        assertEquals(2026, imported.year)
        assertEquals(calendar.terms, imported.terms)
        // The public page has no 종강 row; the intranet's 종강 (D-106) is the last 기말시험 day in both terms.
        assertEquals(
            listOf(
                "1학기 종강 행이 없어 기말시험 마지막 날(2026-06-22)을 학기 끝으로 썼어요.",
                "2학기 종강 행이 없어 기말시험 마지막 날(2026-12-18)을 학기 끝으로 썼어요.",
            ),
            imported.notes,
        )
    }

    @Test
    fun `the 2026 page gives the curated academic events with two known differences`() {
        val imported = AcademicCalendarMapper.map(CalendarPageParser.parse(page2026))
        val curated = calendar.academicEvents.toSet()
        val parsed = imported.events.toSet()

        assertEquals(imported.events.size, parsed.size)
        assertEquals(22, imported.events.size)
        // The page lists 2학기 등록 from 08.17 (a 대체공휴일); the curated file starts it on 08-18.
        assertEquals(setOf(CalendarEvent("등록금 납부", "2026-08-18", "2026-08-21")), curated - parsed)
        // The page's 2학기 1차/2차 수강 신청 (08.18 ~ 08.21) has no curated entry.
        assertEquals(
            setOf(
                CalendarEvent("등록금 납부", "2026-08-17", "2026-08-21"),
                CalendarEvent("수강신청", "2026-08-18", "2026-08-21"),
            ),
            parsed - curated,
        )
        // 1차 (02.23 ~ 02.24), 2차 (02.26 ~ 02.27) and 신입생 (02.27) 수강 신청 become one period.
        assertTrue(CalendarEvent("수강신청", "2026-02-23", "2026-02-27") in parsed)
        assertEquals(imported.events.sortedWith(compareBy({ it.start }, { it.end }, { it.name })), imported.events)
    }

    @Test
    fun `rows the import does not use are reported, seasonal and cart rows by rule`() {
        val imported = AcademicCalendarMapper.map(CalendarPageParser.parse(page2026))
        val unmapped = imported.unused.filterNot { it.excluded }.map { it.row.name }
        val excluded = imported.unused.filter { it.excluded }

        assertEquals(33, unmapped.size)
        assertTrue(unmapped.containsAll(listOf("1학기 강의계획서 입력", "2026학년도 입학식", "하계방학", "5. 5(화) 어린이날 보강", "입시기간")))
        assertEquals(11, excluded.size)
        assertEquals(setOf("계절학기", "수업소감(중간 수업평가)", "예비수강(장바구니)"), excluded.map { it.reason }.toSet())
        // 75 rows: 6 term rows, 25 event rows joined into 22 events, 44 unused.
        assertEquals(44, imported.unused.size)
        assertEquals(listOf("07.01 10시 ~ 2025.07.03 12시"), imported.corrected.map { it.dateText })
        assertTrue(imported.unreadable.isEmpty())
    }

    @Test
    fun `a newer year is added beside the existing years without changing them`() {
        val imported = AcademicCalendarMapper.map(CalendarPageParser.parse(page2026As(2027)))

        val candidate = AcademicCalendarMerger.merge(calendar, imported) as CalendarImportResult.Candidate
        val merged = candidate.payload

        assertNull(serviceCalendarProblem(merged))
        assertEquals(listOf("2026-1", "2026-2", "2027-1", "2027-2"), merged.terms.map { it.id })
        assertEquals(calendar.terms, merged.terms.take(2))
        assertEquals(
            CalendarTerm("2027-1", "2027-03-03", "2027-06-22", CalendarPeriod("2027-04-21", "2027-04-27"), CalendarPeriod("2027-06-16", "2027-06-22")),
            merged.terms[2],
        )
        assertEquals(calendar.holidays, merged.holidays)
        assertEquals(calendar.campusShuttleSuspensions, merged.campusShuttleSuspensions)
        assertEquals(calendar.academicEvents, merged.academicEvents.take(calendar.academicEvents.size))
        assertEquals(calendar.academicEvents.size + 22, merged.academicEvents.size)
        assertTrue(merged.academicEvents.drop(calendar.academicEvents.size).all { it.start.startsWith("2027-") })
        // The other-year label is corrected to the page year whatever year the page is.
        assertEquals("2025 → 2027", imported.corrected.single().yearCorrection)
    }

    @Test
    fun `merging is idempotent and never rewrites a year that already has terms`() {
        val imported2027 = AcademicCalendarMapper.map(CalendarPageParser.parse(page2026As(2027)))
        val once = (AcademicCalendarMerger.merge(calendar, imported2027) as CalendarImportResult.Candidate).payload

        assertTrue(AcademicCalendarMerger.merge(once, imported2027) is CalendarImportResult.NothingToDo)
        // The live 2026 page differs from the curated 2026 events, but 2026 already has terms: nothing changes.
        val imported2026 = AcademicCalendarMapper.map(CalendarPageParser.parse(page2026))
        assertEquals(
            CalendarImportResult.NothingToDo("2026년 페이지가 calendar.json의 마지막 학기 연도(2026)보다 새롭지 않아요."),
            AcademicCalendarMerger.merge(calendar, imported2026),
        )
        // An older page never reaches back either.
        val imported2025 = AcademicCalendarMapper.map(CalendarPageParser.parse(page2026As(2025)))
        assertTrue(AcademicCalendarMerger.merge(calendar, imported2025) is CalendarImportResult.NothingToDo)
    }

    @Test
    fun `a hand-added event of the new year is kept and not duplicated`() {
        val handAdded = CalendarEvent("복학 신청", "2027-02-02", "2027-02-27")
        val existing = calendar.copy(academicEvents = calendar.academicEvents + handAdded)
        val imported = AcademicCalendarMapper.map(CalendarPageParser.parse(page2026As(2027)))

        val merged = (AcademicCalendarMerger.merge(existing, imported) as CalendarImportResult.Candidate).payload

        assertNull(serviceCalendarProblem(merged))
        assertEquals(existing.academicEvents, merged.academicEvents.filter { it in existing.academicEvents })
        assertEquals(1, merged.academicEvents.count { it == handAdded })
        assertEquals(existing.academicEvents.size + 21, merged.academicEvents.size)
    }

    @Test
    fun `a newer year without a 개강 row is not proposed`() {
        val imported = AcademicCalendarMapper.map(CalendarPageParser.parse(fixture("academic_calendar_2027_partial_page.html")))

        assertEquals(2027, imported.year)
        assertTrue(imported.terms.isEmpty())
        assertTrue(imported.events.isEmpty())
        assertEquals(
            CalendarImportResult.NothingToDo("2027년 페이지에 아직 개강 일정이 없어요."),
            AcademicCalendarMerger.merge(calendar, imported),
        )
    }

    @Test
    fun `the candidate keeps the hand-kept layout so its diff is only additions`() {
        assertEquals(calendarText.replace("\r\n", "\n"), CalendarJsonFormat.format(calendar))

        val imported = AcademicCalendarMapper.map(CalendarPageParser.parse(page2026As(2027)))
        val merged = (AcademicCalendarMerger.merge(calendar, imported) as CalendarImportResult.Candidate).payload
        val text = CalendarJsonFormat.format(merged)
        assertEquals(merged, Json.decodeFromString<ServiceCalendarPayload>(text))
        // Every original line is still there in order; at most a trailing comma is added (last term, last event).
        val original = calendarText.replace("\r\n", "\n").lines().map { it.removeSuffix(",") }
        val candidateLines = text.lines().map { it.removeSuffix(",") }
        var next = 0
        for (line in original) {
            next = candidateLines.subList(next, candidateLines.size).indexOf(line).also { assertTrue(line, it >= 0) } + next + 1
        }
        assertEquals(original.size + 2 * 5 + 22, candidateLines.size) // two 5-line terms, 22 event lines
    }

    @Test
    fun `the import command reads the next year's page and writes the candidate and its summary`() {
        val directory = Files.createTempDirectory("calendar-import")
        val source = directory.resolve("calendar.json").also { Files.writeString(it, calendarText) }
        val out = directory.resolve("out/calendar.json")
        val requested = mutableListOf<String>()

        val result = CalendarImportCommand { url -> requested += url; page2026As(2027) }.run(source, out)

        assertEquals(listOf("https://www.dima.ac.kr/?p=78&nyear=2027"), requested)
        assertTrue(result is CalendarImportResult.Candidate)
        assertEquals((result as CalendarImportResult.Candidate).payload, Json.decodeFromString<ServiceCalendarPayload>(Files.readString(out)))
        val summary = Files.readString(directory.resolve("out/calendar.summary.md"))
        assertTrue(summary.contains("## 2027 학사일정 가져오기 후보"))
        assertTrue(summary.contains("| 2027-1 | 2027-03-03 | 2027-06-22 | 2027-04-21 ~ 2027-04-27 | 2027-06-16 ~ 2027-06-22 |"))
        assertTrue(summary.contains("### 학사 일정 22건"))
        assertTrue(summary.contains("### 규칙에 맞지 않아 가져오지 않은 행 33건"))
        assertTrue(summary.contains("연도 표기를 고쳐 읽은 행: `07.01 10시 ~ 2025.07.03 12시` `1학기 성적 열람 및 성적 정정` (2025 → 2027)"))
        assertEquals(calendarText, Files.readString(source))
    }

    @Test
    fun `the import command writes nothing when the page has no newer terms`() {
        val directory = Files.createTempDirectory("calendar-import")
        val source = directory.resolve("calendar.json").also { Files.writeString(it, calendarText) }
        val out = directory.resolve("calendar-candidate.json")

        val partial = CalendarImportCommand { fixture("academic_calendar_2027_partial_page.html") }.run(source, out)
        // The site may ignore nyear and answer with the current year.
        val ignored = CalendarImportCommand { page2026 }.run(source, out)

        assertTrue(partial is CalendarImportResult.NothingToDo)
        assertEquals(CalendarImportResult.NothingToDo("2027년을 요청했지만 2026년 페이지가 왔어요."), ignored)
        assertFalse(Files.exists(out))
        assertFalse(Files.exists(directory.resolve("calendar-candidate.summary.md")))
    }

    @Test
    fun `page text reaches the summary only as inline code`() {
        val hostile = "@owner `rm` [x](https://evil.example) <img>"
        val imported = AcademicCalendarMapper.map(
            CalendarPageParser.parse(page2026As(2027).replace("입시기간", hostile.replace("<", "&lt;").replace(">", "&gt;"))),
        )
        val candidate = AcademicCalendarMerger.merge(calendar, imported) as CalendarImportResult.Candidate

        val summary = CalendarImportSummary.markdown(candidate, "https://www.dima.ac.kr/?p=78&nyear=2027")

        assertTrue(summary.contains("`@owner rm [x](https://evil.example) <img>` — 규칙 없음"))
        assertFalse(summary.contains("`rm`"))
    }

    @Test
    fun `the calendar page is fetched only through the hardened client's allowlist`() {
        val publicAddress = { _: String -> arrayOf(InetAddress.getByName("8.8.8.8")) }
        for (allowed in listOf("https://www.dima.ac.kr/?p=78", "https://www.dima.ac.kr/?p=78&nyear=2027", "https://dima.ac.kr/?p=78&nyear=2030")) {
            assertEquals(allowed, MealRemoteUrlPolicy.requireAllowed(allowed, MealRemoteEndpoint.DIMA_ACADEMIC_CALENDAR, publicAddress).toString())
        }
        for (rejected in listOf(
            "http://www.dima.ac.kr/?p=78",
            "https://www.dima.ac.kr/?p=79",
            "https://www.dima.ac.kr/?p=78&nyear=2027&nmonth=3",
            "https://www.dima.ac.kr/index.php?p=78",
            "https://www.dima.ac.kr.evil.example/?p=78",
            "https://www.dima.ac.kr:8443/?p=78",
        )) {
            assertThrows(rejected, IllegalArgumentException::class.java) {
                MealRemoteUrlPolicy.requireAllowed(rejected, MealRemoteEndpoint.DIMA_ACADEMIC_CALENDAR, publicAddress)
            }
        }
        val failure = assertThrows(IllegalArgumentException::class.java) {
            MealRemoteUrlPolicy.requireAllowed("https://www.dima.ac.kr/?p=79", MealRemoteEndpoint.DIMA_ACADEMIC_CALENDAR, publicAddress)
        }
        assertEquals("허용되지 않은 학사일정 원격 URL입니다.", failure.message)
        assertThrows(IllegalArgumentException::class.java) {
            MealRemoteUrlPolicy.requireAllowed("https://www.dima.ac.kr/?p=78", MealRemoteEndpoint.DIMA_ACADEMIC_CALENDAR) {
                arrayOf(InetAddress.getByName("10.0.0.1"))
            }
        }
    }

    /** The real 2026 page relabelled as another year: the same markup and rows, read as that year. */
    private fun page2026As(year: Int) = page2026.replace("2026년도 학사일정", "${year}년도 학사일정")

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/fixtures/$name")).readText()

    private fun projectRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        repeat(4) {
            if (Files.exists(current.resolve("data-source/calendar.json"))) return current
            current = current.parent ?: return@repeat
        }
        error("프로젝트 루트의 data-source/calendar.json을 찾지 못했습니다.")
    }
}
