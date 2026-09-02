package com.example.dimanow.shuttle

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.ShuttleDeparture
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FieldShuttleOverridesTest {
    @Test
    fun `field photo additions are projected once without changing official rows`() {
        val official = listOf(
            ShuttleDeparture("A", "one-room", "TO_MAIN", DayOfWeek.MONDAY, LocalTime.of(14, 40), CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, LocalTime.of(14, 45)),
        )

        val first = FieldShuttleOverrides.apply(official)
        val second = FieldShuttleOverrides.apply(first)

        assertEquals(21, first.size)
        assertEquals(first, second)
        assertEquals(
            listOf("14:30", "15:30", "16:30", "17:30"),
            first.filter { it.sourceRouteId == "A-field-extra" && it.serviceDay == DayOfWeek.MONDAY }.map { it.time.toString() },
        )
        assertEquals(listOf(null), first.filter { it.sourceRouteId == "A-field-extra" }.map { it.arrivalTime }.distinct())
    }
}
