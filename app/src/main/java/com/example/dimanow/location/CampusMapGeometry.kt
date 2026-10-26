package com.example.dimanow.location

import com.example.dimanow.domain.GeoPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Mean Earth radius in metres, the one [LocationResolver] measures zones and 4402 stops with (D-131). */
internal const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Great-circle (haversine) distance in metres between two points (D-131(5)): the same formula [LocationResolver] uses
 * for zone and 4402-stop distances, so the 캠퍼스 지도 page and the location guidance agree on how far a place is.
 */
internal fun greatCircleMeters(first: GeoPoint, second: GeoPoint): Double {
    val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
    val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
    val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(Math.toRadians(first.latitude)) * cos(Math.toRadians(second.latitude)) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** A point on the drawn map's flat plane, in metres east and north of the projection's origin (D-131). */
internal data class PlanePoint(val east: Double, val north: Double)

/**
 * Equirectangular projection around [origin] for the 캠퍼스 지도 page (D-131(5)): east = Δlongitude·cos(origin
 * latitude)·R and north = Δlatitude·R, in radians. Over the campus (about 1.5 km) it stays within a metre of the
 * great-circle distance, which is all a drawn map needs; north is up and both axes share one scale.
 */
internal class LocalProjection(val origin: GeoPoint) {
    private val metersPerRadianEast = EARTH_RADIUS_METERS * cos(Math.toRadians(origin.latitude))

    fun toPlane(point: GeoPoint): PlanePoint = PlanePoint(
        east = Math.toRadians(point.longitude - origin.longitude) * metersPerRadianEast,
        north = Math.toRadians(point.latitude - origin.latitude) * EARTH_RADIUS_METERS,
    )

    companion object {
        /** Projection around the plain average of [points] (their centroid), as the map centres on its places. */
        fun aroundCentroid(points: List<GeoPoint>): LocalProjection {
            require(points.isNotEmpty())
            return LocalProjection(GeoPoint(points.map { it.latitude }.average(), points.map { it.longitude }.average()))
        }
    }
}

/** The smallest east/north rectangle holding a set of plane points (D-131). */
internal data class PlaneBounds(val minEast: Double, val maxEast: Double, val minNorth: Double, val maxNorth: Double) {
    val eastSpan: Double get() = maxEast - minEast
    val northSpan: Double get() = maxNorth - minNorth

    companion object {
        fun of(points: List<PlanePoint>): PlaneBounds {
            require(points.isNotEmpty())
            return PlaneBounds(
                minEast = points.minOf { it.east },
                maxEast = points.maxOf { it.east },
                minNorth = points.minOf { it.north },
                maxNorth = points.maxOf { it.north },
            )
        }
    }
}

/** A position on the drawn map in pixels from its top-left corner (D-131). */
internal data class ScreenPoint(val x: Float, val y: Float)

/**
 * Fits [bounds] into a [width]×[height] pixel map with at least [paddingX]/[paddingY] on each side (D-131(5)). One
 * scale serves both axes, so the campus keeps its shape; north is up (a larger north is a smaller y) and the content is
 * centred along the axis with room to spare.
 */
internal class MapViewport(
    val bounds: PlaneBounds,
    val width: Float,
    val height: Float,
    paddingX: Float,
    paddingY: Float,
) {
    /** Pixels per metre on both axes. */
    val pixelsPerMeter: Double = min(
        (width - 2 * paddingX).coerceAtLeast(1f) / max(bounds.eastSpan, MIN_SPAN_METERS),
        (height - 2 * paddingY).coerceAtLeast(1f) / max(bounds.northSpan, MIN_SPAN_METERS),
    )
    private val offsetX = (width - bounds.eastSpan * pixelsPerMeter) / 2
    private val offsetY = (height - bounds.northSpan * pixelsPerMeter) / 2

    fun toScreen(point: PlanePoint): ScreenPoint = ScreenPoint(
        x = (offsetX + (point.east - bounds.minEast) * pixelsPerMeter).toFloat(),
        y = (offsetY + (bounds.maxNorth - point.north) * pixelsPerMeter).toFloat(),
    )

    /** The east coordinate under pixel column [x]; the inverse of [toScreen] along x. */
    fun eastAt(x: Float): Double = bounds.minEast + (x - offsetX) / pixelsPerMeter

    /** The north coordinate under pixel row [y]; the inverse of [toScreen] along y. */
    fun northAt(y: Float): Double = bounds.maxNorth - (y - offsetY) / pixelsPerMeter

    private companion object {
        /** A single point (or a line) still gets a finite scale. */
        const val MIN_SPAN_METERS = 1.0
    }
}

/**
 * Height for a map [width] pixels wide that shows [bounds] at the width's scale (D-131(5)), so the campus is as large
 * as the card allows, clamped to [minHeight]..[maxHeight]; a clamped height letterboxes the campus instead of
 * stretching it.
 */
internal fun fittedMapHeight(
    width: Float,
    bounds: PlaneBounds,
    paddingX: Float,
    paddingY: Float,
    minHeight: Float,
    maxHeight: Float,
): Float {
    val innerWidth = (width - 2 * paddingX).coerceAtLeast(1f)
    val ratio = if (bounds.eastSpan <= 0.0) 1.0 else bounds.northSpan / bounds.eastSpan
    return (innerWidth * ratio + 2 * paddingY).toFloat().coerceIn(minHeight, max(minHeight, maxHeight))
}

/** Lengths the scale bar may show, in metres (D-131). */
private val SCALE_STEPS_METERS = listOf(10, 20, 50, 100, 200, 250, 500, 1_000, 2_000, 5_000, 10_000)

/**
 * The longest round length (`100m`, `250m`, `1km`) whose bar fits in [maxPixels] at [pixelsPerMeter] (D-131(5)),
 * or null when even 10 m would not fit.
 */
internal fun scaleBarMeters(pixelsPerMeter: Double, maxPixels: Float): Int? =
    SCALE_STEPS_METERS.lastOrNull { it * pixelsPerMeter <= maxPixels }

/** The scale bar's caption: `250m`, `1km`, `2km` (D-131). */
internal fun scaleBarLabel(meters: Int): String =
    if (meters >= 1_000 && meters % 1_000 == 0) "${meters / 1_000}km" else "${meters}m"

/**
 * How far a place is, as the 캠퍼스 지도 list says it (D-131(5)): metres rounded to 10 below a kilometre (`320m`),
 * one decimal up to 10 km (`1.2km`), whole kilometres beyond (`32km`), and `10m 이내` when closer than 10 m —
 * a phone's position is rarely finer than that.
 */
internal fun formatDistance(meters: Double): String {
    if (meters < 10.0) return "10m 이내"
    val roundedMeters = (meters / 10.0).roundToInt() * 10
    if (roundedMeters < 1_000) return "${roundedMeters}m"
    val kilometers = meters / 1_000.0
    return if (kilometers < 9.95) {
        String.format(Locale.ROOT, "%.1fkm", kilometers)
    } else {
        "${kilometers.roundToInt()}km"
    }
}

private val COMPASS_WORDS = listOf("북쪽", "북동쪽", "동쪽", "남동쪽", "남쪽", "남서쪽", "서쪽", "북서쪽")

/** Which of the eight compass directions [to] lies in, seen from [from] (`북동쪽`), for spoken descriptions (D-131). */
internal fun compassWord(from: GeoPoint, to: GeoPoint): String {
    val plane = LocalProjection(from).toPlane(to)
    val bearing = (Math.toDegrees(atan2(plane.east, plane.north)) + 360.0) % 360.0
    return COMPASS_WORDS[((bearing / 45.0).roundToInt()) % COMPASS_WORDS.size]
}

/** An axis-aligned rectangle in map pixels: a marker, a label or a fixed map element (D-131). */
internal data class MapBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun overlaps(other: MapBox): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun overlapArea(other: MapBox): Float =
        max(0f, min(right, other.right) - max(left, other.left)) * max(0f, min(bottom, other.bottom) - max(top, other.top))

    fun isInside(width: Float, height: Float): Boolean = left >= 0f && top >= 0f && right <= width && bottom <= height

    /** The part of this box outside a [width]×[height] map, as an area. */
    fun outsideArea(width: Float, height: Float): Float =
        this.width * this.height - MapBox(0f, 0f, width, height).overlapArea(this)

    /** This box moved by the least amount that puts it inside a [width]×[height] map (when it fits). */
    fun clampedInto(width: Float, height: Float): MapBox {
        val dx = when {
            left < 0f -> -left
            right > width -> width - right
            else -> 0f
        }
        val dy = when {
            top < 0f -> -top
            bottom > height -> height - bottom
            else -> 0f
        }
        return MapBox(left + dx, top + dy, right + dx, bottom + dy)
    }

    companion object {
        fun around(center: ScreenPoint, width: Float, height: Float): MapBox =
            MapBox(center.x - width / 2, center.y - height / 2, center.x + width / 2, center.y + height / 2)
    }
}

