package com.example.dimanow.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Synthetic six-course fall 2026 timetable for tests (the app itself ships no timetable, D-099). */
object SampleSchedule {
    fun create(): TermSchedule = TermSchedule(
        termStart = LocalDate.of(2026, 8, 24),
        termEnd = LocalDate.of(2026, 12, 18),
        courses = listOf(
            course(DayOfWeek.MONDAY, 10, 0, 12, 50, "영상제작실습", "본관 301", "김교수"),
            course(DayOfWeek.TUESDAY, 9, 0, 11, 50, "미디어글쓰기", "본관 205", "이교수"),
            course(DayOfWeek.TUESDAY, 13, 0, 15, 50, "사운드디자인", "본관 410", "박교수"),
            course(DayOfWeek.TUESDAY, 16, 0, 18, 50, "방송기획", "본관 512", "최교수"),
            course(DayOfWeek.THURSDAY, 13, 0, 14, 50, "콘텐츠마케팅", "본관 102", "정교수"),
            course(DayOfWeek.FRIDAY, 10, 0, 12, 50, "포트폴리오", "본관 220", "한교수"),
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
