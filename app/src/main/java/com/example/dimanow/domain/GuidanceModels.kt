package com.example.dimanow.domain

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Instant

enum class CampusZoneId {
    YEIN,
    MAIN,
    ONE_ROOM,
    OUTSIDE,
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

sealed interface ZoneGeometry {
    data class Circle(val radiusMeters: Int) : ZoneGeometry

    data class Polygon(
        val version: String,
        val vertices: List<GeoPoint>,
        val wakeRadiusMeters: Int,
    ) : ZoneGeometry {
        init {
            require(vertices.size >= 3)
        }
    }
}

data class CampusZone(
    val id: CampusZoneId,
    val center: GeoPoint,
    val radiusMeters: Int = 250,
    val geometry: ZoneGeometry = ZoneGeometry.Circle(radiusMeters),
)

data class Course(
    val weekday: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val name: String,
    val room: String,
    val professor: String,
    val zone: CampusZoneId,
    val id: Long = 0,
)

data class ShuttleDeparture(
    val sourceRouteId: String,
    val sourceStopId: String,
    val direction: String,
    val serviceDay: DayOfWeek,
    val time: LocalTime,
    val originZone: CampusZoneId,
    val destinationZone: CampusZoneId? = null,
    val arrivalTime: LocalTime? = null,
)

enum class GuidancePhase {
    NONE,
    BEFORE_CLASS,
    IN_CLASS,
    RETURN,
}

data class ClassContent(
    val title: String,
    val detail: String,
    val startTime: String? = null,
    val courseName: String? = null,
    val room: String? = null,
    val remainingText: String? = null,
)

data class ShuttleLine(
    val text: String,
)

enum class CountdownMeaning {
    CLASS_START,
    SHUTTLE_DEPARTURE,
}

data class GuidanceSnapshot(
    val classContent: ClassContent?,
    val shuttleLines: List<ShuttleLine>,
    val phase: GuidancePhase,
    val shouldAlert: Boolean = false,
    val countdownTarget: Instant? = null,
    val expiresAt: Instant? = null,
    val countdownMeaning: CountdownMeaning? = null,
    val requiresMinuteUpdates: Boolean = false,
)

enum class MealValidationState {
    VALID,
    NEEDS_REVIEW,
    STALE,
}

data class MealDay(
    val date: java.time.LocalDate,
    val menuLines: List<String>,
    val hours: String,
    val sourceUrl: String,
    val sourceImageUrl: String,
    val validationState: MealValidationState,
)
