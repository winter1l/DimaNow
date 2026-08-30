package com.example.dimanow.widget

import com.example.dimanow.domain.CampusZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeWidgetRefreshPlannerTest {
    @Test
    fun `first runtime snapshot refreshes widgets`() {
        assertTrue(RuntimeWidgetRefreshPlanner().shouldRefresh(previous = null, current = CampusZoneId.OUTSIDE))
    }

    @Test
    fun `same effective zone does not wake widgets again`() {
        assertFalse(RuntimeWidgetRefreshPlanner().shouldRefresh(previous = CampusZoneId.MAIN, current = CampusZoneId.MAIN))
    }

    @Test
    fun `outside to main refreshes widgets immediately`() {
        assertTrue(RuntimeWidgetRefreshPlanner().shouldRefresh(previous = CampusZoneId.OUTSIDE, current = CampusZoneId.MAIN))
    }
}
