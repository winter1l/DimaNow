package com.example.dimanow.guidance

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.ShuttleDeparture
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ShuttleReportEligibilityTest {
    private val engine = GuidanceEngine()
    private val rows = listOf(
        ShuttleDeparture("B-evening", "yein", "TO_MAIN", DayOfWeek.TUESDAY, LocalTime.of(18, 40), CampusZoneId.YEIN, CampusZoneId.MAIN, LocalTime.of(18, 45)),
        ShuttleDeparture("A-evening", "one-room", "TO_MAIN", DayOfWeek.TUESDAY, LocalTime.of(18, 50), CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, LocalTime.of(18, 55)),
        ShuttleDeparture("A-evening", "stadium-stop", "TO_YEIN", DayOfWeek.TUESDAY, LocalTime.of(18, 55), CampusZoneId.MAIN, CampusZoneId.YEIN, LocalTime.of(19, 0)),
    )

    @Test
    fun `arrival-only calls never appear as departures to report`() {
        val topology = engine.prepareShuttleTopology(rows)
        assertEquals(emptyList<ShuttleReportableEvent>(), engine.reportableMissedEvents(at("18:46"), CampusZoneId.MAIN, topology))
        assertEquals(listOf("18:55"), engine.reportableMissedEvents(at("18:55"), CampusZoneId.MAIN, topology).map { it.stopCall.expectedTime.toString() })
        assertEquals(emptyList<ShuttleReportableEvent>(), engine.reportableMissedEvents(at("19:01"), CampusZoneId.YEIN, topology))
    }

    @Test
    fun `published evening departures remain reportable when the loop includes waiting time`() {
        val laterRows = listOf(
            rows[0].copy(time = LocalTime.of(19, 0), arrivalTime = LocalTime.of(19, 5)),
            rows[1].copy(time = LocalTime.of(19, 20), arrivalTime = LocalTime.of(19, 25)),
            rows[2].copy(time = LocalTime.of(19, 25), arrivalTime = LocalTime.of(19, 30)),
        )
        val topology = engine.prepareShuttleTopology(rows + laterRows)
        assertEquals(listOf("19:25"), engine.reportableMissedEvents(at("19:31"), CampusZoneId.MAIN, topology).map { it.stopCall.expectedTime.toString() })
        assertEquals(LocalTime.of(19, 5), topology.runs.last().stopCalls[1].expectedTime)
    }

    @Test
    fun `last published departure is reportable without a complete following loop`() {
        val last = rows[0].copy(time = LocalTime.of(21, 55), arrivalTime = LocalTime.of(22, 0))
        val topology = engine.prepareShuttleTopology(rows + last)
        assertEquals(listOf("21:55"), engine.reportableMissedEvents(at("21:56"), CampusZoneId.YEIN, topology).map { it.stopCall.expectedTime.toString() })
    }

    private fun at(time: String) = ZonedDateTime.parse("2026-09-08T${time}:00+09:00[Asia/Seoul]")
}
