package com.example.dimanow.live

import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dimanow.domain.ClassContent
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.GuidanceSnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveSurfacePresentationTest {
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
                shuttleLines = listOf(com.example.dimanow.domain.ShuttleLine("운동장  5분, 30분")),
                phase = GuidancePhase.RETURN,
                countdownTarget = Instant.parse("2026-08-27T10:00:00Z"),
            ),
            requestPromotion = true,
            presentation = LiveDisplayOptions(chipContent = LiveChipContent.COUNTDOWN),
        )

        assertEquals("운동장  5분, 30분", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
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
    fun countdownChipOptionLeavesOnlyTheSystemChronometerInThePill() {
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
            presentation = LiveDisplayOptions(chipContent = LiveChipContent.COUNTDOWN),
        )

        assertNull(notification.shortCriticalText)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.extras.getBoolean("android.chronometerCountDown"))
    }
}
