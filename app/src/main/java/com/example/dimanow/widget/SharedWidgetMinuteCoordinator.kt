package com.example.dimanow.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.dimanow.domain.CampusZoneId
import java.time.ZonedDateTime
import java.util.EnumMap

enum class WidgetMinuteKind {
    SHUTTLE,
    SUMMARY,
}

class RuntimeWidgetRefreshPlanner {
    fun shouldRefresh(previous: CampusZoneId?, current: CampusZoneId): Boolean = previous == null || previous != current
}

class SharedWidgetMinuteCoordinator(private val context: Context) {
    private val requirements = EnumMap<WidgetMinuteKind, Boolean>(WidgetMinuteKind::class.java)
    private var legacyAlarmsCancelled = false

    @Synchronized
    fun report(now: ZonedDateTime, kind: WidgetMinuteKind, requiresMinuteUpdate: Boolean) {
        requirements[kind] = requiresMinuteUpdate
        if (!legacyAlarmsCancelled) {
            cancelLegacyAlarms()
            legacyAlarmsCancelled = true
        }
        val manager = AppWidgetManager.getInstance(context)
        val shuttleCount = manager.getAppWidgetIds(ComponentName(context, ShuttleWidgetProvider::class.java)).size
        val summaryCount = manager.getAppWidgetIds(ComponentName(context, CampusSummaryWidgetProvider::class.java)).size
        val plan = SharedWidgetMinutePlanner().plan(
            now = now,
            shuttle = WidgetMinuteSurface(
                widgetCount = shuttleCount,
                requiresMinuteUpdate = requirements[WidgetMinuteKind.SHUTTLE] ?: (shuttleCount > 0),
            ),
            summary = WidgetMinuteSurface(
                widgetCount = summaryCount,
                requiresMinuteUpdate = requirements[WidgetMinuteKind.SUMMARY] ?: (summaryCount > 0),
            ),
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = minutePendingIntent()
        if (!plan.shouldSchedule) {
            alarm.cancel(pendingIntent)
            return
        }
        val trigger = plan.triggerAt!!.toEpochMilli()
        if (alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        }
    }

    private fun minutePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        SHARED_REQUEST_CODE,
        Intent(context, SharedWidgetMinuteReceiver::class.java).setAction(ACTION_MINUTE_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelLegacyAlarms() {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(
            PendingIntent.getBroadcast(
                context,
                5103,
                Intent(context, ShuttleWidgetProvider::class.java)
                    .setAction("com.example.dimanow.action.UPDATE_SHUTTLE_WIDGET_MINUTE"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        alarm.cancel(
            PendingIntent.getBroadcast(
                context,
                9102,
                Intent(context, CampusSummaryWidgetProvider::class.java)
                    .setAction("com.example.dimanow.action.UPDATE_CAMPUS_SUMMARY_WIDGET_MINUTE"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    private companion object {
        const val SHARED_REQUEST_CODE = 5003
        const val ACTION_MINUTE_TICK = "com.example.dimanow.action.UPDATE_ALL_WIDGETS_MINUTE"
    }
}

class SharedWidgetMinuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ShuttleWidgetProvider.updateAll(context)
        CampusSummaryWidgetProvider.updateAll(context)
    }
}
