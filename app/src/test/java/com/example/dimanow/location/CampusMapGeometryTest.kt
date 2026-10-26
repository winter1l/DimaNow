package com.example.dimanow.location

import com.example.dimanow.domain.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-131(5): the 캠퍼스 지도 page's projection, distance, scale bar and label placement helpers. */
class CampusMapGeometryTest {
    private val stop34710 = GeoPoint(37.0569667, 127.3613500)
    private val stop33243 = GeoPoint(37.0556667, 127.3650333)
    private val main = GeoPoint(37.0594160, 127.3585957)

    @Test
    fun `great circle distance between the two 4402 stops`() {
        assertEquals(357.389, greatCircleMeters(stop34710, stop33243), 0.01)
        assertEquals(365.935, greatCircleMeters(stop34710, main), 0.01)
        assertEquals(0.0, greatCircleMeters(main, main), 0.0)
    }

    @Test
    fun `equirectangular projection puts north up and shrinks east by the cosine of the latitude`() {
        val projection = LocalProjection(GeoPoint(37.0, 127.0))

        val north = projection.toPlane(GeoPoint(37.001, 127.0))
        assertEquals(0.0, north.east, 1e-9)
        assertEquals(111.195, north.north, 0.001)

        val east = projection.toPlane(GeoPoint(37.0, 127.001))
        assertEquals(88.804, east.east, 0.001)
        assertEquals(0.0, east.north, 1e-9)

        val southWest = projection.toPlane(GeoPoint(36.999, 126.999))
        assertTrue(southWest.east < 0 && southWest.north < 0)
    }

    @Test
    fun `across the campus the projection stays within a metre of the great circle distance`() {
        val points = listOf(main, GeoPoint(37.0609666, 127.3535671), GeoPoint(37.0558538, 127.3627537), stop34710, stop33243)
        val projection = LocalProjection.aroundCentroid(points)
        assertEquals(37.05777396, projection.origin.latitude, 1e-9)
        assertEquals(127.36025996, projection.origin.longitude, 1e-9)
        for (first in points) {
            for (second in points) {
                val a = projection.toPlane(first)
                val b = projection.toPlane(second)
                val planeDistance = kotlin.math.hypot(a.east - b.east, a.north - b.north)
                assertEquals(greatCircleMeters(first, second), planeDistance, 1.0)
            }
        }
    }

    @Test
    fun `the viewport keeps one scale on both axes, centres the content and leaves the padding`() {
        val bounds = PlaneBounds(minEast = 0.0, maxEast = 1_000.0, minNorth = 0.0, maxNorth = 500.0)
        val viewport = MapViewport(bounds, width = 300f, height = 300f, paddingX = 20f, paddingY = 20f)

        assertEquals(0.26, viewport.pixelsPerMeter, 1e-9)
        // North-west corner: left padding, centred vertically ((300 - 130) / 2).
        assertEquals(ScreenPoint(20f, 85f), viewport.toScreen(PlanePoint(0.0, 500.0)))
        // South-east corner.
        assertEquals(ScreenPoint(280f, 215f), viewport.toScreen(PlanePoint(1_000.0, 0.0)))
        // Further north is higher on the screen.
        assertTrue(viewport.toScreen(PlanePoint(0.0, 400.0)).y < viewport.toScreen(PlanePoint(0.0, 100.0)).y)
        // eastAt / northAt undo toScreen.
        assertEquals(250.0, viewport.eastAt(viewport.toScreen(PlanePoint(250.0, 0.0)).x), 1e-3)
        assertEquals(125.0, viewport.northAt(viewport.toScreen(PlanePoint(0.0, 125.0)).y), 1e-3)
    }

    @Test
    fun `a tall area fits by height and is centred across`() {
        val bounds = PlaneBounds(minEast = 0.0, maxEast = 100.0, minNorth = 0.0, maxNorth = 1_000.0)
        val viewport = MapViewport(bounds, width = 300f, height = 220f, paddingX = 10f, paddingY = 10f)

        assertEquals(0.2, viewport.pixelsPerMeter, 1e-9)
        assertEquals(ScreenPoint(140f, 10f), viewport.toScreen(PlanePoint(0.0, 1_000.0)))
        assertEquals(ScreenPoint(160f, 210f), viewport.toScreen(PlanePoint(100.0, 0.0)))
    }

    @Test
    fun `the map height follows the width's scale within its limits`() {
        val wide = PlaneBounds(0.0, 1_000.0, 0.0, 500.0)
        val square = PlaneBounds(0.0, 1_000.0, 0.0, 1_000.0)
        val tall = PlaneBounds(0.0, 100.0, 0.0, 1_000.0)

        assertEquals(220f, fittedMapHeight(300f, wide, 20f, 20f, minHeight = 220f, maxHeight = 480f), 0.001f)
        assertEquals(300f, fittedMapHeight(300f, square, 20f, 20f, minHeight = 220f, maxHeight = 480f), 0.001f)
        assertEquals(480f, fittedMapHeight(300f, tall, 20f, 20f, minHeight = 220f, maxHeight = 480f), 0.001f)
    }

