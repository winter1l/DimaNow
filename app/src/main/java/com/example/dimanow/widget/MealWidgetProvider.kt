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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    (context.applicationContext as DimaNowApplication).mealSource.refresh()
                    updateAll(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val data = (context.applicationContext as DimaNowApplication).mealSource.data.first()
                val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
                val today = now.toLocalDate()
                val meal = data.days.firstOrNull { it.date == today }
                val serviceStatus = mealServiceStatus(meal, now.toLocalTime())
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_meal)
                    val options = manager.getAppWidgetOptions(id)
                    val layoutPlan = MealWidgetLayoutPlanner().plan(
                        minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250),
                        minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110),
                    )
                    val paddingPx = (layoutPlan.horizontalPaddingDp * context.resources.displayMetrics.density).toInt()
                    views.setViewPadding(R.id.meal_widget_root, paddingPx, paddingPx, paddingPx, paddingPx)
                    views.setInt(R.id.meal_widget_menu, "setMaxLines", layoutPlan.menuMaxLines)
                    views.setTextViewTextSize(R.id.meal_widget_menu, TypedValue.COMPLEX_UNIT_SP, layoutPlan.menuTextSp)
                    views.setTextViewText(
                        R.id.meal_widget_menu,
                        meal?.menuLines?.joinToString("\n") ?: if (today.dayOfWeek.value >= 6) "오늘은 제공 식단이 없습니다" else "메뉴 확인 필요",
                    )
                    // 위젯 헤더는 좁으므로 운영 전에는 접두어 없이 시작 시각만 짧게 표시한다
                    val hoursLabel = if (serviceStatus.state == com.example.dimanow.meal.MealServiceState.BEFORE_OPEN) {
                        serviceStatus.label.removePrefix("운영 전 · ")
                    } else {
                        serviceStatus.label
                    }
                    views.setTextViewText(R.id.meal_widget_hours, hoursLabel)
                    views.setTextViewText(R.id.meal_widget_state, data.error.orEmpty())
                    views.setViewVisibility(R.id.meal_widget_state, if (data.error == null) View.GONE else View.VISIBLE)
                    val openApp = Intent(context, MainActivity::class.java).apply {
                        putExtra("TARGET_PAGE", "MEAL")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(context, 7101, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.meal_widget_root, pendingIntent)
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.example.dimanow.action.REFRESH_MEAL_WIDGET"
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MealWidgetProvider::class.java))
            context.sendBroadcast(Intent(context, MealWidgetProvider::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }
}
