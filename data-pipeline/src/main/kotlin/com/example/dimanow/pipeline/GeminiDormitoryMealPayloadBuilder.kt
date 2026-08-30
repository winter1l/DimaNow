package com.example.dimanow.pipeline

import com.example.dimanow.sync.DormitoryMealDayPayload
import com.example.dimanow.sync.DormitoryMealPayload
import com.example.dimanow.sync.DormitoryMealSectionPayload
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int

class DormitoryMealWeekMismatchException : IllegalArgumentException("이번 주 기숙사 식단표가 아니에요")

class GeminiDormitoryMealPayloadBuilder {
    private val json = Json { ignoreUnknownKeys = true }

    fun build(responseJson: String, referenceDate: LocalDate, sourceImageUrl: String): DormitoryMealPayload {
        require(sourceImageUrl.startsWith("https://")) { "기숙사 식단 이미지 주소가 HTTPS가 아닙니다." }
        val root = json.parseToJsonElement(responseJson) as JsonObject
        val days = (root.getValue("days") as JsonArray).map { element ->
            val day = element as JsonObject
            val date = resolveDate(
                referenceDate,
                (day.getValue("month") as JsonPrimitive).int,
                (day.getValue("day") as JsonPrimitive).int,
            )
            val sections = (day.getValue("sections") as JsonArray).map { sectionElement ->
                val section = sectionElement as JsonObject
                val name = (section.getValue("name") as JsonPrimitive).content.trim()
                val hours = (section["hours"] as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty)
                val menuLines = (section.getValue("menuLines") as JsonArray)
                    .map { (it as JsonPrimitive).content.trim() }
                    .filter(String::isNotEmpty)
                require(name.isNotEmpty() && menuLines.isNotEmpty()) { "기숙사 식단 구분이나 메뉴가 비어 있습니다." }
                DormitoryMealSectionPayload(name, hours, menuLines)
            }
            require(sections.isNotEmpty()) { "요일별 기숙사 식단이 비어 있습니다." }
            date to sections
        }
        require(days.size == 5) { "평일 기숙사 식단 5일을 확인하지 못했습니다." }
        val weekStart = days.first().first
        require(
            weekStart.dayOfWeek == DayOfWeek.MONDAY &&
                days.map { it.first } == (0L..4L).map(weekStart::plusDays),
        ) { "월요일부터 금요일까지 연속된 날짜가 아닙니다." }
        val currentWeekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val allowedWeekStarts = buildSet {
            add(currentWeekStart)
            if (referenceDate.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                add(referenceDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY)))
            }
        }
        if (weekStart !in allowedWeekStarts) throw DormitoryMealWeekMismatchException()
        return DormitoryMealPayload(
            weekStart = weekStart.toString(),
            weekEnd = weekStart.plusDays(6).toString(),
            sourceImageUrl = sourceImageUrl,
            days = days.map { (date, sections) -> DormitoryMealDayPayload(date.toString(), sections) },
        )
    }

    private fun resolveDate(referenceDate: LocalDate, month: Int, day: Int): LocalDate =
        ((referenceDate.year - 1)..(referenceDate.year + 1))
            .mapNotNull { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
            .minBy { date -> kotlin.math.abs(ChronoUnit.DAYS.between(referenceDate, date)) }
}
