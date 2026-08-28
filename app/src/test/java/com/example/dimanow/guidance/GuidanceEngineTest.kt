package com.example.dimanow.guidance

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.CountdownMeaning
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.GuidancePause
import com.example.dimanow.domain.ShuttleDeparture
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidanceEngineTest {
    @Test
    fun `guidance paused until disabled suppresses a later class without a fixed end date`() {
        val course = Course(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0), "조명기초및실습", "덕성관 402", "이용창", CampusZoneId.MAIN)
        val pause = GuidancePause.untilDisabled(LocalDate.of(2026, 8, 27))

        val snapshot = GuidanceEngine().snapshot(
            ZonedDateTime.of(2026, 9, 7, 9, 30, 0, 0, ZoneId.of("Asia/Seoul")),
            LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 18), listOf(course), emptySet(),
            CampusZoneId.YEIN, true, guidancePause = pause,
        )

        assertEquals(true, pause.isUntilDisabled)
        assertEquals(GuidancePhase.NONE, snapshot.phase)
    }

    @Test
    fun `guidance pause range suppresses class dependent guidance and resumes the next day`() {
        val course = Course(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0), "조명기초및실습", "덕성관 402", "이용창", CampusZoneId.MAIN)
        val pause = GuidancePause(LocalDate.of(2026, 8, 30), LocalDate.of(2026, 8, 31))
        val paused = GuidanceEngine().snapshot(
            ZonedDateTime.of(2026, 8, 31, 9, 30, 0, 0, ZoneId.of("Asia/Seoul")),
            LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 18), listOf(course), emptySet(),
            CampusZoneId.YEIN, true, guidancePause = pause,
        )
        val resumed = GuidanceEngine().snapshot(
            ZonedDateTime.of(2026, 9, 7, 9, 30, 0, 0, ZoneId.of("Asia/Seoul")),
            LocalDate.of(2026, 8, 24), LocalDate.of(2026, 12, 18), listOf(course), emptySet(),
            CampusZoneId.YEIN, true, guidancePause = pause,
        )

        assertEquals(GuidancePhase.NONE, paused.phase)
        assertEquals(GuidancePhase.BEFORE_CLASS, resumed.phase)
    }
    @Test
    fun `general shuttle board keeps simultaneous destinations and rounds remaining minutes up`() {
        val now = ZonedDateTime.of(2026, 9, 1, 18, 50, 1, 0, ZoneId.of("Asia/Seoul"))
        val departures = listOf(
            ShuttleDeparture("A", "main-a", "TO_ONE_ROOM", DayOfWeek.TUESDAY, LocalTime.of(18, 55), CampusZoneId.MAIN, CampusZoneId.ONE_ROOM),
            ShuttleDeparture("B", "main-b", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(18, 55), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("B-variant", "main-b2", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(18, 55), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("B", "main-b", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 20), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )

        val board = GuidanceEngine().shuttleBoard(
            now = now,
            originZone = CampusZoneId.MAIN,
            departures = departures,
            purpose = ShuttleBoardPurpose.GENERAL,
        )

        assertEquals(listOf(CampusZoneId.YEIN, CampusZoneId.ONE_ROOM), board.rows.map { it.destinationZone })
        assertEquals(listOf(5L, 30L), board.rows[0].departures.map { it.remainingMinutes })
        assertEquals(listOf(5L), board.rows[1].departures.map { it.remainingMinutes })
    }

    @Test
    fun `prepared shuttle schedule keeps literal board behavior without rebuilding raw rows`() {
        val now = ZonedDateTime.of(2026, 9, 1, 18, 50, 1, 0, ZoneId.of("Asia/Seoul"))
        val raw = listOf(
            ShuttleDeparture("route-late", "main", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 20), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("route-first", "main", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(18, 55), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("route-variant", "main", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(18, 55), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("one-room", "main", "TO_ONE_ROOM", DayOfWeek.TUESDAY, LocalTime.of(19, 5), CampusZoneId.MAIN, CampusZoneId.ONE_ROOM),
        )
        val engine = GuidanceEngine()

        val index = engine.prepareShuttleSchedule(raw)
        val board = engine.shuttleBoard(
            now = now,
            originZone = CampusZoneId.MAIN,
            index = index,
            purpose = ShuttleBoardPurpose.GENERAL,
        )

        assertEquals(listOf(CampusZoneId.YEIN, CampusZoneId.ONE_ROOM), board.rows.map { it.destinationZone })
        assertEquals(listOf(5L, 30L), board.rows[0].departures.map { it.remainingMinutes })
        assertEquals(listOf("route-first", "route-late"), board.rows[0].departures.map { it.departure.sourceRouteId })
        assertEquals(4, raw.size)
    }

    @Test
    fun `next departures preserve raw routes but return unique destination times for display`() {
        val now = ZonedDateTime.of(2026, 9, 1, 18, 50, 0, 0, ZoneId.of("Asia/Seoul"))
        val departures = listOf(
            ShuttleDeparture("to-one-room", "official-main", "TO_ONE_ROOM", DayOfWeek.TUESDAY, LocalTime.of(18, 55), CampusZoneId.MAIN, CampusZoneId.ONE_ROOM),
            ShuttleDeparture("route-a-evening", "official-stadium-a", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 0), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("route-b-evening", "official-stadium-b", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 0), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("route-b", "official-main", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 30), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )

        val selected = GuidanceEngine().nextDepartures(
            now = now,
            originZone = CampusZoneId.MAIN,
            destinationZone = CampusZoneId.YEIN,
            departures = departures,
        )

        assertEquals(listOf("route-a-evening", "route-b"), selected.map { it.sourceRouteId })
        assertEquals(4, departures.size)
    }

    @Test
    fun `full timetable marks the first official stadium departure as the stop transition`() {
        val departures = listOf(
            ShuttleDeparture("B", "university-headquarters", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(18, 30), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 0), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 30), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )

        val annotated = GuidanceEngine().annotatedServiceDepartures(
            serviceDay = DayOfWeek.TUESDAY,
            originZone = CampusZoneId.MAIN,
            destinationZone = CampusZoneId.YEIN,
            departures = departures,
        )

        assertEquals(
            listOf("18:30 (첫차)", "19:00 · 운동장 전환", "19:30 (막차) · 운동장"),
            annotated.map { it.displayText },
        )
    }

    @Test
    fun `class guidance starts exactly sixty minutes before class`() {
        val now = ZonedDateTime.of(2026, 8, 31, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val course = Course(
            weekday = DayOfWeek.MONDAY,
            start = LocalTime.of(10, 0),
            end = LocalTime.of(12, 50),
            name = "조명기초및실습",
            room = "덕성관 402",
            professor = "이용창",
            zone = CampusZoneId.MAIN,
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = listOf(course),
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.MAIN,
            automaticClassGuidance = true,
        )

        assertEquals(GuidancePhase.BEFORE_CLASS, snapshot.phase)
        assertEquals("10:00 · 조명기초및실습", snapshot.classContent?.title)
        assertEquals("시작까지 60분 · 덕성관 402", snapshot.classContent?.detail)
        assertEquals(emptyList<String>(), snapshot.shuttleLines.map { it.text })
    }

    @Test
    fun `before a main class from yein shows two remaining shuttle times`() {
        val now = ZonedDateTime.of(2026, 8, 31, 9, 18, 0, 0, ZoneId.of("Asia/Seoul"))
        val course = Course(
            weekday = DayOfWeek.MONDAY,
            start = LocalTime.of(10, 0),
            end = LocalTime.of(12, 50),
            name = "조명기초및실습",
            room = "덕성관 402",
            professor = "이용창",
            zone = CampusZoneId.MAIN,
        )
        val departures = listOf(
            ShuttleDeparture("route-a", "official-yein", "TO_MAIN", DayOfWeek.MONDAY, LocalTime.of(9, 23), CampusZoneId.YEIN, CampusZoneId.MAIN),
            ShuttleDeparture("route-a", "official-yein", "TO_MAIN", DayOfWeek.MONDAY, LocalTime.of(9, 48), CampusZoneId.YEIN, CampusZoneId.MAIN),
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = listOf(course),
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.YEIN,
            automaticClassGuidance = true,
            shuttleDepartures = departures,
        )

        assertEquals(GuidancePhase.BEFORE_CLASS, snapshot.phase)
        assertEquals("10:00 · 조명기초및실습", snapshot.classContent?.title)
        assertEquals("시작까지 42분 · 덕성관 402", snapshot.classContent?.detail)
        assertEquals(listOf("엔터관  5분, 30분"), snapshot.shuttleLines.map { it.text })
    }

    @Test
    fun `class commute selects the class destination and collapses same-time route variants`() {
        val now = ZonedDateTime.of(2026, 8, 31, 9, 18, 0, 0, ZoneId.of("Asia/Seoul"))
        val course = Course(
            weekday = DayOfWeek.MONDAY,
            start = LocalTime.of(10, 0),
            end = LocalTime.of(12, 50),
            name = "조명기초및실습",
            room = "덕성관 402",
            professor = "이용창",
            zone = CampusZoneId.MAIN,
        )
        val departures = listOf(
            ShuttleDeparture("wrong-route", "official-yein", "TO_OUTSIDE", DayOfWeek.MONDAY, LocalTime.of(9, 20), CampusZoneId.YEIN, CampusZoneId.OUTSIDE),
            ShuttleDeparture("route-a", "official-yein-a", "TO_MAIN", DayOfWeek.MONDAY, LocalTime.of(9, 23), CampusZoneId.YEIN, CampusZoneId.MAIN),
            ShuttleDeparture("route-b", "official-yein-b", "TO_MAIN", DayOfWeek.MONDAY, LocalTime.of(9, 23), CampusZoneId.YEIN, CampusZoneId.MAIN),
            ShuttleDeparture("route-a", "official-yein", "TO_MAIN", DayOfWeek.MONDAY, LocalTime.of(9, 48), CampusZoneId.YEIN, CampusZoneId.MAIN),
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = listOf(course),
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.YEIN,
            automaticClassGuidance = true,
            shuttleDepartures = departures,
        )

        assertEquals(listOf("엔터관  5분, 30분"), snapshot.shuttleLines.map { it.text })
    }

    @Test
    fun `at class start class context remains without an unreliable end countdown`() {
        val now = ZonedDateTime.of(2026, 8, 31, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val course = Course(
            weekday = DayOfWeek.MONDAY,
            start = LocalTime.of(10, 0),
            end = LocalTime.of(12, 50),
            name = "조명기초및실습",
            room = "덕성관 402",
            professor = "이용창",
            zone = CampusZoneId.MAIN,
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = listOf(course),
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.MAIN,
            automaticClassGuidance = true,
        )

        assertEquals(GuidancePhase.IN_CLASS, snapshot.phase)
        assertEquals("10:00 · 조명기초및실습", snapshot.classContent?.title)
        assertEquals("수업 중 · 덕성관 402", snapshot.classContent?.detail)
        assertNull(snapshot.countdownTarget)
    }

    @Test
    fun `during the first fifteen minutes class context is IN_CLASS`() {
        val now = ZonedDateTime.of(2026, 8, 31, 10, 10, 0, 0, ZoneId.of("Asia/Seoul"))
        val course = Course(
            weekday = DayOfWeek.MONDAY,
            start = LocalTime.of(10, 0),
            end = LocalTime.of(12, 50),
            name = "조명기초및실습",
            room = "덕성관 402",
            professor = "이용창",
            zone = CampusZoneId.MAIN,
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = listOf(course),
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.MAIN,
            automaticClassGuidance = true,
        )

        assertEquals(GuidancePhase.IN_CLASS, snapshot.phase)
        assertEquals("수업 중 · 덕성관 402", snapshot.classContent?.detail)
        assertEquals(null, snapshot.countdownTarget)
        assertEquals(emptyList<String>(), snapshot.shuttleLines.map { it.text })
    }

    @Test
    fun `fifteen minutes after class start in-class guidance ends`() {
        val now = ZonedDateTime.of(2026, 8, 31, 10, 15, 0, 0, ZoneId.of("Asia/Seoul"))
        val course = Course(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 50), "조명기초및실습", "덕성관 402", "이용창", CampusZoneId.MAIN)
        val departures = listOf(
            ShuttleDeparture("A", "main", "TO_ONE_ROOM", DayOfWeek.MONDAY, LocalTime.of(10, 35), CampusZoneId.MAIN, CampusZoneId.ONE_ROOM),
            ShuttleDeparture("B", "main", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(10, 32), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = listOf(course),
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.MAIN,
            automaticClassGuidance = true,
            shuttleDepartures = departures,
            homeBase = HomeBase.ONE_ROOM,
        )

        assertEquals(GuidancePhase.NONE, snapshot.phase)
    }

    @Test
    fun `at class end guidance disappears without an alert`() {
        val now = ZonedDateTime.of(2026, 8, 31, 12, 50, 0, 0, ZoneId.of("Asia/Seoul"))
        val course = Course(
            weekday = DayOfWeek.MONDAY,
            start = LocalTime.of(10, 0),
            end = LocalTime.of(12, 50),
            name = "조명기초및실습",
            room = "덕성관 402",
            professor = "이용창",
            zone = CampusZoneId.MAIN,
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = listOf(course),
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.MAIN,
            automaticClassGuidance = true,
        )

        assertEquals(GuidancePhase.NONE, snapshot.phase)
        assertEquals(null, snapshot.classContent)
        assertEquals(false, snapshot.shouldAlert)
    }

    @Test
    fun `after a tuesday class the next adjacent class becomes active`() {
        val now = ZonedDateTime.of(2026, 9, 1, 12, 15, 0, 0, ZoneId.of("Asia/Seoul"))
        val courses = listOf(
            Course(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(11, 50), "스튜디오기초실습", "기예관 122", "이상운", CampusZoneId.MAIN),
            Course(DayOfWeek.TUESDAY, LocalTime.of(13, 0), LocalTime.of(15, 50), "카메라기초및실습", "덕성관 210", "김재호", CampusZoneId.MAIN),
            Course(DayOfWeek.TUESDAY, LocalTime.of(16, 0), LocalTime.of(18, 50), "음향기초실습", "덕성관 303", "이화현", CampusZoneId.MAIN),
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = courses,
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.MAIN,
            automaticClassGuidance = true,
        )

        assertEquals(GuidancePhase.BEFORE_CLASS, snapshot.phase)
        assertEquals("13:00 · 카메라기초및실습", snapshot.classContent?.title)
        assertEquals("시작까지 45분 · 덕성관 210", snapshot.classContent?.detail)
    }

    @Test
    fun `after final class at main guidance becomes shuttle only`() {
        val now = ZonedDateTime.of(2026, 9, 1, 19, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val courses = listOf(
            Course(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(11, 50), "스튜디오기초실습", "기예관 122", "이상운", CampusZoneId.MAIN),
            Course(DayOfWeek.TUESDAY, LocalTime.of(13, 0), LocalTime.of(15, 50), "카메라기초및실습", "덕성관 210", "김재호", CampusZoneId.MAIN),
            Course(DayOfWeek.TUESDAY, LocalTime.of(16, 0), LocalTime.of(18, 50), "음향기초실습", "덕성관 303", "이화현", CampusZoneId.MAIN),
        )
        val departures = listOf(
            ShuttleDeparture("route-b", "official-main-hq", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 5), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("route-b", "official-stadium", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 30), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = courses,
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.MAIN,
            automaticClassGuidance = true,
            shuttleDepartures = departures,
        )

        assertEquals(GuidancePhase.RETURN, snapshot.phase)
        assertEquals(null, snapshot.classContent)
        assertEquals(listOf("본관  5분, 30분"), snapshot.shuttleLines.map { it.text })
    }

    @Test
    fun `after the official main stop changes the now bar uses stadium`() {
        val now = ZonedDateTime.of(2026, 9, 1, 19, 30, 0, 0, ZoneId.of("Asia/Seoul"))
        val courses = listOf(
            Course(DayOfWeek.TUESDAY, LocalTime.of(16, 0), LocalTime.of(19, 20), "음향기초실습", "덕성관 303", "이화현", CampusZoneId.MAIN),
        )
        val departures = listOf(
            ShuttleDeparture("B", "university-headquarters", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 20), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 35), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(20, 0), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = courses,
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.MAIN,
            automaticClassGuidance = true,
            shuttleDepartures = departures,
        )

        assertEquals(listOf("운동장  5분, 30분"), snapshot.shuttleLines.map { it.text })
    }

    @Test
    fun `after final class return continues toward the selected one room home base`() {
        val now = ZonedDateTime.of(2026, 8, 31, 12, 50, 0, 0, ZoneId.of("Asia/Seoul"))
        val course = Course(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 50), "조명기초및실습", "덕성관 402", "이용창", CampusZoneId.MAIN)
        val departures = listOf(
            ShuttleDeparture("A", "main", "TO_ONE_ROOM", DayOfWeek.MONDAY, LocalTime.of(12, 55), CampusZoneId.MAIN, CampusZoneId.ONE_ROOM),
            ShuttleDeparture("A", "main", "TO_ONE_ROOM", DayOfWeek.MONDAY, LocalTime.of(13, 20), CampusZoneId.MAIN, CampusZoneId.ONE_ROOM),
            ShuttleDeparture("B", "main", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(12, 52), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = listOf(course),
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.MAIN,
            automaticClassGuidance = true,
            shuttleDepartures = departures,
            homeBase = HomeBase.ONE_ROOM,
        )

        assertEquals(GuidancePhase.RETURN, snapshot.phase)
        assertEquals(null, snapshot.classContent)
        assertEquals(listOf("본관  5분, 30분"), snapshot.shuttleLines.map { it.text })
        assertEquals(now.plusMinutes(5).toInstant(), snapshot.countdownTarget)
    }

    @Test
    fun `main return ignores other destinations and collapses route variants at the same time`() {
        val now = ZonedDateTime.of(2026, 9, 1, 18, 50, 0, 0, ZoneId.of("Asia/Seoul"))
        val courses = listOf(
            Course(DayOfWeek.TUESDAY, LocalTime.of(16, 0), LocalTime.of(18, 50), "음향기초실습", "덕성관 303", "이화현", CampusZoneId.MAIN),
        )
        val departures = listOf(
            ShuttleDeparture("route-a", "official-main-hq", "TO_ONE_ROOM", DayOfWeek.TUESDAY, LocalTime.of(18, 55), CampusZoneId.MAIN, CampusZoneId.ONE_ROOM),
            ShuttleDeparture("route-b", "official-main-hq", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(18, 55), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("route-a-evening", "official-stadium-a", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 0), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("route-b-evening", "official-stadium-b", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 0), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("route-b", "official-main-hq", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 30), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = courses,
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.MAIN,
            automaticClassGuidance = true,
            shuttleDepartures = departures,
        )

        assertEquals(GuidancePhase.RETURN, snapshot.phase)
        assertEquals(listOf("본관  5분, 10분"), snapshot.shuttleLines.map { it.text })
    }

    @Test
    fun `one room return excludes a main connection that leaves before arrival`() {
        val now = ZonedDateTime.of(2026, 9, 1, 19, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val courses = listOf(
            Course(DayOfWeek.TUESDAY, LocalTime.of(16, 0), LocalTime.of(18, 50), "음향기초실습", "덕성관 303", "이화현", CampusZoneId.MAIN),
        )
        val departures = listOf(
            ShuttleDeparture("route-c", "official-one-room", "TO_MAIN", DayOfWeek.TUESDAY, LocalTime.of(19, 5), CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, LocalTime.of(19, 15)),
            ShuttleDeparture("route-c", "official-one-room", "TO_MAIN", DayOfWeek.TUESDAY, LocalTime.of(19, 30), CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, LocalTime.of(19, 40)),
            ShuttleDeparture("route-b", "official-main-hq", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 12), CampusZoneId.MAIN, CampusZoneId.YEIN, LocalTime.of(19, 25)),
            ShuttleDeparture("route-b", "official-main-hq", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 18), CampusZoneId.MAIN, CampusZoneId.YEIN, LocalTime.of(19, 31)),
            ShuttleDeparture("route-b", "official-stadium", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 48), CampusZoneId.MAIN, CampusZoneId.YEIN, LocalTime.of(20, 1)),
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = courses,
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.ONE_ROOM,
            automaticClassGuidance = true,
            shuttleDepartures = departures,
        )

        assertEquals(GuidancePhase.RETURN, snapshot.phase)
        assertEquals(null, snapshot.classContent)
        assertEquals(
            listOf("원룸촌  5분, 30분", "본관  18분, 48분"),
            snapshot.shuttleLines.map { it.text },
        )
    }

    @Test
    fun `two leg now bar uses stadium when every reachable main connection boards there`() {
        val now = ZonedDateTime.of(2026, 9, 1, 19, 30, 0, 0, ZoneId.of("Asia/Seoul"))
        val courses = listOf(
            Course(DayOfWeek.TUESDAY, LocalTime.of(16, 0), LocalTime.of(19, 20), "음향기초실습", "덕성관 303", "이화현", CampusZoneId.MAIN),
        )
        val departures = listOf(
            ShuttleDeparture("C", "one-room", "TO_MAIN", DayOfWeek.TUESDAY, LocalTime.of(19, 35), CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, LocalTime.of(19, 45)),
            ShuttleDeparture("C", "one-room", "TO_MAIN", DayOfWeek.TUESDAY, LocalTime.of(20, 0), CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, LocalTime.of(20, 10)),
            ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 50), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(20, 20), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = courses,
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.ONE_ROOM,
            automaticClassGuidance = true,
            shuttleDepartures = departures,
        )

        assertEquals(
            listOf("원룸촌  5분, 30분", "운동장  20분, 50분"),
            snapshot.shuttleLines.map { it.text },
        )
    }

    @Test
    fun `one room return collapses route variants without accepting an impossible transfer`() {
        val now = ZonedDateTime.of(2026, 9, 1, 19, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val courses = listOf(
            Course(DayOfWeek.TUESDAY, LocalTime.of(16, 0), LocalTime.of(18, 50), "음향기초실습", "덕성관 303", "이화현", CampusZoneId.MAIN),
        )
        val departures = listOf(
            ShuttleDeparture("route-c", "official-one-room", "TO_MAIN", DayOfWeek.TUESDAY, LocalTime.of(19, 5), CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, LocalTime.of(19, 15)),
            ShuttleDeparture("route-c-variant", "official-one-room-variant", "TO_MAIN", DayOfWeek.TUESDAY, LocalTime.of(19, 5), CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, LocalTime.of(19, 15)),
            ShuttleDeparture("route-c", "official-one-room", "TO_MAIN", DayOfWeek.TUESDAY, LocalTime.of(19, 30), CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, LocalTime.of(19, 40)),
            ShuttleDeparture("route-b", "official-main-hq", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 12), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("route-a", "official-main-hq", "TO_ONE_ROOM", DayOfWeek.TUESDAY, LocalTime.of(19, 16), CampusZoneId.MAIN, CampusZoneId.ONE_ROOM),
            ShuttleDeparture("route-a-evening", "official-stadium-a", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 18), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("route-b-evening", "official-stadium-b", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 18), CampusZoneId.MAIN, CampusZoneId.YEIN),
            ShuttleDeparture("route-b", "official-main-hq", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(19, 48), CampusZoneId.MAIN, CampusZoneId.YEIN),
        )

        val snapshot = GuidanceEngine().snapshot(
            now = now,
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = courses,
            noClassDates = emptySet(),
            resolvedZone = CampusZoneId.ONE_ROOM,
            automaticClassGuidance = true,
            shuttleDepartures = departures,
        )

        assertEquals(GuidancePhase.RETURN, snapshot.phase)
        assertEquals(
            listOf("원룸촌  5분, 30분", "본관  18분, 48분"),
            snapshot.shuttleLines.map { it.text },
        )
    }

    @Test
    fun `a configured no class date suppresses class guidance`() {
        val date = LocalDate.of(2026, 8, 31)
        val snapshot = GuidanceEngine().snapshot(
            now = date.atTime(9, 30).atZone(ZoneId.of("Asia/Seoul")),
            termStart = LocalDate.of(2026, 8, 24),
            termEnd = LocalDate.of(2026, 12, 18),
            courses = listOf(Course(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 50), "조명기초및실습", "덕성관 402", "이용창", CampusZoneId.MAIN)),
            noClassDates = setOf(date),
            resolvedZone = CampusZoneId.YEIN,
            automaticClassGuidance = true,
        )

        assertEquals(GuidancePhase.NONE, snapshot.phase)
        assertEquals(null, snapshot.classContent)
        assertEquals(emptyList<String>(), snapshot.shuttleLines.map { it.text })
    }

    @Test
    fun `first and last labels use deduplicated service day origin and destination slots`() {
        val departures = listOf(
            ShuttleDeparture("route-a", "yein-a", "TO_MAIN", DayOfWeek.MONDAY, LocalTime.of(7, 30), CampusZoneId.YEIN, CampusZoneId.MAIN),
            ShuttleDeparture("route-a-variant", "yein-b", "TO_MAIN", DayOfWeek.MONDAY, LocalTime.of(7, 30), CampusZoneId.YEIN, CampusZoneId.MAIN),
            ShuttleDeparture("route-a", "yein-a", "TO_MAIN", DayOfWeek.MONDAY, LocalTime.of(12, 0), CampusZoneId.YEIN, CampusZoneId.MAIN),
            ShuttleDeparture("route-a", "yein-a", "TO_MAIN", DayOfWeek.MONDAY, LocalTime.of(21, 10), CampusZoneId.YEIN, CampusZoneId.MAIN),
            ShuttleDeparture("route-c", "yein-a", "TO_ONE_ROOM", DayOfWeek.MONDAY, LocalTime.of(22, 0), CampusZoneId.YEIN, CampusZoneId.ONE_ROOM),
            ShuttleDeparture("route-a", "yein-a", "TO_MAIN", DayOfWeek.TUESDAY, LocalTime.of(6, 0), CampusZoneId.YEIN, CampusZoneId.MAIN),
        )

        val labels = GuidanceEngine().annotatedServiceDepartures(
            serviceDay = DayOfWeek.MONDAY,
            originZone = CampusZoneId.YEIN,
            destinationZone = CampusZoneId.MAIN,
            departures = departures,
        ).map { it.displayText }

        assertEquals(listOf("07:30 (첫차)", "12:00", "21:10 (막차)"), labels)
    }
}
