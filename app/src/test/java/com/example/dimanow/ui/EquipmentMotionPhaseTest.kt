package com.example.dimanow.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EquipmentMotionPhaseTest {
    @Test
    fun `without a catalog the page shows the read, its failure or the sign-in prompt`() {
        assertEquals(EquipmentCatalogPhase.LOADING, equipmentCatalogPhase(null, loading = true, failed = false, canSignIn = true))
        // A retry after a failure shows the loading line again, not the old failure.
        assertEquals(EquipmentCatalogPhase.LOADING, equipmentCatalogPhase(null, loading = true, failed = true, canSignIn = true))
        assertEquals(EquipmentCatalogPhase.ERROR, equipmentCatalogPhase(null, loading = false, failed = true, canSignIn = true))
        assertEquals(EquipmentCatalogPhase.START, equipmentCatalogPhase(null, loading = false, failed = false, canSignIn = true))
        assertEquals(EquipmentCatalogPhase.NONE, equipmentCatalogPhase(null, loading = false, failed = false, canSignIn = false))
    }

    @Test
    fun `a catalog in hand stays on screen through a refresh and its failure`() {
        assertEquals(EquipmentCatalogPhase.EMPTY, equipmentCatalogPhase(0, loading = false, failed = false, canSignIn = true))
        assertEquals(EquipmentCatalogPhase.READY, equipmentCatalogPhase(4, loading = false, failed = false, canSignIn = true))
        // Pull-to-refresh and a failed refresh do not swap the groups for a loading line or an error.
        assertEquals(EquipmentCatalogPhase.READY, equipmentCatalogPhase(4, loading = true, failed = false, canSignIn = true))
        assertEquals(EquipmentCatalogPhase.READY, equipmentCatalogPhase(4, loading = false, failed = true, canSignIn = true))
    }

    @Test
    fun `an open model shows its read first, then a failure, then the days`() {
        assertEquals(EquipmentDaysPhase.LOADING, equipmentDaysPhase(loading = true, failed = true, hasDays = true))
        assertEquals(EquipmentDaysPhase.ERROR, equipmentDaysPhase(loading = false, failed = true, hasDays = true))
        assertEquals(EquipmentDaysPhase.DAYS, equipmentDaysPhase(loading = false, failed = false, hasDays = true))
        assertEquals(EquipmentDaysPhase.NONE, equipmentDaysPhase(loading = false, failed = false, hasDays = false))
    }
}
