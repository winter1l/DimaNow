package com.example.dimanow.ui

import com.example.dimanow.school.EquipmentGroup
import com.example.dimanow.school.EquipmentModel
import org.junit.Assert.assertEquals
import org.junit.Test

/** D-131 (Q-032 (g)): the equipment catalog as lazy items, one per heading, model row and day panel. */
class EquipmentCatalogRowsTest {
    private val cameras = EquipmentGroup("카메라(일반대여)", listOf(EquipmentModel(1, "A7", 3), EquipmentModel(2, "FX3", 1)))
    private val lights = EquipmentGroup("조명(일반대여)", listOf(EquipmentModel(7, "LED", 2)))

    @Test
    fun `each group is a heading followed by its models as one segmented list`() {
        val rows = equipmentCatalogRows(listOf(cameras, lights)) { _, _ -> false }

        assertEquals(
            listOf(
                "equipment_group_카메라(일반대여)",
                "equipment_model_카메라(일반대여)_1",
                "equipment_model_카메라(일반대여)_2",
                "equipment_group_조명(일반대여)",
                "equipment_model_조명(일반대여)_7",
            ),
            rows.map { it.key },
        )
        val models = rows.filterIsInstance<EquipmentCatalogRow.Model>()
        assertEquals(listOf(0 to 2, 1 to 2, 0 to 1), models.map { it.index to it.count })
    }

    @Test
    fun `an open or closing panel takes the next place in its group's shapes`() {
        val rows = equipmentCatalogRows(listOf(cameras)) { _, model -> model.no == 1L }

        val panel = rows[2] as EquipmentCatalogRow.Panel
        assertEquals("equipment_model_카메라(일반대여)_1_days", panel.key)
        assertEquals("equipment_model_카메라(일반대여)_1", panel.modelKey)
        assertEquals(1 to 3, panel.index to panel.count)
        val last = rows[3] as EquipmentCatalogRow.Model
        assertEquals(2 to 3, last.index to last.count)
    }

    @Test
    fun `a name or number listed twice still gets unique keys`() {
        val twice = EquipmentGroup("카메라(일반대여)", listOf(EquipmentModel(1, "A7", 3), EquipmentModel(1, "A7", 3)))
        val rows = equipmentCatalogRows(listOf(twice, twice)) { _, _ -> true }

        val keys = rows.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
