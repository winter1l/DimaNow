package com.example.dimanow.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DefaultSchedule
import com.example.dimanow.meal.DormitoryMealData
import com.example.dimanow.meal.DormitoryMealDay
import com.example.dimanow.meal.DormitoryMealSection
import com.example.dimanow.meal.MealData
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.theme.DIMANowTheme
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test

class HomeMealEmphasisTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun mealEmphasisTracksOpeningAndClosingBoundaries() {
        val now = mutableStateOf(ZonedDateTime.parse("2026-09-15T11:59:00+09:00[Asia/Seoul]"))
        val dark = mutableStateOf(false)
        val dormitory = DormitoryMealData(
            days = listOf(DormitoryMealDay(LocalDate.parse("2026-09-15"),
                listOf(DormitoryMealSection("중식", "12:00~14:00", listOf("테스트 식단"))),
                "https://example.invalid/meal.jpg")),
            lastSuccess = null, lastAttempt = null, error = null,
        )
        composeRule.setContent {
            DIMANowTheme(darkTheme = dark.value) {
                DashboardScreen(
                    schedule = DefaultSchedule.create(), zone = CampusZoneId.YEIN, automatic = true,
                    shuttle = ShuttleData(emptyList(), null, null, null, "https://example.invalid", null),
                    meal = MealData(emptyList(), null, null, null, "https://example.invalid", null, null),
                    dormitoryMeal = dormitory, now = now.value,
                )
            }
        }
        val card = composeRule.onNodeWithTag("dashboard_meal_card")
        card.performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "운영 시간 아님"))
        composeRule.runOnIdle { now.value = now.value.withHour(12).withMinute(0) }
        card.performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "운영 중"))
        capture("meal-open-light.png")
        composeRule.runOnIdle { dark.value = true }
        composeRule.waitForIdle()
        capture("meal-open-dark.png")
        composeRule.runOnIdle { now.value = now.value.withHour(14) }
        card.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "운영 시간 아님"))
    }

    private fun capture(name: String) {
        val bitmap = composeRule.onNodeWithTag("dashboard_meal_card").captureToImage().asAndroidBitmap()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
