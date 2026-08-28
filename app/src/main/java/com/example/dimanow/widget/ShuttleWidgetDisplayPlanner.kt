package com.example.dimanow.widget

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DisplayVocabulary
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.guidance.GuidanceEngine
import com.example.dimanow.guidance.ShuttleBoard
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class ShuttleWidgetCapsule(
    val text: String,
    val isLast: Boolean,
)

data class ShuttleWidgetRow(
    val destinationTag: String,
    val capsules: List<ShuttleWidgetCapsule>,
)

/**
 * 앱 홈 화면의 셔틀 캡슐(D-020/D-021)과 같은 구조를 위젯용으로 계획한다.
 * 목적지 태그 + 다가오는 출발 최대 2개 캡슐(막차는 경고색), 운행 종료 시 막차 시각 안내.
 */
data class ShuttleWidgetDisplayPlan(
    val headerTagText: String,
    val rows: List<ShuttleWidgetRow>,
    val emptyText: String? = null,
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
                emptyText = "교내 진입 시 자동 안내",
            )
        }
        val engine = GuidanceEngine()
        val upcomingDepartures = board.rows.flatMap { row -> row.departures.map { it.departure } }
        val origin = engine.boardingOriginName(originZone, upcomingDepartures)

        if (board.rows.isNotEmpty()) {
            val rows = board.rows.map { row ->
                val annotated = engine.annotatedServiceDepartures(
                    serviceDay = now.dayOfWeek,
                    originZone = originZone,
                    destinationZone = row.destinationZone,
                    departures = allDepartures,
                )
                val capsules = row.departures.take(2).map { countdown ->
                    val service = annotated.firstOrNull { it.departure.time == countdown.departure.time }
                    // 좁은 셀에서도 잘리지 않도록 D-010의 압축 표기 `N분(HH:mm)`을 쓰고,
                    // 막차는 텍스트 라벨 대신 경고색 캡슐(D-016)로 구분한다. 운동장은 D-019에 따라 유지.
                    val stopLabel = when {
                        service?.isBoardingStopTransition == true -> "운동장 전환"
                        service?.isStadiumStop == true -> "운동장"
                        else -> null
                    }
                    ShuttleWidgetCapsule(
                        text = buildString {
                            append(if (countdown.remainingMinutes <= 0) "곧 출발" else "${countdown.remainingMinutes}분")
                            // 컴팩트(2x1)에서는 남은 분만 남기고, 운동장 표기는 D-019에 따라 짧게 유지한다
                            if (!compact) append("(${countdown.departure.time.format(TIME)})")
                            if (stopLabel != null) append(if (compact) "·운동장" else "·$stopLabel")
                        },
                        isLast = service?.isLast == true,
                    )
                }
                ShuttleWidgetRow(
                    destinationTag = "${DisplayVocabulary.originName(row.destinationZone)}행",
                    capsules = capsules,
                )
            }
            return ShuttleWidgetDisplayPlan(headerTagText = "DIMA 셔틀 · $origin", rows = rows)
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
                val lastTime = annotated.lastOrNull()?.departure?.time
                ShuttleWidgetRow(
                    destinationTag = "${DisplayVocabulary.originName(destination)}행",
                    capsules = listOf(
                        ShuttleWidgetCapsule(
                            text = if (!compact && lastTime != null) "운행 종료·막차 ${lastTime.format(TIME)}" else "운행 종료",
                            isLast = false,
                        ),
                    ),
                )
            }
        return if (endedRows.isEmpty()) {
            ShuttleWidgetDisplayPlan(
                headerTagText = "DIMA 셔틀 · $origin",
                rows = emptyList(),
                emptyText = "$origin  운행 종료",
            )
        } else {
            ShuttleWidgetDisplayPlan(headerTagText = "DIMA 셔틀 · $origin", rows = endedRows)
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
