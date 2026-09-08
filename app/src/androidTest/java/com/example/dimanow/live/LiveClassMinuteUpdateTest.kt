package com.example.dimanow.live

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.MainActivity
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.time.MinuteTicker
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveClassMinuteUpdateTest {
    @Test
    fun classTextUpdatesInBackgroundAndReachesInClassWithoutAnotherAlarm() = runBlocking {
        // This end-to-end fixture replaces the emulator timetable. Never run it on a user's phone.
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val app = ApplicationProvider.getApplicationContext<DimaNowApplication>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val repository = app.repository
        repository.ensureSeeded()
        val original = repository.schedule.first()
        val manager = app.getSystemService(NotificationManager::class.java)
        val alarms = app.getSystemService(AlarmManager::class.java)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            original.courses.forEach { repository.deleteCourse(it.id) }
            repository.clearGuidancePause()
            val today = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE).toLocalDate()
            repository.removeNoClassDate(today)
            repository.setTerm(today.minusDays(1), today.plusDays(1))
            app.preferences.setTestLocationMode(true, CampusZoneId.MAIN)
            app.preferences.setNotificationGuidanceMode(GuidanceKind.CLASS, NotificationGuidanceMode.LIVE_UPDATE)
            app.preferences.setNotificationGuidanceMode(GuidanceKind.CAMPUS_SHUTTLE, NotificationGuidanceMode.OFF)
            app.preferences.setNotificationGuidanceMode(GuidanceKind.BUS_4402, NotificationGuidanceMode.OFF)
            app.preferences.setLiveChipContent(LiveChipContent.COUNTDOWN)
            app.preferences.setLiveClassOrder(LiveClassOrder.COURSE_FIRST)
            // Leave enough time to post the first notification before the next minute boundary.
            while (ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE).second > 45) delay(250)
            val startsAt = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
                .withSecond(0).withNano(0).plusMinutes(2)
            repository.saveCourse(Course(
                startsAt.dayOfWeek, startsAt.toLocalTime(), startsAt.plusMinutes(50).toLocalTime(),
                "분 갱신 검증", "덕성관 402", "테스트", CampusZoneId.MAIN,
            ))
            val runtime = withTimeout(10_000) {
                while (true) {
                    val value = app.guidanceRuntimeCoordinator.awaitSnapshot()
                    if (value.schedule.courses.singleOrNull()?.name == "분 갱신 검증") return@withTimeout value
                    delay(100)
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }
            val snapshot = app.guidanceEngine.snapshot(
                ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE), runtime.schedule.termStart,
                runtime.schedule.termEnd, runtime.schedule.courses, emptySet(), CampusZoneId.MAIN, true,
            )
            app.liveSurfaceController.show(snapshot)
            awaitText(manager, "시작까지 2분")
            // No visible activity, screen event, or scheduled boundary alarm should drive these updates.
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            delay(1_000)
            PendingIntent.getBroadcast(app, 6301, Intent(app, GuidanceAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let(alarms::cancel)

            val minute = awaitText(manager, "시작까지 1분", timeoutMillis = 70_000)
            assertEquals("시작까지 1분 · 덕성관 402", minute.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals("시작까지 1분 · 덕성관 402", minute.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
            assertEquals(startsAt.toInstant().toEpochMilli(), minute.`when`)
            assertTrue(minute.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
            assertNull(minute.shortCriticalText)

            val inClass = awaitText(manager, "수업 중", timeoutMillis = 70_000)
            assertEquals("수업 중 · 덕성관 402", inClass.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertFalse(inClass.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        } finally {
            app.liveSurfaceController.cancel()
            repository.schedule.first().courses.forEach { repository.deleteCourse(it.id) }
            original.courses.forEach { repository.saveCourse(it) }
            repository.setTerm(original.termStart, original.termEnd)
            original.noClassDates.forEach { repository.addNoClassDate(it) }
            original.guidancePause?.let { repository.setGuidancePause(it) }
            scenario.close()
        }
    }

    private suspend fun awaitText(
        manager: NotificationManager,
        expected: String,
        timeoutMillis: Long = 10_000,
    ): Notification {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var last: Notification? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            last = manager.activeNotifications.firstOrNull { it.id == AndroidLiveSurfaceController.NOTIFICATION_ID }?.notification
            if (last?.extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() == expected) return last
            delay(100)
        }
        error("Expected notification '$expected', last subtext=${last?.extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)}")
    }
}
