package com.example.dimanow.live

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.domain.ClassContent
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.GuidanceSnapshot
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SamsungNowBarPhysicalTest {
    @Test
    fun eligibleLiveUpdateIsActuallyPromotedBySamsung() {
        assumePhysicalLiveTestWasExplicitlyRequested()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val controller = AndroidLiveSurfaceController(context)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val now = Instant.now()

        controller.ensureChannel()
        val notification = controller.buildNotification(
            GuidanceSnapshot(
                classContent = ClassContent(
                    title = "DIMA Now 실시간 테스트",
                    detail = "시작까지 10분 · 본관",
                ),
                shuttleLines = emptyList(),
                phase = GuidancePhase.BEFORE_CLASS,
                countdownTarget = now.plusSeconds(600),
                expiresAt = now.plusSeconds(1_200),
            ),
            requestPromotion = true,
        )
        assertTrue(notification.hasPromotableCharacteristics())
        notificationManager.notify(TEST_NOTIFICATION_ID, notification)

        SystemClock.sleep(10_000)
        val active = notificationManager.activeNotifications.firstOrNull { it.id == TEST_NOTIFICATION_ID }
        assertTrue("active test notification missing", active != null)
        assertTrue(
            "Samsung did not set FLAG_PROMOTED_ONGOING: flags=${active?.notification?.flags}",
            active?.notification?.flags?.and(Notification.FLAG_PROMOTED_ONGOING) != 0,
        )
    }

    @Test
    fun cancelPhysicalLiveUpdateTestNotification() {
        assumePhysicalLiveTestWasExplicitlyRequested()
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(NotificationManager::class.java)
            .cancel(TEST_NOTIFICATION_ID)
    }

    private fun assumePhysicalLiveTestWasExplicitlyRequested() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("physicalLivePromotion") == "true")
        assumeTrue(Build.VERSION.SDK_INT >= 36)
        assumeTrue(Build.MANUFACTURER.equals("samsung", ignoreCase = true))
    }

    private companion object {
        const val TEST_NOTIFICATION_ID = 6299
    }
}