/** A label to place beside the marker at [anchor], whose own half-size is [markerRadius] (D-131). */
internal data class MapLabelRequest(val anchor: ScreenPoint, val markerRadius: Float, val width: Float, val height: Float)

/**
 * Places each label beside its marker without covering another marker, an earlier label or [fixed] map elements (the
 * scale bar, the north mark), and inside the [width]×[height] map (D-131(5)). Each label tries the end (right) side
 * first, then the start, below and above, then the four diagonals, [gap] pixels from its marker; when every position
 * collides it takes the one that covers least and is moved inside the map. Labels are placed in order, so earlier ones
 * win the free sides.
 */
internal fun placeMapLabels(
    labels: List<MapLabelRequest>,
    width: Float,
    height: Float,
    gap: Float,
    fixed: List<MapBox> = emptyList(),
): List<MapBox> {
    val markers = labels.map { MapBox.around(it.anchor, it.markerRadius * 2, it.markerRadius * 2) }
    val placed = mutableListOf<MapBox>()
    labels.forEachIndexed { index, label ->
        val (x, y) = label.anchor
        val reach = label.markerRadius + gap
        // A diagonal label clears the marker's corner, which a round marker leaves ~0.7 of its radius out.
        val corner = label.markerRadius * DIAGONAL_REACH + gap
        val (w, h) = label.width to label.height
        val candidates = listOf(
            MapBox(x + reach, y - h / 2, x + reach + w, y + h / 2),
            MapBox(x - reach - w, y - h / 2, x - reach, y + h / 2),
            MapBox(x - w / 2, y + reach, x + w / 2, y + reach + h),
            MapBox(x - w / 2, y - reach - h, x + w / 2, y - reach),
            MapBox(x + corner, y - corner - h, x + corner + w, y - corner),
            MapBox(x + corner, y + corner, x + corner + w, y + corner + h),
            MapBox(x - corner - w, y - corner - h, x - corner, y - corner),
            MapBox(x - corner - w, y + corner, x - corner, y + corner + h),
        )
        val obstacles = markers.filterIndexed { other, _ -> other != index } + placed + fixed
        fun cost(box: MapBox): Float = obstacles.sumOf { it.overlapArea(box).toDouble() }.toFloat() +
            box.outsideArea(width, height)
        val chosen = candidates.firstOrNull { box -> box.isInside(width, height) && obstacles.none { it.overlaps(box) } }
            ?: candidates.minBy(::cost).clampedInto(width, height)
        placed += chosen
    }
    return placed
}

