package com.example.dimanow.ui

import android.app.UiAutomation
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortraitOrientationTest {
    @Test
    fun mainActivityDeclaresPortraitOrientation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )

        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            activityInfo.screenOrientation,
        )
    }

    /** D-104 supersedes D-054 on large screens: the Android 16 opt-out from resizability is gone. */
    @Test
    fun mainActivityNoLongerOptsOutOfLargeScreenResizability() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            context.packageManager.getProperty(
                "android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY",
                ComponentName(context, MainActivity::class.java),
            )
        }
    }

    /** Phones stay portrait (D-054); a large screen follows the device's rotation (D-104). */
    @Test
    fun phonesStayPortraitAndLargeScreensFollowTheRotation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0)

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun orientationAfter(rotation: Int): Pair<Int, Boolean> {
                    automation.setRotation(rotation)
                    instrumentation.waitForIdleSync()
                    Thread.sleep(750)
                    var result = 0 to false
                    scenario.onActivity { activity ->
                        val configuration = activity.resources.configuration
                        result = configuration.orientation to
                            (configuration.smallestScreenWidthDp >= MainActivity.LARGE_SCREEN_SMALLEST_WIDTH_DP)
                    }
                    return result
                }
                instrumentation.waitForIdleSync()
                val (natural, largeScreen) = orientationAfter(UiAutomation.ROTATION_FREEZE_0)
                val (turned, _) = orientationAfter(UiAutomation.ROTATION_FREEZE_90)
                if (largeScreen) {
                    assertNotEquals("a large screen must follow the rotation", natural, turned)
                } else {
                    assertEquals(Configuration.ORIENTATION_PORTRAIT, natural)
                    assertEquals(Configuration.ORIENTATION_PORTRAIT, turned)
                }
            }
        } finally {
            automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        }
    }
}
