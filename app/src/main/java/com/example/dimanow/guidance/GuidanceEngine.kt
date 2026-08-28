package com.example.dimanow.guidance

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.ClassContent
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.CountdownMeaning
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.GuidanceSnapshot
import com.example.dimanow.domain.GuidancePause
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.domain.ShuttleLine
import com.example.dimanow.domain.DisplayVocabulary
import java.time.Duration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class HomeBase(val zone: CampusZoneId) {
    YEIN(CampusZoneId.YEIN),
    ONE_ROOM(CampusZoneId.ONE_ROOM),
}

enum class ShuttleBoardPurpose {
    GENERAL,
    RETURN,
}

data class ShuttleCountdown(
    val departure: ShuttleDeparture,
    val remainingMinutes: Long,
)

data class ShuttleBoardRow(
    val originZone: CampusZoneId,
    val destinationZone: CampusZoneId,
    val departures: List<ShuttleCountdown>,
)

data class ShuttleBoard(val rows: List<ShuttleBoardRow>)

class ShuttleScheduleIndex internal constructor(
    internal val rawDepartures: List<ShuttleDeparture>,
    internal val grouped: Map<ShuttleScheduleKey, List<ShuttleDeparture>>,
    internal val destinations: Map<Pair<DayOfWeek, CampusZoneId>, List<CampusZoneId>>,
)

internal data class ShuttleScheduleKey(
    val serviceDay: DayOfWeek,
    val originZone: CampusZoneId,
    val destinationZone: CampusZoneId,
)

data class AnnotatedServiceDeparture(
    val departure: ShuttleDeparture,
    val isFirst: Boolean,
    val isLast: Boolean,
    val isStadiumStop: Boolean = false,
    val isBoardingStopTransition: Boolean = false,
) {
    val displayText: String
        get() = departure.time.format(DateTimeFormatter.ofPattern("HH:mm")) + when {
            isFirst && isLast -> " (첫차·막차)"
            isFirst -> " (첫차)"
            isLast -> " (막차)"
            else -> ""
        } + when {
            isBoardingStopTransition -> " · 운동장 전환"
            isStadiumStop -> " · 운동장"
            else -> ""
        }
}

class GuidanceEngine {
    fun prepareShuttleSchedule(departures: List<ShuttleDeparture>): ShuttleScheduleIndex {
        val grouped = departures
            .filter { it.destinationZone != null }
            .groupBy { ShuttleScheduleKey(it.serviceDay, it.originZone, it.destinationZone!!) }
            .mapValues { (_, values) -> values.sortedBy { it.time }.distinctBy { it.time } }
        val destinations = grouped.keys
            .groupBy({ it.serviceDay to it.originZone }, { it.destinationZone })
            .mapValues { (_, values) -> values.distinct().sortedBy { it.ordinal } }
        return ShuttleScheduleIndex(departures, grouped, destinations)
    }

    fun boardingOriginName(
        originZone: CampusZoneId,
        displayedDepartures: List<ShuttleDeparture>,
    ): String = if (
        originZone == CampusZoneId.MAIN &&
        displayedDepartures.minByOrNull { it.time }?.sourceStopId == STADIUM_STOP_ID
    ) {
        "운동장"
    } else {
        DisplayVocabulary.originName(originZone)
    }

    fun shuttleBoard(
        now: ZonedDateTime,
        originZone: CampusZoneId,
        departures: List<ShuttleDeparture>,
        purpose: ShuttleBoardPurpose,
        homeBase: HomeBase = HomeBase.YEIN,
        limitPerDestination: Int = 2,
    ): ShuttleBoard = shuttleBoard(
        now = now,
        originZone = originZone,
        index = prepareShuttleSchedule(departures),
        purpose = purpose,
        homeBase = homeBase,
        limitPerDestination = limitPerDestination,
    )

