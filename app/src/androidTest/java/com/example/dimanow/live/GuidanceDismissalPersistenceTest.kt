package com.example.dimanow.live

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.guidance.GuidanceEngine
import com.example.dimanow.time.MinuteTicker
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuidanceDismissalPersistenceTest {
    @Test
    fun endingOneClassSurvivesClosingAndReopeningStorageWithoutMutingNextClass() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.preferencesDataStoreFile("dismiss-test-${UUID.randomUUID()}")
        val firstJob = SupervisorJob()
        val secondJob = SupervisorJob()
        val today = LocalDate.now(MinuteTicker.CAMPUS_ZONE)
        val now = today.atTime(9, 30).atZone(MinuteTicker.CAMPUS_ZONE)
        val courses = listOf(
            Course(today.dayOfWeek, LocalTime.of(10, 0), LocalTime.of(11, 0), "첫 수업", "본관", "교수", CampusZoneId.MAIN, id = 901),
            Course(today.dayOfWeek, LocalTime.of(13, 0), LocalTime.of(14, 0), "다음 수업", "본관", "교수", CampusZoneId.MAIN, id = 902),
        )
        fun snapshot(at: java.time.ZonedDateTime) = GuidanceEngine().snapshot(
            at, today, today.plusWeeks(2), courses, emptySet(), CampusZoneId.MAIN, true,
        )
        try {
            val firstStore = GuidanceDismissalStore(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file },
            ))
            assertTrue(firstStore.dismiss(snapshot(now)))
            firstJob.cancelAndJoin()
            // Reopen the file with a fresh DataStore, without the first instance's cached flow.
            val reopenedStore = GuidanceDismissalStore(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file },
            ))
            val remembered = reopenedStore.dismissedKeys.first()
            assertTrue(LiveSurfaceController.isDismissed(snapshot(now.plusMinutes(1)), remembered))
            assertTrue(LiveSurfaceController.isDismissed(snapshot(now.plusMinutes(31)), remembered))
            assertFalse(LiveSurfaceController.isDismissed(snapshot(now.withHour(12)), remembered))
            assertFalse(LiveSurfaceController.isDismissed(snapshot(now.plusWeeks(1)), remembered))
        } finally {
            firstJob.cancelAndJoin()
            secondJob.cancelAndJoin()
            file.delete()
        }
    }
}
