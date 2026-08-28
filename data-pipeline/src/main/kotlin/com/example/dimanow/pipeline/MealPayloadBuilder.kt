package com.example.dimanow.pipeline

import com.example.dimanow.sync.MealDayPayload
import com.example.dimanow.sync.MealPayload
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
}

sealed interface MealValidationResult {
    data class Valid(val weekStart: LocalDate, val days: Map<LocalDate, List<String>>) : MealValidationResult
    data class Invalid(val reason: String) : MealValidationResult
}

class MealPayloadBuilder(private val minimumMenuLinesPerDay: Int = 2) {
    fun build(
        lines: List<OcrLine>,
        referenceDate: LocalDate,
        hours: String,
        sourceUrl: String,
        sourceImageUrl: String,
    ): MealPayload {
        val validation = validate(lines, referenceDate)
        require(validation is MealValidationResult.Valid) {
            (validation as MealValidationResult.Invalid).reason
        }
        return MealPayload(
            weekStart = validation.weekStart.toString(),
            weekEnd = validation.weekStart.plusDays(6).toString(),
            days = validation.days.map { (date, menuLines) ->
                MealDayPayload(date.toString(), menuLines, hours, sourceUrl, sourceImageUrl)
            },
        )
    }

    fun validate(lines: List<OcrLine>, referenceDate: LocalDate): MealValidationResult {
        val headers = lines.mapNotNull { line ->
            val match = DATE_HEADER.find(line.text) ?: return@mapNotNull null
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val date = runCatching { LocalDate.of(resolveYear(referenceDate, month), month, day) }.getOrNull()
                ?: return@mapNotNull null
            Header(line, date)
        }.sortedBy { it.date }
        if (headers.size != 5) return MealValidationResult.Invalid("평일 날짜 헤더 5개를 확인하지 못했습니다.")
        val weekStart = headers.first().date
        if (weekStart.dayOfWeek != DayOfWeek.MONDAY || headers.map { it.date } != (0L..4L).map(weekStart::plusDays)) {
            return MealValidationResult.Invalid("월요일부터 금요일까지 연속된 날짜가 아닙니다.")
        }
        val nextWeekAllowed = referenceDate.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) &&
            weekStart == referenceDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        if (referenceDate !in weekStart..weekStart.plusDays(6) && !nextWeekAllowed) {
            return MealValidationResult.Invalid("현재 기준 주와 식단 날짜가 일치하지 않습니다.")
        }
        val headerBottom = headers.maxOf { it.line.bottom }
        val menu = headers.associate { it.date to mutableListOf<String>() }
        lines.asSequence()
            .filter { it.top > headerBottom && DATE_HEADER.find(it.text) == null }
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.length >= 2 && it.text !in IGNORED }
            .filter { isPlausibleMenuLine(it.text) }
            .sortedBy { it.top }
            .forEach { line ->
                val closest = headers.minBy { abs(it.line.centerX - line.centerX) }
                menu.getValue(closest.date) += line.text
            }
        if (menu.values.any { it.size < minimumMenuLinesPerDay }) {
            return MealValidationResult.Invalid("요일별 메뉴 줄 수가 최소 기준보다 적습니다.")
        }
        return MealValidationResult.Valid(weekStart, menu.mapValues { it.value.toList() })
    }

    private fun isPlausibleMenuLine(text: String): Boolean {
        val normalized = text.uppercase()
        return !TIME_RANGE.containsMatchIn(text) &&
            !text.contains("동아방송예술대학교") &&
            !normalized.contains("DONG-AH") &&
            !normalized.contains("INSTITUTE OF MEDIA") &&
            !text.contains("식사 제공 시간") &&
            !text.contains("변경될 수 있습니다")
    }

    private fun resolveYear(reference: LocalDate, month: Int): Int = when {
        reference.monthValue == 12 && month == 1 -> reference.year + 1
        reference.monthValue == 1 && month == 12 -> reference.year - 1
        else -> reference.year
    }

    private data class Header(val line: OcrLine, val date: LocalDate)

    private companion object {
        val DATE_HEADER = Regex("(\\d{1,2})\\s*[./]\\s*(\\d{1,2})")
        val TIME_RANGE = Regex("\\d{1,2}:\\d{2}\\s*[~–-]\\s*\\d{1,2}:\\d{2}")
        val IGNORED = setOf("메뉴", "중식", "학생식당")
    }
}
