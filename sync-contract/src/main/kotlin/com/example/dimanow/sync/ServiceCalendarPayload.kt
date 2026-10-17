package com.example.dimanow.sync

import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * Admin-maintained service calendar (D-099 clause 7), published as the `calendar` dataset.
 *
 * - [terms]: DIMA semester periods, used to suggest a term on first run and after a term ends.
 * - [holidays]: Korean public and substitute holidays (KASI 월력요항). The campus A/B lines run on weekdays
 *   except public holidays, and route 4402 uses its Sunday/holiday table on them.
 * - [campusShuttleSuspensions]: extra date ranges without campus shuttle service (vacation, school events).
 */
@Serializable
data class ServiceCalendarPayload(
    val schemaVersion: Int = 1,
    val terms: List<CalendarTerm>,
    val holidays: List<CalendarHoliday>,
    val campusShuttleSuspensions: List<CalendarDateRange> = emptyList(),
)

@Serializable
data class CalendarTerm(val id: String, val start: String, val end: String)

@Serializable
data class CalendarHoliday(val date: String, val name: String)

@Serializable
data class CalendarDateRange(val start: String, val end: String, val reason: String)

/** Validates [payload]; returns null when it is acceptable, otherwise a Korean reason. */
fun serviceCalendarProblem(payload: ServiceCalendarPayload): String? {
    if (payload.schemaVersion != 1) return "지원하지 않는 달력 형식이에요."
    if (payload.terms.size > 40 || payload.holidays.size > 400 || payload.campusShuttleSuspensions.size > 100) {
        return "달력 항목 수가 너무 많아요."
    }
    val textOk = { value: String, max: Int -> value.isNotBlank() && value.length <= max && value.none { it.isISOControl() } }
    var previousTermEnd: LocalDate? = null
    for (term in payload.terms) {
        if (!term.id.matches(Regex("[0-9]{4}-(1|2)"))) return "학기 ID가 올바르지 않아요: ${term.id}"
        val start = term.start.toDateOrNull() ?: return "학기 시작일이 올바르지 않아요: ${term.id}"
        val end = term.end.toDateOrNull() ?: return "학기 종료일이 올바르지 않아요: ${term.id}"
        if (end.isBefore(start) || start.plusDays(200).isBefore(end)) return "학기 기간이 올바르지 않아요: ${term.id}"
        if (previousTermEnd != null && !start.isAfter(previousTermEnd)) return "학기는 날짜순이고 겹치지 않아야 해요: ${term.id}"
        previousTermEnd = end
    }
    val holidayDates = payload.holidays.map { holiday ->
        if (!textOk(holiday.name, 40)) return "공휴일 이름이 올바르지 않아요: ${holiday.date}"
        holiday.date.toDateOrNull() ?: return "공휴일 날짜가 올바르지 않아요: ${holiday.date}"
    }
    if (holidayDates.toSet().size != holidayDates.size) return "같은 공휴일 날짜가 두 번 있어요."
    if (holidayDates != holidayDates.sorted()) return "공휴일은 날짜순이어야 해요."
    for (range in payload.campusShuttleSuspensions) {
        if (!textOk(range.reason, 60)) return "운행 중지 사유가 올바르지 않아요: ${range.start}"
        val start = range.start.toDateOrNull() ?: return "운행 중지 시작일이 올바르지 않아요: ${range.start}"
        val end = range.end.toDateOrNull() ?: return "운행 중지 종료일이 올바르지 않아요: ${range.start}"
        if (end.isBefore(start) || start.plusDays(120).isBefore(end)) return "운행 중지 기간이 올바르지 않아요: ${range.start}"
    }
    return null
}

/** Decoded, validated calendar used by the app and the pipeline. */
data class ServiceCalendar(
    val terms: List<Term>,
    val holidays: Map<LocalDate, String>,
    val campusShuttleSuspensions: List<Suspension>,
) {
    data class Term(val id: String, val start: LocalDate, val end: LocalDate)
    data class Suspension(val start: LocalDate, val end: LocalDate, val reason: String)

    fun isPublicHoliday(date: LocalDate): Boolean = date in holidays

    /** Why the campus shuttle does not run on [date], or null when its normal weekday/Sunday timetable applies. */
    fun campusShuttleSuspension(date: LocalDate): String? {
        campusShuttleSuspensions.firstOrNull { !date.isBefore(it.start) && !date.isAfter(it.end) }?.let { return it.reason }
        // A/B run on weekdays except public holidays; the C line runs on Sundays (no holiday rule is published).
        val holiday = holidays[date] ?: return null
        return if (date.dayOfWeek == DayOfWeek.SUNDAY) null else holiday
    }

    fun termContaining(date: LocalDate): Term? = terms.firstOrNull { !date.isBefore(it.start) && !date.isAfter(it.end) }

    fun nextTermAfter(date: LocalDate): Term? = terms.firstOrNull { it.start.isAfter(date) }

    /** The last date this calendar knows holidays for (holiday rules are unknown after it). */
    val holidayCoverageEnd: LocalDate? get() = holidays.keys.maxOrNull()

    companion object {
        val EMPTY = ServiceCalendar(emptyList(), emptyMap(), emptyList())

        fun from(payload: ServiceCalendarPayload): ServiceCalendar {
            serviceCalendarProblem(payload)?.let { throw IllegalArgumentException(it) }
            return ServiceCalendar(
                terms = payload.terms.map { Term(it.id, LocalDate.parse(it.start), LocalDate.parse(it.end)) },
                holidays = payload.holidays.associate { LocalDate.parse(it.date) to it.name },
                campusShuttleSuspensions = payload.campusShuttleSuspensions.map {
                    Suspension(LocalDate.parse(it.start), LocalDate.parse(it.end), it.reason)
                },
            )
        }
    }
}

private fun String.toDateOrNull(): LocalDate? =
    if (matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) runCatching { LocalDate.parse(this) }.getOrNull() else null
