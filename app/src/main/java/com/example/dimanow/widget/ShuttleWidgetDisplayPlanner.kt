package com.example.dimanow.widget

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DisplayVocabulary
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.guidance.GuidanceEngine
import com.example.dimanow.guidance.ShuttleBoard
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Capsule fill, mirroring the app's shuttle capsules (D-040/D-083, last service D-016). */
enum class ShuttleWidgetCapsuleTone {
    /** The nearest departure of a destination: solid primary. */
    NEAREST,

    /** The nearest departure is also the last one: solid error. */
    NEAREST_LAST,

    /** A later departure: the soft container. */
    LATER,

    /** A later departure that is the last one: error container. */
    LAST,

    /** No more departures today: the soft container. */
    ENDED,
}

data class ShuttleWidgetCapsule(
    val text: String,
    val tone: ShuttleWidgetCapsuleTone,
) {
    val isLast: Boolean get() = tone == ShuttleWidgetCapsuleTone.NEAREST_LAST || tone == ShuttleWidgetCapsuleTone.LAST
}

data class ShuttleWidgetRow(
    val destinationTag: String,
    val capsules: List<ShuttleWidgetCapsule>,
)

/**
 * 앱 홈 화면의 셔틀 캡슐(D-020/D-021)과 같은 구조를 위젯용으로 계획한다.
 * 목적지 태그 + 다가오는 출발 최대 2개 캡슐, 운행 종료 시 막차 시각 안내.
 * [spokenSummary]는 위젯 전체를 한 번에 읽어 주는 접근성 설명이다.
 */
data class ShuttleWidgetDisplayPlan(
    val headerTagText: String,
    val rows: List<ShuttleWidgetRow>,
    val emptyText: String? = null,
    val spokenSummary: String = "",
)

