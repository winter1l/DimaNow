package com.example.dimanow.calendar

import com.example.dimanow.sync.ServiceCalendar
import com.example.dimanow.ui.academicEventDates
import com.example.dimanow.ui.koreanDateLabel
import java.time.LocalDate

/** One morning reminder about a 학사일정 period (D-107); [key] keeps its notification apart from the others. */
internal data class AcademicReminder(val key: String, val title: String, val text: String)

/**
 * What the morning reminder says on [today]: periods that start today, multi-day periods whose last day is
 * today, and exam weeks that start tomorrow. Empty on most days.
 */
internal fun academicReminders(today: LocalDate, calendar: ServiceCalendar): List<AcademicReminder> {
    val agenda = calendar.academicAgenda(today, lookAheadDays = 1)
    return agenda.mapNotNull { event ->
        val exam = event.name == "중간고사" || event.name == "기말고사"
        when {
            event.start == today && event.start == event.end ->
                AcademicReminder("${event.name}|${event.start}|day", "오늘은 ${event.name} 날이에요", koreanDateLabel(event.start))
            event.start == today ->
                AcademicReminder("${event.name}|${event.start}|start", "오늘부터 ${event.name}", "${koreanDateLabel(event.end)}까지예요.")
            event.end == today ->
                AcademicReminder("${event.name}|${event.start}|end", "오늘이 ${event.name} 마지막 날이에요", academicEventDates(event))
            exam && event.start == today.plusDays(1) ->
                AcademicReminder("${event.name}|${event.start}|eve", "내일부터 ${event.name}", academicEventDates(event))
            else -> null
        }
    }
}
