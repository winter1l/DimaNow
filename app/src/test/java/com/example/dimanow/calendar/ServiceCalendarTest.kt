package com.example.dimanow.calendar

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.guidance.GuidanceEngine
import com.example.dimanow.guidance.ShuttleBoardPurpose
import com.example.dimanow.sync.ServiceCalendarPayload
import com.example.dimanow.transit.Bus4402ServiceCalendar
import com.example.dimanow.transit.Bus4402ServiceType
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCalendarTest {
    private val calendar = BuiltInServiceCalendar.calendar
    private val kst = ZoneId.of("Asia/Seoul")

    @Test
    fun `the bundled calendar equals the published data-source file`() {
        val file = listOf(File("../data-source/calendar.json"), File("data-source/calendar.json")).first { it.isFile }
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(
            json.decodeFromString<ServiceCalendarPayload>(file.readText()),
            json.decodeFromString<ServiceCalendarPayload>(BuiltInServiceCalendar.JSON.trim()),
        )
    }

    @Test
    fun `weekday public holidays suspend the campus shuttle but Sunday holidays keep the Sunday line`() {
        assertEquals("한글날", calendar.campusShuttleSuspension(LocalDate.of(2026, 10, 9)))
        assertEquals("대체공휴일(개천절)", calendar.campusShuttleSuspension(LocalDate.of(2026, 10, 5)))
        // 2027-02-07 설날 is a Sunday: the C line still runs.
        assertNull(calendar.campusShuttleSuspension(LocalDate.of(2027, 2, 7)))
        assertNull(calendar.campusShuttleSuspension(LocalDate.of(2026, 10, 8)))
    }

    @Test
    fun `4402 uses the Sunday and holiday table on 2027 holidays too`() {
        assertEquals(Bus4402ServiceType.SUNDAY_HOLIDAY, Bus4402ServiceCalendar.serviceType(LocalDate.of(2027, 2, 9)))
        assertEquals(Bus4402ServiceType.SUNDAY_HOLIDAY, Bus4402ServiceCalendar.serviceType(LocalDate.of(2027, 7, 19)))
        assertEquals(Bus4402ServiceType.WEEKDAY, Bus4402ServiceCalendar.serviceType(LocalDate.of(2027, 2, 10)))
    }

    @Test
    fun `the guidance engine offers no campus departure on a public holiday`() {
        val departure = ShuttleDeparture("A", "one-room", "TO_MAIN", DayOfWeek.FRIDAY, LocalTime.of(10, 0), CampusZoneId.ONE_ROOM, CampusZoneId.MAIN)
        val engine = GuidanceEngine(campusShuttleSuspension = calendar::campusShuttleSuspension)
        val holiday = ZonedDateTime.of(2026, 10, 9, 9, 0, 0, 0, kst)
        val ordinaryFriday = ZonedDateTime.of(2026, 10, 16, 9, 0, 0, 0, kst)

        assertTrue(engine.shuttleBoard(holiday, CampusZoneId.ONE_ROOM, listOf(departure), ShuttleBoardPurpose.GENERAL).rows.isEmpty())
        assertTrue(engine.nextDepartures(holiday, CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, listOf(departure)).isEmpty())
        assertEquals(1, engine.shuttleBoard(ordinaryFriday, CampusZoneId.ONE_ROOM, listOf(departure), ShuttleBoardPurpose.GENERAL).rows.size)
    }

    @Test
    fun `a new user in the published fall term gets that term`() {
        val term = AcademicTerms.suggest(LocalDate.of(2026, 10, 17), calendar)
        assertEquals(LocalDate.of(2026, 8, 24), term.start)
        assertEquals(LocalDate.of(2026, 12, 18), term.end)
        assertTrue(term.published)
    }

    @Test
    fun `after the last published term the next spring is estimated from the usual pattern`() {
        val term = AcademicTerms.suggest(LocalDate.of(2026, 12, 21), calendar)
        // 2027-03-01 is 3·1절, so the first school day is Tuesday 2027-03-02.
        assertEquals(LocalDate.of(2027, 3, 2), term.start)
        assertEquals(LocalDate.of(2027, 6, 21), term.end)
        assertFalse(term.published)
        assertTrue(AcademicTerms.hasEnded(LocalDate.of(2026, 12, 18), LocalDate.of(2026, 12, 19)))
        assertFalse(AcademicTerms.hasEnded(LocalDate.of(2026, 12, 18), LocalDate.of(2026, 12, 18)))
    }

    @Test
    fun `summer vacation suggests the published fall term`() {
        val term = AcademicTerms.suggest(LocalDate.of(2026, 7, 10), calendar)
        assertEquals(LocalDate.of(2026, 8, 24), term.start)
        assertTrue(term.published)
    }
}
