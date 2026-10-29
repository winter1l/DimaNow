package com.example.dimanow.location

import com.example.dimanow.domain.GeoPoint

/** A base-map area on the drawn map's plane (D-132). */
internal class PlaneArea(val kind: MapAreaKind, val rings: List<List<PlanePoint>>, val name: String?)

/** A building footprint on the plane (D-132), with the school building it belongs to, if any. */
internal class PlaneBuilding(
    val osmId: Long,
    val name: String?,
    val ring: List<PlanePoint>,
    val campusBuilding: CampusBuilding?,
) {
    val centroid: PlanePoint = ringCentroid(ring)
    val bounds: PlaneBounds = PlaneBounds.of(ring)
}

/** A road, path or stream on the plane (D-132). */
internal class PlaneLine(val kind: MapLineKind, val points: List<PlanePoint>, val name: String?)

/** Who a map label names, which decides its look (D-132). */
internal enum class MapLabelRole {
    /** A [CampusBuilding]: its number badge and name, shown from the home view on. */
    SCHOOL_BUILDING,
    /** Any other named footprint (학생회관, 원룸, shops). */
    BUILDING,
    /** A named green, field or plaza (운동장, 한울마당, 원룸 A촌). */
    AREA,
    WATER,
}

/**
 * One label the map may draw over [anchor] (D-132), in priority order within [CampusMapScene.labels]; it is drawn only
 * from [minZoom] on, and only where it has room ([placeCenteredLabels]).
 */
internal data class MapLabelSpec(
    val text: String,
    val anchor: PlanePoint,
    val role: MapLabelRole,
    val minZoom: Double,
    val number: Int? = null,
    val building: CampusBuilding? = null,
)

/**
 * The bundled OpenStreetMap base map projected onto the 캠퍼스 지도's plane (D-132), with what drawing, tapping and
 * focusing need: footprints tied to [CampusBuilding]s, the labels in priority order, the campus [home] the map opens on
 * and the [limit] it may be panned within.
 */
