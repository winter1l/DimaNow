package com.example.dimanow.ui

import com.example.dimanow.school.EquipmentDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class EquipmentSummaryTest {
    private val today = LocalDate.of(2026, 10, 23)

    private fun day(offset: Long, open: Boolean = true, free: Int = 0) = EquipmentDay(today.plusDays(offset), open, free, 3)

    @Test
    fun `the row names the soonest day with a free unit`() {
        assertEquals("오늘 2대 빌릴 수 있어요", equipmentAvailabilitySummary(listOf(day(0, free = 2), day(1, free = 3)), today))
        assertEquals("내일 1대 빌릴 수 있어요", equipmentAvailabilitySummary(listOf(day(0), day(1, free = 1)), today))
        // Closed days and fully booked days are skipped.
        assertEquals(
            "10월 27일 (화)에 3대 빌릴 수 있어요",
            equipmentAvailabilitySummary(listOf(day(0), day(1, open = false), day(2, open = false), day(3), day(4, free = 3)), today),
        )
    }

    @Test
    fun `no free day in the window says so`() {
        assertEquals("2주 안에 빈 날이 없어요", equipmentAvailabilitySummary(listOf(day(0), day(1, open = false)), today))
        assertEquals("2주 안에 빈 날이 없어요", equipmentAvailabilitySummary(emptyList(), today))
    }
}
