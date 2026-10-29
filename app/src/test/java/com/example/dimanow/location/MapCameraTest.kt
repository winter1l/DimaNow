package com.example.dimanow.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-132: the 캠퍼스 지도 camera — home view, pinch around a point, pan within the base map, focus — and label placement. */
class MapCameraTest {
    // A 1000 × 500 m campus inside a 2000 × 1000 m base map, on a 300 × 300 px map with 20 px padding.
    private val home = PlaneBounds(minEast = 0.0, maxEast = 1_000.0, minNorth = 0.0, maxNorth = 500.0)
    private val limit = PlaneBounds(minEast = -500.0, maxEast = 1_500.0, minNorth = -250.0, maxNorth = 750.0)
    private val space = MapCameraSpace(home, limit, width = 300f, height = 300f, padding = 20f, maxZoom = 8.0)

    @Test
    fun `the home view fits the campus as the plain viewport does`() {
        assertEquals(0.26, space.baseScale, 1e-9)
        val camera = space.homeCamera
        assertEquals(MapCamera(500.0, 250.0, 1.0), camera)
        val viewport = space.viewport(camera)
        // The same corners as MapViewport puts them: the campus spans 20..280 px across, centred down.
        assertEquals(ScreenPoint(20f, 85f), viewport.toScreen(PlanePoint(0.0, 500.0)))
        assertEquals(ScreenPoint(280f, 215f), viewport.toScreen(PlanePoint(1_000.0, 0.0)))
    }

    @Test
    fun `screen and plane are inverses under any camera`() {
        val viewport = space.viewport(MapCamera(321.0, 123.0, 3.5))
        val point = PlanePoint(400.0, 150.0)
        val screen = viewport.toScreen(point)
        val back = viewport.toPlane(screen.x, screen.y)
        assertEquals(point.east, back.east, 1e-3)
        assertEquals(point.north, back.north, 1e-3)
        // North is up.
        assertTrue(viewport.toScreen(PlanePoint(400.0, 200.0)).y < screen.y)
    }

    @Test
    fun `pinching keeps the point under the fingers where it is`() {
        val start = space.homeCamera
        val focusX = 200f
        val focusY = 120f
        val under = space.viewport(start).toPlane(focusX, focusY)
        val zoomed = space.zoom(start, 2.0, focusX, focusY)
        assertEquals(2.0, zoomed.zoom, 1e-9)
        val after = space.viewport(zoomed).toScreen(under)
        assertEquals(focusX, after.x, 1e-3f)
        assertEquals(focusY, after.y, 1e-3f)
    }

    @Test
    fun `zoom stays between the home view and the closest zoom`() {
        assertEquals(1.0, space.zoom(space.homeCamera, 0.25, 150f, 150f).zoom, 1e-9)
        assertEquals(8.0, space.zoom(space.homeCamera, 100.0, 150f, 150f).zoom, 1e-9)
        assertEquals(1.0, space.clamp(MapCamera(500.0, 250.0, 0.1)).zoom, 1e-9)
    }

    @Test
    fun `panning moves the content with the finger and stops at the base map's edge`() {
        val zoomed = MapCamera(500.0, 250.0, 4.0)
        val pixelsPerMeter = space.baseScale * 4.0
        // Dragging the content right and down by 52 px shows what lies west and north.
        val panned = space.pan(zoomed, 52f, 52f)
        assertEquals(500.0 - 52 / pixelsPerMeter, panned.centerEast, 1e-6)
        assertEquals(250.0 + 52 / pixelsPerMeter, panned.centerNorth, 1e-6)
        // A long drag stops with the view's edge on the limit.
        val far = space.pan(zoomed, 100_000f, -100_000f)
        val halfSpan = 150 / pixelsPerMeter
        assertEquals(limit.minEast + halfSpan, far.centerEast, 1e-6)
        assertEquals(limit.minNorth + halfSpan, far.centerNorth, 1e-6)
    }

    @Test
    fun `where the whole limit fits along an axis the view is centred on it`() {
        // At zoom 1 the view is 300 / 0.26 ≈ 1154 m tall, more than the 1000 m limit, so north is centred.
        val camera = space.clamp(MapCamera(0.0, 0.0, 1.0))
        assertEquals(250.0, camera.centerNorth, 1e-9)
        // Across, 1154 m of 2000 m: the centre may move but keeps the view inside.
        assertEquals(-500.0 + 150 / 0.26, camera.centerEast, 1e-6)
    }