/** How far out, in marker radii, a diagonal label starts: past a round marker's corner (√½ ≈ 0.71) with a little air. */
private const val DIAGONAL_REACH = 0.75f

/** The 캠퍼스 지도's camera (D-132): the plane point at the map's centre and the zoom over the home view (1 = home). */
internal data class MapCamera(val centerEast: Double, val centerNorth: Double, val zoom: Double)

/**
 * Pixels on the drawn map under one camera (D-132): [pixelsPerMeter] on both axes, north up, the camera's centre at
 * the middle of the [width]×[height] map.
 */
internal class CameraViewport(
    val width: Float,
    val height: Float,
    val camera: MapCamera,
    val pixelsPerMeter: Double,
) {
    fun toScreen(point: PlanePoint): ScreenPoint = ScreenPoint(
        x = (width / 2 + (point.east - camera.centerEast) * pixelsPerMeter).toFloat(),
        y = (height / 2 - (point.north - camera.centerNorth) * pixelsPerMeter).toFloat(),
    )

    /** The plane point under pixel ([x], [y]); the inverse of [toScreen]. */
    fun toPlane(x: Float, y: Float): PlanePoint = PlanePoint(
        east = camera.centerEast + (x - width / 2) / pixelsPerMeter,
        north = camera.centerNorth - (y - height / 2) / pixelsPerMeter,
    )
}