class ShuttleWidgetDisplayPlanner {
    fun plan(
        now: ZonedDateTime,
        originZone: CampusZoneId,
        board: ShuttleBoard,
        allDepartures: List<ShuttleDeparture>,
        compact: Boolean = false,
    ): ShuttleWidgetDisplayPlan {
        if (originZone == CampusZoneId.OUTSIDE) {
            return ShuttleWidgetDisplayPlan(
                headerTagText = "DIMA 셔틀",
                rows = emptyList(),
                emptyText = OUTSIDE_TEXT,
                spokenSummary = "DIMA 셔틀. $OUTSIDE_TEXT",
            )
        }
        val engine = GuidanceEngine()
        val upcomingDepartures = board.rows.flatMap { row -> row.departures.map { it.departure } }
        val origin = engine.boardingOriginName(originZone, upcomingDepartures)
        val header = "DIMA 셔틀 · $origin"
        val spokenHeader = "DIMA 셔틀, $origin 출발"

        if (board.rows.isNotEmpty()) {
            val spokenRows = mutableListOf<String>()
            val rows = board.rows.map { row ->
                val annotated = engine.annotatedServiceDepartures(
                    serviceDay = now.dayOfWeek,
                    originZone = originZone,
                    destinationZone = row.destinationZone,
                    departures = allDepartures,
                )
                val destinationTag = "${DisplayVocabulary.originName(row.destinationZone)}행"
                val spokenCapsules = mutableListOf<String>()
                val capsules = row.departures.take(2).mapIndexed { index, countdown ->
                    val service = annotated.firstOrNull { it.departure.time == countdown.departure.time }
                    val isLast = service?.isLast == true
                    // D-090: 운동장 정류장에서 타는 출발은 `운동장`, 정류장이 바뀌는 첫 편은 `운동장 전환`
                    val stopLabel = when {
                        service?.isBoardingStopTransition == true -> "운동장 전환"
                        service?.isStadiumStop == true -> "운동장"
                        else -> null
                    }
                    val minutes = countdown.remainingMinutes
                    val clock = countdown.departure.time.format(TIME)
                    spokenCapsules += buildString {
                        append(spokenCountdown(minutes, clock))
                        if (isLast) append(", 막차")
                        if (stopLabel != null) append(", $stopLabel")
                    }
                    ShuttleWidgetCapsule(
                        text = buildString {
                            // D-069: 60분 이내에만 남은 분, 그보다 멀면 출발 시각만 보여 준다.
                            // 넓은 셀은 D-010의 압축 표기 `N분(HH:mm)`로 시각을 함께 적는다.
                            when {
                                minutes > COUNTDOWN_WINDOW_MINUTES -> append(clock)
                                else -> {
                                    append(if (minutes <= 0) "곧 출발" else "${minutes}분")
                                    if (!compact) append("($clock)")
                                }
                            }
                            // 컴팩트(2x1)에서는 운동장 표기를 짧게 유지한다
                            if (stopLabel != null) append(if (compact) "·운동장" else "·$stopLabel")
                        },
                        tone = when {
                            index == 0 && isLast -> ShuttleWidgetCapsuleTone.NEAREST_LAST
                            index == 0 -> ShuttleWidgetCapsuleTone.NEAREST
                            isLast -> ShuttleWidgetCapsuleTone.LAST
                            else -> ShuttleWidgetCapsuleTone.LATER
                        },
                    )
                }
                spokenRows += "$destinationTag ${spokenCapsules.joinToString(", 다음 ")}"
                ShuttleWidgetRow(destinationTag = destinationTag, capsules = capsules)
            }
            return ShuttleWidgetDisplayPlan(
                headerTagText = header,
                rows = rows,
                spokenSummary = spokenSentence(spokenHeader, spokenRows),
            )
        }

        // 운행 종료: 오늘 일정이 있는 목적지별로 막차 시각을 함께 안내한다
        val endedRows = allDepartures
            .filter { it.serviceDay == now.dayOfWeek && it.originZone == originZone }
            .mapNotNull { it.destinationZone }
            .distinct()
            .sortedBy { it.ordinal }
            .map { destination ->
                val annotated = engine.annotatedServiceDepartures(
                    serviceDay = now.dayOfWeek,
                    originZone = originZone,
                    destinationZone = destination,
                    departures = allDepartures,
                )
                destination to annotated.lastOrNull()?.departure?.time
            }
        if (endedRows.isEmpty()) {
            return ShuttleWidgetDisplayPlan(
                headerTagText = header,
                rows = emptyList(),
                emptyText = NO_SERVICE_TEXT,
                spokenSummary = "$spokenHeader. $NO_SERVICE_TEXT",
            )
        }
        return ShuttleWidgetDisplayPlan(
            headerTagText = header,
            rows = endedRows.map { (destination, lastTime) ->
                ShuttleWidgetRow(
                    destinationTag = "${DisplayVocabulary.originName(destination)}행",
                    capsules = listOf(
                        ShuttleWidgetCapsule(
                            text = if (!compact && lastTime != null) "운행 종료·막차 ${lastTime.format(TIME)}" else ENDED,
                            tone = ShuttleWidgetCapsuleTone.ENDED,
                        ),
                    ),
                )
            },
            spokenSummary = spokenSentence(
                spokenHeader,
                endedRows.map { (destination, lastTime) -> endedSpoken(destination, lastTime) },
            ),
        )
    }

    private fun spokenCountdown(minutes: Long, clock: String): String = when {
        minutes > COUNTDOWN_WINDOW_MINUTES -> "$clock 출발"
        minutes <= 0 -> "곧 출발, $clock"
        else -> "${minutes}분 후 $clock"
    }

    private fun endedSpoken(destination: CampusZoneId, lastTime: LocalTime?): String =
        "${DisplayVocabulary.originName(destination)}행 $ENDED" + (lastTime?.let { ", 막차 ${it.format(TIME)}" } ?: "")

    private fun spokenSentence(header: String, rows: List<String>): String =
        (listOf(header) + rows).joinToString(". ")

    companion object {
        /** D-069: minutes are spoken and shown only within one hour of departure. */
        const val COUNTDOWN_WINDOW_MINUTES = 60L
        const val ENDED = "운행 종료"
        // Same wording as the Home shuttle card
        private const val OUTSIDE_TEXT = "교내 진입 시 자동 안내"
        private const val NO_SERVICE_TEXT = "오늘은 운행하지 않아요"
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
