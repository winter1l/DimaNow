package com.example.dimanow.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dimanow.live.LiveChipContent
import com.example.dimanow.live.LiveClassOrder
import com.example.dimanow.live.GuidanceKind
import com.example.dimanow.live.NotificationGuidanceMode
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.location.LocationMode
import com.example.dimanow.location.TransitStopProximityState
import java.time.Instant
import com.example.dimanow.update.AppUpdateRelease
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesUpgradeTest {
    @Test
    fun notificationGuidanceModesPersistIndependently() = runTest {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext())

        preferences.setNotificationGuidanceMode(GuidanceKind.CLASS, NotificationGuidanceMode.OFF)
        preferences.setNotificationGuidanceMode(GuidanceKind.CAMPUS_SHUTTLE, NotificationGuidanceMode.STANDARD)
        preferences.setNotificationGuidanceMode(GuidanceKind.BUS_4402, NotificationGuidanceMode.LIVE_UPDATE)

        val saved = preferences.notificationGuidancePolicy.first()
        assertEquals(NotificationGuidanceMode.OFF, saved.classGuidance)
        assertEquals(NotificationGuidanceMode.STANDARD, saved.campusShuttle)
        assertEquals(NotificationGuidanceMode.LIVE_UPDATE, saved.bus4402)
    }

    @Test
    fun testModeCanSelectA4402StopWithoutChangingTheCampusZone() = runTest {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext())

        preferences.setTestLocationMode(true, CampusZoneId.ONE_ROOM)
        preferences.setTestTransitStop("33243")

        assertEquals(CampusZoneId.ONE_ROOM, preferences.effectiveZone.first())
        assertEquals("33243", preferences.effectiveTransitStopNumber.first())
    }

    @Test
    fun transitStopDwellStateSurvivesAReceiverProcessBoundary() = runTest {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext())
        val state = TransitStopProximityState(
            candidateStopNumber = "34710",
            candidateSince = Instant.parse("2026-09-04T01:00:00Z"),
            activeStopNumber = "34710",
            lastValidAt = Instant.parse("2026-09-04T01:00:31Z"),
        )

        preferences.setTransitStopProximityState(state)

        assertEquals(state, preferences.transitStopProximityState.first())
        assertEquals("34710", preferences.activeTransitStopNumber.first())
    }

    @Test
    fun nowBarSetupGuideRemainsCompletedAfterTheUserFinishesIt() = runTest {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext())

        preferences.setNowBarSetupCompleted(true)

        assertTrue(preferences.nowBarSetupCompleted.first())
    }

    @Test
    fun testLocationModeAndSelectedZonePersistUntilTheUserTurnsItOff() = runTest {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext())

        preferences.setTestLocationMode(true, CampusZoneId.ONE_ROOM)

        assertEquals(LocationMode.TEST, preferences.locationMode.first())
        assertEquals(CampusZoneId.ONE_ROOM, preferences.testZone.first())
        assertEquals(CampusZoneId.ONE_ROOM, preferences.effectiveZone.first())
    }

    @Test
    fun homeBaseChoicePersistsAndMarksTheUpgradeChoiceConfirmed() = runTest {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext())

        preferences.setHomeBase(HomeBase.ONE_ROOM)

        assertEquals(HomeBase.ONE_ROOM, preferences.homeBase.first())
        assertTrue(preferences.homeBaseSelectionConfirmed.first())
    }

    @Test
    fun previouslyStoredFalseCannotDisableAutomaticClassGuidanceAfterUpgrade() = runTest {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext())

        preferences.setAutomaticClassGuidance(false)

        assertTrue(preferences.automaticClassGuidance.first())
    }

    @Test
    fun livePresentationChoicesPersistTogether() = runTest {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext())

        preferences.setLiveChipContent(LiveChipContent.CLASSROOM)
        preferences.setLiveClassOrder(LiveClassOrder.CLASSROOM_FIRST)

        assertEquals(LiveChipContent.CLASSROOM, preferences.liveDisplayOptions.first().chipContent)
        assertEquals(LiveClassOrder.CLASSROOM_FIRST, preferences.liveDisplayOptions.first().classOrder)
    }

    @Test
    fun updateCheckDismissalAndPreparedApkMetadataPersistTogether() = runTest {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext())
        val release = AppUpdateRelease(
            "1.2",
            "https://github.com/winter1l/DimaNow/releases/tag/v1.2",
            "https://github.com/winter1l/DimaNow/releases/download/v1.2/DIMA-Now-v1.2-optimized.apk",
            1234,
            "a".repeat(64),
        )

        preferences.recordAppUpdateCheck(1_788_134_400_000, release)
        preferences.dismissAppUpdateVersion("1.2")
        preferences.recordPreparedAppUpdate("/cache/updates/v1.2.apk", "1.2")

        val saved = preferences.appUpdatePreferences.first()
        assertEquals(1_788_134_400_000, saved.lastCheckedEpochMillis)
        assertEquals(release, saved.cachedRelease)
        assertEquals("1.2", saved.dismissedVersion)
        assertEquals("/cache/updates/v1.2.apk", saved.preparedPath)
    }
}
