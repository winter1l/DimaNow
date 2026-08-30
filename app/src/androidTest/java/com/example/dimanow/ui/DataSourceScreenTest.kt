package com.example.dimanow.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.material3.MaterialTheme
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.MealDay
import com.example.dimanow.domain.MealValidationState
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.meal.MealData
import com.example.dimanow.meal.MealRefreshResult
import com.example.dimanow.meal.MealSource
import com.example.dimanow.meal.DormitoryMealData
import com.example.dimanow.meal.DormitoryMealDay
import com.example.dimanow.meal.DormitoryMealSection
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.shuttle.ShuttleRefreshResult
import com.example.dimanow.shuttle.ShuttleSource
import com.example.dimanow.theme.DIMANowTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class DataSourceScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun nearestNormalDepartureUsesTheSolidPrimaryColorInLightTheme() {
        val now = ZonedDateTime.of(2026, 8, 31, 9, 30, 0, 0, ZoneId.of("Asia/Seoul"))
        val departures = listOf(
            departure("A", "main", LocalTime.of(8, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
            departure("A", "main", LocalTime.of(10, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
            departure("A", "main", LocalTime.of(11, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
        )
        var expected = Color.Unspecified
        composeRule.setContent {
            DIMANowTheme(darkTheme = false) {
                expected = MaterialTheme.colorScheme.primary
                ShuttleScreen(FakeShuttleSource(departures), CampusZoneId.MAIN, now = now)
            }
        }

        composeRule.onNodeWithTag("next_departure_0").captureToImage().assertMostly(expected)
    }

    @Test
    fun nearestLastDepartureUsesTheSolidErrorColorInLightTheme() {
        val now = ZonedDateTime.of(2026, 8, 31, 8, 30, 0, 0, ZoneId.of("Asia/Seoul"))
        val departures = listOf(
            departure("A", "main", LocalTime.of(8, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
            departure("A", "main", LocalTime.of(9, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
        )
        var expected = Color.Unspecified
        composeRule.setContent {
            DIMANowTheme(darkTheme = false) {
                expected = MaterialTheme.colorScheme.error
                ShuttleScreen(FakeShuttleSource(departures), CampusZoneId.MAIN, now = now)
            }
        }

        composeRule.onNodeWithTag("next_departure_0").captureToImage().assertMostly(expected)
    }

    @Test
    fun shuttleCacheDescribesRawWeeklyRowsAndUniqueUserDepartureSlots() {
        val serviceDay = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).dayOfWeek
        val departures = listOf(
            departure("A", "headquarters-a", LocalTime.of(19, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("B", "headquarters-b", LocalTime.of(19, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("B", "headquarters", LocalTime.of(19, 30), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("C", "one-room", LocalTime.of(19, 0), CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, serviceDay),
        )
        composeRule.setContent {
            DataAndSourcesCard(
                shuttleData = FakeShuttleSource(departures).data.value,
                mealData = FakeMealSource().data.value,
            )
        }

        val expected = "기기 동기화: 2026년 8월 26일 21:00 KST"
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(expected).assertExists()
        composeRule.onNodeWithText("서버 게시: 2026년 8월 26일 20:30 KST").assertExists()
        composeRule.onNodeWithText("공식 주간 시간표 4행 · 사용자 출발 슬롯 3개").assertExists()
        composeRule.onNodeWithText("캠퍼스 구역 CAMPUS_ZONES_V2_USER_2026_08_27 · © OpenStreetMap contributors").assertExists()
    }

    @Test
    fun shuttleRefreshShowsDisabledLoadingControlUntilTheSingleRefreshFinishes() {
        val source = BlockingShuttleSource()
        composeRule.setContent {
            ShuttleScreen(shuttleSource = source, currentZone = CampusZoneId.OUTSIDE)
        }

        composeRule.onNodeWithContentDescription("셔틀 새로고침").performClick()
        composeRule.waitUntil(5_000) { source.started.isCompleted }
        composeRule.onNodeWithContentDescription("셔틀 새로고침 중").assertExists()

        source.release.complete(Unit)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("2건 저장 완료").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("셔틀 새로고침").assertExists()
    }

    @Test
    fun shuttleUsesOnlyATopRefreshActionAndMovesDataDetailsOutOfTheScreen() {
        val serviceDay = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).dayOfWeek
        composeRule.setContent {
            ShuttleScreen(
                shuttleSource = FakeShuttleSource(
                    listOf(
                        departure("A", "headquarters", LocalTime.of(8, 10), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
                    ),
                ),
                currentZone = CampusZoneId.MAIN,
            )
        }

        composeRule.onNodeWithContentDescription("셔틀 새로고침").assertExists()
        composeRule.onNodeWithText("셔틀 데이터 상태").assertDoesNotExist()
        composeRule.onNodeWithText("셔틀 데이터 관리").assertDoesNotExist()
        composeRule.onNodeWithText("요일 선택").assertDoesNotExist()
    }

    @Test
    fun userSeesEveryShuttleDayAndOriginWithoutChoosingFilters() {
        val serviceDay = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).dayOfWeek
        val departures = listOf(
            departure("A", "headquarters-a", LocalTime.of(8, 10), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("B", "headquarters-b", LocalTime.of(8, 10), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("B", "headquarters", LocalTime.of(8, 40), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("B", "headquarters", LocalTime.of(9, 10), CampusZoneId.MAIN, CampusZoneId.YEIN, serviceDay),
            departure("A", "headquarters", LocalTime.of(8, 20), CampusZoneId.MAIN, CampusZoneId.ONE_ROOM, serviceDay),
            departure("B", "headquarters", LocalTime.NOON, CampusZoneId.MAIN, CampusZoneId.YEIN, DayOfWeek.TUESDAY),
        )
        composeRule.setContent {
            ShuttleScreen(
                shuttleSource = FakeShuttleSource(departures),
                currentZone = CampusZoneId.MAIN,
                // 공식 저녁 운동장 운행으로 전환되기 전이면서 모든 fixture 운행이 끝난
                // 시각을 사용해, 본관 그룹 헤더와 전체 시간표를 안정적으로 검증한다.
                now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
                    .withHour(18).withMinute(0).withSecond(0).withNano(0),
            )
        }

        composeRule.onNodeWithText("셔틀버스").assertExists()
        composeRule.onNodeWithText("본관").assertExists()
        composeRule.onNodeWithText("현재 위치").assertExists()
        composeRule.onNodeWithText("엔터관행").assertExists()
        composeRule.onNodeWithText("원룸촌행").assertExists()
        composeRule.onNodeWithText("본관 → 엔터관").assertDoesNotExist()
        composeRule.onNodeWithText("본관 → 원룸촌").assertDoesNotExist()
        composeRule.onNodeWithText("총 3회 (첫차 08:10 · 막차 09:10)").assertExists()
        composeRule.onNodeWithText("08:10 (첫차)").assertExists()
        composeRule.onNodeWithText("08:40").assertExists()
        composeRule.onNodeWithText("09:10 (막차)").assertExists()
    }

    @Test
    fun mainGroupAndDepartureLabelsSwitchToStadiumForOfficialEveningService() {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
            .withHour(19).withMinute(30).withSecond(0).withNano(0)
        val departures = listOf(
            departure("B", "university-headquarters", LocalTime.of(19, 20), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
            departure("B-evening", "stadium-stop", LocalTime.of(19, 35), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
            departure("B-evening", "stadium-stop", LocalTime.of(20, 0), CampusZoneId.MAIN, CampusZoneId.YEIN, now.dayOfWeek),
        )
        composeRule.setContent {
            ShuttleScreen(
                shuttleSource = FakeShuttleSource(departures),
                currentZone = CampusZoneId.MAIN,
                now = now,
            )
        }

        composeRule.onNodeWithText("20:00 (막차) · 운동장").assertExists()
        composeRule.onNodeWithText("5분 후 · 운동장 전환").assertExists()
        composeRule.onNodeWithText("19:35 · 운동장 전환").assertExists()
    }

    @Test
    fun userSeesTheWholeCurrentWeekMealWithoutChoosingADay() {
        val meals = listOf(
            mealDay("2026-08-21", "이전 식단"),
            mealDay("2026-08-24", "제육볶음", "미역국"),
            mealDay("2026-08-25", "돈가스"),
            mealDay("2026-08-28", "김치볶음밥"),
            mealDay("2026-08-31", "다음 식단"),
        )
        composeRule.setContent {
            MealScreen(
                mealSource = FakeMealSource(meals),
                today = LocalDate.parse("2026-08-27"),
            )
        }

        composeRule.onNodeWithText("이번 주 식단 (월 ~ 금)").assertDoesNotExist()
        composeRule.onNodeWithText("8/24 (월)").assertExists()
        composeRule.onNodeWithText("제육볶음 · 미역국").assertExists()
        composeRule.onNodeWithText("8/28 (금)").assertExists()
        composeRule.onAllNodesWithText("등록된 식단 없음").assertCountEquals(2)
        composeRule.onAllNodesWithText("이전 식단").assertCountEquals(0)
        composeRule.onAllNodesWithText("다음 식단").assertCountEquals(0)
    }

    @Test
    fun dormitoryTabShowsUploadOnlyWhenTheCurrentWeekIsMissing() {
        composeRule.setContent {
            MealScreen(
                mealSource = FakeMealSource(),
                today = LocalDate.parse("2026-08-27"),
            )
        }

        composeRule.onNodeWithText("본관 학생식당").assertExists()
        composeRule.onNodeWithText("기숙사").performClick()
        composeRule.onNodeWithText("사진 올리기").assertExists()
        composeRule.onAllNodesWithText("등록된 식단 없음").assertCountEquals(5)
    }

    @Test
    fun currentDormitoryWeekKeepsEveryMealSectionAndHidesUpload() {
        val dormitoryDays = listOf(
            DormitoryMealDay(
                date = LocalDate.parse("2026-08-24"),
                sections = listOf(
                    DormitoryMealSection("조식", "08:00~09:30", listOf("떡국", "쌀밥")),
                    DormitoryMealSection("중식", "12:00~14:00", listOf("미역국", "오징어무침")),
                    DormitoryMealSection("석식", "18:00~19:30", listOf("콩나물불고기")),
                ),
                sourceImageUrl = "https://example.invalid/dorm.jpg",
            ),
        )
        composeRule.setContent {
            MealScreen(
                mealSource = FakeMealSource(dormitoryDays = dormitoryDays),
                today = LocalDate.parse("2026-08-27"),
            )
        }

        composeRule.onNodeWithText("기숙사").performClick()
        composeRule.onNodeWithText("조식 · 08:00~09:30").assertExists()
        composeRule.onNodeWithText("떡국 · 쌀밥").assertExists()
        composeRule.onNodeWithText("중식 · 12:00~14:00").assertExists()
        composeRule.onNodeWithText("석식 · 18:00~19:30").assertExists()
        composeRule.onNodeWithText("사진 올리기").assertDoesNotExist()
    }

    @Test
    fun dormitoryUploadRefreshesFirstAndStopsWhenTheServerAlreadyHasThisWeek() {
        val source = DuplicateDormitoryMealSource()
        composeRule.setContent {
            MealScreen(mealSource = source, today = LocalDate.parse("2026-08-27"))
        }

        composeRule.onNodeWithText("기숙사").performClick()
        composeRule.onNodeWithText("사진 올리기").performClick()

        composeRule.waitUntil(5_000) { source.refreshCount == 1 }
        composeRule.onNodeWithText("사진 선택").assertDoesNotExist()
        composeRule.onNodeWithText("이번 주 기숙사 식단을 불러왔어요").assertExists()
        composeRule.onNodeWithText("사진 올리기").assertDoesNotExist()
    }

    @Test
    fun missingDormitoryWeekOffersOnlyPhotoOrCameraAfterRefresh() {
        val source = FakeMealSource()
        composeRule.setContent {
            MealScreen(mealSource = source, today = LocalDate.parse("2026-08-27"))
        }

        composeRule.onNodeWithText("기숙사").performClick()
        composeRule.onNodeWithText("사진 올리기").performClick()

        composeRule.onNodeWithText("사진 선택").assertExists()
        composeRule.onNodeWithText("카메라 촬영").assertExists()
    }

    @Test
    fun todaysMealExplainsThatServiceHasNotOpenedYet() {
        composeRule.setContent {
            MealScreen(
                mealSource = FakeMealSource(listOf(mealDay("2026-08-27", "제육볶음"))),
                today = LocalDate.parse("2026-08-27"),
                nowTime = LocalTime.of(11, 29),
            )
        }

        composeRule.onNodeWithText("운영 전 · 11:30부터").assertExists()
        composeRule.onNodeWithText("제육볶음").assertExists()
    }

    @Test
    fun mealUsesOnlyATopRefreshActionAndMovesDataDetailsOutOfTheScreen() {
        composeRule.setContent {
            MealScreen(
                mealSource = FakeMealSource(listOf(mealDay("2026-08-24", "제육볶음"))),
                today = LocalDate.parse("2026-08-27"),
            )
        }

        composeRule.onNodeWithContentDescription("식단 새로고침").assertExists()
        composeRule.onNodeWithText("식단 데이터 상태").assertDoesNotExist()
        composeRule.onNodeWithText("식단 데이터 관리").assertDoesNotExist()
    }

    @Test
    fun mealRefreshExplainsThatTheNewWeekHasNotBeenPublishedYet() {
        composeRule.setContent {
            MealScreen(
                mealSource = object : MealSource {
                    override val data = MutableStateFlow(MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null))
                    override suspend fun refresh() = MealRefreshResult.NotPublishedYet
                },
                today = LocalDate.parse("2026-08-31"),
            )
        }

        composeRule.onNodeWithContentDescription("식단 새로고침").performClick()
        composeRule.onNodeWithText("아직 새 식단이 올라오지 않았어요").assertExists()
    }

    private fun departure(
        route: String,
        stop: String,
        time: LocalTime,
        origin: CampusZoneId,
        destination: CampusZoneId,
        serviceDay: DayOfWeek = DayOfWeek.MONDAY,
    ) = ShuttleDeparture(route, stop, "DIRECTION", serviceDay, time, origin, destination)

    private fun mealDay(date: String, vararg lines: String) = MealDay(
        date = LocalDate.parse(date),
        menuLines = lines.toList(),
        hours = "11:30~14:00",
        sourceUrl = "https://www.dima.ac.kr/?p=1",
        sourceImageUrl = "https://example.invalid/menu.jpg",
        validationState = MealValidationState.VALID,
    )

    private class FakeShuttleSource(departures: List<ShuttleDeparture>) : ShuttleSource {
        override val data = MutableStateFlow(
            ShuttleData(
                departures,
                Instant.parse("2026-08-26T12:00:10Z"),
                Instant.parse("2026-08-26T12:00:10Z"),
                null,
                "https://www.dima.ac.kr/?p=97",
                null,
                Instant.parse("2026-08-26T11:30:00Z"),
            ),
        )
        override suspend fun refresh() = ShuttleRefreshResult.Success(data.value.departures.size, Instant.parse("2026-08-26T12:00:10Z"))
    }

    private class FakeMealSource(
        days: List<MealDay> = emptyList(),
        dormitoryDays: List<DormitoryMealDay> = emptyList(),
    ) : MealSource {
        override val data = MutableStateFlow(MealData(days, null, null, null, "https://www.dima.ac.kr/?p=1", null, "11:30~14:00"))
        override val dormitoryData = MutableStateFlow(DormitoryMealData(dormitoryDays, null, null, null))
        override suspend fun refresh() = MealRefreshResult.Failure("unused")
    }

    private class DuplicateDormitoryMealSource : MealSource {
        override val data = MutableStateFlow(MealData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=1", null, null))
        override val dormitoryData = MutableStateFlow(DormitoryMealData(emptyList(), null, null, null))
        var refreshCount = 0
        override suspend fun refresh() = MealRefreshResult.Failure("unused")
        override suspend fun refreshDormitory(): MealRefreshResult {
            refreshCount++
            dormitoryData.value = DormitoryMealData(
                listOf(DormitoryMealDay(LocalDate.parse("2026-08-24"), listOf(DormitoryMealSection("중식", null, listOf("제육볶음"))), "https://example.invalid/dorm.jpg")),
                Instant.parse("2026-08-27T01:00:00Z"),
                Instant.parse("2026-08-27T01:00:00Z"),
                null,
            )
            return MealRefreshResult.Success(LocalDate.parse("2026-08-24"), Instant.parse("2026-08-27T01:00:00Z"))
        }
    }

    private class BlockingShuttleSource : ShuttleSource {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        override val data = MutableStateFlow(
            ShuttleData(emptyList(), null, null, null, "https://www.dima.ac.kr/?p=97", null),
        )
        override suspend fun refresh(): ShuttleRefreshResult {
            started.complete(Unit)
            release.await()
            return ShuttleRefreshResult.Success(2, Instant.parse("2026-08-26T12:00:10Z"))
        }
    }

    private fun androidx.compose.ui.graphics.ImageBitmap.assertMostly(expected: Color) {
        val pixels = toPixelMap()
        var matches = 0
        val expectedArgb = expected.toArgb()
        val histogram = mutableMapOf<Int, Int>()
        fun channel(argb: Int, shift: Int) = (argb ushr shift) and 0xff
        for (x in 0 until width) {
            for (y in 0 until height) {
                val actual = pixels[x, y].toArgb()
                histogram[actual] = (histogram[actual] ?: 0) + 1
                val closeEnough = listOf(16, 8, 0).all { shift ->
                    kotlin.math.abs(channel(actual, shift) - channel(expectedArgb, shift)) <= 3
                }
                if (closeEnough) matches++
            }
        }
        org.junit.Assert.assertTrue(
            "expected solid color ${expected.toArgb().toUInt().toString(16)} to cover the departure capsule, matched $matches/${width * height}; top=${histogram.entries.sortedByDescending { it.value }.take(8).joinToString { it.key.toUInt().toString(16) + ":" + it.value }}",
            matches >= (width * height) / 2,
        )
    }
}
