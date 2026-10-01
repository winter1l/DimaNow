package com.example.dimanow.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.TermSchedule
import com.example.dimanow.meal.MealData
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.theme.DIMANowTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test

/** D-094(14): the Home hero counts minutes only within an hour; the no-menu message appears once. */
class HomeHeroCountdownTest {
    @get:Rule val compose = createComposeRule()

    private val seoul = ZoneId.of("Asia/Seoul")
    // 2026-09-30 is a Wednesday.
    private val today = LocalDate.parse("2026-09-30")
    private val schedule = TermSchedule(
        termStart = LocalDate.parse("2026-08-24"),
        termEnd = LocalDate.parse("2026-12-18"),
        courses = listOf(
            Course(today.dayOfWeek, LocalTime.of(13, 0), LocalTime.of(14, 50), "프리젠테이션영어", "덕성관 510-1", "", CampusZoneId.MAIN, id = 1),
        ),
    )
    private val emptyShuttle = ShuttleData(emptyList(), null, null, null, "https://example.invalid", null)
    private val noMenu = MealData(emptyList(), null, null, null, "https://example.invalid", null, null)

    @Test
    fun heroShowsTheStartClockBeyondAnHourAndCountsDownWithinIt() {
        val now = mutableStateOf(ZonedDateTime.of(today, LocalTime.of(1, 28), seoul))
        compose.setContent {
            DIMANowTheme(darkTheme = false) {
                DashboardScreen(
                    schedule = schedule, zone = CampusZoneId.MAIN,
                    shuttle = emptyShuttle, meal = noMenu, now = now.value,
                )
            }
        }

        // 692 minutes away: the start clock, never "시작까지 692분".
        compose.onNodeWithText("13:00 시작").assertExists()
        compose.onAllNodesWithText("시작까지", substring = true).assertCountEquals(0)
        capture("home-hero-start-clock")

        compose.runOnIdle { now.value = ZonedDateTime.of(today, LocalTime.of(11, 59), seoul) }
        compose.waitForIdle()
        compose.onNodeWithText("13:00 시작").assertExists()

        compose.runOnIdle { now.value = ZonedDateTime.of(today, LocalTime.of(12, 0), seoul) }
        compose.waitForIdle()
        compose.onNodeWithText("시작까지 60분").assertExists()
        compose.onAllNodesWithText("13:00 시작").assertCountEquals(0)

        compose.runOnIdle { now.value = ZonedDateTime.of(today, LocalTime.of(12, 48), seoul) }
        compose.waitForIdle()
        compose.onNodeWithText("시작까지 12분").assertExists()
        capture("home-hero-countdown")

        compose.runOnIdle { now.value = ZonedDateTime.of(today, LocalTime.of(13, 5), seoul) }
        compose.waitForIdle()
        compose.onNodeWithText("수업 중").assertExists()
    }

    @Test
    fun mealNoMenuDayShowsItsMessageOnlyOnce() {
        compose.setContent {
            DIMANowTheme(darkTheme = false) {
                DashboardScreen(
                    schedule = schedule, zone = CampusZoneId.MAIN,
                    shuttle = emptyShuttle, meal = noMenu,
                    now = ZonedDateTime.of(today, LocalTime.of(12, 0), seoul),
                )
            }
        }
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("dashboard_meal_card"))

        compose.onNodeWithText("오늘 등록된 식단이 없어요", useUnmergedTree = true).assertExists()
        // The header status pill no longer repeats the same news.
        compose.onNodeWithText("오늘은 제공 식단이 없어요", useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodes(
            hasText("식단", substring = true) and hasText("없", substring = true) and
                hasAnyAncestor(hasTestTag("dashboard_meal_card")),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        capture("home-meal-no-menu", tag = "dashboard_meal_card")
    }

    private fun capture(name: String, tag: String? = null) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "phase6").apply { mkdirs() }
        val node = if (tag != null) compose.onNodeWithTag(tag) else compose.onRoot()
        File(directory, "$name.png").outputStream().use {
            node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
