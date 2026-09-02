package com.example.dimanow.ui

import android.app.UiAutomation
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun mainActivityRetainsPortraitRestrictionOnAndroid16LargeScreens() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val property = context.packageManager.getProperty(
            "android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY",
            ComponentName(context, MainActivity::class.java),
        )

        assertTrue(property.boolean)
    }

    @Test
    fun mainActivityStaysPortraitWhenTheLargeScreenRotates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0)

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                instrumentation.waitForIdleSync()
                automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
                instrumentation.waitForIdleSync()
                Thread.sleep(750)

                scenario.onActivity { activity ->
                    assertEquals(
                        Configuration.ORIENTATION_PORTRAIT,
                        activity.resources.configuration.orientation,
                    )
                }
            }
        } finally {
            automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        }
    }
}