/**
 * Where the 캠퍼스 지도 can look (D-132). Zoom 1 is the home view: [home] (the campus) fitted into the [width]×[height]
 * map with [padding], as [MapViewport] fits it. Zooming stays between 1 and [maxZoom], and panning keeps the view
 * inside [limit] (the bundled base map), so the student cannot drag the campus out of sight; along an axis where the
 * whole limit fits, the view is centred on it.
 */
internal class MapCameraSpace(
    val home: PlaneBounds,
    val limit: PlaneBounds,
    val width: Float,
    val height: Float,
    val padding: Float,
    val maxZoom: Double = CAMPUS_MAP_MAX_ZOOM,
) {
    /** Pixels per metre at zoom 1. */
    val baseScale: Double = MapViewport(home, width, height, padding, padding).pixelsPerMeter

    val homeCamera: MapCamera
        get() = clamp(MapCamera((home.minEast + home.maxEast) / 2, (home.minNorth + home.maxNorth) / 2, 1.0))

    fun viewport(camera: MapCamera): CameraViewport = CameraViewport(width, height, camera, baseScale * camera.zoom)

    /** [camera] with its zoom in range and its view inside [limit]. */
    fun clamp(camera: MapCamera): MapCamera {
        val zoom = camera.zoom.coerceIn(1.0, max(1.0, maxZoom))
        val pixelsPerMeter = baseScale * zoom
        val halfEast = width / 2 / pixelsPerMeter
        val halfNorth = height / 2 / pixelsPerMeter
        return MapCamera(
            centerEast = clampAxis(camera.centerEast, limit.minEast, limit.maxEast, halfEast),
            centerNorth = clampAxis(camera.centerNorth, limit.minNorth, limit.maxNorth, halfNorth),
            zoom = zoom,
        )
    }

    /** Zooms by [factor] keeping the plane point under pixel ([focusX], [focusY]) where it is (pinch, double tap). */
    fun zoom(camera: MapCamera, factor: Double, focusX: Float, focusY: Float): MapCamera {
        val anchor = viewport(camera).toPlane(focusX, focusY)
        val zoom = (camera.zoom * factor).coerceIn(1.0, max(1.0, maxZoom))
        val pixelsPerMeter = baseScale * zoom
        return clamp(
            MapCamera(
                centerEast = anchor.east - (focusX - width / 2) / pixelsPerMeter,
                centerNorth = anchor.north + (focusY - height / 2) / pixelsPerMeter,
                zoom = zoom,
            ),
        )
    }

    /** Moves the content by ([dx], [dy]) pixels, as a finger drags it. */
    fun pan(camera: MapCamera, dx: Float, dy: Float): MapCamera {
        val pixelsPerMeter = baseScale * camera.zoom
        return clamp(
            camera.copy(
                centerEast = camera.centerEast - dx / pixelsPerMeter,
                centerNorth = camera.centerNorth + dy / pixelsPerMeter,
            ),
        )
    }

    /**
     * A camera centred on [target] (a building's footprint) zoomed so it spans about [fill] of the map, at most
     * [CAMPUS_MAP_FOCUS_MAX_ZOOM] so a small building still shows its neighbours.
     */
    fun focus(target: PlaneBounds, fill: Double = FOCUS_FILL): MapCamera {
        val visibleEast = width / baseScale
        val visibleNorth = height / baseScale
        val zoom = min(
            fill * visibleEast / max(target.eastSpan, 1.0),
            fill * visibleNorth / max(target.northSpan, 1.0),
        ).coerceIn(1.0, max(1.0, min(maxZoom, CAMPUS_MAP_FOCUS_MAX_ZOOM)))
        return clamp(MapCamera((target.minEast + target.maxEast) / 2, (target.minNorth + target.maxNorth) / 2, zoom))
    }

    private fun clampAxis(center: Double, min: Double, max: Double, half: Double): Double =
        if (max - min <= 2 * half) (min + max) / 2 else center.coerceIn(min + half, max - half)

    private companion object {
        /** A focused building spans about a quarter of the map, so its neighbours and paths stay in view. */
        const val FOCUS_FILL = 0.25
    }
}

