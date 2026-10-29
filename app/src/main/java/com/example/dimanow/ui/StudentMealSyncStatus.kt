package com.example.dimanow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.dimanow.meal.MealData
import com.example.dimanow.meal.hasCurrentStudentWeek
import com.example.dimanow.ui.motion.dimaContentReveal
import java.time.LocalDate

/** What the student cafeteria line above the day's menu says (D-129). */
internal enum class StudentMealSyncLine { NONE, NOT_POSTED, FAILED }

/**
 * D-129: only a download the app itself could not finish is a failure. When the data server answered that it
 * has no new week yet (`WAITING`) or could not read this week's post (`NEEDS_REVIEW`), the week simply has not
 * come in, which before the cafeteria posts on Monday is the usual case; showing it as an error read as a fault.
 */
internal fun studentMealSyncLine(meal: MealData, today: LocalDate): StudentMealSyncLine {
    val currentWeek = meal.hasCurrentStudentWeek(today)
    val serverWaiting = meal.serverState == "WAITING" || meal.serverState == "NEEDS_REVIEW"
    return when {
        currentWeek && (meal.error == null || serverWaiting) -> StudentMealSyncLine.NONE
        meal.error == null || serverWaiting -> StudentMealSyncLine.NOT_POSTED
        else -> StudentMealSyncLine.FAILED
    }
}

/**
 * Student cafeteria sync state above the day's menu, in the shared empty/error style (D-094(14)).
 * A failed download offers the same refresh as pulling the list; the raw error text stays in
 * Settings diagnostics.
 *
 * D-137: the caller emits it only for a [line] other than [StudentMealSyncLine.NONE], so no empty row takes the list's
 * spacing, and passes the list item's motion as [modifier]; a change between not posted and failed swaps the message
 * in place ([dimaContentReveal]).
 */
@Composable
internal fun StudentMealSyncStatus(
    meal: MealData,
    today: LocalDate,
    line: StudentMealSyncLine,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    AnimatedContent(
        targetState = line,
        // The line keeps the larger height while it changes, so the day items below glide once instead of chasing it.
        transitionSpec = { dimaContentReveal() using null },
        label = "student_meal_sync",
        modifier = modifier,
    ) { shown ->
        when (shown) {
            StudentMealSyncLine.NONE -> Unit
            StudentMealSyncLine.NOT_POSTED -> EmptyState(
                message = "이번 주 식단이 아직 올라오지 않았어요",
                supporting = "식단이 올라오면 자동으로 보여 드릴게요",
                modifier = Modifier.testTag("student_meal_waiting"),
            )
            StudentMealSyncLine.FAILED -> ErrorState(
                message = if (meal.hasCurrentStudentWeek(today)) "식단을 새로 확인하지 못했어요" else "이번 주 식단을 불러오지 못했어요",
                supporting = "잠시 후 다시 확인할게요. 지금 확인하려면 다시 시도해 주세요",
                onRetry = onRetry,
                modifier = Modifier.testTag("student_meal_error"),
            )
        }
    }
}
