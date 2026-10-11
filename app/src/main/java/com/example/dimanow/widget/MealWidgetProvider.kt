package com.example.dimanow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.MainActivity
import com.example.dimanow.R
import java.time.LocalDate
import java.time.ZonedDateTime
import com.example.dimanow.meal.mealServiceStatus
import com.example.dimanow.time.MinuteTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MealWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context, manager, ids)

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) = update(context, appWidgetManager, intArrayOf(appWidgetId))

    private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val data = (context.applicationContext as DimaNowApplication).mealSource.data.first()
                val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
                val today = now.toLocalDate()
                val meal = data.days.firstOrNull { it.date == today }
                val serviceStatus = mealServiceStatus(meal, now.toLocalTime())
                val content = MealWidgetContentPlanner().plan(
                    meal = meal,
                    status = serviceStatus,
                    isWeekend = today.dayOfWeek.value >= 6,
                    refreshFailed = data.error != null,
                )
                ids.forEach { id ->
                    val options = manager.getAppWidgetOptions(id)
                    val layoutPlan = MealWidgetLayoutPlanner().plan(
                        minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250),
                        minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110),
                    )
                    val views = mealWidgetViews(context, content, layoutPlan)
                    val openApp = Intent(context, MainActivity::class.java).apply {
                        putExtra("TARGET_PAGE", "MEAL")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(context, 7101, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(android.R.id.background, pendingIntent)
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MealWidgetProvider::class.java))
            context.sendBroadcast(Intent(context, MealWidgetProvider::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }
}

/** Presentation-only binding of the meal widget content and size plan. */
internal fun mealWidgetViews(context: Context, content: MealWidgetContent, layoutPlan: MealWidgetLayoutPlan): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_meal)
    val resources = context.resources
    val paddingPx = (layoutPlan.horizontalPaddingDp * resources.displayMetrics.density).toInt()
    views.setViewPadding(android.R.id.background, paddingPx, paddingPx, paddingPx, paddingPx)
    views.setInt(R.id.meal_widget_menu, "setMaxLines", layoutPlan.menuMaxLines)
    val menuSize = when (layoutPlan.menuText) {
        WidgetTextRole.CAPTION -> R.dimen.widget_text_caption
        WidgetTextRole.BODY -> R.dimen.widget_text_body
    }
    views.setTextViewTextSize(R.id.meal_widget_menu, TypedValue.COMPLEX_UNIT_PX, resources.getDimension(menuSize))
    views.setTextViewText(R.id.meal_widget_menu, content.menuText)
    views.setTextViewText(R.id.meal_widget_hours, content.hoursLabel)
    views.setTextViewText(R.id.meal_widget_state, content.stateText.orEmpty())
    views.setViewVisibility(R.id.meal_widget_state, if (content.stateText == null) View.GONE else View.VISIBLE)
    views.setContentDescription(android.R.id.background, content.spokenSummary)
    return views
}
