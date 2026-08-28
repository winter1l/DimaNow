package com.example.dimanow.location

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.data.AppPreferences
import com.example.dimanow.domain.CampusZone
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.GeoPoint
import com.example.dimanow.domain.ZoneGeometry
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import com.example.dimanow.location.LocationMode

class CampusGeofenceManager(private val context: Context) {
    private val client = LocationServices.getGeofencingClient(context)
    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            4101,
            Intent(context, CampusGeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun sync(zones: List<CampusZone>) {
        if (zones.isEmpty()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val geofences = zones.map { zone ->
            val wakeRadius = when (val geometry = zone.geometry) {
                is ZoneGeometry.Circle -> geometry.radiusMeters
                is ZoneGeometry.Polygon -> geometry.wakeRadiusMeters
            }
            Geofence.Builder()
                .setRequestId(zone.id.name)
                .setCircularRegion(zone.center.latitude, zone.center.longitude, wakeRadius.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT or Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(120_000)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofences(geofences)
            .build()
        try {
            client.removeGeofences(pendingIntent).addOnCompleteListener {
                try {
                    client.addGeofences(request, pendingIntent)
                } catch (_: SecurityException) {
                    // Permission diagnostics surface the degraded state.
                }
            }
        } catch (_: SecurityException) {
            // Permission diagnostics surface the degraded state.
        }
    }

    fun clear() {
        client.removeGeofences(pendingIntent)
    }
}

class CampusGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as DimaNowApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(context, intent, app.preferences, app.repository.zones.first())
                app.guidanceRuntimeCoordinator.requestRefresh()
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(
        context: Context,
        intent: Intent,
        preferences: AppPreferences,
        zones: List<CampusZone>,
    ) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (preferences.locationMode.first() == LocationMode.TEST) return
        val triggered = event.triggeringGeofences.orEmpty().map { it.requestId }.toSet()
        val active = preferences.activeGeofenceIds.first().toMutableSet()
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_DWELL -> active += triggered
            Geofence.GEOFENCE_TRANSITION_EXIT -> active -= triggered
            else -> return
        }
        if (active.isEmpty() && event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            preferences.setLocationState(CampusZoneId.OUTSIDE, emptySet())
            return
        }
        val last = preferences.lastResolvedZone.first()
        val sample = try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val cancellation = CancellationTokenSource()
                val location = withTimeoutOrNull(10_000) {
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
                }.also { if (it == null) cancellation.cancel() }
                    ?: client.lastLocation.await()?.takeIf {
                        System.currentTimeMillis() - it.time <= 2 * 60_000 && it.accuracy <= 100f
                    }
                location?.let {
                    LocationSample(
                        point = GeoPoint(it.latitude, it.longitude),
                        accuracyMeters = it.accuracy,
                        capturedAt = Instant.ofEpochMilli(it.time),
                    )
                }
            } else null
        } catch (_: Exception) {
            null
        }
        val activeZones = zones.filter { it.id.name in active }
        val resolved = LocationResolver().resolve(sample, activeZones, last, explicitExitFromAll = false)
        preferences.setLocationState(resolved, active)
    }
}
