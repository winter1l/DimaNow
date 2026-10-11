package com.example.dimanow.widget

import com.example.dimanow.domain.MealDay
import com.example.dimanow.meal.MealServiceState
import com.example.dimanow.meal.MealServiceStatus

/** Widget type-scale role for the menu text (res/values/dimens.xml `widget_text_*`). */
enum class WidgetTextRole {
    CAPTION,
    BODY,
}

data class MealWidgetLayoutPlan(
    val menuMaxLines: Int,
    val menuText: WidgetTextRole,
    val horizontalPaddingDp: Int,
)

class MealWidgetLayoutPlanner {
    fun plan(minWidthDp: Int, minHeightDp: Int): MealWidgetLayoutPlan =
        if (minWidthDp < 180 || minHeightDp < 100) {
            MealWidgetLayoutPlan(menuMaxLines = 8, menuText = WidgetTextRole.CAPTION, horizontalPaddingDp = 10)
        } else {
            MealWidgetLayoutPlan(menuMaxLines = 8, menuText = WidgetTextRole.BODY, horizontalPaddingDp = 16)
        }
}

data class MealWidgetContent(
    val hoursLabel: String,
    val menuText: String,
    val stateText: String?,
    val spokenSummary: String,
)

/** Wording of the 2x2 meal widget; the provider only binds it. */
class MealWidgetContentPlanner {
    fun plan(meal: MealDay?, status: MealServiceStatus, isWeekend: Boolean, refreshFailed: Boolean): MealWidgetContent {
        val menuLines = meal?.menuLines.orEmpty()
        val menuText = if (menuLines.isNotEmpty()) {
            menuLines.joinToString("\n")
        } else if (isWeekend) {
            "주말에는 학생식당을 쉬어요"
        } else {
            "오늘 등록된 식단이 없어요"
        }
        // 위젯 헤더는 좁으므로 운영 전에는 접두어 없이 시작 시각만 짧게 표시한다 (D-031)
        val hoursLabel = when (status.state) {
            MealServiceState.BEFORE_OPEN -> status.label.removePrefix("운영 전 · ")
            // 식단이 없는 날은 본문이 한 번만 알린다 (D-094(14))
            MealServiceState.NO_MENU -> ""
            else -> status.label
        }
        // 기술적인 오류 원문은 설정의 진단 카드에만 둔다 (D-094(14))
        val stateText = if (refreshFailed) "식단을 새로 받지 못했어요" else null
        val spoken = buildList {
            add("학생식당")
            // The spoken form keeps the full status (`운영 전 · 11:00부터`), not the shortened header.
            if (status.state != MealServiceState.NO_MENU && status.label.isNotBlank()) add(status.label)
            add(if (menuLines.isNotEmpty()) menuLines.joinToString(", ") else menuText)
            stateText?.let(::add)
        }.joinToString(". ")
        return MealWidgetContent(hoursLabel = hoursLabel, menuText = menuText, stateText = stateText, spokenSummary = spoken)
    }
}
