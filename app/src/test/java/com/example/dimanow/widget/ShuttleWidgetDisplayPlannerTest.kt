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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// D-031: 위젯 캡슐은 앱 홈과 같은 구조로 남은 분·첫차/막차 표기와 함께
// D-010의 절대 출발 시각 병기를 복원한다 (구 rowText의 '시각 금지' 단언을 대체).
class ShuttleWidgetDisplayPlannerTest {
    private val zoneId = ZoneId.of("Asia/Seoul")

    @Test
    fun `upcoming departures become destination-tagged capsules with clock times and last-bus emphasis`() {
        val now = ZonedDateTime.of(2026, 8, 24, 9, 18, 20, 0, zoneId)
        val allDepartures = listOf(departure(8, 0), departure(9, 23), departure(9, 48))
        val board = ShuttleBoard(
            listOf(
                ShuttleBoardRow(
                    originZone = CampusZoneId.YEIN,
                    destinationZone = CampusZoneId.MAIN,
                    departures = listOf(
                        ShuttleCountdown(allDepartures[1], 5),
                        ShuttleCountdown(allDepartures[2], 30),
                    ),
                ),
            ),
        )

        val plan = ShuttleWidgetDisplayPlanner().plan(now, CampusZoneId.YEIN, board, allDepartures)

        assertEquals("DIMA 셔틀 · 엔터관", plan.headerTagText)
        assertNull(plan.emptyText)
        assertEquals(1, plan.rows.size)
        assertEquals("본관행", plan.rows[0].destinationTag)
        assertEquals("5분(09:23)", plan.rows[0].capsules[0].text)
        assertFalse(plan.rows[0].capsules[0].isLast)
        assertEquals("30분(09:48)", plan.rows[0].capsules[1].text)
        assertTrue(plan.rows[0].capsules[1].isLast)
    }

    @Test
    fun `ended service keeps destination rows with the last-bus clock time`() {
        val now = ZonedDateTime.of(2026, 8, 24, 22, 0, 0, 0, zoneId)
        val allDepartures = listOf(departure(8, 0), departure(9, 48))

        val plan = ShuttleWidgetDisplayPlanner().plan(now, CampusZoneId.YEIN, ShuttleBoard(emptyList()), allDepartures)

        assertEquals("DIMA 셔틀 · 엔터관", plan.headerTagText)
        assertEquals(1, plan.rows.size)
        assertEquals("본관행", plan.rows[0].destinationTag)
        assertEquals("운행 종료·막차 09:48", plan.rows[0].capsules[0].text)
    }

    @Test
    fun `no service today falls back to a plain terminal line`() {
        val now = ZonedDateTime.of(2026, 8, 24, 22, 0, 0, 0, zoneId)

        val plan = ShuttleWidgetDisplayPlanner().plan(now, CampusZoneId.MAIN, ShuttleBoard(emptyList()), emptyList())

        assertTrue(plan.rows.isEmpty())
        assertEquals("본관  운행 종료", plan.emptyText)
    }

