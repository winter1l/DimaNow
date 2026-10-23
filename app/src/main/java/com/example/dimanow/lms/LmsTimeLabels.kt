package com.example.dimanow.lms

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How much time a deadline leaves, as a row's trailing label (D-117); [urgent] within about two days. */
internal data class LmsDueLabel(val text: String, val urgent: Boolean)

/**
 * `30분 남음`, `오늘 23:59`, `내일 18:00`, `3일 남음`, `56일 남음` ahead of [now]; `오늘 지남`, `4일 지남` after it.
 * Days count calendar dates in [zone], so 23:59 tomorrow is `내일` even at 00:01 today. The row's second line
 * already gives the date, so the label never repeats it.
 */
internal fun lmsDueLabel(due: Instant, now: Instant, zone: ZoneId = SEOUL): LmsDueLabel {
    val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), due.atZone(zone).toLocalDate())
    val clock = CLOCK.format(due.atZone(zone))
    return when {
        due.isBefore(now) -> LmsDueLabel(if (days == 0L) "오늘 지남" else "${-days}일 지남", urgent = false)
        Duration.between(now, due) < Duration.ofHours(1) ->
            LmsDueLabel("${Duration.between(now, due).toMinutes().coerceAtLeast(1)}분 남음", urgent = true)
        days == 0L -> LmsDueLabel("오늘 $clock", urgent = true)
        days == 1L -> LmsDueLabel("내일 $clock", urgent = true)
        else -> LmsDueLabel("${days}일 남음", urgent = false)
    }
}

/** A row's second line: the exact deadline, or when an undated item was posted. */
internal fun lmsWhenLine(item: LmsItem, zone: ZoneId = SEOUL): String? =
    item.dueAt?.let { "${FULL.format(it.atZone(zone))} 마감" }
        ?: item.registeredAt?.let { "${DAY.format(it.atZone(zone))} 등록" }

/** When the list was last read from the LMS, said plainly above it (D-117). */
internal fun lmsUpdatedLabel(at: Instant, now: Instant, zone: ZoneId = SEOUL): String {
    val days = ChronoUnit.DAYS.between(at.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
    val day = when (days) {
        0L -> "오늘"
        1L -> "어제"
        else -> DAY.format(at.atZone(zone))
    }
    return "$day ${CLOCK.format(at.atZone(zone))}에 불러왔어요"
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("M월 d일")
private val FULL = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN)
