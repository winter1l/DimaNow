package com.example.dimanow.calendar

import com.example.dimanow.sync.ServiceCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A suggested semester for the timetable; [published] is false when it is only an estimate. */
data class SuggestedTerm(val start: LocalDate, val end: LocalDate, val published: Boolean)

/**
 * Suggests the semester a new user is in (D-099 clause 3): the published DIMA term containing [today], else the
 * next published one, else an estimate from DIMA's usual pattern (spring from the first school day of March for
 * 16 weeks, fall from the last full week of August to the Friday of its 17th week).
 */
object AcademicTerms {
    fun suggest(today: LocalDate, calendar: ServiceCalendar = ServiceCalendarStore.current): SuggestedTerm {
        calendar.termContaining(today)?.let { return SuggestedTerm(it.start, it.end, published = true) }
        val estimate = estimate(today, calendar)
        calendar.nextTermAfter(today)?.takeIf { !it.start.isAfter(estimate.start.plusDays(45)) }
            ?.let { return SuggestedTerm(it.start, it.end, published = true) }
        return estimate
    }

    /** True when [termEnd] has passed, so the user should set up the next semester. */
    fun hasEnded(termEnd: LocalDate, today: LocalDate): Boolean = today.isAfter(termEnd)

    private fun estimate(today: LocalDate, calendar: ServiceCalendar): SuggestedTerm {
        val year = today.year
        val spring = spring(year, calendar)
        val fall = fall(year)
        return when {
            !today.isAfter(spring.end) -> spring
            !today.isAfter(fall.end) -> fall
            else -> spring(year + 1, calendar)
        }
    }

    private fun spring(year: Int, calendar: ServiceCalendar): SuggestedTerm {
        var start = LocalDate.of(year, 3, 2)
        while (start.dayOfWeek == DayOfWeek.SATURDAY || start.dayOfWeek == DayOfWeek.SUNDAY || calendar.isPublicHoliday(start)) {
            start = start.plusDays(1)
        }
        return SuggestedTerm(start, start.plusWeeks(16).minusDays(1), published = false)
    }

    private fun fall(year: Int): SuggestedTerm {
        val start = LocalDate.of(year, 8, 28).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return SuggestedTerm(start, start.plusWeeks(16).plusDays(4), published = false)
    }
}