    @Test
    fun `main evening capsules use stadium labels and header`() {
        val now = ZonedDateTime.of(2026, 8, 24, 19, 30, 0, 0, zoneId)
        val headquarters = ShuttleDeparture("B", "university-headquarters", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(19, 20), CampusZoneId.MAIN, CampusZoneId.YEIN)
        val stadiumFirst = ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(19, 35), CampusZoneId.MAIN, CampusZoneId.YEIN)
        val stadiumLast = ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(20, 0), CampusZoneId.MAIN, CampusZoneId.YEIN)
        val allDepartures = listOf(headquarters, stadiumFirst, stadiumLast)
        val board = ShuttleBoard(
            listOf(
                ShuttleBoardRow(
                    originZone = CampusZoneId.MAIN,
                    destinationZone = CampusZoneId.YEIN,
                    departures = listOf(ShuttleCountdown(stadiumFirst, 5), ShuttleCountdown(stadiumLast, 30)),
                ),
            ),
        )

        val plan = ShuttleWidgetDisplayPlanner().plan(now, CampusZoneId.MAIN, board, allDepartures)

        assertEquals("DIMA 셔틀 · 운동장", plan.headerTagText)
        assertEquals("엔터관행", plan.rows[0].destinationTag)
        assertEquals("5분(19:35)·운동장 전환", plan.rows[0].capsules[0].text)
        assertEquals("30분(20:00)·운동장", plan.rows[0].capsules[1].text)
        assertTrue(plan.rows[0].capsules[1].isLast)
    }

    @Test
    fun `compact style keeps minutes only and preserves every destination row`() {
        val now = ZonedDateTime.of(2026, 8, 24, 9, 18, 20, 0, zoneId)
        val toYein = ShuttleDeparture("A", "headquarters", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(9, 23), CampusZoneId.MAIN, CampusZoneId.YEIN)
        val toOneRoom = ShuttleDeparture("C", "headquarters", "TO_ONE_ROOM", DayOfWeek.MONDAY, LocalTime.of(9, 33), CampusZoneId.MAIN, CampusZoneId.ONE_ROOM)
        val board = ShuttleBoard(
            listOf(
                ShuttleBoardRow(CampusZoneId.MAIN, CampusZoneId.YEIN, listOf(ShuttleCountdown(toYein, 5))),
                ShuttleBoardRow(CampusZoneId.MAIN, CampusZoneId.ONE_ROOM, listOf(ShuttleCountdown(toOneRoom, 15))),
            ),
        )

        val plan = ShuttleWidgetDisplayPlanner().plan(now, CampusZoneId.MAIN, board, listOf(toYein, toOneRoom), compact = true)

        assertEquals(2, plan.rows.size)
        assertEquals("엔터관행", plan.rows[0].destinationTag)
        assertEquals("5분", plan.rows[0].capsules[0].text)
        assertEquals("원룸촌행", plan.rows[1].destinationTag)
        assertEquals("15분", plan.rows[1].capsules[0].text)
    }

    @Test
    fun `compact stadium capsule keeps the short stadium marker`() {
        val now = ZonedDateTime.of(2026, 8, 24, 19, 30, 0, 0, zoneId)
        val stadium = ShuttleDeparture("B-evening", "stadium-stop", "TO_YEIN", DayOfWeek.MONDAY, LocalTime.of(19, 35), CampusZoneId.MAIN, CampusZoneId.YEIN)
        val board = ShuttleBoard(
            listOf(ShuttleBoardRow(CampusZoneId.MAIN, CampusZoneId.YEIN, listOf(ShuttleCountdown(stadium, 5)))),
        )

        val plan = ShuttleWidgetDisplayPlanner().plan(now, CampusZoneId.MAIN, board, listOf(stadium), compact = true)

        assertEquals("5분·운동장", plan.rows[0].capsules[0].text)
    }

    @Test
    fun `outside campus keeps the automatic guidance hint`() {
        val now = ZonedDateTime.of(2026, 8, 24, 9, 0, 0, 0, zoneId)

        val plan = ShuttleWidgetDisplayPlanner().plan(now, CampusZoneId.OUTSIDE, ShuttleBoard(emptyList()), emptyList())

        assertEquals("DIMA 셔틀", plan.headerTagText)
        assertTrue(plan.rows.isEmpty())
        assertEquals("교내 진입 시 자동 안내", plan.emptyText)
    }

    private fun departure(hour: Int, minute: Int) = ShuttleDeparture(
        sourceRouteId = "route-b",
        sourceStopId = "main",
        direction = "TO_MAIN",
        serviceDay = DayOfWeek.MONDAY,
        time = LocalTime.of(hour, minute),
        originZone = CampusZoneId.YEIN,
        destinationZone = CampusZoneId.MAIN,
    )
}
