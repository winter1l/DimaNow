package com.example.dimanow.live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Grants POST_NOTIFICATIONS before each test that posts or reads the guidance notification.
 *
 * A fresh install (CI) starts without the runtime permission, and the controller then reports
 * NOTIFICATION_PERMISSION_DENIED instead of posting. Granting from adb before the run is not
 * enough because Gradle reinstalls the app. This does what androidx.test's GrantPermissionRule
 * does on API 28+, without adding a dependency to the verified dependency set.
 */
class NotificationPermissionRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val context = instrumentation.targetContext
                instrumentation.uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
                assertEquals(
                    "POST_NOTIFICATIONS must be granted before the test",
                    PackageManager.PERMISSION_GRANTED,
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
                )
            }
            base.evaluate()
        }
    }
}
