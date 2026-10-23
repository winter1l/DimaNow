package com.example.dimanow.dorm

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutingRulesTest {
    private val scheduled = RollCallStatus.Scheduled(LocalTime.of(23, 0), null)
    private val noRollCall = RollCallStatus.NotToday(LocalDate.of(2026, 10, 27))

    @Test
    fun `on a 점호 day applying from 21_00 asks first`() {
        assertNull(OutingRules.warning(LocalTime.of(20, 59), scheduled))
        assertEquals(OutingWarning.AfterRollCallDeadline("21:00 전까지"), OutingRules.warning(LocalTime.of(21, 0), scheduled))
        assertEquals(OutingWarning.AfterRollCallDeadline("21:00 전까지"), OutingRules.warning(LocalTime.of(23, 58), scheduled))
        // No 점호 today: the evening is fine.
        assertNull(OutingRules.warning(LocalTime.of(22, 0), noRollCall))
    }

    @Test
    fun `an announced deadline counts its own minute`() {
        val notice = RollCallNotice(LocalDate.of(2026, 10, 20), held = true, applyBy = LocalTime.of(20, 59), receivedAt = Instant.EPOCH)
        val announced = RollCallStatus.Announced(notice)
        assertNull(OutingRules.warning(LocalTime.of(20, 59), announced))
        assertEquals(OutingWarning.AfterRollCallDeadline("20:59까지"), OutingRules.warning(LocalTime.of(21, 0), announced))
        val cancelled = RollCallStatus.Announced(notice.copy(held = false, applyBy = null))
        assertNull(OutingRules.warning(LocalTime.of(22, 0), cancelled))
    }

    @Test
    fun `from 23_59 until the door opens an overnight stay is past its deadline`() {
        for (time in listOf(LocalTime.of(23, 59), LocalTime.MIDNIGHT, LocalTime.of(3, 0), LocalTime.of(5, 59))) {
            assertEquals(time.toString(), OutingWarning.OvernightClosed, OutingRules.warning(time, noRollCall))
            assertEquals(time.toString(), OutingWarning.OvernightClosed, OutingRules.warning(time, scheduled))
        }
        assertNull(OutingRules.warning(LocalTime.of(6, 0), noRollCall))
    }
}
