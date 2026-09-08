package com.example.dimanow.live

import com.example.dimanow.R
import com.example.dimanow.domain.ClassContent
import com.example.dimanow.domain.CountdownMeaning
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.GuidanceSnapshot
import com.example.dimanow.domain.ShuttleLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSurfacePlannerTest {
    @Test
    fun `background foreground-service denial degrades without crashing live guidance`() {
        val started = LiveSurfaceController.startUpdaterSafely {
            throw IllegalStateException("foreground start not allowed")
        }

        assertEquals(false, started)
    }

    @Test
    fun `screen on over the keyguard keeps the configured classroom in the status pill`() {
        val snapshot = GuidanceSnapshot(
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
        )

        assertEquals(
            "덕성관 402",
            LiveSurfaceController.statusChipText(
                snapshot = snapshot,
                presentation = LiveDisplayOptions(chipContent = LiveChipContent.CLASSROOM),
                deviceLocked = true,
            ),
        )
    }

    @Test
    fun `classroom first lock screen does not replace the configured classroom status pill`() {
        val snapshot = GuidanceSnapshot(
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
        )

        assertEquals(
            "덕성관 402",
            LiveSurfaceController.statusChipText(
                snapshot = snapshot,
                presentation = LiveDisplayOptions(
                    chipContent = LiveChipContent.CLASSROOM,
                    classOrder = LiveClassOrder.CLASSROOM_FIRST,
                ),
                deviceLocked = true,
            ),
        )
    }

    @Test
    fun `a shuttle countdown chip shows its destination and whole remaining minutes`() {
        val snapshot = GuidanceSnapshot(
            classContent = null,
            shuttleLines = listOf(
                ShuttleLine(text = "원룸촌  12분, 27분", destination = "본관행", minutes = 12),
            ),
            phase = GuidancePhase.RETURN,
            countdownMeaning = CountdownMeaning.SHUTTLE_DEPARTURE,
        )

        assertEquals(
            "본관행 12분",
            LiveSurfaceController.statusChipText(
                snapshot = snapshot,
                presentation = LiveDisplayOptions(chipContent = LiveChipContent.COUNTDOWN),
                deviceLocked = false,
            ),
        )
        assertEquals(R.drawable.ic_stat_shuttle, LiveSurfaceController.smallIcon(snapshot))
        assertEquals(false, LiveSurfaceController.usesSystemChronometer(snapshot))
    }

    @Test
    fun `a class countdown also leaves text empty for the system chronometer`() {
        val snapshot = GuidanceSnapshot(
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
            countdownMeaning = CountdownMeaning.CLASS_START,
            countdownTarget = java.time.Instant.parse("2026-09-04T01:00:00Z"),
        )

        assertEquals(
            null,
            LiveSurfaceController.statusChipText(
                snapshot = snapshot,
                presentation = LiveDisplayOptions(chipContent = LiveChipContent.COUNTDOWN),
                deviceLocked = false,
            ),
        )
        assertEquals(R.drawable.ic_stat_class, LiveSurfaceController.smallIcon(snapshot))
        assertEquals(true, LiveSurfaceController.usesSystemChronometer(snapshot))
    }

    @Test
    fun `standard and off modes never request promotion or a minute foreground updater`() {
        assertEquals(
            LiveDeliveryPlan(shouldPost = true, requestPromotion = false, startMinuteUpdater = false),
            LiveDeliveryPlanner.plan(NotificationGuidanceMode.STANDARD, requiresMinuteUpdates = true),
        )
        assertEquals(
            LiveDeliveryPlan(shouldPost = false, requestPromotion = false, startMinuteUpdater = false),
            LiveDeliveryPlanner.plan(NotificationGuidanceMode.OFF, requiresMinuteUpdates = true),
        )
    }

    @Test
    fun `an empty snapshot falls back to the app icon instead of guessing a mode`() {
        val snapshot = GuidanceSnapshot(classContent = null, shuttleLines = emptyList(), phase = GuidancePhase.NONE)

        assertEquals(R.drawable.ic_stat_dima, LiveSurfaceController.smallIcon(snapshot))
        assertEquals(
            null,
            LiveSurfaceController.statusChipText(
                snapshot = snapshot,
                presentation = LiveDisplayOptions(chipContent = LiveChipContent.COUNTDOWN),
                deviceLocked = false,
            ),
        )
    }

    @Test
    fun `live settings falls back to app notification settings when the promoted screen is unavailable`() {
        assertEquals(
            LiveSettingsDestination.APP_NOTIFICATION_SETTINGS,
            LiveSettingsPlanner.plan(
                promotedSettingsAvailable = false,
                appNotificationSettingsAvailable = true,
                appDetailsAvailable = true,
            ),
        )
    }

    @Test
    fun `eligible app configuration is distinguished from system promotion permission`() {
        assertEquals(
            LivePromotionReadiness.SYSTEM_NOT_ALLOWED,
            LivePromotionReadinessEvaluator.evaluate(
                apiLevel = 36,
                notificationsAllowed = true,
                manifestPermissionDeclared = true,
                promotableCharacteristics = true,
                channelImportance = 2,
                canPostPromotedNotifications = false,
            ),
        )
    }

    @Test
    fun `api below 36 uses normal notification fallback`() {
        assertEquals(
            LiveSurfaceResult.FALLBACK_NOTIFICATION,
            LiveSurfacePlanner.plan(apiLevel = 35, notificationsAllowed = true, promotedEligible = false),
        )
    }

    @Test
    fun `denied or revoked notification permission suppresses the live surface`() {
        assertEquals(
            LiveSurfaceResult.NOTIFICATION_PERMISSION_DENIED,
            LiveSurfacePlanner.plan(apiLevel = 36, notificationsAllowed = false, promotedEligible = true),
        )
    }

    @Test
    fun `api 36 without promoted eligibility still uses the fallback`() {
        assertEquals(
            LiveSurfaceResult.FALLBACK_NOTIFICATION,
            LiveSurfacePlanner.plan(apiLevel = 36, notificationsAllowed = true, promotedEligible = false),
        )
    }
}
