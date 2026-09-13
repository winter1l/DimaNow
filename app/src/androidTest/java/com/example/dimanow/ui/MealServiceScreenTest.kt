package com.example.dimanow.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.domain.MealDay
import com.example.dimanow.domain.MealValidationState
import com.example.dimanow.meal.*
import com.example.dimanow.theme.DIMANowTheme
import java.time.LocalDate
import java.time.LocalTime
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class MealServiceScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.parse("2026-09-08")

    @Test
    fun dormitoryOpensAtTheCurrentMealAndHighlightsIt() {
        compose.setContent {
            DIMANowTheme(darkTheme = true) {
                Surface { MealScreen(source(), today = today, nowTime = LocalTime.of(12, 30), initialVenue = MealVenue.DORMITORY) }
            }
        }
        compose.onNodeWithText("운영 중 · 14:00까지").assertIsDisplayed()
        compose.onNodeWithText("중식").assertIsDisplayed()
        compose.onNodeWithText("조식").assertIsNotDisplayed()
        screenshot("dormitory-open-dark")
    }

    @Test
    fun minuteTicksPreserveManualScrollingAndClosingMovesToTheNextMeal() {
        val time = mutableStateOf(LocalTime.of(12, 30))
        val source = source()
        compose.setContent {
            DIMANowTheme(darkTheme = true) {
                Surface { MealScreen(source, today = today, nowTime = time.value, initialVenue = MealVenue.DORMITORY) }
            }
        }
        compose.onNodeWithTag("meal_list").performScrollToIndex(0)
        compose.onNodeWithText("조식").assertIsDisplayed()
        compose.runOnIdle { time.value = LocalTime.of(12, 31) }
        compose.onNodeWithText("조식").assertIsDisplayed()
        screenshot("dormitory-ended-breakfast-dark")

        compose.runOnIdle { time.value = LocalTime.of(14, 0) }
        compose.onNodeWithText("석식").assertIsDisplayed()
        compose.onNodeWithText("운영 전 · 18:00부터").assertIsDisplayed()
        compose.onNodeWithText("운영 중", substring = true).assertDoesNotExist()
        screenshot("dormitory-next-dinner-dark")

        compose.runOnIdle { time.value = LocalTime.of(18, 0) }
        compose.onNodeWithText("운영 중 · 19:30까지").assertIsDisplayed()
        compose.runOnIdle { time.value = LocalTime.of(19, 30) }
        compose.onNodeWithText("석식").assertIsDisplayed()
        compose.onNodeWithText("운영 중", substring = true).assertDoesNotExist()
        screenshot("dormitory-all-ended-dark")
    }

    @Test
    fun cafeteriaHighlightsOpenServiceAndHidesLastCheckWithoutLosingTheMenu() {
        val time = mutableStateOf(LocalTime.of(11, 30))
        val source = source()
        compose.setContent {
            DIMANowTheme(darkTheme = false) {
                Surface { MealScreen(source, today = today, nowTime = time.value) }
            }
        }
        compose.onNodeWithText("운영 중 · 14:00까지").assertIsDisplayed()
        compose.onNodeWithText("마지막 확인", substring = true).assertDoesNotExist()
        compose.onNodeWithText("이번 주 식단 게시를 기다리고 있어요").assertDoesNotExist()
        compose.onNodeWithText("제육볶음").assertIsDisplayed()
        screenshot("cafeteria-open-light")
        compose.runOnIdle { time.value = LocalTime.of(14, 0) }
        compose.onNodeWithText("운영 종료").assertIsDisplayed()
        compose.onNodeWithText("제육볶음").assertIsDisplayed()
        screenshot("cafeteria-ended-light")
    }

    @Test
    fun choosingAnotherDayStartsAtItsHeadingWithoutHighlightingItsMealsAsOpen() {
        compose.setContent {
            DIMANowTheme(darkTheme = false) {
                Surface { MealScreen(source(), today = today, nowTime = LocalTime.of(12, 30), initialVenue = MealVenue.DORMITORY) }
            }
        }
        compose.onNodeWithTag("meal_day_WEDNESDAY").performClick()
        compose.onNodeWithText("9월 9일 수요일").assertIsDisplayed()
        compose.onNodeWithText("조식").assertIsDisplayed()
        compose.onNodeWithText("운영 중", substring = true).assertDoesNotExist()
        screenshot("dormitory-future-light")
        compose.onNodeWithTag("meal_day_MONDAY").performClick()
        compose.onNodeWithText("9월 7일 월요일").assertIsDisplayed()
        compose.onNodeWithText("조식").assertIsDisplayed()
        compose.onNodeWithText("운영 중", substring = true).assertDoesNotExist()
        screenshot("dormitory-past-light")
    }

    private fun screenshot(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "meal-service-proof").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun source(): MealSource = object : MealSource {
        override val data = MutableStateFlow(MealData(
            (-1L..3L).map { offset -> MealDay(today.plusDays(offset), listOf("제육볶음", "미역국"), "11:30~14:00", "https://example.invalid", "https://example.invalid/menu.jpg", MealValidationState.VALID) },
            null, null, null, "https://example.invalid", null, "11:30~14:00",
        ))
        override val dormitoryData = MutableStateFlow(DormitoryMealData(
            (-1L..1L).map { offset -> DormitoryMealDay(today.plusDays(offset), listOf(
                DormitoryMealSection("조식", "08:00~09:30", listOf("떡국", "계란말이", "배추김치", "흰쌀밥")),
                DormitoryMealSection("간편식", null, listOf("시리얼", "우유", "토스트", "딸기잼")),
                DormitoryMealSection("중식", "12:00~14:00", listOf("미역국", "제육볶음", "흰쌀밥", "배추김치")),
                DormitoryMealSection("라면", null, listOf("신라면")),
                DormitoryMealSection("석식", "18:00~19:30", listOf("불고기", "된장국", "흰쌀밥", "깍두기")),
            ), "https://example.invalid/menu.jpg") }, null, null, null,
        ))
        override suspend fun refresh() = MealRefreshResult.NotPublishedYet
        override suspend fun refreshIfDue(trigger: MealRefreshTrigger, now: java.time.Instant): MealRefreshResult? = null
    }
}
