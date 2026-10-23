package com.example.dimanow.dorm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One 점호 announcement as the app keeps it (D-112): only what it says about one day — held or not, at what time,
 * until when outings can be applied for, and why not — plus when it arrived. The message itself is not kept.
 */
data class RollCallNotice(
    val date: LocalDate,
    val held: Boolean,
    val time: LocalTime? = null,
    val applyBy: LocalTime? = null,
    val reason: String? = null,
    val receivedAt: Instant,
)

/**
 * Reads a 예인관 KakaoTalk announcement about 점호 (D-112). The 자치회 posts, for example,
 * `[시험 기간 점호 미실시 안내] 금일 점호는 중간고사 기간으로 인해 실시되지 않습니다.` or
 * `[금일 점호 안내] 금일 23시에 점호가 진행될 예정입니다. … 외출 및 외박 신청은 20시 59분까지 가능하며 …`.
 * Returns null for anything that does not clearly say whether 점호 takes place.
 */
object RollCallNoticeParser {
    // Narrow on purpose: the held notice also says "노크하지 않습니다" about closed doors.
    private val CANCELLED = Regex("""(실시|진행)\s*(되|하)지\s*않|미\s*실시|점호\s*(는|가)?\s*없|점호\s*취소|취소\s*되었""")
    private val HELD = Regex("""(실시|진행)\s*(될\s*예정|합니다|됩니다|예정)""")
    private val TIME = Regex("""(\d{1,2})\s*시(?:\s*(\d{1,2})\s*분)?""")
    private val APPLY_BY = Regex("""신청은?\s*(\d{1,2})\s*시(?:\s*(\d{1,2})\s*분)?\s*까지""")
    private val DATE = Regex("""(\d{1,2})\s*월\s*(\d{1,2})\s*일""")
    private val REASON = Regex("""([가-힣]+\s*기간)\s*(?:으로|로)\s*인해""")

    fun parse(text: String, receivedAt: ZonedDateTime): RollCallNotice? {
        if ("점호" !in text) return null
        val body = text.replace(Regex("""\s+"""), " ")
        val cancelled = CANCELLED.containsMatchIn(body)
        val held = !cancelled && HELD.containsMatchIn(body)
        if (!cancelled && !held) return null
        val today = receivedAt.toLocalDate()
        val date = when {
            "금일" in body || "오늘" in body -> today
            "내일" in body || "명일" in body -> today.plusDays(1)
            else -> DATE.find(body)?.let { match ->
                val (month, day) = match.destructured
                runCatching {
                    val candidate = LocalDate.of(today.year, month.toInt(), day.toInt())
                    // A date well before this one is next year's (a December notice about January).
                    if (candidate.isBefore(today.minusMonths(6))) candidate.plusYears(1) else candidate
                }.getOrNull()
            } ?: today
        }
        val applyBy = APPLY_BY.find(body)?.let { time(it) }
        // The 점호 time is the first clock time that is not the application deadline.
        val time = if (held) {
            TIME.findAll(body).filter { match -> APPLY_BY.find(body)?.range?.contains(match.range.first) != true }
                .firstNotNullOfOrNull { time(it) }
        } else {
            null
        }
        return RollCallNotice(
            date = date,
            held = held,
            time = time,
            applyBy = applyBy,
            reason = if (cancelled) REASON.find(body)?.groupValues?.get(1) else null,
            receivedAt = receivedAt.toInstant(),
        )
    }

    private fun time(match: MatchResult): LocalTime? {
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }
}

/** What the 기숙사 page says about 점호 on a day (D-112). */
sealed interface RollCallStatus {
    /** The 자치회 announced it on KakaoTalk. */
    data class Announced(val notice: RollCallNotice) : RollCallStatus

    /** No announcement: the usual Tuesday 23:00 점호; [mayBeSkipped] names an exam week, when it is often called off. */
    data class Scheduled(val time: LocalTime, val mayBeSkipped: String?) : RollCallStatus

    /** A Tuesday that is a public holiday: 점호 is not held on holidays. */
    data class Holiday(val name: String) : RollCallStatus

    /** Not a 점호 day; [next] is the next Tuesday. */
    data class NotToday(val next: LocalDate) : RollCallStatus
}

object RollCallRules {
    /** 점호 is every Tuesday at 23:00, except on public holidays (2026-2 orientation). */
    val USUAL_TIME: LocalTime = LocalTime.of(23, 0)

    /** On a 점호 day, outings must be applied for before 21:00; past the 02:00 curfew, by 23:59 the day before. */
    val USUAL_APPLY_BY: LocalTime = LocalTime.of(21, 0)

    /**
     * Today's 점호: the newest announcement for [date] wins; otherwise the usual rule, noting a public holiday
     * ([holidayName]) or an exam week ([examName]).
     */
    fun status(date: LocalDate, notices: List<RollCallNotice>, holidayName: String?, examName: String?): RollCallStatus {
        notices.filter { it.date == date }.maxByOrNull { it.receivedAt }?.let { return RollCallStatus.Announced(it) }
        if (date.dayOfWeek != DayOfWeek.TUESDAY) {
            val daysToTuesday = (DayOfWeek.TUESDAY.value - date.dayOfWeek.value + 7) % 7
            return RollCallStatus.NotToday(date.plusDays(daysToTuesday.toLong()))
        }
        if (holidayName != null) return RollCallStatus.Holiday(holidayName)
        return RollCallStatus.Scheduled(USUAL_TIME, examName)
    }
}

/** 예인관 opening hours from the 2026-2 orientation (D-112). */
object DormHours {
    val DOOR_OPENS: LocalTime = LocalTime.of(6, 0)
    val DOOR_CLOSES: LocalTime = LocalTime.of(2, 0)

    /** True while [time] lies in [opens, closes), where [closes] may be after midnight. */
    fun isOpen(time: LocalTime, opens: LocalTime, closes: LocalTime): Boolean =
        if (opens < closes) time >= opens && time < closes else time >= opens || time < closes

    fun doorOpen(time: LocalTime): Boolean = isOpen(time, DOOR_OPENS, DOOR_CLOSES)

    val CAMPUS_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
}