    @Test
    fun `focusing a building centres it and zooms until it spans part of the map`() {
        val building = PlaneBounds(minEast = 600.0, maxEast = 650.0, minNorth = 300.0, maxNorth = 330.0)
        val camera = space.focus(building)
        assertEquals(625.0, camera.centerEast, 1e-9)
        assertEquals(315.0, camera.centerNorth, 1e-9)
        // 0.25 × 1154 m / 50 m ≈ 5.8, capped by the focus cap (4.5) below the map's closest zoom (8).
        assertEquals(CAMPUS_MAP_FOCUS_MAX_ZOOM, camera.zoom, 1e-9)
        val large = space.focus(PlaneBounds(0.0, 200.0, 0.0, 200.0))
        assertEquals(0.25 * (300 / 0.26) / 200.0, large.zoom, 1e-6)
        // A target larger than a quarter of the home view stays at the home zoom.
        assertEquals(1.0, space.focus(PlaneBounds(0.0, 600.0, 0.0, 600.0)).zoom, 1e-9)
    }

    @Test
    fun `the move to a focused building changes the zoom evenly`() {
        val from = MapCamera(0.0, 0.0, 1.0)
        val to = MapCamera(100.0, -50.0, 4.0)
        assertEquals(from, interpolateCamera(from, to, 0f))
        assertEquals(MapCamera(50.0, -25.0, 2.0), interpolateCamera(from, to, 0.5f))
        val end = interpolateCamera(from, to, 1f)
        assertEquals(4.0, end.zoom, 1e-9)
        assertEquals(100.0, end.centerEast, 1e-9)
    }

    @Test
    fun `centred labels take their spot in order and drop out when there is no room`() {
        val labels = listOf(
            CenteredLabelRequest(ScreenPoint(100f, 100f), 60f, 20f),
            // Same anchor: moves below the first.
            CenteredLabelRequest(ScreenPoint(100f, 100f), 60f, 20f),
            // Same anchor again: above.
            CenteredLabelRequest(ScreenPoint(100f, 100f), 60f, 20f),
            // No room left at this anchor: left out.
            CenteredLabelRequest(ScreenPoint(100f, 100f), 60f, 20f),
            null,
            // Over a fixed control: left out.
            CenteredLabelRequest(ScreenPoint(280f, 280f), 30f, 16f),
        )
        val boxes = placeCenteredLabels(labels, 300f, 300f, obstacles = listOf(MapBox(250f, 250f, 300f, 300f)), gap = 2f)
        assertEquals(MapBox(70f, 90f, 130f, 110f), boxes[0])
        assertEquals(MapBox(70f, 112f, 130f, 132f), boxes[1])
        assertEquals(MapBox(70f, 68f, 130f, 88f), boxes[2])
        assertNull(boxes[3])
        assertNull(boxes[4])
        assertNull(boxes[5])
        // A label hanging off the map is left out rather than squeezed in.
        assertNull(placeCenteredLabels(listOf(CenteredLabelRequest(ScreenPoint(5f, 150f), 60f, 20f)), 300f, 300f).single())
    }

    @Test
    fun `a ring's centre and what lies inside it`() {
        val square = listOf(PlanePoint(0.0, 0.0), PlanePoint(10.0, 0.0), PlanePoint(10.0, 10.0), PlanePoint(0.0, 10.0))
        assertEquals(PlanePoint(5.0, 5.0), ringCentroid(square))
        // An L shape's centre of area, not its vertex average.
        val ell = listOf(
            PlanePoint(0.0, 0.0), PlanePoint(20.0, 0.0), PlanePoint(20.0, 10.0),
            PlanePoint(10.0, 10.0), PlanePoint(10.0, 20.0), PlanePoint(0.0, 20.0),
        )
        val centre = ringCentroid(ell)
        assertEquals(25.0 / 3, centre.east, 1e-9)
        assertEquals(25.0 / 3, centre.north, 1e-9)
        assertTrue(ringContains(ell, PlanePoint(5.0, 15.0)))
        assertTrue(ringContains(ell, PlanePoint(15.0, 5.0)))
        assertFalse(ringContains(ell, PlanePoint(15.0, 15.0)))
        assertFalse(ringContains(ell, PlanePoint(-1.0, 5.0)))
    }
}