internal class CampusMapScene(
    val projection: LocalProjection,
    val areas: List<PlaneArea>,
    val buildings: List<PlaneBuilding>,
    val lines: List<PlaneLine>,
    val limit: PlaneBounds,
    val home: PlaneBounds,
    val attribution: String,
) {
    /** The labels, most important first: school buildings by number, then nearby names, areas and the rest. */
    val labels: List<MapLabelSpec> = buildList {
        CampusBuilding.ordered.forEach { building ->
            buildings.firstOrNull { it.osmId == building.labelWayId }?.let {
                add(MapLabelSpec(building.displayName, it.centroid, MapLabelRole.SCHOOL_BUILDING, 1.0, building.number, building))
            }
        }
        areas.filter { it.name != null && it.kind == MapAreaKind.RESIDENTIAL }.forEach {
            add(MapLabelSpec(it.name!!, ringCentroid(it.rings.first()), MapLabelRole.AREA, 1.0))
        }
        // A school building's other named footprint (학생회관 of 지성관) keeps OSM's name too.
        buildings.filter { it.name != null && it.osmId in CAMPUS_SIDE_WAY_IDS && it.osmId != it.campusBuilding?.labelWayId }.forEach {
            add(MapLabelSpec(it.name!!, it.centroid, MapLabelRole.BUILDING, NEARBY_LABEL_ZOOM))
        }
        areas.filter { it.name != null && it.kind in NAMED_GROUND }.forEach {
            add(MapLabelSpec(it.name!!, ringCentroid(it.rings.first()), MapLabelRole.AREA, NEARBY_LABEL_ZOOM))
        }
        areas.filter { it.name != null && it.kind == MapAreaKind.WATER }.forEach {
            add(MapLabelSpec(it.name!!, ringCentroid(it.rings.first()), MapLabelRole.WATER, 1.0))
        }
        buildings.filter { it.name != null && it.campusBuilding == null && it.osmId !in CAMPUS_SIDE_WAY_IDS }.forEach {
            add(MapLabelSpec(it.name!!, it.centroid, MapLabelRole.BUILDING, CLOSE_LABEL_ZOOM))
        }
    }

    /** Named roads with their polylines, for labels along the road. */
    val namedRoads: Map<String, List<List<PlanePoint>>> = lines
        .filter { it.name != null && it.kind != MapLineKind.STREAM }
        .groupBy({ it.name!! }, { it.points })

    /** The footprint under [point] (the smallest, so a wing wins over what surrounds it), or null. */
    fun buildingAt(point: PlanePoint): PlaneBuilding? = buildings
        .filter { point.east in it.bounds.minEast..it.bounds.maxEast && point.north in it.bounds.minNorth..it.bounds.maxNorth }
        .filter { ringContains(it.ring, point) }
        .minByOrNull { it.bounds.eastSpan * it.bounds.northSpan }

    /** Every footprint of [building] together, for focusing; null when the base map lacks them. */
    fun boundsOf(building: CampusBuilding): PlaneBounds? = buildings
        .filter { it.campusBuilding == building }
        .flatMap { it.ring }
        .takeIf { it.isNotEmpty() }
        ?.let(PlaneBounds::of)

    /** The middle of [building]'s labelled footprint, as a map point (for `지도 앱에서 열기`). */
    fun centerOf(building: CampusBuilding): GeoPoint? = buildings.firstOrNull { it.osmId == building.labelWayId }
        ?.let { projection.toGeo(it.centroid) }

    companion object {
        /**
         * Projects [map] (D-132). [home] covers the school buildings, the 원룸촌 areas and [extraHomePoints] (the 4402
         * stops, and the student when near); [limit] is the base map's box widened to hold the home view.
         */
        fun from(map: CampusBaseMap, projection: LocalProjection, extraHomePoints: List<GeoPoint>): CampusMapScene {
            val areas = map.areas.map { area -> PlaneArea(area.kind, area.rings.map { ring -> ring.map(projection::toPlane) }, area.name) }
            val buildings = map.buildings.map { shape ->
                PlaneBuilding(shape.osmId, shape.name, shape.ring.map(projection::toPlane), CampusBuilding.byWayId(shape.osmId))
            }
            val lines = map.lines.map { line -> PlaneLine(line.kind, line.points.map(projection::toPlane), line.name) }
            val homePoints = buildings.filter { it.campusBuilding != null }.flatMap { it.ring } +
                areas.filter { it.kind == MapAreaKind.RESIDENTIAL }.flatMap { it.rings.first() } +
                extraHomePoints.map(projection::toPlane)
            val home = if (homePoints.isEmpty()) PlaneBounds.of(map.bounds.corners.map(projection::toPlane)) else PlaneBounds.of(homePoints)
            val limit = PlaneBounds.of(map.bounds.corners.map(projection::toPlane) + listOf(PlanePoint(home.minEast, home.minNorth), PlanePoint(home.maxEast, home.maxNorth)))
            return CampusMapScene(projection, areas, buildings, lines, limit, home, map.attribution)
        }

        /** Named footprints next to the school buildings, labelled from a little zoom on (학생회관, 동아리방, 교직원기숙사). */
        private val CAMPUS_SIDE_WAY_IDS = setOf(471_488_711L, 471_507_163L, 471_507_165L)

        private val NAMED_GROUND = setOf(MapAreaKind.GREEN, MapAreaKind.PITCH, MapAreaKind.PLAZA, MapAreaKind.INDUSTRIAL)

        /** Zoom from which names near the school buildings appear. */
        const val NEARBY_LABEL_ZOOM = 1.6

        /** Zoom from which every other name (원룸 buildings, shops) appears. */
        const val CLOSE_LABEL_ZOOM = 3.0
    }
}

/** The plane point back on the map (D-132); the inverse of [LocalProjection.toPlane]. */
internal fun LocalProjection.toGeo(point: PlanePoint): GeoPoint = GeoPoint(
    latitude = origin.latitude + Math.toDegrees(point.north / EARTH_RADIUS_METERS),
    longitude = origin.longitude + Math.toDegrees(point.east / (EARTH_RADIUS_METERS * kotlin.math.cos(Math.toRadians(origin.latitude)))),
)

/**
 * The fixed projection origin of the 캠퍼스 지도 (D-132): the middle of the bundled base map's box, so every layer and
 * the camera share one plane whatever the student's position.
 */
internal val CAMPUS_MAP_ORIGIN = GeoPoint(37.0585, 127.3590)
