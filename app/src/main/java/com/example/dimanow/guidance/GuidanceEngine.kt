package com.example.dimanow.guidance

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.ClassContent
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.CountdownMeaning
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.GuidanceSnapshot
import com.example.dimanow.domain.GuidanceKind
import com.example.dimanow.domain.GuidancePause
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.domain.ShuttleLine
import com.example.dimanow.domain.DisplayVocabulary
import java.time.Duration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.example.dimanow.location.NearbyTransitStop
import com.example.dimanow.transit.Bus4402Schedule
import com.example.dimanow.transit.Bus4402ServiceCalendar

enum class HomeBase(val zone: CampusZoneId) {
    YEIN(CampusZoneId.YEIN),
    ONE_ROOM(CampusZoneId.ONE_ROOM),
}

enum class ShuttleBoardPurpose {
    GENERAL,
    RETURN,
}

enum class ShuttleServicePattern {
    DAY_A,
    DAY_B,
    EVENING_LOOP,
    FIELD_OVERRIDE,
    SUNDAY,
    OTHER,
}

data class ShuttleStopCall(
    val id: String,
    val sequence: Int,
    val zone: CampusZoneId,
    val stopId: String,
    val expectedTime: java.time.LocalTime,
)

data class ShuttleVehicleRun(
    val id: String,
    val serviceDay: DayOfWeek,
    val pattern: ShuttleServicePattern,
    val stopCalls: List<ShuttleStopCall>,
)

data class ShuttleTopology(val runs: List<ShuttleVehicleRun>)

data class ShuttleReportAggregate(
    val runId: String,
    val stopCallId: String,
    val stopSequence: Int,
    val count: Int,
)

