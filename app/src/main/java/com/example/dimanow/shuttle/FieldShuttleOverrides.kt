package com.example.dimanow.shuttle

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.ShuttleDeparture
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Verified additions written on the physical shuttle timetable photographed on 2026-09-02.
 *
 * These departures are projected over the official cache. The cache itself remains an exact copy
 * of the published source, and missing return/arrival times are intentionally not inferred.
 */
object FieldShuttleOverrides {
    private const val ROUTE_ID = "A-field-extra"
    private const val STOP_ID = "one-room"
    private const val DIRECTION = "TO_MAIN"

    private val serviceDays = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )
    private val departureTimes = listOf(
        LocalTime.of(14, 30),
        LocalTime.of(15, 30),
        LocalTime.of(16, 30),
        LocalTime.of(17, 30),
    )

    fun apply(officialDepartures: List<ShuttleDeparture>): List<ShuttleDeparture> {
        val existingKeys = officialDepartures.mapTo(mutableSetOf()) { it.eventKey() }
        val additions = buildList {
            serviceDays.forEach { serviceDay ->
                departureTimes.forEach { time ->
                    val departure = ShuttleDeparture(
                        sourceRouteId = ROUTE_ID,
                        sourceStopId = STOP_ID,
                        direction = DIRECTION,
                        serviceDay = serviceDay,
                        time = time,
                        originZone = CampusZoneId.ONE_ROOM,
                        destinationZone = CampusZoneId.MAIN,
                        arrivalTime = null,
                    )
                    if (existingKeys.add(departure.eventKey())) add(departure)
                }
            }
        }
        return officialDepartures + additions
    }

    private fun ShuttleDeparture.eventKey() = listOf(
        sourceRouteId,
        sourceStopId,
        direction,
        serviceDay.name,
        time.toString(),
        originZone.name,
        destinationZone?.name.orEmpty(),
    ).joinToString("|")
}
