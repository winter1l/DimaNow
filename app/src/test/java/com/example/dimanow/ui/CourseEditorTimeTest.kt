package com.example.dimanow.ui

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseEditorTimeTest {
    @Test
    fun `moving the start keeps the class length`() {
        assertEquals(LocalTime.of(14, 30), shiftedEnd(LocalTime.of(10, 0), LocalTime.of(11, 30), LocalTime.of(13, 0)))
    }

    @Test
    fun `an invalid previous range falls back to one hour`() {
        assertEquals(LocalTime.of(10, 0), shiftedEnd(LocalTime.of(11, 0), LocalTime.of(10, 0), LocalTime.of(9, 0)))
        assertEquals(LocalTime.of(10, 0), shiftedEnd(LocalTime.of(11, 0), LocalTime.of(11, 0), LocalTime.of(9, 0)))
    }

    @Test
    fun `a length that would cross midnight stops at the end of the day`() {
        assertEquals(LocalTime.of(23, 59), shiftedEnd(LocalTime.of(10, 0), LocalTime.of(13, 0), LocalTime.of(22, 0)))
    }
}
