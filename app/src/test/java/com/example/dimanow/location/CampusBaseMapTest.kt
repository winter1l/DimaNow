package com.example.dimanow.location

import com.example.dimanow.domain.GeoPoint
import com.example.dimanow.transit.Bus4402Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** D-132: the bundled OpenStreetMap base map parses, holds every school building, and projects into the map's scene. */
class CampusBaseMapTest {
    private val assetText = File("src/main/assets/${CampusBaseMapAsset.PATH}").readText(Charsets.UTF_8)
    private val map = CampusBaseMapParser.parse(assetText)
    private val projection = LocalProjection(CAMPUS_MAP_ORIGIN)
    private val stops = Bus4402Schedule.official.stops.map { it.point }
    private val scene = CampusMapScene.from(map, projection, stops)

    @Test
    fun `the asset is the clipped campus extract with its credit`() {
        assertEquals(GeoBounds(37.0520, 127.3480, 37.0650, 127.3700), map.bounds)
        assertEquals("© OpenStreetMap contributors", map.attribution)
        assertEquals("2026-10-26", map.extracted)
        assertEquals(36, map.buildings.size)
        assertTrue(map.areas.size >= 10)
        assertTrue(map.lines.size >= 50)
        // Clipped to the box and rounded to seven decimals.
        val points = map.buildings.flatMap { it.ring } + map.areas.flatMap { it.rings.flatten() } + map.lines.flatMap { it.points }
        points.forEach { point ->
            assertTrue(point.toString(), point.latitude in 37.0520..37.0650 && point.longitude in 127.3480..127.3700)
        }
        assertTrue(assetText.length < 40_000)
    }

    @Test
    fun `every school building has its OpenStreetMap footprints`() {
        val names = mapOf(
            471_488_712L to "대학 본부",
            469_419_567L to null,
            471_488_710L to "지성관",
            471_488_711L to "학생회관",
            471_488_709L to "기예관",
            471_488_708L to "덕성관",
            471_488_705L to "DIMA 영화촬영 스튜디오",
            471_488_702L to "디자인센터",
            471_488_704L to "엔터테인먼트관",
            468_690_098L to "동아방송예술대학 체육관",
            471_488_701L to "예인관",
        )
        CampusBuilding.entries.forEach { building ->
            building.osmWayIds.forEach { id ->
                val shape = map.building(id)
                assertNotNull("${building.name} way $id", shape)
                assertEquals(names[id], shape!!.name)
            }
        }
    }

    @Test
    fun `roads, paths and ground cover keep their classes and names`() {
        assertTrue(map.lines.any { it.kind == MapLineKind.MAJOR && it.name == "서동대로" })
        assertTrue(map.lines.any { it.kind == MapLineKind.MAJOR && it.name == "국사봉로" })
        assertTrue(map.lines.any { it.kind == MapLineKind.MINOR && it.name == "동아예대길" })
        assertTrue(map.lines.any { it.kind == MapLineKind.PATH })
        assertTrue(map.lines.any { it.kind == MapLineKind.STEPS })
        assertTrue(map.areas.any { it.kind == MapAreaKind.WOOD })
        assertTrue(map.areas.any { it.kind == MapAreaKind.PITCH && it.name == "운동장" })
        assertTrue(map.areas.any { it.kind == MapAreaKind.WATER && it.name == "화암저수지" })
        assertTrue(map.areas.any { it.kind == MapAreaKind.RESIDENTIAL && it.name == "원룸 A촌" })
    }

    @Test
    fun `a small asset parses, skipping kinds it does not know`() {
        val parsed = CampusBaseMapParser.parse(
            """
            {"version":2,"bounds":[37.0,127.0,37.1,127.1],
             "areas":[{"k":"wood","r":[[37.01,127.01,37.02,127.01,37.02,127.02]]},{"k":"lava","r":[[37.0,127.0,37.0,127.1,37.1,127.1]]},
                      {"k":"water","r":[[37.0,127.0,37.0]]}],
             "buildings":[{"id":7,"n":"가","r":[37.0,127.0,37.0,127.001,37.001,127.001,37.5]},{"id":8,"r":[37.0,127.0]}],
             "lines":[{"k":"major","n":"길","p":[37.0,127.0,37.01,127.01]},{"k":"canal","p":[37.0,127.0,37.01,127.01]}]}
            """.trimIndent(),
        )
        assertEquals(listOf(MapAreaKind.WOOD), parsed.areas.map { it.kind })
        assertEquals(listOf(GeoPoint(37.01, 127.01), GeoPoint(37.02, 127.01), GeoPoint(37.02, 127.02)), parsed.areas.single().rings.single())
        // The trailing odd number is dropped; a footprint with fewer than three points is skipped.
        assertEquals(listOf(7L), parsed.buildings.map { it.osmId })
        assertEquals(3, parsed.buildings.single().ring.size)
        assertEquals("가", parsed.buildings.single().name)
        assertEquals(listOf("길"), parsed.lines.map { it.name })
        assertEquals(OSM_ATTRIBUTION, parsed.attribution)
    }