    fun shuttleBoard(
        now: ZonedDateTime,
        originZone: CampusZoneId,
        index: ShuttleScheduleIndex,
        purpose: ShuttleBoardPurpose,
        homeBase: HomeBase = HomeBase.YEIN,
        limitPerDestination: Int = 2,
    ): ShuttleBoard {
        val destinations = when (purpose) {
            ShuttleBoardPurpose.GENERAL -> index.destinations[now.dayOfWeek to originZone].orEmpty()
            ShuttleBoardPurpose.RETURN -> listOf(homeBase.zone).filterNot { it == originZone }
        }
        val rows = destinations.mapNotNull { destination ->
            val service = index.grouped[
                ShuttleScheduleKey(now.dayOfWeek, originZone, destination),
            ].orEmpty()
            val firstFuture = firstNotBefore(service, now.toLocalTime())
            val countdowns = service.asSequence()
                .drop(firstFuture)
                .mapNotNull { departure ->
                    val target = now.toLocalDate().atTime(departure.time).atZone(now.zone)
                    val remainingMillis = Duration.between(now, target).toMillis()
                    if (remainingMillis < 0) null
                    else ShuttleCountdown(departure, (remainingMillis + 59_999L) / 60_000L)
                }
                .take(limitPerDestination)
                .toList()
            countdowns.takeIf { it.isNotEmpty() }?.let {
                ShuttleBoardRow(originZone, destination, it)
            }
        }
        return ShuttleBoard(rows)
    }

    fun annotatedServiceDepartures(
        serviceDay: DayOfWeek,
        originZone: CampusZoneId,
        destinationZone: CampusZoneId,
        departures: List<ShuttleDeparture>,
    ): List<AnnotatedServiceDeparture> {
        val serviceSlots = departures
            .asSequence()
            .filter {
                it.serviceDay == serviceDay &&
                    it.originZone == originZone &&
                    it.destinationZone == destinationZone
            }
            .sortedBy { it.time }
            .distinctBy { it.time }
            .toList()
        val stadiumTransitionTime = departures.asSequence()
            .filter {
                it.serviceDay == serviceDay &&
                    it.originZone == CampusZoneId.MAIN &&
                    it.sourceStopId == STADIUM_STOP_ID
            }
            .minOfOrNull { it.time }
        return serviceSlots.mapIndexed { index, departure ->
            val isStadiumStop = departure.sourceStopId == STADIUM_STOP_ID
            AnnotatedServiceDeparture(
                departure = departure,
                isFirst = index == 0,
                isLast = index == serviceSlots.lastIndex,
                isStadiumStop = isStadiumStop,
                isBoardingStopTransition = isStadiumStop && departure.time == stadiumTransitionTime,
            )
        }
    }

    fun nextDepartures(
        now: ZonedDateTime,
        originZone: CampusZoneId,
        destinationZone: CampusZoneId,
        departures: List<ShuttleDeparture>,
        limit: Int = 2,
    ): List<ShuttleDeparture> = departures
        .asSequence()
        .filter {
            it.serviceDay == now.dayOfWeek &&
                it.originZone == originZone &&
                it.destinationZone == destinationZone &&
                !it.time.isBefore(now.toLocalTime())
        }
        .sortedBy { it.time }
        .distinctBy { it.time }
        .take(limit)
        .toList()

