package com.example.dimanow.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dimanow.live.LiveChipContent
import com.example.dimanow.live.LiveClassOrder
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.location.LocationMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesUpgradeTest {
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
}