data class ShuttleReportableEvent(
    val run: ShuttleVehicleRun,
    val stopCall: ShuttleStopCall,
)

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
    fun reportableMissedEvents(
        now: ZonedDateTime,
        zone: CampusZoneId,
        topology: ShuttleTopology,
    ): List<ShuttleReportableEvent> = topology.runs.asSequence()
        .filter { it.serviceDay == now.dayOfWeek }
        .flatMap { run -> run.stopCalls.asSequence().map { ShuttleReportableEvent(run, it) } }
        .filter { it.stopCall.zone == zone && canReportMissedShuttle(now, it.run, it.stopCall, topology) }
        .sortedByDescending { it.stopCall.expectedTime }
        .toList()

    fun stopCallForDeparture(
        topology: ShuttleTopology,
        departure: ShuttleDeparture,
    ): ShuttleReportableEvent? {
        val candidates = topology.runs.asSequence()
            .filter { it.serviceDay == departure.serviceDay }
            .flatMap { run -> run.stopCalls.asSequence().map { ShuttleReportableEvent(run, it) } }
            .filter { event ->
                event.stopCall.zone == departure.originZone &&
                    event.stopCall.expectedTime == departure.time &&
                    event.stopCall.stopId == departure.sourceStopId
            }
            .toList()
        return when {
            departure.sourceRouteId == "B-evening" && departure.originZone == CampusZoneId.YEIN -> candidates.firstOrNull { it.run.pattern == ShuttleServicePattern.EVENING_LOOP && it.stopCall.sequence == 0 }
            departure.sourceRouteId.endsWith("-evening") && departure.originZone == CampusZoneId.ONE_ROOM -> candidates.firstOrNull { it.run.pattern == ShuttleServicePattern.EVENING_LOOP && it.stopCall.sequence == 2 }
            departure.sourceRouteId.endsWith("-evening") && departure.originZone == CampusZoneId.MAIN -> candidates.firstOrNull { it.run.pattern == ShuttleServicePattern.EVENING_LOOP && it.stopCall.sequence == 3 }
            else -> candidates.firstOrNull { it.run.pattern != ShuttleServicePattern.EVENING_LOOP && it.stopCall.sequence == 0 }
        }
    }

    fun affectedReportCount(
        run: ShuttleVehicleRun,
        stopCall: ShuttleStopCall,
        reports: List<ShuttleReportAggregate>,
    ): Int = reports.asSequence()
        .filter { it.runId == run.id && it.stopSequence <= stopCall.sequence }
        .sumOf { it.count }

    fun canReportMissedShuttle(
        now: ZonedDateTime,
        run: ShuttleVehicleRun,
        stopCall: ShuttleStopCall,
        topology: ShuttleTopology,
    ): Boolean {
        if (now.dayOfWeek != run.serviceDay) return false
        val expectedAt = now.toLocalDate().atTime(stopCall.expectedTime).atZone(now.zone)
        if (now.isBefore(expectedAt)) return false
        val nextSameStop = topology.runs.asSequence()
            .filter { it.serviceDay == run.serviceDay }
            .flatMap { candidate -> candidate.stopCalls.asSequence() }
            .filter { candidate ->
                candidate.id != stopCall.id &&
                    candidate.stopId == stopCall.stopId &&
                    candidate.expectedTime.isAfter(stopCall.expectedTime)
            }
            .minByOrNull { it.expectedTime }
            ?.let { now.toLocalDate().atTime(it.expectedTime).atZone(now.zone) }
        val closesAt = listOfNotNull(expectedAt.plusMinutes(15), nextSameStop).minOrNull()!!
        return now.isBefore(closesAt)
    }

    /**
     * Builds the physical-run projection used by delay reporting. Raw official rows remain intact.
     * Daytime A/B rows are separate services; the A/B evening rows are one three-stop loop.
     */
    fun prepareShuttleTopology(departures: List<ShuttleDeparture>): ShuttleTopology {
        val daytime = departures.asSequence()
            .filterNot { it.sourceRouteId.endsWith("-evening") }
            .map { departure ->
                val pattern = when (departure.sourceRouteId) {
                    "A" -> ShuttleServicePattern.DAY_A
                    "B" -> ShuttleServicePattern.DAY_B
                    "A-field-extra" -> ShuttleServicePattern.FIELD_OVERRIDE
                    "C" -> ShuttleServicePattern.SUNDAY
                    else -> ShuttleServicePattern.OTHER
                }
                val runId = listOf(
                    pattern.name.lowercase(),
                    departure.serviceDay.name.lowercase(),
                    departure.time.toString().replace(":", ""),
                    departure.originZone.name.lowercase(),
                ).joinToString("-")
                val calls = buildList {
                    add(
                        ShuttleStopCall(
                            id = "$runId:0",
                            sequence = 0,
                            zone = departure.originZone,
                            stopId = departure.sourceStopId,
                            expectedTime = departure.time,
                        ),
                    )
                    departure.arrivalTime?.let { arrival ->
                        val destination = requireNotNull(departure.destinationZone)
                        add(
                            ShuttleStopCall(
                                id = "$runId:1",
                                sequence = 1,
                                zone = destination,
                                stopId = defaultStopId(destination),
                                expectedTime = arrival,
                            ),
                        )
                    }
                }
                ShuttleVehicleRun(runId, departure.serviceDay, pattern, calls)
            }
            .toList()

        val evening = departures.asSequence()
            .filter {
                it.sourceRouteId == "A-evening" &&
                    it.originZone == CampusZoneId.ONE_ROOM &&
                    it.destinationZone == CampusZoneId.MAIN
            }
            .mapNotNull { oneRoom ->
                val firstMainTime = oneRoom.time.minusMinutes(5)
                val firstLeg = departures.firstOrNull {
                    it.serviceDay == oneRoom.serviceDay &&
                        it.sourceRouteId == "B-evening" &&
                        it.originZone == CampusZoneId.YEIN &&
                        it.destinationZone == CampusZoneId.MAIN &&
                        it.arrivalTime == firstMainTime
                } ?: return@mapNotNull null
                val secondMainTime = oneRoom.arrivalTime ?: return@mapNotNull null
                val finalLeg = departures.firstOrNull {
                    it.serviceDay == oneRoom.serviceDay &&
                        it.sourceRouteId.endsWith("-evening") &&
                        it.originZone == CampusZoneId.MAIN &&
                        it.destinationZone == CampusZoneId.YEIN &&
                        it.time == secondMainTime
                } ?: return@mapNotNull null
                val finalYeinTime = finalLeg.arrivalTime ?: return@mapNotNull null
                val runId = "evening-loop-${oneRoom.serviceDay.name.lowercase()}-${oneRoom.time.toString().replace(":", "")}"
                val callData = listOf(
                    Triple(CampusZoneId.YEIN, firstLeg.sourceStopId, firstLeg.time),
                    Triple(CampusZoneId.MAIN, STADIUM_STOP_ID, firstMainTime),
                    Triple(CampusZoneId.ONE_ROOM, oneRoom.sourceStopId, oneRoom.time),
                    Triple(CampusZoneId.MAIN, STADIUM_STOP_ID, secondMainTime),
                    Triple(CampusZoneId.YEIN, defaultStopId(CampusZoneId.YEIN), finalYeinTime),
                )
                ShuttleVehicleRun(
                    id = runId,
                    serviceDay = oneRoom.serviceDay,
                    pattern = ShuttleServicePattern.EVENING_LOOP,
                    stopCalls = callData.mapIndexed { sequence, (zone, stopId, time) ->
                        ShuttleStopCall("$runId:$sequence", sequence, zone, stopId, time)
                    },
                )
            }
            .toList()

        return ShuttleTopology(
            (daytime + evening).sortedWith(compareBy<ShuttleVehicleRun> { it.serviceDay.value }.thenBy { it.stopCalls.first().expectedTime }),
        )
    }

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
        nearbyTransitStop: NearbyTransitStop? = null,
        bus4402Schedule: Bus4402Schedule = Bus4402Schedule.official,
    ): GuidanceSnapshot {
        nearbyTransitStop?.let { stop ->
            bus4402Snapshot(now, stop, bus4402Schedule)?.let { return it }
        }
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
                        ShuttleLine(
                            text = "$boardingOrigin  ${returnRow.departures.joinToString(", ") { "${it.remainingMinutes}분" }}",
                            destination = DisplayVocabulary.destinationName(returnRow.destinationZone),
                            minutes = returnRow.departures.first().remainingMinutes,
                        ),
                    ),
                    phase = GuidancePhase.RETURN,
                    countdownTarget = firstTarget,
                    expiresAt = now.toLocalDate().atTime(returnRow.departures.last().departure.time).atZone(now.zone).toInstant(),
                    countdownMeaning = CountdownMeaning.SHUTTLE_DEPARTURE,
                    requiresMinuteUpdates = true,
                    kind = GuidanceKind.CAMPUS_SHUTTLE,
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
                    ShuttleLine(
                        text = "${DisplayVocabulary.originName(resolvedZone)}  ${firstRemaining.joinToString(", ") { "${it}분" }}",
                        destination = DisplayVocabulary.destinationName(CampusZoneId.MAIN),
                        minutes = firstRemaining.first(),
                    ),
                )
                if (connections.isNotEmpty()) {
                    val connectionOrigin = boardingOriginName(CampusZoneId.MAIN, connectionDepartures)
                    lines += ShuttleLine(
                        text = "$connectionOrigin  ${connections.joinToString(", ") { "${it}분" }}",
                        destination = DisplayVocabulary.destinationName(homeBase.zone),
                        minutes = connections.first(),
                    )
                }
                return GuidanceSnapshot(
                    classContent = null,
                    shuttleLines = lines,
                    phase = GuidancePhase.RETURN,
                    countdownTarget = now.toLocalDate().atTime(firstLegs.first().time).atZone(now.zone).toInstant(),
                    expiresAt = now.toLocalDate().atTime(shuttleDepartures.maxOf { it.time }).atZone(now.zone).toInstant(),
                    countdownMeaning = CountdownMeaning.SHUTTLE_DEPARTURE,
                    requiresMinuteUpdates = true,
                    kind = GuidanceKind.CAMPUS_SHUTTLE,
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
            else listOf(
                ShuttleLine(
                    text = "$origin  ${remaining.joinToString(", ") { "${it}분" }}",
                    destination = DisplayVocabulary.destinationName(course.zone),
                    minutes = remaining.first(),
                ),
            )
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
            kind = GuidanceKind.CLASS,
        )
    }

    fun bus4402Snapshot(
        now: ZonedDateTime,
        nearbyStop: NearbyTransitStop,
        schedule: Bus4402Schedule = Bus4402Schedule.official,
    ): GuidanceSnapshot? {
        val serviceType = Bus4402ServiceCalendar.serviceType(now.toLocalDate())
        val departures = schedule.departures(serviceType, nearbyStop.stopNumber)
            .asSequence()
            .filterNot { it.time.isBefore(now.toLocalTime()) }
            .take(2)
            .toList()
        if (departures.isEmpty()) return null
        val countdowns = departures.map { departure ->
            val target = now.toLocalDate().atTime(departure.time).atZone(now.zone)
            val millis = Duration.between(now, target).toMillis()
            (millis.coerceAtLeast(0L) + 59_999L) / 60_000L
        }
        val firstTarget = now.toLocalDate().atTime(departures.first().time).atZone(now.zone).toInstant()
        return GuidanceSnapshot(
            classContent = null,
            shuttleLines = listOf(
                ShuttleLine(
                    text = "${nearbyStop.displayName}  ${countdowns.joinToString(", ") { minutes ->
                        if (minutes == 0L) "곧" else "${minutes}분"
                    }}",
                    destination = "강남행",
                    minutes = countdowns.first(),
                ),
            ),
            phase = GuidancePhase.TRANSIT,
            countdownTarget = firstTarget,
            expiresAt = now.toLocalDate().atTime(departures.last().time).atZone(now.zone).toInstant(),
            countdownMeaning = CountdownMeaning.SHUTTLE_DEPARTURE,
            requiresMinuteUpdates = true,
            kind = GuidanceKind.BUS_4402,
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

        fun defaultStopId(zone: CampusZoneId): String = when (zone) {
            CampusZoneId.YEIN -> "yein"
            CampusZoneId.MAIN -> "university-headquarters"
            CampusZoneId.ONE_ROOM -> "one-room"
            CampusZoneId.OUTSIDE -> error("OUTSIDE에는 셔틀 정류장이 없습니다.")
        }
    }
}
