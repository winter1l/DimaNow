package com.example.dimanow.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Whether the app may read the location, and the one action that asks for it. */
internal class LocationAccess(val granted: Boolean, val request: () -> Unit)

/**
 * In-context location request (D-102): a screen asks where it needs the location instead of sending people
 * to Settings. Android's own prompt is shown while Android still allows it; after a lasting refusal the
 * same action opens this app's system settings. The state is re-read on every resume, and a change starts or
 * stops the location-based guidance at once.
 */
@Composable
internal fun rememberLocationAccess(): LocationAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val app = context.applicationContext as? com.example.dimanow.DimaNowApplication
    var granted by remember { mutableStateOf(context.hasLocationPermission()) }
    var refused by rememberSaveable { mutableStateOf(false) }
    fun reread() {
        val actual = context.hasLocationPermission()
        if (actual != granted) {
            granted = actual
            app?.refreshGuidancePermissions()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reread() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        reread()
        refused = !granted
    }
    return LocationAccess(granted) {
        val promptAvailable = !refused ||
            activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) } == true
        if (promptAvailable) {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            openAppDetailsSettings(context)
        }
    }
}

private fun Context.hasLocationPermission(): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
