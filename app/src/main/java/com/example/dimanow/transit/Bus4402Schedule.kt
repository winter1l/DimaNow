package com.example.dimanow.transit

import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import com.example.dimanow.domain.GeoPoint

enum class Bus4402ServiceType(val label: String) {
    WEEKDAY("평일"),
    SATURDAY("토요일"),
    SUNDAY_HOLIDAY("일요일·공휴일"),
}

data class Bus4402Schedule(
    val routeId: String,
    val sourceUrl: String,
    val effectiveDate: LocalDate,
    val stops: List<Bus4402Stop>,
    private val timesByServiceType: Map<Bus4402ServiceType, List<LocalTime>>,
) {
    fun times(serviceType: Bus4402ServiceType): List<LocalTime> = timesByServiceType[serviceType].orEmpty()

    fun departures(serviceType: Bus4402ServiceType, stopNumber: String): List<Bus4402Departure> {
        val stop = stops.firstOrNull { it.stopNumber == stopNumber } ?: return emptyList()
        return times(serviceType).map { originTime ->
            Bus4402Departure(
                stop = stop,
                time = originTime.plusMinutes(stop.originOffsetMinutes.toLong()),
                estimated = stop.originOffsetMinutes != 0,
            )
        }
    }

    companion object {
        val official = Bus4402Schedule(
            routeId = "231000139",
            sourceUrl = "https://www.anseong.go.kr/depart/contents.do?mId=0303010000",
            effectiveDate = LocalDate.of(2025, 4, 28),
            stops = listOf(
                Bus4402Stop(
                    stopNumber = "34710",
                    stationId = "231001421",
                    officialName = "동아방송예술대학교시외버스정류장",
                    displayName = "대학 셔틀 정류장",
                    point = GeoPoint(37.0569667, 127.3613500),
                    originOffsetMinutes = 0,
                ),
                Bus4402Stop(
                    stopNumber = "33243",
                    stationId = "231000470",
                    officialName = "동아방송예술대학교입구",
                    displayName = "원룸촌 앞",
                    point = GeoPoint(37.0556667, 127.3650333),
                    originOffsetMinutes = 1,
                ),
            ),
            timesByServiceType = mapOf(
                Bus4402ServiceType.WEEKDAY to times(
                    "05:00", "05:30", "06:00", "06:20", "06:50", "07:10", "07:30", "08:10",
                    "08:50", "09:30", "10:10", "10:50", "11:30", "12:10", "12:40", "13:10",
                    "13:40", "14:10", "14:40", "15:10", "15:40", "16:10", "16:40", "17:10",
                    "17:40", "18:10", "18:40", "19:20", "20:00", "20:40", "21:20", "22:00",
                ),
                Bus4402ServiceType.SATURDAY to times(
                    "05:00", "05:50", "06:40", "07:30", "08:10", "09:00", "09:50", "10:30",
                    "11:10", "11:50", "12:40", "13:20", "14:00", "14:40", "15:20", "16:10",
                    "17:00", "17:40", "18:30", "19:20", "20:00", "20:40", "21:20", "22:00",
                ),
                Bus4402ServiceType.SUNDAY_HOLIDAY to times(
                    "05:00", "05:40", "06:20", "07:00", "07:40", "08:20", "08:50", "09:20",
                    "10:00", "10:40", "11:20", "12:00", "12:40", "13:20", "14:00", "14:40",
                    "15:20", "16:00", "16:40", "17:20", "18:00", "18:30", "19:00", "19:30",
                    "20:00", "20:40", "21:20", "22:00",
                ),
            ),
        )

        private fun times(vararg values: String): List<LocalTime> = values.map(LocalTime::parse)
    }
}

data class Bus4402Stop(
    val stopNumber: String,
    val stationId: String,
    val officialName: String,
    val displayName: String,
    val point: GeoPoint,
    val originOffsetMinutes: Int,
)

data class Bus4402Departure(
    val stop: Bus4402Stop,
    val time: LocalTime,
    val estimated: Boolean,
)

object Bus4402ServiceCalendar {
    private val publicHolidays2026 = setOf(
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 2, 16),
        LocalDate.of(2026, 2, 17),
        LocalDate.of(2026, 2, 18),
        LocalDate.of(2026, 3, 1),
        LocalDate.of(2026, 3, 2),
        LocalDate.of(2026, 5, 5),
        LocalDate.of(2026, 5, 24),
        LocalDate.of(2026, 5, 25),
        LocalDate.of(2026, 6, 3),
        LocalDate.of(2026, 6, 6),
        LocalDate.of(2026, 8, 15),
        LocalDate.of(2026, 8, 17),
        LocalDate.of(2026, 9, 24),
        LocalDate.of(2026, 9, 25),
        LocalDate.of(2026, 9, 26),
        LocalDate.of(2026, 10, 3),
        LocalDate.of(2026, 10, 5),
        LocalDate.of(2026, 10, 9),
        LocalDate.of(2026, 12, 25),
    )

    fun serviceType(date: LocalDate): Bus4402ServiceType = when {
        date in publicHolidays2026 -> Bus4402ServiceType.SUNDAY_HOLIDAY
        date.dayOfWeek == DayOfWeek.SATURDAY -> Bus4402ServiceType.SATURDAY
        date.dayOfWeek == DayOfWeek.SUNDAY -> Bus4402ServiceType.SUNDAY_HOLIDAY
        else -> Bus4402ServiceType.WEEKDAY
    }
}
