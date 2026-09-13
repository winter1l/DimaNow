package com.example.dimanow.live

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dimanow.DimaNowApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Minute updates without a foreground-service keeper notification. */
internal class LiveMinuteUpdateScheduler(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Synchronized
    fun start() {
        scheduleWake(context)
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(60_000 - System.currentTimeMillis() % 60_000)
                (context.applicationContext as DimaNowApplication).guidanceRuntimeCoordinator.requestRefresh()
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    @Synchronized
    fun resumeAt(triggerAt: java.time.Instant) {
        stop()
        scheduleWake(context, triggerAt.toEpochMilli())
    }

    companion object {
        fun scheduleWake(context: Context, triggerAtMillis: Long? = null) {
            val now = System.currentTimeMillis()
            val trigger = triggerAtMillis ?: (now + 60_000 - now % 60_000)
            val alarm = context.getSystemService(AlarmManager::class.java)
            val pending = pendingIntent(context)
            // Idle-mode quotas may defer alarms; the in-process ticker and screen-on refresh
            // keep visible guidance current without posting an information-free service card.
            if (alarm.canScheduleExactAlarms()) {
                try {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                    return
                } catch (_: SecurityException) {
                    // Permission can be revoked between the check and scheduling.
                }
            }
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }

        private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            6302,
            Intent(context, LiveMinuteUpdateReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class LiveMinuteUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // Leave a retry if cold-start initialization exceeds the broadcast time budget.
                LiveMinuteUpdateScheduler.scheduleWake(context)
                withTimeout(8_000) {
                    val app = context.applicationContext as DimaNowApplication
                    app.guidanceOrchestrator.refresh(app.guidanceRuntimeCoordinator.awaitSnapshot())
                }
            } finally {
                pending.finish()
            }
        }
    }
}
