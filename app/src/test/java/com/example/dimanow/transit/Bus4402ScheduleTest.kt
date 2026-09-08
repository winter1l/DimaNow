package com.example.dimanow.transit

import java.time.LocalTime
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class Bus4402ScheduleTest {
    @Test
    fun `official origin timetable preserves every published first and last departure`() {
        val schedule = Bus4402Schedule.official

        assertEquals(32, schedule.times(Bus4402ServiceType.WEEKDAY).size)
        assertEquals(LocalTime.of(5, 0), schedule.times(Bus4402ServiceType.WEEKDAY).first())
        assertEquals(LocalTime.of(22, 0), schedule.times(Bus4402ServiceType.WEEKDAY).last())
        assertEquals(24, schedule.times(Bus4402ServiceType.SATURDAY).size)
        assertEquals(LocalTime.of(5, 0), schedule.times(Bus4402ServiceType.SATURDAY).first())
        assertEquals(LocalTime.of(22, 0), schedule.times(Bus4402ServiceType.SATURDAY).last())
        assertEquals(28, schedule.times(Bus4402ServiceType.SUNDAY_HOLIDAY).size)
        assertEquals(LocalTime.of(5, 0), schedule.times(Bus4402ServiceType.SUNDAY_HOLIDAY).first())
        assertEquals(LocalTime.of(22, 0), schedule.times(Bus4402ServiceType.SUNDAY_HOLIDAY).last())
    }

    @Test
    fun `the one room stop is an explicit one minute estimate and the opposite stop is excluded`() {
        val schedule = Bus4402Schedule.official

        assertEquals(listOf("34710", "33243"), schedule.stops.map { it.stopNumber })
        assertEquals(
            LocalTime.of(5, 0),
            schedule.departures(Bus4402ServiceType.WEEKDAY, "34710").first().time,
        )
        val estimated = schedule.departures(Bus4402ServiceType.WEEKDAY, "33243").first()
        assertEquals(LocalTime.of(5, 1), estimated.time)
        assertEquals(true, estimated.estimated)
    }

    @Test
    fun `a weekday public holiday uses the sunday holiday service`() {
        assertEquals(
            Bus4402ServiceType.SUNDAY_HOLIDAY,
            Bus4402ServiceCalendar.serviceType(LocalDate.of(2026, 9, 25)),
        )
        assertEquals(
            Bus4402ServiceType.SATURDAY,
            Bus4402ServiceCalendar.serviceType(LocalDate.of(2026, 9, 19)),
        )
        assertEquals(
            Bus4402ServiceType.WEEKDAY,
            Bus4402ServiceCalendar.serviceType(LocalDate.of(2026, 9, 4)),
        )
    }
}
