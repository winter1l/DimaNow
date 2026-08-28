package com.example.dimanow.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class TermSchedule(
    val termStart: LocalDate,
    val termEnd: LocalDate,
    val courses: List<Course>,
    val noClassDates: Set<LocalDate> = emptySet(),
    val guidancePause: GuidancePause? = null,
)

data class GuidancePause(
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
) {
    init {
        require(!endDateInclusive.isBefore(startDate))
    }

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDate) && !date.isAfter(endDateInclusive)

    val isUntilDisabled: Boolean
        get() = endDateInclusive == LocalDate.MAX

    companion object {
        fun untilDisabled(startDate: LocalDate): GuidancePause =
            GuidancePause(startDate, LocalDate.MAX)
    }
}

object DefaultSchedule {
    fun create(): TermSchedule = TermSchedule(
        termStart = LocalDate.of(2026, 8, 24),
        termEnd = LocalDate.of(2026, 12, 18),
        courses = listOf(
            course(DayOfWeek.MONDAY, 10, 0, 12, 50, "조명기초및실습", "덕성관 402", "이용창"),
            course(DayOfWeek.TUESDAY, 9, 0, 11, 50, "스튜디오기초실습", "기예관 122", "이상운"),
            course(DayOfWeek.TUESDAY, 13, 0, 15, 50, "카메라기초및실습", "덕성관 210", "김재호"),
            course(DayOfWeek.TUESDAY, 16, 0, 18, 50, "음향기초실습", "덕성관 303", "이화현"),
            course(DayOfWeek.THURSDAY, 13, 0, 14, 50, "프리젠테이션영어", "덕성관 510-1", "이효정"),
            course(DayOfWeek.FRIDAY, 10, 0, 12, 50, "방송시스템전기", "기예관 412", "박창묵"),
        ),
    )

    private fun course(
        weekday: DayOfWeek,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        name: String,
        room: String,
        professor: String,
    ) = Course(
        weekday = weekday,
        start = LocalTime.of(startHour, startMinute),
        end = LocalTime.of(endHour, endMinute),
        name = name,
        room = room,
        professor = professor,
        zone = CampusZoneId.MAIN,
    )
}
