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
    TRANSIT,
}

enum class GuidanceKind {
    CLASS,
    CAMPUS_SHUTTLE,
    BUS_4402,
}

data class ClassContent(
    val title: String,
    val detail: String,
    val startTime: String? = null,
    val courseName: String? = null,
    val room: String? = null,
    val remainingText: String? = null,
)

/**
 * 안내 표면 한 줄.
 *
 * @param destination 이 줄이 향하는 목적지 표기("본관행"). 나우바 상단 칩이 쓴다 (D-058).
 * @param minutes 가장 가까운 출발까지 남은 분. 목적지와 함께 "본관행 12분"을 만든다.
 */
data class ShuttleLine(
    val text: String,
    val destination: String? = null,
    val minutes: Long? = null,
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
    val kind: GuidanceKind? = null,
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
