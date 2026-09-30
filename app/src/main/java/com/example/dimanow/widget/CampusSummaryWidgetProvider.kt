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
                    schedule.coursesOn(today)
                }

                val plan = CampusSummaryWidgetPlanner().plan(
                    now = now,
                    currentZone = zone,
                    todayCourses = todayCourses,
                    shuttleBoard = shuttleBoard,
                    todayMeal = todayMeal,
                )

                ids.forEach { id ->
                    val options = manager.getAppWidgetOptions(id)
                    val views = campusSummaryWidgetViews(
                        context, plan,
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250),
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110),
                    )

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
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CampusSummaryWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            context.sendBroadcast(Intent(context, CampusSummaryWidgetProvider::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }
}

/** Presentation-only adaptation; all schedule, countdown and update decisions stay in the planner. */
internal fun campusSummaryWidgetViews(
    context: Context,
    plan: CampusSummaryWidgetPlan,
    widthDp: Int,
    heightDp: Int,
): RemoteViews {
    val fontScale = context.resources.configuration.fontScale
    // App layout decision: the three-panel layout needs room for its separate labels and content.
    val compact = heightDp < 180 * fontScale || widthDp < 300
    val views = RemoteViews(context.packageName, if (compact) R.layout.widget_campus_summary_compact else R.layout.widget_campus_summary)
    if (compact) {
        val course = "${plan.courseTitle} · ${plan.courseDetail}"
        val shuttle = "${plan.shuttleTitle} · ${plan.shuttleContent}"
        val meal = "${plan.mealTitle} · ${plan.mealContent}"
        views.setTextViewText(R.id.summary_widget_compact_header, "${plan.headerLocationText} · ${plan.headerDateText}")
        views.setTextViewText(R.id.summary_widget_compact_course, course)
        views.setTextViewText(R.id.summary_widget_compact_shuttle, shuttle)
        views.setTextViewText(R.id.summary_widget_compact_meal, meal)
        listOf(
            R.id.summary_widget_compact_course to course,
            R.id.summary_widget_compact_shuttle to shuttle,
            R.id.summary_widget_compact_meal to meal,
        ).forEach { (viewId, text) ->
            // Keep the complete summary available to accessibility when visual text is ellipsized.
            views.setContentDescription(viewId, text)
            // A short widget guarantees one whole line per summary; never show half a second line.
            views.setInt(viewId, "setMaxLines", 1)
        }
    } else {
        views.setTextViewText(R.id.summary_widget_location, plan.headerLocationText)
        views.setTextViewText(R.id.summary_widget_date, plan.headerDateText)
        views.setTextViewText(R.id.summary_widget_course_title, plan.courseTitle)
        views.setTextViewText(R.id.summary_widget_course_detail, plan.courseDetail)
        views.setTextViewText(R.id.summary_widget_shuttle_title, plan.shuttleTitle)
        views.setTextViewText(R.id.summary_widget_shuttle_content, plan.shuttleContent)
        views.setTextViewText(R.id.summary_widget_meal_title, plan.mealTitle)
        views.setTextViewText(R.id.summary_widget_meal_content, plan.mealContent)
    }
    return views
}
