package com.example.dimanow.shuttle

import com.example.dimanow.domain.CampusZoneId
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DimaShuttleParserTest {
    @Test
    fun `official A B C table shapes preserve source stops and map display zones`() {
        val html = checkNotNull(javaClass.getResource("/fixtures/shuttle_official_shape.html")).readText()

        val departures = DimaShuttleParser().parse(html)

        val monday = departures.filter { it.serviceDay == DayOfWeek.MONDAY }
        assertTrue(monday.any {
            it.sourceRouteId == "A" && it.sourceStopId == "one-room" &&
                it.originZone == CampusZoneId.ONE_ROOM && it.destinationZone == CampusZoneId.MAIN &&
                it.time == LocalTime.of(8, 20) && it.arrivalTime == LocalTime.of(8, 25)
        })
        assertTrue(monday.any {
            it.sourceRouteId == "B" && it.sourceStopId == "university-headquarters" &&
                it.originZone == CampusZoneId.MAIN && it.destinationZone == CampusZoneId.YEIN &&
                it.time == LocalTime.of(8, 25)
        })
        assertTrue(monday.any {
            it.sourceRouteId == "B-evening" && it.sourceStopId == "stadium-stop" &&
                it.originZone == CampusZoneId.MAIN && it.time == LocalTime.of(18, 55)
        })
        assertEquals(2, departures.count { it.sourceRouteId == "C" && it.serviceDay == DayOfWeek.SUNDAY })
    }
}
