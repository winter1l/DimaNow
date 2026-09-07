package com.example.dimanow.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dimanow.meal.MealData
import com.example.dimanow.meal.hasCurrentStudentWeek
import java.time.LocalDate

@Composable
internal fun StudentMealSyncStatus(meal: MealData, today: LocalDate) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (!meal.hasCurrentStudentWeek(today)) {
            Text("이번 주 식단 게시를 기다리고 있어요", style = MaterialTheme.typography.bodyMedium)
        }
        if (meal.error != null) {
            Text("식단을 확인하지 못했어요. 잠시 후 다시 확인할게요.", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "마지막 확인: ${meal.lastAttempt?.let(::formatSourceSuccessTime) ?: "아직 확인하지 않았어요"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
