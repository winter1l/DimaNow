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
    if (meal.hasCurrentStudentWeek(today) && meal.error == null) return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (!meal.hasCurrentStudentWeek(today)) {
            Text("이번 주 식단을 아직 불러오지 못했어요", style = MaterialTheme.typography.bodyMedium)
        }
        if (meal.error != null) {
            Text("식단을 확인하지 못했어요. 잠시 후 다시 확인할게요.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
