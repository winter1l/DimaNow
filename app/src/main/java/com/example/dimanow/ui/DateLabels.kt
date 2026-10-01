package com.example.dimanow.ui

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A class start counts down in minutes only inside this window (D-069 style, D-031 widget rule). */
internal const val CLASS_COUNTDOWN_WINDOW_MINUTES = 60L

private val KOREAN_DATE = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val KOREAN_DATE_WITH_YEAR = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/**
 * A date written for people rather than as ISO text (D-094(14)): `9월 30일 (수)`.
 * The year is added only when it differs from [today]'s year.
 */
internal fun koreanDateLabel(date: LocalDate, today: LocalDate? = null): String =
    if (today != null && date.year != today.year) date.format(KOREAN_DATE_WITH_YEAR) else date.format(KOREAN_DATE)

/** Whole minutes until [startsAt], rounded up (the same ceiling the widgets and notifications use). */
internal fun minutesUntil(now: ZonedDateTime, startsAt: ZonedDateTime): Long =
    (Duration.between(now, startsAt).toMillis().coerceAtLeast(0) + 59_999L) / 60_000L

/**
 * Label for a class that has not started yet (D-094(14), D-069 style).
 *
 * Inside [CLASS_COUNTDOWN_WINDOW_MINUTES] it counts down (`시작까지 12분`); further away it names the
 * start clock (`13:00 시작`, `내일 13:00 시작`, `금요일 13:00 시작`) instead of a large minute count.
 */
internal fun classStartLabel(now: ZonedDateTime, startsAt: ZonedDateTime): String {
    val minutes = minutesUntil(now, startsAt)
    if (minutes in 1..CLASS_COUNTDOWN_WINDOW_MINUTES) return "시작까지 ${minutes}분"
    val local = startsAt.withZoneSameInstant(now.zone)
    val clock = local.format(CLOCK)
    val today = now.toLocalDate()
    val day = local.toLocalDate()
    return when {
        day == today -> "$clock 시작"
        day == today.plusDays(1) -> "내일 $clock 시작"
        day.isAfter(today) && day.isBefore(today.plusDays(7)) -> "${koreanWeekdayLabel(day.dayOfWeek)} $clock 시작"
        else -> "${koreanDateLabel(day, today)} $clock 시작"
    }
}

/** A date with its year, for values that can span years such as term dates: `2026년 8월 24일 (월)`. */
internal fun koreanDateLabelWithYear(date: LocalDate): String = date.format(KOREAN_DATE_WITH_YEAR)

/** Full Korean weekday name: `월요일` … `일요일`. */
fun koreanWeekdayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "월요일"
    DayOfWeek.TUESDAY -> "화요일"
    DayOfWeek.WEDNESDAY -> "수요일"
    DayOfWeek.THURSDAY -> "목요일"
    DayOfWeek.FRIDAY -> "금요일"
    DayOfWeek.SATURDAY -> "토요일"
    DayOfWeek.SUNDAY -> "일요일"
}
