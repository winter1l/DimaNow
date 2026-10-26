package com.example.dimanow.location

import com.example.dimanow.domain.CampusZone
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DefaultCampusZones
import com.example.dimanow.domain.GeoPoint
import com.example.dimanow.domain.ZoneGeometry
import com.example.dimanow.transit.Bus4402Schedule
import com.example.dimanow.transit.Bus4402Stop
import java.util.Locale

/**
 * What kind of place a 캠퍼스 지도 marker is (D-131(5)). The school zones and 원룸촌 are told apart as
 * [LocationResolver] does (school circles take priority over the 원룸촌 polygon); 4402 stops are their own kind.
 */
internal enum class CampusPlaceKind(val label: String) {
    SCHOOL_ZONE("학교 구역"),
    HOUSING_ZONE("생활 구역"),
    BUS_4402_STOP("4402 정류장"),
}

/**
 * One place on the 캠퍼스 지도 page (D-131(5)), with a coordinate the app already ships: a verified zone centre
 * (D-005, D-018) or an official 4402 stop. [detail] follows the kind on the list's second line (the stop number).
 */
internal data class CampusPlace(
    val id: String,
    val name: String,
    val kind: CampusPlaceKind,
    val point: GeoPoint,
    val detail: String? = null,
) {
    /** The list's kind line without the distance: `학교 구역`, `4402 정류장 34710`. */
    val kindLine: String get() = listOfNotNull(kind.label, detail).joinToString(" ")
}

/**
 * The places the read-only 캠퍼스 지도 page shows (D-131(5)), built only from coordinates already in the app: the
 * bundled zone centres ([DefaultCampusZones], D-005/D-018; the page never changes a zone) and the official 4402 stops
 * ([Bus4402Schedule.official]). The campus shuttle's boarding stops (본관, 엔터관, 운동장) have no coordinates in the
 * app — departures carry only their origin zone — so they are not drawn.
 */
internal object CampusPlaces {
    /** 본관, 엔터관·예인관, 원룸촌, then the 4402 stops in route order. */
    val all: List<CampusPlace> by lazy { from(DefaultCampusZones.all, Bus4402Schedule.official.stops) }

    /** The zone areas drawn faintly under the markers: each zone's approved polygon, by zone (D-018). */
    val zoneAreas: List<Pair<CampusZoneId, List<GeoPoint>>> by lazy { areasOf(DefaultCampusZones.all) }

    fun from(zones: List<CampusZone>, stops: List<Bus4402Stop>): List<CampusPlace> {
        val zonePlaces = ZONE_ORDER.mapNotNull { id ->
            zones.firstOrNull { it.id == id }?.let { zone ->
                CampusPlace(
                    id = "zone_${id.name.lowercase(Locale.ROOT)}",
                    name = zoneName(id),
                    kind = if (id == CampusZoneId.ONE_ROOM) CampusPlaceKind.HOUSING_ZONE else CampusPlaceKind.SCHOOL_ZONE,
                    point = zone.center,
                )
            }
        }
        val stopPlaces = stops.map { stop ->
            CampusPlace(
                id = "bus4402_${stop.stopNumber}",
                name = stop.displayName,
                kind = CampusPlaceKind.BUS_4402_STOP,
                point = stop.point,
                detail = stop.stopNumber,
            )
        }
        return zonePlaces + stopPlaces
    }

    fun areasOf(zones: List<CampusZone>): List<Pair<CampusZoneId, List<GeoPoint>>> = ZONE_ORDER.mapNotNull { id ->
        (zones.firstOrNull { it.id == id }?.geometry as? ZoneGeometry.Polygon)?.let { id to it.vertices }
    }

    /** D-131(5) names 엔터관·예인관 together: the shuttle stop is 엔터관, the zone around it 예인관. */
    private fun zoneName(id: CampusZoneId): String = when (id) {
        CampusZoneId.MAIN -> "본관"
        CampusZoneId.YEIN -> "엔터관·예인관"
        CampusZoneId.ONE_ROOM -> "원룸촌"
        CampusZoneId.OUTSIDE -> error("OUTSIDE has no place")
    }

    private val ZONE_ORDER = listOf(CampusZoneId.MAIN, CampusZoneId.YEIN, CampusZoneId.ONE_ROOM)
}

/** How far from the map's origin the student may be and still be drawn on the 캠퍼스 지도 (D-131(5), D-132). */
internal const val CAMPUS_MAP_STUDENT_LIMIT_METERS = 1_000.0

/**
 * The list's second line for [place] (D-131(5)): its kind, then how far it is from the student when the position is
 * known (`학교 구역 · 내 위치에서 320m`).
 */
internal fun campusPlaceSupportingText(place: CampusPlace, student: GeoPoint?): String =
    listOfNotNull(
        place.kindLine,
        student?.let { "내 위치에서 ${formatDistance(greatCircleMeters(it, place.point))}" },
    ).joinToString(" · ")

/**
 * What a screen reader says for [place]'s marker on the drawn map (D-131(5)): its name and kind, and, when the
 * student's position is known, the direction and distance from there (`본관, 학교 구역, 내 위치에서 북동쪽 320m`).
 */
internal fun campusPlaceSpokenDescription(place: CampusPlace, student: GeoPoint?): String {
    val whereFromStudent = student?.let {
        val meters = greatCircleMeters(it, place.point)
        if (meters < 10.0) "내 위치에서 10m 이내" else "내 위치에서 ${compassWord(it, place.point)} ${formatDistance(meters)}"
    }
    return listOfNotNull(place.name, place.kindLine, whereFromStudent).joinToString(", ")
}

/**
 * The `geo:` link that opens [place] in the phone's map app (D-131(5)): `geo:<lat>,<lon>?q=<lat>,<lon>(<label>)`, the
 * form Android documents for a labelled point. Coordinates keep seven decimals (about a centimetre) in a
 * locale-independent form; the label is percent-encoded so spaces, `·` and parentheses cannot end the query early.
 */
internal fun campusPlaceGeoUri(place: CampusPlace): String = geoUri(place.point, place.name)

/** The labelled `geo:` link for any [point] (D-131(5)); a school building's middle uses it too (D-132). */
internal fun geoUri(point: GeoPoint, label: String): String {
    val latitude = String.format(Locale.ROOT, "%.7f", point.latitude)
    val longitude = String.format(Locale.ROOT, "%.7f", point.longitude)
    return "geo:$latitude,$longitude?q=$latitude,$longitude(${percentEncode(label)})"
}

/** RFC 3986 percent-encoding of [text]'s UTF-8 bytes, keeping only the unreserved characters (D-131). */
internal fun percentEncode(text: String): String = buildString {
    text.toByteArray(Charsets.UTF_8).forEach { byte ->
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        if (value < 0x80 && (char.isLetterOrDigit() || char in "-_.~")) {
            append(char)
        } else {
            append('%').append(HEX[value shr 4]).append(HEX[value and 0x0F])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
