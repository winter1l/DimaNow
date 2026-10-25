package com.example.dimanow.widget

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.guidance.ShuttleBoard
import com.example.dimanow.guidance.ShuttleBoardRow
import com.example.dimanow.guidance.ShuttleCountdown
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

// D-128: the 다음 셔틀 tile shows the soonest departure in one line, worded like the shuttle widget.
class ShuttleTileTextTest {
    private val now = ZonedDateTime.of(2026, 8, 24, 9, 18, 0, 0, ZoneId.of("Asia/Seoul"))

    @Test
    fun `the soonest destination wins across board rows`() {
        val departures = listOf(
            departure(9, 30, CampusZoneId.MAIN, CampusZoneId.YEIN),
            departure(10, 0, CampusZoneId.MAIN, CampusZoneId.YEIN),
            departure(9, 23, CampusZoneId.MAIN, CampusZoneId.ONE_ROOM),
            departure(9, 50, CampusZoneId.MAIN, CampusZoneId.ONE_ROOM),
        )
        val board = ShuttleBoard(
            listOf(
                row(CampusZoneId.MAIN, CampusZoneId.YEIN, departures[0] to 12, departures[1] to 42),
                row(CampusZoneId.MAIN, CampusZoneId.ONE_ROOM, departures[2] to 5, departures[3] to 32),
            ),
        )

        val text = tileText(CampusZoneId.MAIN, board, departures)

        assertEquals("원룸촌행 5분 후", text.subtitle)
        assertEquals(planFor(CampusZoneId.MAIN, board, departures).spokenSummary, text.spokenSummary)
    }

    @Test
    fun `the last bus is marked`() {
        val departures = listOf(departure(8, 0), departure(9, 23))
        val board = ShuttleBoard(listOf(row(CampusZoneId.YEIN, CampusZoneId.MAIN, departures[1] to 5)))

        assertEquals("본관행 5분 후 · 막차", tileText(CampusZoneId.YEIN, board, departures).subtitle)
    }

    @Test
    fun `departures beyond the hour show the clock and zero minutes read 곧 출발`() {
        val departures = listOf(departure(8, 0), departure(10, 30), departure(11, 0))
        val far = ShuttleBoard(
            listOf(row(CampusZoneId.YEIN, CampusZoneId.MAIN, departures[1] to 72, departures[2] to 102)),
        )
        val imminent = ShuttleBoard(
            listOf(row(CampusZoneId.YEIN, CampusZoneId.MAIN, departures[1] to 0, departures[2] to 30)),
        )

        assertEquals("본관행 10:30 출발", tileText(CampusZoneId.YEIN, far, departures).subtitle)
        assertEquals("본관행 곧 출발", tileText(CampusZoneId.YEIN, imminent, departures).subtitle)
    }

    @Test
    fun `after the last bus the tile reads 운행 종료`() {
        val departures = listOf(departure(8, 0), departure(9, 0))

        assertEquals("운행 종료", tileText(CampusZoneId.YEIN, ShuttleBoard(emptyList()), departures).subtitle)
    }

    @Test
    fun `outside campus and a missing timetable keep the widget messages`() {
        val departures = listOf(departure(9, 23))
        val board = ShuttleBoard(listOf(row(CampusZoneId.YEIN, CampusZoneId.MAIN, departures[0] to 5)))

        assertEquals("교내 진입 시 자동 안내", tileText(CampusZoneId.OUTSIDE, board, departures).subtitle)
        assertEquals("앱을 열어 시간표를 받아 주세요", tileText(CampusZoneId.YEIN, ShuttleBoard(emptyList()), emptyList()).subtitle)
    }

    private fun tileText(origin: CampusZoneId, board: ShuttleBoard, departures: List<ShuttleDeparture>) =
        shuttleTileText(planFor(origin, board, departures), board)

    private fun planFor(origin: CampusZoneId, board: ShuttleBoard, departures: List<ShuttleDeparture>) =
        ShuttleWidgetDisplayPlanner().plan(now, origin, board, departures, compact = true)

    private fun row(
        origin: CampusZoneId,
        destination: CampusZoneId,
        vararg countdowns: Pair<ShuttleDeparture, Long>,
    ) = ShuttleBoardRow(
        originZone = origin,
        destinationZone = destination,
        departures = countdowns.map { (departure, minutes) -> ShuttleCountdown(departure, minutes) },
    )

    private fun departure(
        hour: Int,
        minute: Int,
        origin: CampusZoneId = CampusZoneId.YEIN,
        destination: CampusZoneId = CampusZoneId.MAIN,
    ) = ShuttleDeparture(
        sourceRouteId = "route-b",
        sourceStopId = "stop",
        direction = "TO_${destination.name}",
        serviceDay = DayOfWeek.MONDAY,
        time = LocalTime.of(hour, minute),
        originZone = origin,
        destinationZone = destination,
    )
}
