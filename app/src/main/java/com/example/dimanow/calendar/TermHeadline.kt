package com.example.dimanow.calendar

import com.example.dimanow.sync.ServiceCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Where today sits in the semester, shown as Home's title (D-106). */
internal sealed interface TermHeadline {
    /** [week] counts Monday-started weeks from the term's first week; [exam] is set during an exam week. */
    data class InTerm(val week: Int, val exam: ServiceCalendar.Exam?) : TermHeadline

    /** Between terms; [daysUntilStart] is null when no next term is known. */
    data class Vacation(val daysUntilStart: Long?) : TermHeadline
}

/**
 * The user's term ([termStart]..[termEnd], which they can edit) gives the week; the published calendar
 * gives exam weeks and, after the user's term ends, the next term's start.
 */
internal fun termHeadline(
    today: LocalDate,
    termStart: LocalDate,
    termEnd: LocalDate,
    calendar: ServiceCalendar = ServiceCalendarStore.current,
): TermHeadline {
    if (!today.isBefore(termStart) && !today.isAfter(termEnd)) {
        val firstMonday = termStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = ChronoUnit.DAYS.between(firstMonday, today) / 7 + 1
        return TermHeadline.InTerm(week.toInt(), calendar.examOn(today))
    }
    val nextStart = if (today.isBefore(termStart)) termStart else calendar.nextTermAfter(today)?.start
    return TermHeadline.Vacation(nextStart?.let { ChronoUnit.DAYS.between(today, it) })
}

/** The part after the week, e.g. `중간고사`, or the vacation countdown. */
internal val TermHeadline.detail: String?
    get() = when (this) {
        is TermHeadline.InTerm -> when (exam) {
            ServiceCalendar.Exam.MIDTERM -> "중간고사"
            ServiceCalendar.Exam.FINAL -> "기말고사"
            null -> null
        }
        is TermHeadline.Vacation -> when (daysUntilStart) {
            null -> null
            1L -> "내일 개강"
            else -> "개강까지 ${daysUntilStart}일"
        }
    }

/** e.g. `8주차`, `15주차 · 기말고사`, `방학 · 개강까지 12일`, `방학`. */
internal val TermHeadline.label: String
    get() {
        val lead = when (this) {
            is TermHeadline.InTerm -> "${week}주차"
            is TermHeadline.Vacation -> "방학"
        }
        return detail?.let { "$lead · $it" } ?: lead
    }
