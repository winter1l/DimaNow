package com.example.dimanow.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.dimanow.domain.GeoPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * Where the student is, for the 캠퍼스 지도 page (D-131(5)), read the way the geofence receiver reads it
 * (`CampusGeofenceReceiver`): a fused current fix with a 10 s limit, and a last-known fix no older than 2 minutes and
 * within 100 m. The page only reads; it never asks for the permission, and without it nothing is read.
 */
internal object StudentLocationReader {
    /** Whether the app may already read the location (fine or approximate). */
    fun canRead(context: Context): Boolean = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * The recent last-known fix at once, when there is one, then a fresh fix (D-131(5)); completes without emitting
     * when the permission is missing or no fix comes. Approximate-only access asks for a balanced-power fix.
     */
    fun fixes(context: Context): Flow<LocationSample> = flow {
        if (!canRead(context)) return@flow
        val client = LocationServices.getFusedLocationProviderClient(context)
        lastKnownFix(context, client)
            ?.takeIf { isRecentEnough(it.time, it.accuracy, System.currentTimeMillis()) }
            ?.let { emit(it.toSample()) }
        currentFix(context, client)?.let { emit(it.toSample()) }
    }

    /** The geofence receiver's bar for a last-known fix: at most 2 minutes old and accurate to 100 m. */
    internal fun isRecentEnough(fixTimeMillis: Long, accuracyMeters: Float, nowMillis: Long): Boolean =
        nowMillis - fixTimeMillis in 0..LAST_FIX_MAX_AGE_MILLIS && accuracyMeters <= LAST_FIX_MAX_ACCURACY_METERS

    private suspend fun lastKnownFix(context: Context, client: FusedLocationProviderClient): Location? {
        if (!canRead(context)) return null
        return try {
            client.lastLocation.await()
        } catch (_: SecurityException) {
            null
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            null
        }
    }

    private suspend fun currentFix(context: Context, client: FusedLocationProviderClient): Location? {
        if (!canRead(context)) return null
        val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val priority = if (precise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(CURRENT_FIX_TIMEOUT_MILLIS) {
                client.getCurrentLocation(priority, cancellation.token).await()
            }
        } catch (_: SecurityException) {
            null
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            null
        } finally {
            cancellation.cancel()
        }
    }

    private fun Location.toSample() = LocationSample(
        point = GeoPoint(latitude, longitude),
        accuracyMeters = accuracy,
        capturedAt = Instant.ofEpochMilli(time),
    )

    private val LOCATION_PERMISSIONS = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    private const val CURRENT_FIX_TIMEOUT_MILLIS = 10_000L
    private const val LAST_FIX_MAX_AGE_MILLIS = 2 * 60_000L
    private const val LAST_FIX_MAX_ACCURACY_METERS = 100f
}
