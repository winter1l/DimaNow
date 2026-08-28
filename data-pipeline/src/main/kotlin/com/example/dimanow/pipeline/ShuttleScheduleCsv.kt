package com.example.dimanow.pipeline

import com.example.dimanow.sync.ShuttlePayloadRecord
import java.time.LocalTime

object ShuttleScheduleCsv {
    fun parse(csv: String): List<ShuttlePayloadRecord> {
        val seen = mutableSetOf<String>()
        val lines = csv.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(lines.firstOrNull() == HEADER) { "셔틀 CSV 헤더가 올바르지 않습니다." }
        val records = lines.drop(1).mapIndexed { index, line ->
            val columns = line.split(',').map(String::trim)
            require(columns.size == 7) { "셔틀 CSV 열은 7개여야 합니다." }
            val origin = zone(columns[2])
            val destination = zone(columns[4])
            require(origin != destination) { "${index + 1}번째 데이터 행의 출발·목적 구역이 같습니다." }
            require(columns[1].matches(Regex("[A-Za-z0-9_-]{1,40}"))) { "${index + 1}번째 데이터 행의 노선 ID가 올바르지 않습니다." }
            val departure = parseTime(columns[5], index, "출발시각")
            val arrival = columns[6].ifBlank { null }?.let { parseTime(it, index, "도착시각") }
            val record = ShuttlePayloadRecord(
                serviceDay = day(columns[0]),
                routeId = columns[1],
                originZone = origin,
                stopId = stop(columns[3]),
                destinationZone = destination,
                direction = "TO_$destination",
                departureTime = departure.toString(),
                arrivalTime = arrival?.toString(),
            )
            val key = listOf(record.serviceDay, record.routeId, record.stopId, record.departureTime, record.direction).joinToString("|")
            require(seen.add(key)) { "${index + 1}번째 데이터 행이 중복되었습니다." }
            record
        }
        require(records.isNotEmpty()) { "셔틀 CSV에 데이터 행이 없습니다." }
        require(records.size <= 2_000) { "셔틀 CSV 데이터 행이 너무 많습니다." }
        return records
    }

    private fun parseTime(value: String, index: Int, label: String): LocalTime = runCatching { LocalTime.parse(value) }
        .getOrElse { throw IllegalArgumentException("${index + 1}번째 데이터 행의 ${label}이 올바르지 않습니다.") }

    private fun day(value: String): String = mapOf(
        "월" to "MONDAY",
        "화" to "TUESDAY",
        "수" to "WEDNESDAY",
        "목" to "THURSDAY",
        "금" to "FRIDAY",
        "토" to "SATURDAY",
        "일" to "SUNDAY",
    ).getValue(value)

    private fun zone(value: String): String = mapOf(
        "예인관" to "YEIN",
        "엔터관" to "YEIN",
        "본관" to "MAIN",
        "원룸촌" to "ONE_ROOM",
    ).getValue(value)

    private fun stop(value: String): String = mapOf(
        "엔터관" to "yein",
        "본관" to "university-headquarters",
        "운동장" to "stadium-stop",
        "원룸촌" to "one-room",
    ).getValue(value)

    private const val HEADER = "운행요일,노선ID,출발구역,승차정류장,목적구역,출발시각,도착시각"
}
