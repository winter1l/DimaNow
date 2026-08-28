package com.example.dimanow.widget

data class MealWidgetLayoutPlan(
    val menuMaxLines: Int,
    val menuTextSp: Float,
    val horizontalPaddingDp: Int,
)

class MealWidgetLayoutPlanner {
    fun plan(minWidthDp: Int, minHeightDp: Int): MealWidgetLayoutPlan =
        if (minWidthDp < 180 || minHeightDp < 100) {
            MealWidgetLayoutPlan(menuMaxLines = 8, menuTextSp = 12f, horizontalPaddingDp = 10)
        } else {
            MealWidgetLayoutPlan(menuMaxLines = 8, menuTextSp = 13.5f, horizontalPaddingDp = 16)
        }
}
