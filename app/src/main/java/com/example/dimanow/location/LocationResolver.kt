package com.example.dimanow.location

import com.example.dimanow.domain.CampusZone
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.time.Instant
import java.time.Duration
import com.example.dimanow.domain.ZoneGeometry
import com.example.dimanow.transit.Bus4402Stop

data class NearbyTransitStop(
    val stopNumber: String,
    val displayName: String,
)

data class TransitStopProximityState(
    val candidateStopNumber: String? = null,
    val candidateSince: Instant? = null,
    val activeStopNumber: String? = null,
    val lastValidAt: Instant? = null,
)

data class TransitStopResolution(
    val stop: NearbyTransitStop?,
    val state: TransitStopProximityState,
)

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
    fun shouldPollNearbyTransitStop(
        activeGeofenceIds: Set<String>,
        previous: TransitStopProximityState,
        guidanceEnabled: Boolean,
    ): Boolean = guidanceEnabled && (
        activeGeofenceIds.any { it.startsWith(TRANSIT_GEOFENCE_PREFIX) } ||
            previous.candidateStopNumber != null ||
            previous.activeStopNumber != null
        )

    fun resolveNearbyTransitStop(
        sample: LocationSample?,
        now: Instant,
        stops: List<Bus4402Stop>,
        previous: TransitStopProximityState,
        activationDistanceMeters: Double = 40.0,
        releaseDistanceMeters: Double = 70.0,
        dwell: Duration = Duration.ofSeconds(30),
        maxSampleAge: Duration = Duration.ofSeconds(30),
        maxAccuracyMeters: Float = 25f,
        noLocationTimeout: Duration = Duration.ofMinutes(2),
    ): TransitStopResolution {
        val activeStop = stops.firstOrNull { it.stopNumber == previous.activeStopNumber }
        if (sample == null) {
            val mayKeep = activeStop != null && previous.lastValidAt?.let {
                Duration.between(it, now) <= noLocationTimeout
            } == true
            return TransitStopResolution(
                stop = activeStop?.takeIf { mayKeep }?.toNearbyTransitStop(),
                state = if (mayKeep) previous else TransitStopProximityState(),
            )
        }

        val age = Duration.between(sample.capturedAt, now)
        if (age.isNegative || age > maxSampleAge || sample.accuracyMeters > maxAccuracyMeters) {
            return TransitStopResolution(stop = null, state = previous.copy(activeStopNumber = null))
        }

        if (activeStop != null) {
            val distance = distanceMeters(sample.point, activeStop.point)
            if (distance <= releaseDistanceMeters) {
                return TransitStopResolution(
                    stop = activeStop.toNearbyTransitStop(),
                    state = previous.copy(lastValidAt = now),
                )
            }
        }

        val nearest = stops
            .map { it to distanceMeters(sample.point, it.point) }
            .filter { (_, distance) -> distance <= activationDistanceMeters }
            .minByOrNull { (_, distance) -> distance }
            ?.first
            ?: return TransitStopResolution(null, TransitStopProximityState())
        val candidateSince = previous.candidateSince
            ?.takeIf { previous.candidateStopNumber == nearest.stopNumber }
            ?: now
        val activated = Duration.between(candidateSince, now) >= dwell
        val state = TransitStopProximityState(
            candidateStopNumber = nearest.stopNumber,
            candidateSince = candidateSince,
            activeStopNumber = nearest.stopNumber.takeIf { activated },
            lastValidAt = now,
        )
        return TransitStopResolution(nearest.toNearbyTransitStop().takeIf { activated }, state)
    }

    fun isFreshSampleAtZone(
        sample: LocationSample?,
        now: Instant,
        configuredZones: List<CampusZone>,
        expectedZone: CampusZoneId,
        maxAge: Duration = Duration.ofMinutes(2),
        maxAccuracyMeters: Float = 100f,
    ): Boolean {
        if (sample == null || expectedZone == CampusZoneId.OUTSIDE) return false
        val age = Duration.between(sample.capturedAt, now)
        if (age.isNegative || age > maxAge || sample.accuracyMeters > maxAccuracyMeters) return false
        return resolve(
            sample = sample,
            configuredZones = configuredZones,
            lastResolvedZone = CampusZoneId.OUTSIDE,
            explicitExitFromAll = false,
            mode = LocationMode.GPS,
        ) == expectedZone
    }

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

    private companion object {
        const val TRANSIT_GEOFENCE_PREFIX = "BUS_4402_"
    }
}

private fun Bus4402Stop.toNearbyTransitStop() = NearbyTransitStop(stopNumber, displayName)
