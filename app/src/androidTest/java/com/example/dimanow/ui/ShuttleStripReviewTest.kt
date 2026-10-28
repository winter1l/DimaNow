package com.example.dimanow.ui

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.shuttle.ShuttleRefreshResult
import com.example.dimanow.shuttle.ShuttleSource
import com.example.dimanow.theme.DIMANowTheme
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/** D-124: the one-strip route cards on a weekday, captured for review under `files/d124-review`. */
class ShuttleStripReviewTest {
    @get:Rule val compose = createComposeRule()

    private val monday = DayOfWeek.MONDAY
    private val enter = "08:25 08:35 08:45 08:55 09:35 09:45 09:55 10:35 10:45 10:55 11:35 11:45 11:55 12:35 12:45 12:55 13:45 13:55 14:45 14:55 15:45 15:55 16:45 16:55 17:45 17:55 18:55 19:25 19:50 20:50 21:30 21:50"
    private val oneRoom = "08:45 09:35 09:45 10:35 10:45 11:35 11:45 12:35 12:45 13:45 14:45 15:45 16:45 17:45"

    private fun route(times: String, destination: CampusZoneId) = times.split(' ').map { clock ->
        val time = LocalTime.parse(clock)
        val stadium = !time.isBefore(LocalTime.of(18, 55))
        ShuttleDeparture(
            if (stadium) "B-evening" else "B",
            if (stadium) "stadium-stop" else "university-headquarters",
            "TO_${destination.name}",
            monday,
            time,
            CampusZoneId.MAIN,
            destination,
        )
    }

    private val source = object : ShuttleSource {
        override val data = MutableStateFlow(
            ShuttleData(
                route(enter, CampusZoneId.YEIN) + route(oneRoom, CampusZoneId.ONE_ROOM),
                Instant.parse("2026-10-26T00:00:00Z"),
                Instant.parse("2026-10-26T00:00:00Z"),
                null,
                "https://www.dima.ac.kr/?p=97",
                null,
            ),
        )
        override suspend fun refresh() = ShuttleRefreshResult.Success(0, Instant.EPOCH, changed = false)
    }

    @Test
    fun weekdayAfternoon() = capture("13-50", 13, 50, dark = false)

    @Test
    fun homeShuttleRowsAtPhoneWidth() {
        val shuttle = source.data.value
        val minute = androidx.compose.runtime.mutableStateOf(ZonedDateTime.of(2026, 10, 26, 10, 3, 0, 0, ZoneId.of("Asia/Seoul")))
        compose.setContent {
            DIMANowTheme(darkTheme = true) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(411.dp)) {
                    DashboardScreen(
                        schedule = com.example.dimanow.domain.SampleSchedule.create(),
                        zone = CampusZoneId.MAIN,
                        shuttle = shuttle,
                        meal = com.example.dimanow.meal.MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null),
                        now = minute.value,
                    )
                }
            }
        }
        compose.waitForIdle()
        saveRoot("home-10-03")
        compose.runOnIdle { minute.value = minute.value.withHour(6).withMinute(30) }
        compose.waitForIdle()
        saveRoot("home-06-30")
    }

    @Test
    fun theNowButtonBringsTheStripBackToTheNextDeparture() {
        compose.setContent {
            DIMANowTheme {
                ShuttleScreen(
                    shuttleSource = source,
                    currentZone = CampusZoneId.MAIN,
                    now = ZonedDateTime.of(2026, 10, 26, 13, 50, 0, 0, ZoneId.of("Asia/Seoul")),
                )
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("next_departure_0"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val strip = compose.onAllNodes(hasScrollToIndexAction() and !hasTestTag("shuttle_list") and hasAnyDescendant(hasTestTag("next_departure_0"))).onFirst()
        repeat(4) { strip.performTouchInput { swipeLeft() } }
        compose.waitForIdle()
        // D-129: the back control is the Arrow shape alone, pointing back at the next departure.
        saveRoot("after-swipe")
        compose.onAllNodes(hasTestTag("departure_strip_back")).onFirst().assertExists().performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasTestTag("next_departure_0"), useUnmergedTree = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun lastServiceAtNightDark() = capture("21-38-dark", 21, 38, dark = true)

    /** D-136: after the day's service the last departure is a quiet past time, not an amber cell. */
    @Test
    fun serviceEndedAtNightDark() = capture("23-00-dark", 23, 0, dark = true)

    private fun saveRoot(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "d124-review")
            .apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun capture(name: String, hour: Int, minute: Int, dark: Boolean) {
        compose.setContent {
            DIMANowTheme(darkTheme = dark) {
                ShuttleScreen(
                    shuttleSource = source,
                    currentZone = CampusZoneId.MAIN,
                    now = ZonedDateTime.of(2026, 10, 26, hour, minute, 0, 0, ZoneId.of("Asia/Seoul")),
                )
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("엔터관행")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "d124-review")
            .apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