/** How far the 캠퍼스 지도 zooms in over the home view (D-132): a building then fills much of the map. */
internal const val CAMPUS_MAP_MAX_ZOOM = 12.0

/** How far focusing a building zooms in (D-132), leaving its neighbours in view. */
internal const val CAMPUS_MAP_FOCUS_MAX_ZOOM = 4.5

/**
 * The camera [fraction] of the way from [from] to [to] (D-132), for the move to a focused building: the centre moves
 * in a straight line and the zoom changes geometrically, so the scale changes evenly.
 */
internal fun interpolateCamera(from: MapCamera, to: MapCamera, fraction: Float): MapCamera {
    val t = fraction.toDouble()
    return MapCamera(
        centerEast = from.centerEast + (to.centerEast - from.centerEast) * t,
        centerNorth = from.centerNorth + (to.centerNorth - from.centerNorth) * t,
        zoom = from.zoom * (to.zoom / from.zoom).pow(t),
    )
}

/** A label drawn over its [anchor] (a building's middle or an area's), [width]×[height] pixels (D-132). */
internal data class CenteredLabelRequest(val anchor: ScreenPoint, val width: Float, val height: Float)

/**
 * Places labels over their anchors in priority order (D-132), as map apps do: each label sits centred on its anchor,
 * else just below or above it, never covering an [obstacles] box (markers, their labels, the map's own controls) or an
 * earlier label, and wholly inside the [width]×[height] map. A label with no free spot is left out (null); at a closer
 * zoom there is room for it. A null request (an anchor off the map) stays null.
 */
internal fun placeCenteredLabels(
    labels: List<CenteredLabelRequest?>,
    width: Float,
    height: Float,
    obstacles: List<MapBox> = emptyList(),
    gap: Float = 0f,
): List<MapBox?> {
    val placed = mutableListOf<MapBox>()
    return labels.map { label ->
        if (label == null) return@map null
        val centered = MapBox.around(label.anchor, label.width, label.height)
        val shift = label.height + gap
        val candidates = listOf(
            centered,
            MapBox(centered.left, centered.top + shift, centered.right, centered.bottom + shift),
            MapBox(centered.left, centered.top - shift, centered.right, centered.bottom - shift),
        )
        candidates.firstOrNull { box ->
            box.isInside(width, height) && obstacles.none { it.overlaps(box) } && placed.none { it.overlaps(box) }
        }?.also { placed += it }
    }
}

/** The centre of a ring's area (D-132), where a building's label goes; the vertex average for a degenerate ring. */
internal fun ringCentroid(ring: List<PlanePoint>): PlanePoint {
    require(ring.isNotEmpty())
    var area = 0.0
    var east = 0.0
    var north = 0.0
    ring.indices.forEach { index ->
        val a = ring[index]
        val b = ring[(index + 1) % ring.size]
        val cross = a.east * b.north - b.east * a.north
        area += cross
        east += (a.east + b.east) * cross
        north += (a.north + b.north) * cross
    }
    if (abs(area) < 1e-9) return PlanePoint(ring.map { it.east }.average(), ring.map { it.north }.average())
    return PlanePoint(east / (3 * area), north / (3 * area))
}

/** Whether [point] lies inside the [ring] (even-odd rule), for tapping a building on the map (D-132). */
internal fun ringContains(ring: List<PlanePoint>, point: PlanePoint): Boolean {
    var inside = false
    var previous = ring.lastOrNull() ?: return false
    ring.forEach { current ->
        if ((current.north > point.north) != (previous.north > point.north)) {
            val eastAtNorth = previous.east +
                (point.north - previous.north) * (current.east - previous.east) / (current.north - previous.north)
            if (point.east < eastAtNorth) inside = !inside
        }
        previous = current
    }
    return inside
}
