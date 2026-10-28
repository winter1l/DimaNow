package com.example.dimanow.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableChipsTest {
    @Test
    fun chipDescriptionsStateMeaningBeforeAndAfterTheClock() {
        assertEquals("지난 시간 17:30", timetableChipDescription("17:30", isPast = true, isNext = false, markers = emptyList()))
        assertEquals("다음 출발 21:30", timetableChipDescription("21:30", isPast = false, isNext = true, markers = emptyList()))
        assertEquals("18:55, 운동장 전환", timetableChipDescription("18:55", isPast = false, isNext = false, markers = listOf("운동장 전환")))
        assertEquals("21:50, 막차", timetableChipDescription("21:50", isPast = false, isNext = false, markers = serviceMarkers(isFirst = false, isLast = true)))
        assertEquals("다음 출발 08:51, 예정", timetableChipDescription("08:51", isPast = false, isNext = true, markers = listOf("예정")))
        // D-124: the large cells say their countdown before the markers.
        assertEquals("다음 출발 19:35, 5분 후, 운동장 전환", timetableChipDescription("19:35", isPast = false, isNext = true, markers = listOf("운동장 전환"), countdown = "5분 후"))
        assertEquals("20:00, 30분 후, 막차", timetableChipDescription("20:00", isPast = false, isNext = false, markers = listOf("막차"), countdown = "30분 후"))
    }

    @Test
    fun countdownShowsTheMinutesHoweverMany() {
        // D-130: minutes past an hour too; only a far 첫차 is drawn differently (nextDepartureLook).
        assertEquals("61분 후 · 첫차", departureCountdownLabel(61, serviceMarkers(isFirst = true, isLast = false)))
        assertEquals("135분 후", departureCountdownLabel(135, emptyList()))
        assertEquals("60분 후", departureCountdownLabel(60, emptyList()))
        assertEquals("곧 출발 · 막차", departureCountdownLabel(0, serviceMarkers(isFirst = false, isLast = true)))
        assertEquals("5분 후 · 첫차·막차 · 운동장", departureCountdownLabel(5, serviceMarkers(isFirst = true, isLast = true) + "운동장"))
    }

    @Test
    fun aLastDepartureIsWarnedUntilItLeavesThenQuietLikeTheOtherPastTimes() {
        // D-136: after the day's service the amber 막차 cell read as if a bus were still to come.
        assertEquals(TimetableChipTone.LAST, timetableChipTone(isFirst = false, isLast = true, isPast = false, isNext = false, isSecond = false))
        assertEquals(TimetableChipTone.PAST, timetableChipTone(isFirst = false, isLast = true, isPast = true, isNext = false, isSecond = false))
        assertEquals(TimetableChipTone.NEXT_LAST, timetableChipTone(isFirst = false, isLast = true, isPast = false, isNext = true, isSecond = false))
        assertEquals(TimetableChipTone.PAST, timetableChipTone(isFirst = false, isLast = false, isPast = true, isNext = false, isSecond = false))
        assertEquals(TimetableChipTone.FIRST, timetableChipTone(isFirst = true, isLast = false, isPast = true, isNext = false, isSecond = false))
    }

    @Test
    fun aNextDepartureMoreThanAnHourOffIsTheOnlyLargeCellAndAQuietTile() {
        // D-127: 06:00 with 첫차 at 07:30 and the next at 08:10.
        val first = nextDepartureLook(isFirst = true, isLast = false, isPast = false, isNext = true, isSecond = false, clock = "07:30", minutesLeft = 90, nextMinutesLeft = 90, nextIsFirst = true)
        assertEquals(NextDepartureLook("07:30", TimetableChipTone.SECOND, farAhead = true), first)
        assertEquals("첫차", largeCellTopLine(TimetableChipSpec(1, "07:30", listOf("첫차"), "", first.tone, false, first.countdown, first.farAhead)))
        val second = nextDepartureLook(isFirst = false, isLast = false, isPast = false, isNext = false, isSecond = true, clock = "08:10", minutesLeft = 130, nextMinutesLeft = 90, nextIsFirst = true)
        assertEquals(NextDepartureLook(null, TimetableChipTone.UPCOMING, farAhead = false), second)
        val near = nextDepartureLook(isFirst = false, isLast = false, isPast = false, isNext = true, isSecond = false, clock = "07:30", minutesLeft = 30, nextMinutesLeft = 30, nextIsFirst = true)
        assertEquals(NextDepartureLook("30분 후", TimetableChipTone.NEXT, farAhead = false), near)
    }

    @Test
    fun aFarDepartureThatIsNotTheFirstBusStillCountsDown() {
        // D-130: a midday gap of 90 minutes keeps both large cells with their minutes.
        val next = nextDepartureLook(isFirst = false, isLast = false, isPast = false, isNext = true, isSecond = false, clock = "14:45", minutesLeft = 90, nextMinutesLeft = 90, nextIsFirst = false)
        assertEquals(NextDepartureLook("90분 후", TimetableChipTone.NEXT, farAhead = false), next)
        val second = nextDepartureLook(isFirst = false, isLast = false, isPast = false, isNext = false, isSecond = true, clock = "15:45", minutesLeft = 150, nextMinutesLeft = 90, nextIsFirst = false)
        assertEquals(NextDepartureLook("150분 후", TimetableChipTone.SECOND, farAhead = false), second)
    }
}
