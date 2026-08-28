package com.example.dimanow.live

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.GuidanceSnapshot
import com.example.dimanow.domain.ShuttleLine
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LiveMinuteUpdateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var updateJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (updateJob?.isActive == true) return START_NOT_STICKY
        val app = application as DimaNowApplication
        val controller = app.liveSurfaceController
        controller.ensureChannel()
        startForeground(
            AndroidLiveSurfaceController.NOTIFICATION_ID,
            controller.buildNotification(
                GuidanceSnapshot(
                    classContent = null,
                    shuttleLines = listOf(ShuttleLine("셔틀 시간 계산 중")),
                    phase = GuidancePhase.RETURN,
                ),
                requestPromotion = false,
            ),
        )
        updateJob = scope.launch {
            while (true) {
                val now = ZonedDateTime.now(com.example.dimanow.time.MinuteTicker.CAMPUS_ZONE)
                val runtime = app.guidanceRuntimeCoordinator.awaitSnapshot()
                val schedule = runtime.schedule
                val snapshot = app.guidanceEngine.snapshot(
                    now, schedule.termStart, schedule.termEnd, schedule.courses, schedule.noClassDates,
                    runtime.resolvedZone, true, runtime.shuttle.departures,
                    preparedSchedule = runtime.shuttleIndex,
                    homeBase = runtime.homeBase,
                    guidancePause = schedule.guidancePause,
                )
                if (snapshot.phase == GuidancePhase.NONE || !snapshot.requiresMinuteUpdates) {
                    stopSelf()
                    break
                }
                val notification = controller.buildNotification(
                    snapshot,
                    requestPromotion = true,
                    presentation = runtime.displayOptions,
                )
                startForeground(AndroidLiveSurfaceController.NOTIFICATION_ID, notification)
                delay(60_000 - (System.currentTimeMillis() % 60_000))
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
