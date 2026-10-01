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
    }

    @Test
    fun countdownAppearsOnlyWithinOneHour() {
        assertEquals("다음 출발 · 첫차", departureCountdownLabel(61, serviceMarkers(isFirst = true, isLast = false)))
        assertEquals("60분 후", departureCountdownLabel(60, emptyList()))
        assertEquals("곧 출발 · 막차", departureCountdownLabel(0, serviceMarkers(isFirst = false, isLast = true)))
        assertEquals("5분 후 · 첫차·막차 · 운동장", departureCountdownLabel(5, serviceMarkers(isFirst = true, isLast = true) + "운동장"))
    }

    @Test
    fun lastDepartureKeepsItsWarningToneEvenWhenPast() {
        assertEquals(TimetableChipTone.LAST, timetableChipTone(isFirst = false, isLast = true, isPast = true, isNext = false, isSecond = false))
        assertEquals(TimetableChipTone.NEXT_LAST, timetableChipTone(isFirst = false, isLast = true, isPast = false, isNext = true, isSecond = false))
        assertEquals(TimetableChipTone.PAST, timetableChipTone(isFirst = false, isLast = false, isPast = true, isNext = false, isSecond = false))
        assertEquals(TimetableChipTone.FIRST, timetableChipTone(isFirst = true, isLast = false, isPast = true, isNext = false, isSecond = false))
    }
}
