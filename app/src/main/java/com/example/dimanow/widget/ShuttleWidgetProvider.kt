package com.example.dimanow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.MainActivity
import com.example.dimanow.R
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.guidance.ShuttleBoardPurpose
import com.example.dimanow.time.MinuteTicker
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShuttleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        update(context, manager, intArrayOf(appWidgetId))
    }

    override fun onDisabled(context: Context) {
        (context.applicationContext as DimaNowApplication).widgetMinuteCoordinator.report(
            ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE),
            WidgetMinuteKind.SHUTTLE,
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
                val data = runtime.shuttle
                val zone = runtime.resolvedZone
                val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
                val guidance = app.guidanceEngine
                // D-032: 위젯은 본관에서도 엔터관·원룸촌 양방향을 모두 보여주는 GENERAL 보드를 쓴다
                val board = guidance.shuttleBoard(
                    now = now,
                    originZone = zone,
                    index = runtime.shuttleIndex,
                    purpose = ShuttleBoardPurpose.GENERAL,
                )

                ids.forEach { id ->
                    // 좁거나(2x1) 낮은(1행 높이) 셀은 컴팩트 모드: 헤더 아래 한 줄에 모든 행선지의 남은 분만 요약
                    val options = manager.getAppWidgetOptions(id)
                    val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                    // 세로 화면에서의 실제 높이는 MAX_HEIGHT가 말해준다 (MIN_HEIGHT는 가로 모드 기준)
                    val portraitHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 50)
                    val compact = minWidthDp < 190 || portraitHeightDp < 140
                    manager.updateAppWidget(id, views(context, zone, now, board, data.departures, compact))
                }
                app.widgetMinuteCoordinator.report(now, WidgetMinuteKind.SHUTTLE, board.rows.isNotEmpty())
            } finally {
                pending.finish()
            }
        }
    }

    private fun views(
        context: Context,
        zone: CampusZoneId,
        now: ZonedDateTime,
        board: com.example.dimanow.guidance.ShuttleBoard,
        allDepartures: List<ShuttleDeparture>,
        compact: Boolean,
    ): RemoteViews {
        val display = ShuttleWidgetDisplayPlanner().plan(now, zone, board, allDepartures, compact)
        val views = shuttleWidgetViews(context, display, compact)
        val openApp = Intent(context, MainActivity::class.java).apply {
            putExtra("TARGET_PAGE", "SHUTTLE")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, 5101, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(android.R.id.background, pendingIntent)
        return views
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ShuttleWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            context.sendBroadcast(Intent(context, ShuttleWidgetProvider::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }
}

/** Presentation-only binding of a [ShuttleWidgetDisplayPlan]; wording and tones stay in the planner. */
internal fun shuttleWidgetViews(context: Context, display: ShuttleWidgetDisplayPlan, compact: Boolean): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_shuttle)
    views.setTextViewText(R.id.widget_tag, display.headerTagText)
    // TalkBack reads the whole widget once, with clocks even where the cell shows minutes only.
    views.setContentDescription(android.R.id.background, display.spokenSummary)
    views.removeAllViews(R.id.widget_rows)
    if (display.emptyText != null) {
        views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
        views.setTextViewText(R.id.widget_empty, display.emptyText)
    } else {
        views.setViewVisibility(R.id.widget_empty, View.GONE)
    }
    if (compact && display.emptyText == null && display.rows.isNotEmpty()) {
        // 컴팩트(2x1): 헤더는 유지하고 모든 행선지를 액센트 스팬 한 줄로 요약한다 (M3 텍스트 레이아웃)
        views.setViewVisibility(R.id.widget_compact_line, View.VISIBLE)
        views.setTextViewText(R.id.widget_compact_line, buildCompactLine(context, display.rows))
    } else {
        views.setViewVisibility(R.id.widget_compact_line, View.GONE)
        display.rows.forEach { row ->
            val rowViews = RemoteViews(context.packageName, R.layout.widget_shuttle_row)
            rowViews.setTextViewText(R.id.shuttle_row_tag, row.destinationTag)
            bindCapsule(rowViews, R.id.shuttle_row_capsule1, row.capsules.getOrNull(0))
            bindCapsule(rowViews, R.id.shuttle_row_capsule2, row.capsules.getOrNull(1))
            views.addView(R.id.widget_rows, rowViews)
        }
    }
    return views
}

private fun buildCompactLine(context: Context, rows: List<ShuttleWidgetRow>): CharSequence {
    if (rows.all { it.capsules.firstOrNull()?.tone == ShuttleWidgetCapsuleTone.ENDED }) return ShuttleWidgetDisplayPlanner.ENDED
    val accent = context.getColor(R.color.widget_accent)
    val builder = SpannableStringBuilder()
    rows.forEachIndexed { index, row ->
        if (index > 0) builder.append("  ")
        val start = builder.length
        builder.append(row.destinationTag)
        builder.setSpan(ForegroundColorSpan(accent), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.append(" ")
        builder.append(row.capsules.firstOrNull()?.text.orEmpty())
    }
    return builder
}

private fun bindCapsule(rowViews: RemoteViews, viewId: Int, capsule: ShuttleWidgetCapsule?) {
    if (capsule == null) {
        rowViews.setViewVisibility(viewId, View.GONE)
        return
    }
    val (background, textColor) = when (capsule.tone) {
        ShuttleWidgetCapsuleTone.NEAREST -> R.drawable.widget_capsule_next_bg to R.color.widget_on_accent
        ShuttleWidgetCapsuleTone.NEAREST_LAST -> R.drawable.widget_capsule_next_last_bg to R.color.widget_on_warning
        ShuttleWidgetCapsuleTone.LAST -> R.drawable.widget_capsule_last_bg to R.color.widget_capsule_last_text
        ShuttleWidgetCapsuleTone.LATER, ShuttleWidgetCapsuleTone.ENDED -> R.drawable.widget_capsule_bg to R.color.widget_text_primary
    }
    rowViews.setViewVisibility(viewId, View.VISIBLE)
    rowViews.setTextViewText(viewId, capsule.text)
    // Resource references (not resolved colors) so the launcher applies the night variants.
    rowViews.setInt(viewId, "setBackgroundResource", background)
    rowViews.setColorStateList(viewId, "setTextColor", textColor)
}
