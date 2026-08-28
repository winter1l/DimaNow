package com.example.dimanow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.MainActivity
import com.example.dimanow.R
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.guidance.ShuttleBoardPurpose
import com.example.dimanow.time.MinuteTicker
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CampusSummaryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        update(context, manager, intArrayOf(appWidgetId))
    }

    override fun onDisabled(context: Context) {
        (context.applicationContext as DimaNowApplication).widgetMinuteCoordinator.report(
            ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE),
            WidgetMinuteKind.SUMMARY,
            false,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            updateAll(context)
        }
    }

    private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as DimaNowApplication
                val runtime = app.guidanceRuntimeCoordinator.awaitSnapshot()
                val schedule = runtime.schedule
                val shuttleData = runtime.shuttle
                val mealData = app.mealSource.data.first()
                val zone = runtime.resolvedZone
                val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
                val today = now.toLocalDate()
                val engine = app.guidanceEngine
                val shuttleBoard = engine.shuttleBoard(
                    now = now,
                    originZone = zone,
                    index = runtime.shuttleIndex,
                    purpose = ShuttleBoardPurpose.GENERAL,
                )

                val todayMeal = mealData.days.firstOrNull { it.date == today }
                val guidancePaused = today in schedule.noClassDates ||
                    schedule.guidancePause?.contains(today) == true ||
                    today.isBefore(schedule.termStart) || today.isAfter(schedule.termEnd)
                val todayCourses = if (guidancePaused) {
                    emptyList()
                } else {
                    schedule.courses.filter { it.weekday == now.dayOfWeek }
                }

                val plan = CampusSummaryWidgetPlanner().plan(
                    now = now,
                    currentZone = zone,
                    todayCourses = todayCourses,
                    shuttleBoard = shuttleBoard,
                    todayMeal = todayMeal,
                )

                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_campus_summary)
                    views.setTextViewText(R.id.summary_widget_location, plan.headerLocationText)
                    views.setTextViewText(R.id.summary_widget_date, plan.headerDateText)
                    views.setTextViewText(R.id.summary_widget_course_title, plan.courseTitle)
                    views.setTextViewText(R.id.summary_widget_course_detail, plan.courseDetail)
                    views.setTextViewText(R.id.summary_widget_shuttle_title, plan.shuttleTitle)
                    views.setTextViewText(R.id.summary_widget_shuttle_content, plan.shuttleContent)
                    views.setTextViewText(R.id.summary_widget_meal_title, plan.mealTitle)
                    views.setTextViewText(R.id.summary_widget_meal_content, plan.mealContent)

                    val openApp = Intent(context, MainActivity::class.java).apply {
                        putExtra("TARGET_PAGE", "DASHBOARD")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(context, 9101, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.summary_widget_root, pendingIntent)

                    manager.updateAppWidget(id, views)
                }
                app.widgetMinuteCoordinator.report(now, WidgetMinuteKind.SUMMARY, plan.requiresMinuteUpdate)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.dimanow.action.REFRESH_CAMPUS_SUMMARY_WIDGET"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CampusSummaryWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            context.sendBroadcast(Intent(context, CampusSummaryWidgetProvider::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }
}
