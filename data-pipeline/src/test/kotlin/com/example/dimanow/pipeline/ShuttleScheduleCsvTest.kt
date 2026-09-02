package com.example.dimanow.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Files

class ShuttleScheduleCsvTest {
    @Test
    fun `현장 시간표의 평일 원룸촌 30분 추가 운행을 보존한다`() {
        val source = Files.readString(Path.of("..", "data-source", "shuttle.csv"))

        val records = ShuttleScheduleCsv.parse(source)
        val additions = records.filter { it.routeId == "A-field-extra" }

        assertEquals(625, records.size)
        assertEquals(20, additions.size)
        assertEquals(
            listOf("14:30", "15:30", "16:30", "17:30"),
            additions.filter { it.serviceDay == "MONDAY" }.map { it.departureTime },
        )
        assertEquals(listOf("ONE_ROOM"), additions.map { it.originZone }.distinct())
        assertEquals(listOf("MAIN"), additions.map { it.destinationZone }.distinct())
        assertEquals(listOf(null), additions.map { it.arrivalTime }.distinct())
    }

    @Test
    fun `운동장 승차 행을 앱 동기화 레코드로 변환한다`() {
        val csv = """
            운행요일,노선ID,출발구역,승차정류장,목적구역,출발시각,도착시각
            목,B-evening,본관,운동장,엔터관,18:10,18:15
        """.trimIndent()

        val row = ShuttleScheduleCsv.parse(csv).single()

        assertEquals("THURSDAY", row.serviceDay)
        assertEquals("B-evening", row.routeId)
        assertEquals("MAIN", row.originZone)
        assertEquals("stadium-stop", row.stopId)
        assertEquals("YEIN", row.destinationZone)
        assertEquals("TO_YEIN", row.direction)
        assertEquals("18:10", row.departureTime)
        assertEquals("18:15", row.arrivalTime)
    }

    @Test
    fun `동일한 물리 운행 행이 두 번 있으면 게시를 거부한다`() {
        val csv = """
            운행요일,노선ID,출발구역,승차정류장,목적구역,출발시각,도착시각
            월,B,본관,본관,엔터관,08:10,08:15
            월,B,본관,본관,엔터관,08:10,08:15
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            ShuttleScheduleCsv.parse(csv)
        }

        assertEquals("2번째 데이터 행이 중복되었습니다.", error.message)
    }

    @Test
    fun `잘못된 시각이나 비어 있는 시간표는 게시하지 않는다`() {
        val invalidTime = """
            운행요일,노선ID,출발구역,승차정류장,목적구역,출발시각,도착시각
            월,B,본관,본관,엔터관,8시10분,08:15
        """.trimIndent()

        assertEquals(
            "1번째 데이터 행의 출발시각이 올바르지 않습니다.",
            assertThrows(IllegalArgumentException::class.java) { ShuttleScheduleCsv.parse(invalidTime) }.message,
        )
        assertEquals(
            "셔틀 CSV에 데이터 행이 없습니다.",
            assertThrows(IllegalArgumentException::class.java) {
                ShuttleScheduleCsv.parse("운행요일,노선ID,출발구역,승차정류장,목적구역,출발시각,도착시각")
            }.message,
        )
    }
}
