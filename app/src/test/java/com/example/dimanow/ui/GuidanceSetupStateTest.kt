package com.example.dimanow.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceSetupStateTest {
    @Test
    fun `the first run treats notifications plus any location as the essentials`() {
        assertFalse(GuidanceSetupState(notifications = true).essentialsGranted)
        assertFalse(GuidanceSetupState(approximateLocation = true).essentialsGranted)
        assertTrue(GuidanceSetupState(notifications = true, approximateLocation = true).essentialsGranted)
        assertTrue(GuidanceSetupState(notifications = true, preciseLocation = true).essentialsGranted)
    }

    @Test
    fun `the settings summary counts only the items that apply to this phone`() {
        assertEquals(4, GuidanceSetupState().missingCount)
        assertEquals(5, GuidanceSetupState(liveSupported = true).missingCount)
        // A promoted-notification grant does not count while notifications themselves are off.
        assertEquals(5, GuidanceSetupState(liveSupported = true, liveAllowed = true).missingCount)
        assertEquals(0, GuidanceSetupState(true, true, true, true, true, true, true).missingCount)
        assertEquals(0, GuidanceSetupState(true, true, true, true, true, liveSupported = false).missingCount)
    }
}
