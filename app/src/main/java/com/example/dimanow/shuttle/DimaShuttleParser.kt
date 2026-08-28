package com.example.dimanow.shuttle

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.ShuttleDeparture
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.jsoup.Jsoup

class DimaShuttleParser {
    fun parse(html: String): List<ShuttleDeparture> {
        val departures = mutableListOf<ShuttleDeparture>()
        Jsoup.parse(html).select("table").forEach { table ->
            val caption = table.selectFirst("caption")?.text().orEmpty()
            val rows = table.select("tbody tr").map { row -> row.select("td").map { it.text().trim() } }
            when {
                caption.contains("원룸촌에서 대학본부") -> rows.forEach { cells ->
                    val outbound = cells.getOrNull(0).asTime()
                    val inbound = cells.getOrNull(1).asTime()
                    weekdays().forEach { day ->
                        outbound?.let { departures += departure("A", "one-room", "TO_MAIN", day, it, CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, it.plusMinutes(5)) }
                        inbound?.let { departures += departure("A", "university-headquarters", "TO_ONE_ROOM", day, it, CampusZoneId.MAIN, CampusZoneId.ONE_ROOM, it.plusMinutes(5)) }
                    }
                }
                caption.contains("원룸촌에서 운동장정류장") -> rows.forEach { cells ->
                    val oneRoom = cells.getOrNull(0).asTime()
                    val main = cells.getOrNull(1).asTime()
                    weekdays().forEach { day ->
                        oneRoom?.let { departures += departure("A-evening", "one-room", "TO_MAIN", day, it, CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, main ?: it.plusMinutes(5)) }
                        main?.let { departures += departure("A-evening", "stadium-stop", "TO_YEIN", day, it, CampusZoneId.MAIN, CampusZoneId.YEIN, it.plusMinutes(5)) }
                    }
                }
                caption.contains("대학본부에서 엔터관") -> rows.forEach { cells ->
                    val main = cells.getOrNull(0).asTime()
                    val yein = cells.getOrNull(1).asTime()
                    weekdays().forEach { day ->
                        main?.let { departures += departure("B", "university-headquarters", "TO_YEIN", day, it, CampusZoneId.MAIN, CampusZoneId.YEIN, it.plusMinutes(5)) }
                        yein?.let { departures += departure("B", "yein", "TO_MAIN", day, it, CampusZoneId.YEIN, CampusZoneId.MAIN, it.plusMinutes(5)) }
                    }
                }
                caption.contains("운동장정류장에서 엔터관") -> rows.forEach { cells ->
                    val main = cells.getOrNull(0).asTime()
                    val yein = cells.getOrNull(1).asTime()
                    weekdays().forEach { day ->
                        main?.let { departures += departure("B-evening", "stadium-stop", "TO_YEIN", day, it, CampusZoneId.MAIN, CampusZoneId.YEIN, it.plusMinutes(5)) }
                        yein?.let { departures += departure("B-evening", "yein", "TO_MAIN", day, it, CampusZoneId.YEIN, CampusZoneId.MAIN, it.plusMinutes(5)) }
                    }
                }
                caption.contains("일요일에 운행") -> rows.forEach { cells ->
                    val oneRoom = cells.getOrNull(0).asTime()
                    val yein = cells.getOrNull(2).asTime()
                    oneRoom?.let { departures += departure("C", "one-room", "TO_YEIN", DayOfWeek.SUNDAY, it, CampusZoneId.ONE_ROOM, CampusZoneId.YEIN, yein) }
                    yein?.let { departures += departure("C", "yein", "TO_ONE_ROOM", DayOfWeek.SUNDAY, it, CampusZoneId.YEIN, CampusZoneId.ONE_ROOM, it.plusMinutes(10)) }
                }
            }
        }
        return departures.sortedWith(compareBy(ShuttleDeparture::serviceDay, ShuttleDeparture::time, ShuttleDeparture::sourceRouteId))
    }

    private fun departure(
        route: String,
        stop: String,
        direction: String,
        day: DayOfWeek,
        time: LocalTime,
        origin: CampusZoneId,
        destination: CampusZoneId,
        arrival: LocalTime?,
    ) = ShuttleDeparture(route, stop, direction, day, time, origin, destination, arrival)

    private fun weekdays() = DayOfWeek.entries.filter { it.value <= DayOfWeek.FRIDAY.value }

    private fun String?.asTime(): LocalTime? = this
        ?.takeIf { it.matches(Regex("\\d{1,2}:\\d{2}")) }
        ?.let { LocalTime.parse(it, DateTimeFormatter.ofPattern("H:mm")) }
}
