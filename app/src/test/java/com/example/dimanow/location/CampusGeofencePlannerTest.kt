package com.example.dimanow.location

import com.example.dimanow.domain.DefaultCampusZones
import com.example.dimanow.transit.Bus4402Schedule
import org.junit.Assert.assertEquals
import org.junit.Test

class CampusGeofencePlannerTest {
    @Test
    fun `4402 wake geofences are registered only while that guidance category is enabled`() {
        val enabled = CampusGeofencePlanner.plan(
            DefaultCampusZones.all,
            Bus4402Schedule.official.stops,
            includeBus4402 = true,
        )
        val disabled = CampusGeofencePlanner.plan(
            DefaultCampusZones.all,
            Bus4402Schedule.official.stops,
            includeBus4402 = false,
        )

        assertEquals(listOf("BUS_4402_34710", "BUS_4402_33243"), enabled.filter { it.transit }.map { it.id })
        assertEquals(listOf(120f, 120f), enabled.filter { it.transit }.map { it.radiusMeters })
        assertEquals(listOf(30_000, 30_000), enabled.filter { it.transit }.map { it.dwellMillis })
        assertEquals(emptyList<GeofenceSpec>(), disabled.filter { it.transit })
    }
}
