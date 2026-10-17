package com.example.dimanow.domain

import java.time.LocalDate

data class TermSchedule(
    val termStart: LocalDate,
    val termEnd: LocalDate,
    val courses: List<Course>,
    val noClassDates: Set<LocalDate> = emptySet(),
    val guidancePause: GuidancePause? = null,
    val courseOverrides: List<CourseOverride> = emptyList(),
) {
    fun coursesOn(date: LocalDate): List<Course> {
        if (date !in termStart..termEnd || date in noClassDates || guidancePause?.contains(date) == true) return emptyList()
        return courses.filter { it.weekday == date.dayOfWeek }.mapNotNull { course ->
            val override = courseOverrides.firstOrNull { it.courseId == course.id && it.date == date }
            when (override?.kind) {
                CourseOverrideKind.CANCELLED -> null
                CourseOverrideKind.ONLINE -> course.copy(isOnline = true, room = "비대면", zone = CampusZoneId.OUTSIDE)
                CourseOverrideKind.CHANGED -> course.copy(start = override.start ?: course.start,
                    end = override.end ?: course.end, room = override.room ?: course.room)
                null -> course
            }
        }.sortedBy { it.start }
    }
}

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

/**
 * Placeholder schedule while the stored one loads, and the state of a fresh install (D-099 clause 3): the suggested
 * current semester with no courses. Nothing personal ships in the APK.
 */
fun emptyTermSchedule(today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))): TermSchedule {
    val term = com.example.dimanow.calendar.AcademicTerms.suggest(today)
    return TermSchedule(termStart = term.start, termEnd = term.end, courses = emptyList())
}
