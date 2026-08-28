package com.example.dimanow.live

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.TermSchedule
import com.example.dimanow.guidance.GuidanceEngine
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import com.example.dimanow.time.MinuteTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GuidanceOrchestrator(
    private val engine: GuidanceEngine,
    private val controller: LiveSurfaceController,
    private val alarmScheduler: GuidanceAlarmScheduler,
) {
    suspend fun refresh(
        runtime: GuidanceRuntimeSnapshot,
        now: ZonedDateTime = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE),
    ) {
        val schedule = runtime.schedule
        val snapshot = engine.snapshot(
            now = now,
            termStart = schedule.termStart,
            termEnd = schedule.termEnd,
            courses = schedule.courses,
            noClassDates = schedule.noClassDates,
            resolvedZone = runtime.resolvedZone,
            automaticClassGuidance = true,
            shuttleDepartures = runtime.shuttle.departures,
            preparedSchedule = runtime.shuttleIndex,
            homeBase = runtime.homeBase,
            guidancePause = schedule.guidancePause,
        )
        if (snapshot.phase == GuidancePhase.NONE) controller.cancel() else controller.show(snapshot, runtime.displayOptions)
        alarmScheduler.scheduleNext(now, schedule)
    }
}

sealed interface GuidanceAlarmPlan {
    data class Schedule(val triggerAt: ZonedDateTime) : GuidanceAlarmPlan
    data object Cancel : GuidanceAlarmPlan
}

class GuidanceAlarmScheduler(private val context: Context) {
    fun scheduleNext(now: ZonedDateTime, schedule: TermSchedule) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            6301,
            Intent(context, GuidanceAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val plan = planNext(now, schedule)
        if (plan is GuidanceAlarmPlan.Cancel) {
            alarm.cancel(pendingIntent)
            return
        }
        val trigger = (plan as GuidanceAlarmPlan.Schedule).triggerAt.toInstant().toEpochMilli()
        if (alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
    }

    companion object {
        fun planNext(now: ZonedDateTime, schedule: TermSchedule): GuidanceAlarmPlan {
            val candidates = boundaryCandidates(now, schedule.courses)
                .filter { candidate ->
                    val date = candidate.toLocalDate()
                    date in schedule.termStart..schedule.termEnd &&
                        date !in schedule.noClassDates &&
                        schedule.guidancePause?.contains(date) != true
                }
            return candidates.minOrNull()?.let(GuidanceAlarmPlan::Schedule) ?: GuidanceAlarmPlan.Cancel
        }

        fun calculateNextTrigger(now: ZonedDateTime, courses: List<com.example.dimanow.domain.Course>): ZonedDateTime? {
            return boundaryCandidates(now, courses).minOrNull()
        }

        private fun boundaryCandidates(
            now: ZonedDateTime,
            courses: List<com.example.dimanow.domain.Course>,
        ): List<ZonedDateTime> = (0..7).flatMap { dayOffset ->
                val date = now.toLocalDate().plusDays(dayOffset.toLong())
                courses.filter { it.weekday == date.dayOfWeek }.flatMap { course ->
                    val start = date.atTime(course.start).atZone(now.zone)
                    val end = date.atTime(course.end).atZone(now.zone)
                    listOf(start.minusMinutes(60), start, start.plusMinutes(30), end)
                }
            }.filter { it.isAfter(now.plusSeconds(1)) }
    }
}

class GuidanceAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                (context.applicationContext as DimaNowApplication).guidanceRuntimeCoordinator.requestRefresh()
                com.example.dimanow.widget.ShuttleWidgetProvider.updateAll(context)
                com.example.dimanow.widget.CampusSummaryWidgetProvider.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                (context.applicationContext as DimaNowApplication).guidanceRuntimeCoordinator.requestRefresh()
                com.example.dimanow.widget.ShuttleWidgetProvider.updateAll(context)
                com.example.dimanow.widget.CampusSummaryWidgetProvider.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
