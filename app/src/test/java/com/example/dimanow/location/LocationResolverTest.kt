package com.example.dimanow.location

import com.example.dimanow.domain.CampusZone
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DefaultCampusZones
import com.example.dimanow.domain.GeoPoint
import com.example.dimanow.domain.ZoneGeometry
import java.time.Instant
import com.example.dimanow.transit.Bus4402Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationResolverTest {
    @Test
    fun `an active 4402 wake geofence keeps precise stop sampling alive before the inner radius`() {
        val resolver = LocationResolver()

        assertTrue(
            resolver.shouldPollNearbyTransitStop(
                activeGeofenceIds = setOf("BUS_4402_33243"),
                previous = TransitStopProximityState(),
                guidanceEnabled = true,
            ),
        )
        assertTrue(
            resolver.shouldPollNearbyTransitStop(
                activeGeofenceIds = emptySet(),
                previous = TransitStopProximityState(candidateStopNumber = "33243"),
                guidanceEnabled = true,
            ),
        )
        assertFalse(
            resolver.shouldPollNearbyTransitStop(
                activeGeofenceIds = setOf("BUS_4402_33243"),
                previous = TransitStopProximityState(activeStopNumber = "33243"),
                guidanceEnabled = false,
            ),
        )
    }

    @Test
    fun `a 4402 stop activates only after an accurate thirty second dwell and releases beyond seventy meters`() {
        val resolver = LocationResolver()
        val stop = Bus4402Schedule.official.stops.first { it.stopNumber == "33243" }
        val enteredAt = Instant.parse("2026-09-04T00:00:00Z")

        val entering = resolver.resolveNearbyTransitStop(
            sample = LocationSample(stop.point, 12f, enteredAt),
            now = enteredAt,
            stops = listOf(stop),
            previous = TransitStopProximityState(),
        )
        val active = resolver.resolveNearbyTransitStop(
            sample = LocationSample(stop.point, 12f, enteredAt.plusSeconds(31)),
            now = enteredAt.plusSeconds(31),
            stops = listOf(stop),
            previous = entering.state,
        )
        val released = resolver.resolveNearbyTransitStop(
            sample = LocationSample(GeoPoint(37.0556667, 127.3659000), 12f, enteredAt.plusSeconds(40)),
            now = enteredAt.plusSeconds(40),
            stops = listOf(stop),
            previous = active.state,
        )

        assertEquals(null, entering.stop)
        assertEquals("33243", active.stop?.stopNumber)
        assertEquals(null, released.stop)
        assertEquals(
            CampusZoneId.ONE_ROOM,
            resolver.resolve(stop.point, DefaultCampusZones.all, CampusZoneId.OUTSIDE, false),
        )
    }

    @Test
    fun `an inaccurate sample suppresses the 4402 override and missing location expires after two minutes`() {
        val resolver = LocationResolver()
        val stop = Bus4402Schedule.official.stops.first { it.stopNumber == "34710" }
        val lastValid = Instant.parse("2026-09-04T00:00:00Z")
        val activeState = TransitStopProximityState(
            candidateStopNumber = stop.stopNumber,
            candidateSince = lastValid.minusSeconds(40),
            activeStopNumber = stop.stopNumber,
            lastValidAt = lastValid,
        )

        val inaccurate = resolver.resolveNearbyTransitStop(
            LocationSample(stop.point, 30f, lastValid.plusSeconds(10)),
            lastValid.plusSeconds(10),
            listOf(stop),
            activeState,
        )
        val grace = resolver.resolveNearbyTransitStop(null, lastValid.plusSeconds(119), listOf(stop), activeState)
        val expired = resolver.resolveNearbyTransitStop(null, lastValid.plusSeconds(121), listOf(stop), activeState)

        assertEquals(null, inaccurate.stop)
        assertEquals(null, inaccurate.state.activeStopNumber)
        assertEquals("34710", grace.stop?.stopNumber)
        assertEquals(null, expired.stop)
    }

    @Test
    fun `shuttle report location requires a fresh accurate gps sample at the actual stop zone`() {
        val now = Instant.parse("2026-09-02T09:00:00Z")
        val zones = listOf(CampusZone(CampusZoneId.MAIN, GeoPoint(37.0590, 127.3580), 250))
        val resolver = LocationResolver()

        assertEquals(
            true,
            resolver.isFreshSampleAtZone(LocationSample(GeoPoint(37.0590, 127.3580), 18f, now.minusSeconds(30)), now, zones, CampusZoneId.MAIN),
        )
        assertEquals(
            false,
            resolver.isFreshSampleAtZone(LocationSample(GeoPoint(37.0590, 127.3580), 18f, now.minusSeconds(121)), now, zones, CampusZoneId.MAIN),
        )
        assertEquals(
            false,
            resolver.isFreshSampleAtZone(LocationSample(GeoPoint(37.0590, 127.3580), 101f, now), now, zones, CampusZoneId.MAIN),
        )
        assertEquals(
            false,
            resolver.isFreshSampleAtZone(LocationSample(GeoPoint(37.0, 127.0), 18f, now), now, zones, CampusZoneId.MAIN),
        )
    }

    @Test
    fun `approved Yein polygon classifies its lower west area`() {
        val resolved = LocationResolver().resolve(
            sample = GeoPoint(37.0575, 127.3534),
            configuredZones = DefaultCampusZones.all,
            lastResolvedZone = CampusZoneId.OUTSIDE,
            explicitExitFromAll = false,
        )

        assertEquals(CampusZoneId.YEIN, resolved)
    }

    @Test
    fun `approved Main polygon classifies its lower west area`() {
        val resolved = LocationResolver().resolve(
            sample = GeoPoint(37.0565, 127.3590),
            configuredZones = DefaultCampusZones.all,
            lastResolvedZone = CampusZoneId.OUTSIDE,
            explicitExitFromAll = false,
        )

        assertEquals(CampusZoneId.MAIN, resolved)
    }

    @Test
    fun `approved One Room polygon classifies its southern area`() {
        val resolved = LocationResolver().resolve(
            sample = GeoPoint(37.0528, 127.3635),
            configuredZones = DefaultCampusZones.all,
            lastResolvedZone = CampusZoneId.OUTSIDE,
            explicitExitFromAll = false,
        )

        assertEquals(CampusZoneId.ONE_ROOM, resolved)
    }

    @Test
    fun `a point on an approved polygon edge remains inside the zone`() {
        val resolved = LocationResolver().resolve(
            sample = GeoPoint(37.0636703410925, 127.35157370567323),
            configuredZones = DefaultCampusZones.all,
            lastResolvedZone = CampusZoneId.OUTSIDE,
            explicitExitFromAll = false,
        )

        assertEquals(CampusZoneId.YEIN, resolved)
    }

    @Test
    fun `test mode uses the selected zone even when gps reports another zone`() {
        val zones = listOf(
            CampusZone(CampusZoneId.MAIN, GeoPoint(37.0590, 127.3580), 250),
        )

        val resolved = LocationResolver().resolve(
            sample = GeoPoint(37.0590, 127.3580),
            configuredZones = zones,
            lastResolvedZone = CampusZoneId.MAIN,
            explicitExitFromAll = false,
            mode = LocationMode.TEST,
            testZone = CampusZoneId.ONE_ROOM,
        )

        assertEquals(CampusZoneId.ONE_ROOM, resolved)
    }

    @Test
    fun `school circles take priority over the one room polygon and polygon misses become outside`() {
        val oneRoomPolygon = CampusZone(
            id = CampusZoneId.ONE_ROOM,
            center = GeoPoint(37.0560, 127.3630),
            radiusMeters = 250,
            geometry = ZoneGeometry.Polygon(
                version = "ONE_ROOM_TEST",
                vertices = listOf(
                    GeoPoint(37.0550, 127.3620),
                    GeoPoint(37.0570, 127.3620),
                    GeoPoint(37.0570, 127.3640),
                    GeoPoint(37.0550, 127.3640),
                ),
                wakeRadiusMeters = 350,
            ),
        )
        val main = CampusZone(CampusZoneId.MAIN, GeoPoint(37.0568, 127.3630), 120)
        val resolver = LocationResolver()

        val overlap = resolver.resolve(
            sample = LocationSample(GeoPoint(37.0568, 127.3630), 12f, Instant.parse("2026-08-27T09:00:00Z")),
            configuredZones = listOf(oneRoomPolygon, main),
            lastResolvedZone = CampusZoneId.OUTSIDE,
            explicitExitFromAll = false,
        )
        val outside = resolver.resolve(
            sample = LocationSample(GeoPoint(37.0580, 127.3650), 12f, Instant.parse("2026-08-27T09:00:00Z")),
            configuredZones = listOf(oneRoomPolygon, main),
            lastResolvedZone = CampusZoneId.ONE_ROOM,
            explicitExitFromAll = false,
        )

        assertEquals(CampusZoneId.MAIN, overlap)
        assertEquals(CampusZoneId.OUTSIDE, outside)
    }
    @Test
    fun `overlapping zones resolve to the nearest saved center`() {
        val zones = listOf(
            CampusZone(CampusZoneId.YEIN, GeoPoint(37.0610, 127.1570), 300),
            CampusZone(CampusZoneId.MAIN, GeoPoint(37.0615, 127.1575), 300),
        )

        val result = LocationResolver().resolve(
            sample = GeoPoint(37.06145, 127.15745),
            configuredZones = zones,
            lastResolvedZone = CampusZoneId.YEIN,
            explicitExitFromAll = false,
        )

        assertEquals(CampusZoneId.MAIN, result)
    }

    @Test
    fun `missing sample retains the last resolved zone`() {
        val result = LocationResolver().resolve(null as GeoPoint?, emptyList(), CampusZoneId.ONE_ROOM, explicitExitFromAll = false)
        assertEquals(CampusZoneId.ONE_ROOM, result)
    }

    @Test
    fun `explicit exit from every configured geofence becomes outside`() {
        val result = LocationResolver().resolve(GeoPoint(37.0, 127.0), emptyList(), CampusZoneId.MAIN, explicitExitFromAll = true)
        assertEquals(CampusZoneId.OUTSIDE, result)
    }
}
