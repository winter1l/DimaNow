package com.example.dimanow.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class MealWidgetLayoutPlannerTest {
    @Test
    fun `small launcher cell uses compact readable meal layout`() {
        assertEquals(
            MealWidgetLayoutPlan(menuMaxLines = 8, menuTextSp = 12f, horizontalPaddingDp = 10),
            MealWidgetLayoutPlanner().plan(minWidthDp = 110, minHeightDp = 70),
        )
    }

    @Test
    fun `larger launcher cell keeps the regular meal detail layout`() {
        assertEquals(
            MealWidgetLayoutPlan(menuMaxLines = 8, menuTextSp = 13.5f, horizontalPaddingDp = 16),
            MealWidgetLayoutPlanner().plan(minWidthDp = 250, minHeightDp = 110),
        )
    }
}
