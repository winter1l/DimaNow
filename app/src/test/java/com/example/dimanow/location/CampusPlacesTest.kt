package com.example.dimanow.location

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DefaultCampusZones
import com.example.dimanow.domain.GeoPoint
import com.example.dimanow.domain.ZoneGeometry
import com.example.dimanow.transit.Bus4402Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-131(5): the 캠퍼스 지도 places come only from coordinates the app already ships, and the page's texts and links. */
class CampusPlacesTest {
    private val places = CampusPlaces.all
    private fun place(id: String) = places.single { it.id == id }

    @Test
    fun `the map shows the verified zone centres and the two 4402 stops, in that order`() {
        assertEquals(
            listOf(
                Triple("본관", CampusPlaceKind.SCHOOL_ZONE, GeoPoint(37.0594160, 127.3585957)),
                Triple("엔터관·예인관", CampusPlaceKind.SCHOOL_ZONE, GeoPoint(37.0609666, 127.3535671)),
                Triple("원룸촌", CampusPlaceKind.HOUSING_ZONE, GeoPoint(37.0558538, 127.3627537)),
                Triple("대학 셔틀 정류장", CampusPlaceKind.BUS_4402_STOP, GeoPoint(37.0569667, 127.3613500)),
                Triple("원룸촌 앞", CampusPlaceKind.BUS_4402_STOP, GeoPoint(37.0556667, 127.3650333)),
            ),
            places.map { Triple(it.name, it.kind, it.point) },
        )
        assertEquals(listOf("34710", "33243"), places.mapNotNull { it.detail })
    }

    @Test
    fun `every coordinate is one the app already has, and no shuttle boarding stop is invented`() {
        val shipped = DefaultCampusZones.all.map { it.center } + Bus4402Schedule.official.stops.map { it.point }
        assertTrue(places.all { it.point in shipped })
        assertTrue(places.none { "운동장" in it.name })
        assertEquals(places.size, places.map { it.id }.toSet().size)
    }

    @Test
    fun `zone areas are the approved polygons`() {
        val areas = CampusPlaces.zoneAreas
        assertEquals(listOf(CampusZoneId.MAIN, CampusZoneId.YEIN, CampusZoneId.ONE_ROOM), areas.map { it.first })
        areas.forEach { (id, vertices) ->
            val polygon = DefaultCampusZones.all.single { it.id == id }.geometry as ZoneGeometry.Polygon
            assertEquals(polygon.vertices, vertices)
        }
    }

    @Test
    fun `the list line names the kind and the distance once the position is known`() {
        assertEquals("학교 구역", campusPlaceSupportingText(place("zone_main"), student = null))
        assertEquals("생활 구역", campusPlaceSupportingText(place("zone_one_room"), student = null))
        assertEquals("4402 정류장 34710", campusPlaceSupportingText(place("bus4402_34710"), student = null))
        assertEquals(
            "4402 정류장 33243 · 내 위치에서 360m",
            campusPlaceSupportingText(place("bus4402_33243"), student = GeoPoint(37.0569667, 127.3613500)),
        )
    }

    @Test
    fun `a marker says its name, kind, direction and distance`() {
        val atStop = GeoPoint(37.0569667, 127.3613500)
        assertEquals("본관, 학교 구역", campusPlaceSpokenDescription(place("zone_main"), student = null))
        assertEquals("본관, 학교 구역, 내 위치에서 북서쪽 370m", campusPlaceSpokenDescription(place("zone_main"), atStop))
        assertEquals(
            "대학 셔틀 정류장, 4402 정류장 34710, 내 위치에서 10m 이내",
            campusPlaceSpokenDescription(place("bus4402_34710"), atStop),
        )
    }

    @Test
    fun `the geo link carries the point and an encoded label`() {
        assertEquals(
            "geo:37.0594160,127.3585957?q=37.0594160,127.3585957(%EB%B3%B8%EA%B4%80)",
            campusPlaceGeoUri(place("zone_main")),
        )
        val stop = campusPlaceGeoUri(place("bus4402_34710"))
        assertTrue(stop.startsWith("geo:37.0569667,127.3613500?q=37.0569667,127.3613500("))
        assertFalse(' ' in stop)
        assertEquals("A%20b%C2%B7%28c%29-_.~", percentEncode("A b·(c)-_.~"))
    }
}
