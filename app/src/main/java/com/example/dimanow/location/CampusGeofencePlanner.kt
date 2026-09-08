package com.example.dimanow.location

import com.example.dimanow.domain.CampusZone
import com.example.dimanow.domain.GeoPoint
import com.example.dimanow.domain.ZoneGeometry
import com.example.dimanow.transit.Bus4402Stop

data class GeofenceSpec(
    val id: String,
    val center: GeoPoint,
    val radiusMeters: Float,
    val dwellMillis: Int,
    val transit: Boolean,
)

object CampusGeofencePlanner {
    fun plan(
        zones: List<CampusZone>,
        transitStops: List<Bus4402Stop>,
        includeBus4402: Boolean,
    ): List<GeofenceSpec> = buildList {
        zones.forEach { zone ->
            val wakeRadius = when (val geometry = zone.geometry) {
                is ZoneGeometry.Circle -> geometry.radiusMeters
                is ZoneGeometry.Polygon -> geometry.wakeRadiusMeters
            }
            add(GeofenceSpec(zone.id.name, zone.center, wakeRadius.toFloat(), 120_000, transit = false))
        }
        if (includeBus4402) {
            transitStops.forEach { stop ->
                add(GeofenceSpec("BUS_4402_${stop.stopNumber}", stop.point, 120f, 30_000, transit = true))
            }
        }
    }
}
