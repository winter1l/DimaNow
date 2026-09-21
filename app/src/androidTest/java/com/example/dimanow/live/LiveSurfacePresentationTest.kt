package com.example.dimanow.live

import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import com.example.dimanow.R
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dimanow.domain.ClassContent
import com.example.dimanow.domain.CountdownMeaning
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.GuidanceSnapshot
import com.example.dimanow.domain.ShuttleLine
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first

@RunWith(AndroidJUnit4::class)
class LiveSurfacePresentationTest {
    @Test
    fun guidanceNotificationOffersAnImmutableOccurrenceDismissAction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val snapshot = GuidanceSnapshot(ClassContent("실습", "시작까지 10분"), emptyList(), GuidancePhase.BEFORE_CLASS,
            occurrenceKey = "2026-09-14|class|42|10:00|실습")
        val notification = AndroidLiveSurfaceController(context).buildNotification(snapshot, requestPromotion = false)
        val action = notification.actions.single { it.title.toString() == "이번 안내만 종료" }
        assertEquals(true, action.actionIntent.isImmutable)
        val plain = AndroidLiveSurfaceController(context).buildNotification(snapshot.copy(occurrenceKey = null), requestPromotion = false)
        assertEquals(true, plain.actions.isNullOrEmpty())
    }

    @Test
    fun distantBusRemovesExistingCardAndSixtyMinuteBusReturns() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val controller = AndroidLiveSurfaceController(context)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        fun bus(minutes: Long) = GuidanceSnapshot(null,
            listOf(ShuttleLine("4402 · 20:00 출발", "강남행", minutes, "대학 셔틀 정류장")),
            GuidancePhase.TRANSIT, countdownTarget = Instant.now().plusSeconds(minutes * 60),
            countdownMeaning = CountdownMeaning.SHUTTLE_DEPARTURE, requiresMinuteUpdates = true,
            kind = GuidanceKind.BUS_4402)
        try {
            controller.show(bus(60))
            assertEquals(true, manager.activeNotifications.any { it.id == 6201 })
            assertEquals(LiveSurfaceResult.CANCELLED, controller.show(bus(61)))
            assertEquals(false, manager.activeNotifications.any { it.id == 6201 })
            controller.show(bus(60))
            assertEquals(true, manager.activeNotifications.any { it.id == 6201 })
        } finally {
            controller.cancel()
        }
    }

    @Test
    fun activeBusGuidancePostsOnlyTheUsefulCardWithoutAServiceKeeper() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<com.example.dimanow.DimaNowApplication>()
        val preferences = app.preferences
        val previousMode = preferences.locationMode.first()
        val previousZone = preferences.testZone.first()
        val previousStop = preferences.testTransitStopNumber.first()
        val previousPolicy = preferences.notificationGuidancePolicy.first()
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        try {
            preferences.setTestLocationMode(true, com.example.dimanow.domain.CampusZoneId.MAIN)
            preferences.setTestTransitStop("34710")
            preferences.setNotificationGuidanceMode(GuidanceKind.BUS_4402, NotificationGuidanceMode.LIVE_UPDATE)
            androidx.test.core.app.ActivityScenario.launch(com.example.dimanow.MainActivity::class.java).use {
                kotlinx.coroutines.withTimeout(5_000) {
                    while (app.guidanceRuntimeCoordinator.awaitSnapshot().nearbyTransitStop?.stopNumber != "34710") {
                        kotlinx.coroutines.delay(50)
                    }
                }
                val runtime = app.guidanceRuntimeCoordinator.awaitSnapshot().copy(
                    bus4402Schedule = com.example.dimanow.transit.Bus4402Schedule.official.copy(
                        timesByServiceType = com.example.dimanow.transit.Bus4402ServiceType.entries.associateWith {
                            (0 until 1440).map { java.time.LocalTime.ofSecondOfDay(it * 60L) }
                        },
                    ),
                )
                app.guidanceRuntimeCoordinator.update(runtime)
                app.guidanceOrchestrator.refresh(runtime)
                // Android can defer a new FGS notification for ten seconds.
                kotlinx.coroutines.delay(12_000)
                assertEquals(listOf(AndroidLiveSurfaceController.NOTIFICATION_ID), manager.activeNotifications.map { it.id }.sorted())
                assertEquals(0, manager.activeNotifications.single().notification.flags and Notification.FLAG_FOREGROUND_SERVICE)
            }
        } finally {
            preferences.setTestTransitStop(previousStop)
            preferences.setTestLocationMode(previousMode == com.example.dimanow.location.LocationMode.TEST, previousZone)
            preferences.setNotificationGuidanceMode(GuidanceKind.BUS_4402, previousPolicy.bus4402)
            app.liveSurfaceController.cancel()
        }
    }

    @Test
    fun noGuidanceCancelsInsteadOfPostingAnInformationFreeOngoingCard() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val controller = AndroidLiveSurfaceController(context)
        assertEquals(
            LiveSurfaceResult.CANCELLED,
            controller.show(GuidanceSnapshot(null, emptyList(), GuidancePhase.NONE)),
        )
    }

    @Test
    fun bus4402NotificationNamesTheRouteAndActualDepartureInsteadOfCampusReturnGuidance() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val snapshot = com.example.dimanow.guidance.GuidanceEngine().snapshot(
            now = java.time.ZonedDateTime.parse("2026-09-04T18:00:00+09:00[Asia/Seoul]"),
            termStart = java.time.LocalDate.parse("2026-08-24"),
            termEnd = java.time.LocalDate.parse("2026-12-18"),
            courses = emptyList(),
            noClassDates = emptySet(),
            resolvedZone = com.example.dimanow.domain.CampusZoneId.MAIN,
            automaticClassGuidance = true,
            nearbyTransitStop = com.example.dimanow.location.NearbyTransitStop("34710", "대학 셔틀 정류장"),
        )
        val notification = AndroidLiveSurfaceController(context).buildNotification(snapshot, requestPromotion = true)
        assertEquals("4402 · 18:10 출발", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("강남행 10분", notification.shortCriticalText)
        assertNull(snapshot.classContent)
    }

    @Test
    fun legacyQuietChannelMigratesToSilentDefaultImportanceForLiveRefresh() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(
            android.app.NotificationChannel("dima_live_guidance", "Previous guidance", android.app.NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        val controller = AndroidLiveSurfaceController(context)
        val diagnostics = controller.diagnostics()
        val notification = controller.buildNotification(
            GuidanceSnapshot(null, emptyList(), GuidancePhase.NONE),
            requestPromotion = true,
        )
        val channel = manager.getNotificationChannel(notification.channelId)
        assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, diagnostics.channelImportance)
        assertNull(channel.sound)
        assertEquals(false, channel.shouldVibrate())
    }

    @Test
    fun shuttleTitleAndCriticalTextDescribeTheSameVehicleInBothLockStates() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val now = java.time.ZonedDateTime.parse("2026-09-08T18:50:00+09:00[Asia/Seoul]")
        val rows = listOf(18 to 55, 19 to 25).map { (hour, minute) ->
            com.example.dimanow.domain.ShuttleDeparture(
                "A-evening", "stadium-stop", "TO_YEIN", java.time.DayOfWeek.TUESDAY,
                java.time.LocalTime.of(hour, minute), com.example.dimanow.domain.CampusZoneId.MAIN,
                com.example.dimanow.domain.CampusZoneId.YEIN,
            )
        }
        val snapshot = com.example.dimanow.guidance.GuidanceEngine().snapshot(
            now, now.toLocalDate().minusDays(1), now.toLocalDate().plusDays(1),
            listOf(com.example.dimanow.domain.Course(
                java.time.DayOfWeek.TUESDAY, java.time.LocalTime.of(16, 0), java.time.LocalTime.of(18, 0),
                "실습", "본관", "교수", com.example.dimanow.domain.CampusZoneId.MAIN,
            )), emptySet(), com.example.dimanow.domain.CampusZoneId.MAIN, true, rows,
        )
        for (locked in listOf(false, true)) {
            val notification = AndroidLiveSurfaceController(context).buildNotification(
                snapshot, requestPromotion = true, deviceLocked = locked,
            )
            assertEquals("본관 → 엔터관", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
            assertEquals("엔터관행 5분", notification.shortCriticalText)
            assertEquals("5분 후 출발 · 18:55", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        }
    }

    @Test
    fun shuttleOnlyGuidanceDoesNotRepeatTheSameLineAsTitleAndBody() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notification = AndroidLiveSurfaceController(context).buildNotification(
            snapshot = GuidanceSnapshot(
                classContent = null,
                shuttleLines = listOf(com.example.dimanow.domain.ShuttleLine("본관  5분, 30분")),
                phase = GuidancePhase.RETURN,
                countdownTarget = Instant.parse("2026-08-27T10:00:00Z"),
            ),
            requestPromotion = true,
            presentation = LiveDisplayOptions(chipContent = LiveChipContent.CLASSROOM),
        )

        assertEquals("본관  5분, 30분", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertNull(notification.shortCriticalText)
    }

    @Test
    fun shuttleOnlyNowBarKeepsTheOfficialStadiumBoardingLabel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notification = AndroidLiveSurfaceController(context).buildNotification(
            snapshot = GuidanceSnapshot(
                classContent = null,
                shuttleLines = listOf(com.example.dimanow.domain.ShuttleLine("본관  5분, 30분")),
                phase = GuidancePhase.RETURN,
                countdownTarget = Instant.parse("2026-08-27T10:00:00Z"),
            ),
            requestPromotion = true,
            presentation = LiveDisplayOptions(chipContent = LiveChipContent.COUNTDOWN),
        )

        assertEquals("본관  5분, 30분", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
    }

    @Test
    fun classroomChipOptionUsesTheRoomInsteadOfTheCountdown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notification = AndroidLiveSurfaceController(context).buildNotification(
            snapshot = GuidanceSnapshot(
                classContent = ClassContent(
                    title = "10:00 · 조명기초및실습",
                    detail = "시작까지 42분 · 덕성관 402",
                    startTime = "10:00",
                    courseName = "조명기초및실습",
                    room = "덕성관 402",
                    remainingText = "시작까지 42분",
                ),
                shuttleLines = emptyList(),
                phase = GuidancePhase.BEFORE_CLASS,
                countdownTarget = Instant.parse("2026-08-27T01:00:00Z"),
            ),
            requestPromotion = true,
            presentation = LiveDisplayOptions(chipContent = LiveChipContent.CLASSROOM),
        )

        assertEquals("덕성관 402", notification.shortCriticalText)
    }

    @Test
    fun classroomChipKeepsRemainingTimeInTheLockScreenHeader() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notification = AndroidLiveSurfaceController(context).buildNotification(
            snapshot = GuidanceSnapshot(
                classContent = ClassContent(
                    title = "10:00 · 조명기초및실습",
                    detail = "시작까지 42분 · 덕성관 402",
                    startTime = "10:00",
                    courseName = "조명기초및실습",
                    room = "덕성관 402",
                    remainingText = "시작까지 42분",
                ),
                shuttleLines = emptyList(),
                phase = GuidancePhase.BEFORE_CLASS,
                countdownTarget = Instant.parse("2026-08-27T01:00:00Z"),
            ),
            requestPromotion = true,
            presentation = LiveDisplayOptions(chipContent = LiveChipContent.CLASSROOM),
        )

        assertEquals("시작까지 42분", notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        assertEquals("시작까지 42분 · 덕성관 402", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    @Test
    fun screenOnWhileLockedKeepsClassroomInConfiguredStatusPill() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notification = AndroidLiveSurfaceController(context).buildNotification(
            snapshot = GuidanceSnapshot(
                classContent = ClassContent(
                    title = "10:00 · 조명기초및실습",
                    detail = "시작까지 42분 · 덕성관 402",
                    startTime = "10:00",
                    courseName = "조명기초및실습",
                    room = "덕성관 402",
                    remainingText = "시작까지 42분",
                ),
                shuttleLines = emptyList(),
                phase = GuidancePhase.BEFORE_CLASS,
                countdownTarget = Instant.parse("2026-08-27T01:00:00Z"),
            ),
            requestPromotion = true,
            presentation = LiveDisplayOptions(chipContent = LiveChipContent.CLASSROOM),
            deviceLocked = true,
        )

        assertEquals("덕성관 402", notification.shortCriticalText)
        assertEquals("시작까지 42분 · 덕성관 402", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals("시작까지 42분", notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun classroomFirstOptionSwapsCourseAndRoomOnTheLockScreenCard() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notification = AndroidLiveSurfaceController(context).buildNotification(
            snapshot = GuidanceSnapshot(
                classContent = ClassContent(
                    title = "10:00 · 조명기초및실습",
                    detail = "시작까지 42분 · 덕성관 402",
                    startTime = "10:00",
                    courseName = "조명기초및실습",
                    room = "덕성관 402",
                    remainingText = "시작까지 42분",
                ),
                shuttleLines = emptyList(),
                phase = GuidancePhase.BEFORE_CLASS,
                countdownTarget = Instant.parse("2026-08-27T01:00:00Z"),
            ),
            requestPromotion = true,
            presentation = LiveDisplayOptions(classOrder = LiveClassOrder.CLASSROOM_FIRST),
        )

        assertEquals("10:00 · 덕성관 402", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("시작까지 42분 · 조명기초및실습", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    @Test
    fun classroomFirstLockScreenKeepsRoomInStatusPillAndCourseInCard() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notification = AndroidLiveSurfaceController(context).buildNotification(
            snapshot = GuidanceSnapshot(
                classContent = ClassContent(
                    title = "10:00 · 조명기초및실습",
                    detail = "시작까지 42분 · 덕성관 402",
                    startTime = "10:00",
                    courseName = "조명기초및실습",
                    room = "덕성관 402",
                    remainingText = "시작까지 42분",
                ),
                shuttleLines = emptyList(),
                phase = GuidancePhase.BEFORE_CLASS,
                countdownTarget = Instant.parse("2026-08-27T01:00:00Z"),
            ),
            requestPromotion = true,
            presentation = LiveDisplayOptions(
                chipContent = LiveChipContent.CLASSROOM,
                classOrder = LiveClassOrder.CLASSROOM_FIRST,
            ),
            deviceLocked = true,
        )

        assertEquals("10:00 · 덕성관 402", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("시작까지 42분 · 조명기초및실습", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals("시작까지 42분 · 조명기초및실습", notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        assertEquals("덕성관 402", notification.shortCriticalText)
        assertEquals("시작까지 42분", notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun theBuiltNotificationCarriesTheShuttleIconForShuttleGuidanceAndTheClassIconForAClass() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val controller = AndroidLiveSurfaceController(context)

        val shuttle = controller.buildNotification(
            snapshot = GuidanceSnapshot(
                classContent = null,
                shuttleLines = listOf(ShuttleLine(text = "원룸촌  12분", destination = "본관행", minutes = 12)),
                phase = GuidancePhase.RETURN,
                countdownTarget = Instant.parse("2026-08-27T01:00:00Z"),
                countdownMeaning = CountdownMeaning.SHUTTLE_DEPARTURE,
            ),
            requestPromotion = true,
        )
        val inClass = controller.buildNotification(
            snapshot = GuidanceSnapshot(
                classContent = ClassContent(
                    title = "10:00 · 조명기초및실습",
                    detail = "수업 중 · 덕성관 402",
                    startTime = "10:00",
                    courseName = "조명기초및실습",
                    room = "덕성관 402",
                    remainingText = "수업 중",
                ),
                shuttleLines = emptyList(),
                phase = GuidancePhase.IN_CLASS,
                countdownMeaning = CountdownMeaning.CLASS_START,
            ),
            requestPromotion = true,
        )

        // D-058: 상태바/나우바 아이콘은 안내 성격에 따라 실제로 갈린다
        assertEquals(R.drawable.ic_stat_shuttle, shuttle.smallIcon.resId)
        assertEquals(R.drawable.ic_stat_class, inClass.smallIcon.resId)
        assertEquals("본관행 12분", shuttle.shortCriticalText)
        assertEquals(false, shuttle.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
    }

    @Test
    fun shuttleCountdownChipUsesStaticDestinationMinutesInsteadOfTheSystemChronometer() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notification = AndroidLiveSurfaceController(context).buildNotification(
            snapshot = GuidanceSnapshot(
                classContent = ClassContent(
                    title = "10:00 · 조명기초및실습",
                    detail = "시작까지 42분 · 덕성관 402",
                    startTime = "10:00",
                    courseName = "조명기초및실습",
                    room = "덕성관 402",
                    remainingText = "시작까지 42분",
                ),
                shuttleLines = listOf(
                    ShuttleLine(text = "원룸촌  12분, 27분", destination = "본관행", minutes = 12),
                ),
                phase = GuidancePhase.BEFORE_CLASS,
                countdownTarget = Instant.parse("2026-08-27T01:00:00Z"),
                countdownMeaning = CountdownMeaning.SHUTTLE_DEPARTURE,
            ),
            requestPromotion = true,
            presentation = LiveDisplayOptions(chipContent = LiveChipContent.COUNTDOWN),
        )

        assertEquals("본관행 12분", notification.shortCriticalText)
        assertEquals(false, notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(false, notification.extras.getBoolean("android.chronometerCountDown"))
    }
}