    fun snapshot(
        now: ZonedDateTime,
        termStart: LocalDate,
        termEnd: LocalDate,
        courses: List<Course>,
        noClassDates: Set<LocalDate>,
        resolvedZone: CampusZoneId,
        automaticClassGuidance: Boolean,
        shuttleDepartures: List<ShuttleDeparture> = emptyList(),
        preparedSchedule: ShuttleScheduleIndex? = null,
        homeBase: HomeBase = HomeBase.YEIN,
        guidancePause: GuidancePause? = null,
    ): GuidanceSnapshot {
        if (
            !automaticClassGuidance ||
            now.toLocalDate() !in termStart..termEnd ||
            now.toLocalDate() in noClassDates ||
            guidancePause?.contains(now.toLocalDate()) == true
        ) {
            return GuidanceSnapshot(null, emptyList(), GuidancePhase.NONE)
        }

        val todayCourses = courses.filter { it.weekday == now.dayOfWeek }.sortedBy { it.start }
        val finalCourse = todayCourses.lastOrNull()
        if (
            finalCourse != null &&
            !now.toLocalTime().isBefore(finalCourse.end) &&
            resolvedZone == homeBase.zone
        ) {
            return GuidanceSnapshot(null, emptyList(), GuidancePhase.NONE)
        }
        if (
            finalCourse != null &&
            !now.toLocalTime().isBefore(finalCourse.end) &&
            resolvedZone == CampusZoneId.MAIN
        ) {
            val returnRow = shuttleBoard(
                now = now,
                originZone = CampusZoneId.MAIN,
                index = preparedSchedule ?: prepareShuttleSchedule(shuttleDepartures),
                purpose = ShuttleBoardPurpose.RETURN,
                homeBase = homeBase,
            ).rows.firstOrNull()
            if (returnRow != null) {
                val boardingOrigin = boardingOriginName(
                    returnRow.originZone,
                    returnRow.departures.map { it.departure },
                )
                val firstTarget = now.toLocalDate()
                    .atTime(returnRow.departures.first().departure.time)
                    .atZone(now.zone)
                    .toInstant()
                return GuidanceSnapshot(
                    classContent = null,
                    shuttleLines = listOf(
                        ShuttleLine("$boardingOrigin  ${returnRow.departures.joinToString(", ") { "${it.remainingMinutes}분" }}"),
                    ),
                    phase = GuidancePhase.RETURN,
                    countdownTarget = firstTarget,
                    expiresAt = now.toLocalDate().atTime(returnRow.departures.last().departure.time).atZone(now.zone).toInstant(),
                    countdownMeaning = CountdownMeaning.SHUTTLE_DEPARTURE,
                    requiresMinuteUpdates = returnRow.departures.size == 2,
                )
            }
        }
        if (
            finalCourse != null &&
            !now.toLocalTime().isBefore(finalCourse.end) &&
            resolvedZone in setOf(CampusZoneId.ONE_ROOM, CampusZoneId.YEIN) &&
            resolvedZone != homeBase.zone
        ) {
            val firstLegs = shuttleDepartures
                .asSequence()
                .filter {
                    it.originZone == resolvedZone &&
                        it.destinationZone == CampusZoneId.MAIN &&
                        it.serviceDay == now.dayOfWeek &&
                        !it.time.isBefore(now.toLocalTime())
                }
                .sortedBy { it.time }
                .distinctBy { it.time }
                .take(2)
                .toList()
            val earliestArrival = firstLegs.mapNotNull { it.arrivalTime }.minOrNull()
            if (firstLegs.isNotEmpty() && earliestArrival != null) {
                val firstRemaining = firstLegs.map {
                    remainingMinutes(now, now.toLocalDate().atTime(it.time).atZone(now.zone))
                }
                val connectionDepartures = shuttleDepartures
                    .asSequence()
                    .filter {
                        it.originZone == CampusZoneId.MAIN &&
                            it.destinationZone == homeBase.zone &&
                            it.serviceDay == now.dayOfWeek &&
                            !it.time.isBefore(earliestArrival)
                    }
                    .sortedBy { it.time }
                    .distinctBy { it.time }
                    .take(2)
                    .toList()
                val connections = connectionDepartures.map {
                    remainingMinutes(now, now.toLocalDate().atTime(it.time).atZone(now.zone))
                }
                val lines = mutableListOf(
                    ShuttleLine("${DisplayVocabulary.originName(resolvedZone)}  ${firstRemaining.joinToString(", ") { "${it}분" }}"),
                )
                if (connections.isNotEmpty()) {
                    val connectionOrigin = boardingOriginName(CampusZoneId.MAIN, connectionDepartures)
                    lines += ShuttleLine("$connectionOrigin  ${connections.joinToString(", ") { "${it}분" }}")
                }
                return GuidanceSnapshot(
                    classContent = null,
                    shuttleLines = lines,
                    phase = GuidancePhase.RETURN,
                    countdownTarget = now.toLocalDate().atTime(firstLegs.first().time).atZone(now.zone).toInstant(),
                    expiresAt = now.toLocalDate().atTime(shuttleDepartures.maxOf { it.time }).atZone(now.zone).toInstant(),
                    countdownMeaning = CountdownMeaning.SHUTTLE_DEPARTURE,
                    requiresMinuteUpdates = firstLegs.size == 2 || connections.size == 2,
                )
            }
        }
        val course = todayCourses.firstOrNull {
            val classGuidanceCutoff = now.toLocalDate().atTime(it.start).plusMinutes(15).toLocalTime()
            !now.toLocalTime().isBefore(it.start) && now.toLocalTime().isBefore(classGuidanceCutoff)
        } ?: todayCourses.firstOrNull {
            val startsAt = now.toLocalDate().atTime(it.start).atZone(now.zone)
            Duration.between(now, startsAt).toMinutes() in 1L..60L
        }
            ?: return GuidanceSnapshot(null, emptyList(), GuidancePhase.NONE)
        val startsAt = now.toLocalDate().atTime(course.start).atZone(now.zone)
        val endsAt = now.toLocalDate().atTime(course.end).atZone(now.zone)
        val classGuidanceEnd = startsAt.plusMinutes(15)
        val minutesUntilStart = remainingMinutes(now, startsAt)
        val isInClass = !now.isBefore(startsAt) && now.isBefore(classGuidanceEnd)
        if (!isInClass && minutesUntilStart !in 1L..60L) {
            return GuidanceSnapshot(null, emptyList(), GuidancePhase.NONE)
        }
        val phase = if (isInClass) GuidancePhase.IN_CLASS else GuidancePhase.BEFORE_CLASS
        val remainingText = if (isInClass) "수업 중" else "시작까지 ${minutesUntilStart}분"

        val shuttleLine = if (isInClass || resolvedZone == CampusZoneId.MAIN || resolvedZone == CampusZoneId.OUTSIDE) {
            emptyList()
        } else {
            val remaining = shuttleDepartures
                .asSequence()
                .filter {
                    it.originZone == resolvedZone &&
                        it.destinationZone == course.zone &&
                        it.serviceDay == now.dayOfWeek
                }
                .distinctBy { it.time }
                .map { departure ->
                    remainingMinutes(now, now.toLocalDate().atTime(departure.time).atZone(now.zone))
                }
                .filter { it >= 0 }
                .sorted()
                .take(2)
                .toList()
            val origin = DisplayVocabulary.originName(resolvedZone)
            if (remaining.isEmpty()) emptyList()
            else listOf(ShuttleLine("$origin  ${remaining.joinToString(", ") { "${it}분" }}"))
        }

        return GuidanceSnapshot(
            classContent = ClassContent(
                title = "${course.start.format(TIME_FORMAT)} · ${course.name}",
                detail = "$remainingText · ${course.room}",
                startTime = course.start.format(TIME_FORMAT),
                courseName = course.name,
                room = course.room,
                remainingText = remainingText,
            ),
            shuttleLines = shuttleLine,
            phase = phase,
            countdownTarget = if (isInClass) null else startsAt.toInstant(),
            expiresAt = if (isInClass) classGuidanceEnd.toInstant() else startsAt.toInstant(),
            countdownMeaning = if (!isInClass) CountdownMeaning.CLASS_START else null,
            requiresMinuteUpdates = false,
        )
    }

    private companion object {
        const val STADIUM_STOP_ID = "stadium-stop"
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun remainingMinutes(from: ZonedDateTime, to: ZonedDateTime): Long {
            val millis = Duration.between(from, to).toMillis()
            return if (millis < 0) -1 else (millis + 59_999L) / 60_000L
        }

        fun firstNotBefore(departures: List<ShuttleDeparture>, time: java.time.LocalTime): Int {
            var low = 0
            var high = departures.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (departures[middle].time.isBefore(time)) low = middle + 1 else high = middle
            }
            return low
        }
    }
}
