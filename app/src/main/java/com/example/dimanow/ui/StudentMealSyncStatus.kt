package com.example.dimanow.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.dimanow.meal.MealData
import com.example.dimanow.meal.hasCurrentStudentWeek
import java.time.LocalDate

/**
 * Student cafeteria sync state above the day's menu, in the shared empty/error style (D-094(14)).
 * A failed download offers the same refresh as pulling the list; the raw error text stays in
 * Settings diagnostics.
 */
@Composable
internal fun StudentMealSyncStatus(meal: MealData, today: LocalDate, onRetry: (() -> Unit)? = null) {
    val currentWeek = meal.hasCurrentStudentWeek(today)
    if (currentWeek && meal.error == null) return
    if (meal.error != null) {
        ErrorState(
            message = if (currentWeek) "식단을 새로 확인하지 못했어요" else "이번 주 식단을 아직 불러오지 못했어요",
            supporting = "잠시 후 다시 확인할게요. 지금 확인하려면 다시 시도해 주세요",
            onRetry = onRetry,
            modifier = Modifier.testTag("student_meal_error"),
        )
    } else {
        EmptyState(
            message = "이번 주 식단을 아직 불러오지 못했어요",
            supporting = "식단이 올라오면 자동으로 보여 드릴게요",
            modifier = Modifier.testTag("student_meal_waiting"),
        )
    }
}
