package com.example.dimanow.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-131(5): the 캠퍼스 지도 page accepts a last-known fix on the geofence receiver's terms (2 minutes, 100 m). */
class StudentLocationReaderTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `a last known fix counts only while recent and accurate`() {
        assertTrue(StudentLocationReader.isRecentEnough(now - 60_000, 20f, now))
        assertTrue(StudentLocationReader.isRecentEnough(now - 120_000, 100f, now))
        assertFalse(StudentLocationReader.isRecentEnough(now - 120_001, 20f, now))
        assertFalse(StudentLocationReader.isRecentEnough(now - 60_000, 150f, now))
        assertFalse(StudentLocationReader.isRecentEnough(now + 5_000, 20f, now))
    }
}
