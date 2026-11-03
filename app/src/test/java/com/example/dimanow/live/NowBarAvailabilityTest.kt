package com.example.dimanow.live

import org.junit.Assert.assertEquals
import org.junit.Test

/** D-140/D-143: when the Now Bar counts as available, and what a 나우바 choice becomes when it is not. */
class NowBarAvailabilityTest {
    @Test
    fun android16AndLaterCanShowTheNowBarWhateverSamsungsDeveloperOption() {
        assertEquals(NowBarSupport.AVAILABLE, NowBarAvailability.evaluate(36))
        assertEquals(NowBarSupport.AVAILABLE, NowBarAvailability.evaluate(37))
    }

    @Test
    fun androidBefore16HasNoLiveUpdates() {
        assertEquals(NowBarSupport.UNSUPPORTED, NowBarAvailability.evaluate(35))
    }

    @Test
    fun onlyAnUnshowableNowBarChoiceBecomesStandard() {
        val off = NowBarSupport.UNSUPPORTED
        assertEquals(NotificationGuidanceMode.STANDARD, NowBarAvailability.effectiveMode(NotificationGuidanceMode.LIVE_UPDATE, off))
        assertEquals(NotificationGuidanceMode.OFF, NowBarAvailability.effectiveMode(NotificationGuidanceMode.OFF, off))
        assertEquals(NotificationGuidanceMode.STANDARD, NowBarAvailability.effectiveMode(NotificationGuidanceMode.STANDARD, off))
        assertEquals(
            NotificationGuidanceMode.LIVE_UPDATE,
            NowBarAvailability.effectiveMode(NotificationGuidanceMode.LIVE_UPDATE, NowBarSupport.AVAILABLE),
        )
    }
}
