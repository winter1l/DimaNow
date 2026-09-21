package com.example.dimanow.live

import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.guidance.GuidanceEngine
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShuttleNotificationSummaryTest {
    @Test
    fun oneRoomReturnShowsOnlyTheImmediateVehicleInBothLockStates() {
        val snapshot = snapshotAt("2026-09-21T15:44:14+09:00[Asia/Seoul]")
        // Preserve the engine's transfer plan; the notification only summarizes boarding now.
        assertEquals(listOf(6L, 11L), snapshot.shuttleLines.map { it.minutes })
        assertEquals(46L, snapshot.shuttleLines.first().followingMinutes)
        for (locked in listOf(false, true)) {
            val notification = controller().buildNotification(snapshot, true, deviceLocked = locked)
            assertEquals("원룸촌 → 본관", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
            assertEquals("6분 후 출발 · 15:50", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
            assertEquals("6분 후 출발 · 15:50", notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            assertEquals("본관행 6분", notification.shortCriticalText)
        }
    }

    @Test
    fun departureAndFollowingVehicleStayConsistentAcrossAllSurfaces() {
        for ((time, detail, chip) in listOf(
            Triple("15:50:00", "곧 출발 · 15:50", "본관행 곧"),
            Triple("15:50:01", "40분 후 출발 · 16:30", "본관행 40분"),
        )) {
            val notification = controller().buildNotification(snapshotAt("2026-09-21T${time}+09:00[Asia/Seoul]"), true)
            assertEquals("원룸촌 → 본관", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
            assertEquals(detail, notification.extras.getCharSequence(Notification.EXTRA_TEXT))
            assertEquals(chip, notification.shortCriticalText)
        }
    }

    private fun controller() = AndroidLiveSurfaceController(ApplicationProvider.getApplicationContext())

    private fun snapshotAt(time: String): com.example.dimanow.domain.GuidanceSnapshot {
        val now = ZonedDateTime.parse(time)
        fun row(origin: CampusZoneId, destination: CampusZoneId, departure: String, arrival: String) =
            ShuttleDeparture("A", "test-stop", "test-direction", DayOfWeek.MONDAY,
                LocalTime.parse(departure), origin, destination, LocalTime.parse(arrival))
        return GuidanceEngine().snapshot(
            now, now.toLocalDate(), now.toLocalDate(),
            listOf(Course(DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(15, 0),
                "테스트 수업", "본관", "교수", CampusZoneId.MAIN)),
            emptySet(), CampusZoneId.ONE_ROOM, true,
            listOf(
                row(CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, "15:50", "15:53"),
                row(CampusZoneId.ONE_ROOM, CampusZoneId.MAIN, "16:30", "16:33"),
                row(CampusZoneId.MAIN, CampusZoneId.YEIN, "15:55", "16:00"),
                row(CampusZoneId.MAIN, CampusZoneId.YEIN, "16:45", "16:50"),
            ),
        )
    }
}
