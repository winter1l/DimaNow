package com.example.dimanow.school

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentCatalogRetryTest {
    private val failedAt = Instant.parse("2026-10-23T02:52:00Z")

    @Test
    fun `reopening the page soon after a failed read does not read DIMA C again by itself`() {
        assertTrue(equipmentCatalogAutoReadAllowed(null, failedAt))
        assertFalse(equipmentCatalogAutoReadAllowed(failedAt, failedAt.plusSeconds(30)))
        assertFalse(equipmentCatalogAutoReadAllowed(failedAt, failedAt.plusSeconds(299)))
        assertTrue(equipmentCatalogAutoReadAllowed(failedAt, failedAt.plusSeconds(300)))
    }
}