    @Test
    fun `the scene ties footprints to school buildings and labels them first, by number`() {
        val schoolLabels = scene.labels.takeWhile { it.role == MapLabelRole.SCHOOL_BUILDING }
        assertEquals(listOf(1, 2, 3, 5, 8, 10, 13, null, null), schoolLabels.map { it.number })
        assertEquals(listOf("대학본부", "지성관", "기예관", "덕성관", "종합촬영소", "디자인센터", "엔터테인먼트관", "체육관", "예인관"), schoolLabels.map { it.text })
        // 학생회관 keeps OSM's own label beside 지성관, from a little zoom on.
        val studentHall = scene.labels.single { it.text == "학생회관" }
        assertEquals(MapLabelRole.BUILDING, studentHall.role)
        assertEquals(CampusMapScene.NEARBY_LABEL_ZOOM, studentHall.minZoom, 0.0)
        // Shop names wait for a close zoom.
        assertEquals(CampusMapScene.CLOSE_LABEL_ZOOM, scene.labels.single { it.text == "세븐일레븐" }.minZoom, 0.0)
        assertTrue(scene.namedRoads.keys.containsAll(listOf("서동대로", "국사봉로", "동아예대길")))
    }

    @Test
    fun `tapping inside a footprint finds its building, the smallest one under the finger`() {
        CampusBuilding.entries.forEach { building ->
            val footprint = scene.buildings.single { it.osmId == building.labelWayId }
            assertEquals(building.name, building, scene.buildingAt(footprint.centroid)?.campusBuilding)
        }
        assertNull(scene.buildingAt(PlanePoint(10_000.0, 10_000.0)))
    }

    @Test
    fun `a building's bounds hold all its footprints and the home view holds the campus`() {
        val jiseong = scene.boundsOf(CampusBuilding.JISEONG)!!
        CampusBuilding.JISEONG.osmWayIds.forEach { id ->
            scene.buildings.single { it.osmId == id }.ring.forEach { point ->
                assertTrue(point.east in jiseong.minEast..jiseong.maxEast && point.north in jiseong.minNorth..jiseong.maxNorth)
            }
        }
        CampusBuilding.entries.forEach { building ->
            val bounds = scene.boundsOf(building)!!
            assertTrue(building.name, bounds.minEast >= scene.home.minEast && bounds.maxEast <= scene.home.maxEast)
            assertTrue(building.name, bounds.minNorth >= scene.home.minNorth && bounds.maxNorth <= scene.home.maxNorth)
        }
        stops.map(projection::toPlane).forEach { stop ->
            assertTrue(stop.east in scene.home.minEast..scene.home.maxEast && stop.north in scene.home.minNorth..scene.home.maxNorth)
        }
        // The home view lies inside the panning limit.
        assertTrue(scene.limit.minEast <= scene.home.minEast && scene.limit.maxEast >= scene.home.maxEast)
        assertTrue(scene.limit.minNorth <= scene.home.minNorth && scene.limit.maxNorth >= scene.home.maxNorth)
    }

    @Test
    fun `a plane point goes back to the same map point`() {
        val point = GeoPoint(37.0595950, 127.3571450)
        val back = projection.toGeo(projection.toPlane(point))
        assertEquals(point.latitude, back.latitude, 1e-9)
        assertEquals(point.longitude, back.longitude, 1e-9)
        val center = scene.centerOf(CampusBuilding.DEOKSEONG)!!
        assertTrue(greatCircleMeters(center, GeoPoint(37.059595, 127.357145)) < 30.0)
    }
}
