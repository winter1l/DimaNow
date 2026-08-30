package com.example.dimanow.pipeline

import com.example.dimanow.sync.MealDayPayload
import com.example.dimanow.sync.MealPayload
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int

class GeminiMealPayloadBuilder {
    private val json = Json { ignoreUnknownKeys = true }

    fun build(
        responseJson: String,
        referenceDate: LocalDate,
        hours: String,
        sourceUrl: String,
        sourceImageUrl: String,
    ): MealPayload {
        val root = json.parseToJsonElement(responseJson) as JsonObject
        val days = (root.getValue("days") as JsonArray).map { element ->
            val day = element as JsonObject
            val date = resolveDate(
                referenceDate,
                (day.getValue("month") as JsonPrimitive).int,
                (day.getValue("day") as JsonPrimitive).int,
            )
            val menuLines = (day.getValue("menuLines") as JsonArray)
                .map { (it as JsonPrimitive).content.trim() }
                .filter(String::isNotEmpty)
            require(menuLines.isNotEmpty()) { "요일별 메뉴가 비어 있습니다." }
            date to menuLines
        }
        require(days.size == 5) { "평일 식단 5일을 확인하지 못했습니다." }
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
        require(weekStart in allowedWeekStarts) { "현재 기준 주와 식단 날짜가 일치하지 않습니다." }
        return MealPayload(
            weekStart = weekStart.toString(),
            weekEnd = weekStart.plusDays(6).toString(),
            days = days.map { (date, menuLines) ->
                MealDayPayload(date.toString(), menuLines, hours, sourceUrl, sourceImageUrl)
            },
        )
    }

    private fun resolveDate(referenceDate: LocalDate, month: Int, day: Int): LocalDate =
        ((referenceDate.year - 1)..(referenceDate.year + 1))
            .mapNotNull { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
            .minBy { date -> kotlin.math.abs(ChronoUnit.DAYS.between(referenceDate, date)) }
}
