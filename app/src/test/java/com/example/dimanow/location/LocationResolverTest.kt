package com.example.dimanow.location

import com.example.dimanow.domain.CampusZone
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DefaultCampusZones
import com.example.dimanow.domain.GeoPoint
import com.example.dimanow.domain.ZoneGeometry
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationResolverTest {
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