    @Test
    fun `the scale bar takes the longest round length that fits`() {
        assertEquals(250, scaleBarMeters(pixelsPerMeter = 0.25, maxPixels = 90f))
        assertEquals(100, scaleBarMeters(pixelsPerMeter = 0.5, maxPixels = 90f))
        assertEquals(5_000, scaleBarMeters(pixelsPerMeter = 0.001, maxPixels = 6f))
        assertNull(scaleBarMeters(pixelsPerMeter = 10.0, maxPixels = 50f))

        assertEquals("250m", scaleBarLabel(250))
        assertEquals("1km", scaleBarLabel(1_000))
        assertEquals("2km", scaleBarLabel(2_000))
    }

    @Test
    fun `distances read as rounded metres or kilometres`() {
        assertEquals("10m 이내", formatDistance(3.0))
        assertEquals("10m", formatDistance(12.0))
        assertEquals("320m", formatDistance(324.0))
        assertEquals("360m", formatDistance(357.389))
        assertEquals("1.0km", formatDistance(995.0))
        assertEquals("1.2km", formatDistance(1_240.0))
        assertEquals("10km", formatDistance(9_960.0))
        assertEquals("32km", formatDistance(32_400.0))
    }

    @Test
    fun `compass words name the eight directions`() {
        val origin = GeoPoint(37.0, 127.0)
        assertEquals("북쪽", compassWord(origin, GeoPoint(37.001, 127.0)))
        assertEquals("북동쪽", compassWord(origin, GeoPoint(37.001, 127.001)))
        assertEquals("동쪽", compassWord(origin, GeoPoint(37.0, 127.001)))
        assertEquals("남동쪽", compassWord(origin, GeoPoint(36.999, 127.001)))
        assertEquals("남쪽", compassWord(origin, GeoPoint(36.999, 127.0)))
        assertEquals("서쪽", compassWord(origin, GeoPoint(37.0, 126.999)))
        assertEquals("북서쪽", compassWord(stop34710, main))
        assertEquals("남동쪽", compassWord(stop34710, stop33243))
    }

    @Test
    fun `a label sits at the end side when it is free`() {
        val boxes = placeMapLabels(
            listOf(MapLabelRequest(ScreenPoint(100f, 100f), markerRadius = 10f, width = 50f, height = 20f)),
            width = 300f,
            height = 300f,
            gap = 4f,
        )
        assertEquals(listOf(MapBox(114f, 90f, 164f, 110f)), boxes)
    }

    @Test
    fun `a label at the end edge moves to the start side`() {
        val boxes = placeMapLabels(
            listOf(MapLabelRequest(ScreenPoint(290f, 100f), markerRadius = 10f, width = 50f, height = 20f)),
            width = 300f,
            height = 300f,
            gap = 4f,
        )
        assertEquals(listOf(MapBox(226f, 90f, 276f, 110f)), boxes)
    }

    @Test
    fun `a label never covers a neighbouring marker or fixed element`() {
        val boxes = placeMapLabels(
            listOf(
                MapLabelRequest(ScreenPoint(100f, 100f), markerRadius = 10f, width = 50f, height = 20f),
                MapLabelRequest(ScreenPoint(150f, 100f), markerRadius = 10f, width = 50f, height = 20f),
            ),
            width = 300f,
            height = 300f,
            gap = 4f,
            fixed = listOf(MapBox(160f, 80f, 300f, 112f)),
        )
        // The first label's end side would cover the second marker, so it goes to the start side; the second label's
        // end side is under the fixed element and its start side covers the first marker, so it goes below.
        assertEquals(MapBox(36f, 90f, 86f, 110f), boxes[0])
        assertEquals(MapBox(125f, 114f, 175f, 134f), boxes[1])
    }

    @Test
    fun `when every side collides the label still ends inside the map`() {
        val boxes = placeMapLabels(
            listOf(MapLabelRequest(ScreenPoint(10f, 10f), markerRadius = 10f, width = 80f, height = 20f)),
            width = 60f,
            height = 60f,
            gap = 4f,
        )
        val box = boxes.single()
        assertTrue(box.left >= 0f && box.top >= 0f)
        assertEquals(80f, box.width, 0.001f)
    }

    @Test
    fun `boxes overlap only when they share area`() {
        val box = MapBox(0f, 0f, 10f, 10f)
        assertTrue(box.overlaps(MapBox(5f, 5f, 15f, 15f)))
        assertFalse(box.overlaps(MapBox(10f, 0f, 20f, 10f)))
        assertEquals(25f, box.overlapArea(MapBox(5f, 5f, 15f, 15f)), 0.001f)
        assertEquals(50f, MapBox(-5f, 0f, 5f, 10f).outsideArea(100f, 100f), 0.001f)
        assertEquals(MapBox(0f, 90f, 10f, 100f), MapBox(-5f, 95f, 5f, 105f).clampedInto(100f, 100f))
    }
}
