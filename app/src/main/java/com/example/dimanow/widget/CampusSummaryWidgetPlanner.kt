package com.example.dimanow.widget

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.DisplayVocabulary
import com.example.dimanow.domain.MealDay
import com.example.dimanow.guidance.ShuttleBoard
import com.example.dimanow.guidance.GuidanceEngine
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.example.dimanow.meal.MealServiceState
import com.example.dimanow.meal.mealServiceStatus

data class CampusSummaryWidgetPlan(
    val headerLocationText: String,
    val headerDateText: String,
    val courseTitle: String,
    val courseDetail: String,
    val shuttleTitle: String,
    val shuttleContent: String,
    val mealTitle: String,
    val mealContent: String,
    val requiresMinuteUpdate: Boolean,
) {
    /** One spoken description for the whole widget; the visual lines may be ellipsized. */
    val spokenSummary: String
        get() = listOf(
            "DIMA Now, ${headerLocationText.replace(": ", " ")}, $headerDateText",
            "수업, ${courseTitle.replace(CampusSummaryWidgetPlanner.ONGOING_MARK, "진행 중 ")}, $courseDetail",
            "$shuttleTitle, $shuttleContent",
            "$mealTitle, $mealContent",
        ).joinToString(". ")
}

class CampusSummaryWidgetPlanner {
    fun plan(
        now: ZonedDateTime,
        currentZone: CampusZoneId,
        todayCourses: List<Course>,
        shuttleBoard: ShuttleBoard,
        todayMeal: MealDay?,
    ): CampusSummaryWidgetPlan {
        // 1. 헤더 (위치 + 날짜) - AGENTS.md Seam: 엔터관/본관/원룸촌 라벨 준수
        val originName = DisplayVocabulary.originName(currentZone)
        val headerLocationText = "현재 위치: $originName"
        val weekdayKorean = when (now.dayOfWeek) {
            DayOfWeek.MONDAY -> "월"
            DayOfWeek.TUESDAY -> "화"
            DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"
            DayOfWeek.FRIDAY -> "금"
            DayOfWeek.SATURDAY -> "토"
            DayOfWeek.SUNDAY -> "일"
        }
        val headerDateText = "${now.monthValue}월 ${now.dayOfMonth}일 ($weekdayKorean)"

        // 2. 수업 일정 — Now Bar의 15분 컷오프(D-025)와 무관하게 시간표에서 직접 계산해
        //    진행 중 수업은 종료까지, 다음 수업은 하루 종일 표시한다 (D-031)
        val nowTime = now.toLocalTime()
        val sortedCourses = todayCourses.sortedBy { it.start }
        val ongoingCourse = sortedCourses.firstOrNull { !nowTime.isBefore(it.start) && nowTime.isBefore(it.end) }
        val upcomingCourse = sortedCourses.firstOrNull { it.start.isAfter(nowTime) }
        val minutesUntilUpcoming = upcomingCourse?.let {
            val startsAt = now.toLocalDate().atTime(it.start).atZone(now.zone)
            (Duration.between(now, startsAt).seconds + 59) / 60
        }
        val courseTitle = when {
            ongoingCourse != null -> "$ONGOING_MARK${ongoingCourse.name}"
            upcomingCourse != null -> upcomingCourse.name
            else -> "오늘 수업 없음"
        }
        val courseDetail = when {
            ongoingCourse != null -> "${ongoingCourse.room} · ${ongoingCourse.end.format(TIME)}까지"
            upcomingCourse != null && minutesUntilUpcoming != null && minutesUntilUpcoming in 1L..60L ->
                "시작까지 ${minutesUntilUpcoming}분 · ${upcomingCourse.room}"
            upcomingCourse != null -> "${upcomingCourse.room} · ${upcomingCourse.start.format(TIME)} 시작"
            else -> "등록된 일정 없음"
        }
        val courseNeedsMinuteUpdate = minutesUntilUpcoming != null && minutesUntilUpcoming in 1L..60L

        // 3. 셔틀 정보 (현재 구역 기준)
        val shuttleOriginName = GuidanceEngine().boardingOriginName(
            currentZone,
            shuttleBoard.rows.flatMap { row -> row.departures.map { it.departure } },
        )
        val shuttleTitle = "셔틀 ($shuttleOriginName 출발)"
        val shuttleContent = if (currentZone == CampusZoneId.OUTSIDE) {
            "교내 진입 시 자동 안내"
        } else if (shuttleBoard.rows.isEmpty()) {
            "오늘 운행 종료"
        } else {
            shuttleBoard.rows.joinToString(" · ") { row ->
                "${DisplayVocabulary.originName(row.destinationZone)}행 " +
                    row.departures.joinToString(", ") { countdown ->
                        // D-069: minutes only within one hour, otherwise the departure clock
                        when {
                            countdown.remainingMinutes > ShuttleWidgetDisplayPlanner.COUNTDOWN_WINDOW_MINUTES ->
                                countdown.departure.time.format(TIME)
                            countdown.remainingMinutes <= 0 -> "곧 출발"
                            else -> "${countdown.remainingMinutes}분"
                        }
                    }
            }
        }

        // 4. 식단 정보
        val mealStatus = mealServiceStatus(todayMeal, now.toLocalTime())
        // 식단이 없는 날은 본문이 한 번만 알린다 (D-094(14), 식단 위젯과 같은 규칙)
        val mealTitle = if (mealStatus.state == MealServiceState.NO_MENU) "학생식당" else "학생식당 (${mealStatus.label})"
        val mealContent = when {
            todayMeal != null && todayMeal.menuLines.isNotEmpty() -> todayMeal.menuLines.joinToString(" · ")
            now.dayOfWeek.value >= 6 -> "주말에는 식당을 운영하지 않아요"
            else -> "오늘 등록된 식단이 없어요"
        }

        return CampusSummaryWidgetPlan(
            headerLocationText = headerLocationText,
            headerDateText = headerDateText,
            courseTitle = courseTitle,
            courseDetail = courseDetail,
            shuttleTitle = shuttleTitle,
            shuttleContent = shuttleContent,
            mealTitle = mealTitle,
            mealContent = mealContent,
            requiresMinuteUpdate = courseNeedsMinuteUpdate || shuttleBoard.rows.isNotEmpty(),
        )
    }

    companion object {
        /** Marks an ongoing class (D-031); spoken as `진행 중`. */
        const val ONGOING_MARK = "▶ "
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
