package com.example.dimanow.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.example.dimanow.MainActivity
import com.example.dimanow.R
import com.example.dimanow.time.MinuteTicker
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs a worker once a day at a fixed campus time (Q-032 (c)). A 24-hour periodic work starts each period when the
 * previous run ends, so it only ever drifted later and could settle in the night; a one-time work set for the next
 * HH:mm, and queued again by the run itself, stays on the clock.
 */
internal object DailyWork {
    fun delayUntilNext(now: ZonedDateTime, at: LocalTime): Duration {
        var next = now.toLocalDate().atTime(at).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    /** At process start: a run already waiting stays; otherwise the next [at] is booked. */
    fun ensure(context: Context, name: String, at: LocalTime, worker: Class<out ListenableWorker>) =
        enqueue(context, name, at, worker, ExistingWorkPolicy.KEEP)

    /** From the run itself: tomorrow's run waits behind this one. */
    fun next(context: Context, name: String, at: LocalTime, worker: Class<out ListenableWorker>) =
        enqueue(context, name, at, worker, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun enqueue(context: Context, name: String, at: LocalTime, worker: Class<out ListenableWorker>, policy: ExistingWorkPolicy) {
        val delay = delayUntilNext(ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE), at)
        val request = OneTimeWorkRequest.Builder(worker).setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
    }
}

/** What the app's ordinary (non-live) notifications share: permission check, channel, look and tap target. */
internal object CampusNotifications {
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun channel(context: Context, id: String, name: String, description: String) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply { this.description = description },
        )
    }

    /**
     * Opens the app on [targetPage]. SINGLE_TOP with CLEAR_TOP hands a running app the page through `onNewIntent`;
     * without flags a tap only brought the app forward and the page was ignored.
     */
    fun openPage(context: Context, requestCode: Int, targetPage: String): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java)
            .putExtra("TARGET_PAGE", targetPage)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun builder(context: Context, channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_dima)
            .setColor(0xFFEC268FL.toInt())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
}
