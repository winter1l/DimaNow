package com.example.dimanow.location

import com.example.dimanow.domain.CampusZone
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.time.Instant
import com.example.dimanow.domain.ZoneGeometry

data class LocationSample(
    val point: GeoPoint,
    val accuracyMeters: Float,
    val capturedAt: Instant,
)

enum class LocationMode {
    GPS,
    TEST,
}

class LocationResolver {
    fun resolve(
        sample: LocationSample?,
        configuredZones: List<CampusZone>,
        lastResolvedZone: CampusZoneId,
        explicitExitFromAll: Boolean,
        mode: LocationMode = LocationMode.GPS,
        testZone: CampusZoneId? = null,
    ): CampusZoneId = resolve(sample?.point, configuredZones, lastResolvedZone, explicitExitFromAll, mode, testZone)

    fun resolve(
        sample: GeoPoint?,
        configuredZones: List<CampusZone>,
        lastResolvedZone: CampusZoneId,
        explicitExitFromAll: Boolean,
        mode: LocationMode = LocationMode.GPS,
        testZone: CampusZoneId? = null,
    ): CampusZoneId {
        if (mode == LocationMode.TEST && testZone != null) return testZone
        if (explicitExitFromAll) return CampusZoneId.OUTSIDE
        if (sample == null) return lastResolvedZone

        val schoolMatch = configuredZones
            .filter { it.id == CampusZoneId.YEIN || it.id == CampusZoneId.MAIN }
            .map { it to distanceMeters(sample, it.center) }
            .filter { (zone, _) -> contains(zone, sample) }
            .minByOrNull { (_, distance) -> distance }
            ?.first
            ?.id
        if (schoolMatch != null) return schoolMatch

        val oneRoom = configuredZones.firstOrNull { it.id == CampusZoneId.ONE_ROOM }
        if (oneRoom != null && contains(oneRoom, sample)) return CampusZoneId.ONE_ROOM
        return CampusZoneId.OUTSIDE
    }

    private fun contains(zone: CampusZone, point: GeoPoint): Boolean = when (val geometry = zone.geometry) {
        is ZoneGeometry.Circle -> distanceMeters(point, zone.center) <= geometry.radiusMeters
        is ZoneGeometry.Polygon -> pointInPolygon(point, geometry.vertices)
    }

    private fun pointInPolygon(point: GeoPoint, vertices: List<GeoPoint>): Boolean {
        var inside = false
        var previous = vertices.last()
        for (current in vertices) {
            val crosses = (current.latitude > point.latitude) != (previous.latitude > point.latitude) &&
                point.longitude < (previous.longitude - current.longitude) *
                (point.latitude - current.latitude) / (previous.latitude - current.latitude) + current.longitude
            if (crosses) inside = !inside
            previous = current
        }
        return inside
    }

    private fun distanceMeters(first: GeoPoint, second: GeoPoint): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
