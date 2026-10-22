package com.example.dimanow.ui

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DormPointsReadAtLabelTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 10, 23, 2, 0, 0, 0, seoul).toInstant()

    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, seoul).toInstant()

    @Test
    fun `the saved read says when it was made`() {
        assertEquals("오늘 01:58 기준", dormPointsReadAtLabel(at(23, 1, 58), now))
        assertEquals("어제 21:40 기준", dormPointsReadAtLabel(at(22, 21, 40), now))
        assertEquals("10월 20일 (화) 18:05 기준", dormPointsReadAtLabel(at(20, 18, 5), now))
    }
}
