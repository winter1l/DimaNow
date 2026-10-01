package com.example.dimanow.ui

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DateLabelsTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), java.time.LocalTime.parse(time), seoul)

    @Test
    fun datesReadAsKoreanMonthDayAndWeekday() {
        assertEquals("9월 30일 (수)", koreanDateLabel(LocalDate.parse("2026-09-30")))
        assertEquals("9월 30일 (수)", koreanDateLabel(LocalDate.parse("2026-09-30"), today = LocalDate.parse("2026-01-02")))
        assertEquals("2027년 1월 4일 (월)", koreanDateLabel(LocalDate.parse("2027-01-04"), today = LocalDate.parse("2026-12-30")))
    }

    @Test
    fun classCountdownIsShownOnlyWithinSixtyMinutes() {
        val start = at("2026-09-30", "13:00")
        // 692 minutes away: the start clock replaces a large minute count.
        assertEquals("13:00 시작", classStartLabel(at("2026-09-30", "01:28"), start))
        assertEquals("13:00 시작", classStartLabel(at("2026-09-30", "11:59"), start))
        assertEquals("시작까지 60분", classStartLabel(at("2026-09-30", "12:00"), start))
        assertEquals("시작까지 30분", classStartLabel(at("2026-09-30", "12:30:30"), start))
        assertEquals("시작까지 1분", classStartLabel(at("2026-09-30", "12:59:30"), start))
    }

    @Test
    fun laterDaysNameTheDayBeforeTheClock() {
        val now = at("2026-09-30", "20:00")
        assertEquals("내일 13:00 시작", classStartLabel(now, at("2026-10-01", "13:00")))
        assertEquals("금요일 09:00 시작", classStartLabel(now, at("2026-10-02", "09:00")))
        assertEquals("10월 12일 (월) 09:00 시작", classStartLabel(now, at("2026-10-12", "09:00")))
    }
}
