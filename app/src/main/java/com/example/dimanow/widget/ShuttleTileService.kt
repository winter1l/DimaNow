package com.example.dimanow.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.MainActivity
import com.example.dimanow.R
import com.example.dimanow.guidance.ShuttleBoard
import com.example.dimanow.guidance.ShuttleBoardPurpose
import com.example.dimanow.time.MinuteTicker
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The `다음 셔틀` Quick Settings tile (D-128). While the panel is open it shows the soonest campus shuttle from
 * the resolved zone, read from the same runtime snapshot and [ShuttleWidgetDisplayPlanner] as the shuttle widget
 * (no network). It is informational, so it stays INACTIVE; a tap opens the 셔틀 tab.
 */
class ShuttleTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }
        listening?.cancel()
        listening = scope.launch {
            // The panel can stay open across a minute boundary: redraw on each new minute until it closes.
            while (isActive) {
                render()
                val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
                delay(MINUTE_MILLIS - (now.second * 1_000L + now.nano / 1_000_000L) + MINUTE_SLACK_MILLIS)
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openShuttle() } else openShuttle()
    }

    private fun openShuttle() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("TARGET_PAGE", "SHUTTLE")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 5102, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            startActivityAndCollapseBeforeApi34(intent)
        }
    }

    /** Android 12–13 have only the Intent overload, deprecated (and refused) from API 34. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseBeforeApi34(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    private suspend fun render() {
        val text = withContext(Dispatchers.Default) {
            val app = applicationContext as DimaNowApplication
            // A cold process (the tile bound before the app ran) needs a moment to build its first snapshot.
            val runtime = withTimeoutOrNull(SNAPSHOT_WAIT_MILLIS) { app.guidanceRuntimeCoordinator.awaitSnapshot() }
                ?: return@withContext null
            val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
            // D-032: the same GENERAL board as the widget, both directions from 본관
            val board = app.guidanceEngine.shuttleBoard(
                now = now,
                originZone = runtime.resolvedZone,
                index = runtime.shuttleIndex,
                purpose = ShuttleBoardPurpose.GENERAL,
            )
            val plan = ShuttleWidgetDisplayPlanner().plan(now, runtime.resolvedZone, board, runtime.shuttle.departures, compact = true)
            shuttleTileText(plan, board)
        } ?: return
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.shuttle_tile_label)
        tile.subtitle = text.subtitle
        tile.stateDescription = text.spokenSummary
        tile.updateTile()
    }

    private companion object {
        const val SNAPSHOT_WAIT_MILLIS = 3_000L
        const val MINUTE_MILLIS = 60_000L
        const val MINUTE_SLACK_MILLIS = 200L
    }
}

/** The tile's one-line [subtitle] and the widget's whole-board reading for TalkBack (D-128). */
internal data class ShuttleTileText(
    val subtitle: String,
    val spokenSummary: String,
)

/**
 * The soonest departure across the [board]'s destinations, worded like the widget: `본관행 5분 후`, `곧 출발`
 * at zero, the clock beyond the D-069 hour, and `막차` when it is the last one. Outside campus, no timetable or no
 * service today keep the widget's message; after the last bus it reads `운행 종료`.
 * [plan] must be the planner's output for the same [board] (its rows follow the board's rows).
 */
internal fun shuttleTileText(plan: ShuttleWidgetDisplayPlan, board: ShuttleBoard): ShuttleTileText {
    plan.emptyText?.let { return ShuttleTileText(it, plan.spokenSummary) }
    val soonest = board.rows.indices
        .filter { board.rows[it].departures.isNotEmpty() }
        .minByOrNull { board.rows[it].departures.first().remainingMinutes }
        ?: return ShuttleTileText(ShuttleWidgetDisplayPlanner.ENDED, plan.spokenSummary)
    val countdown = board.rows[soonest].departures.first()
    val planRow = plan.rows.getOrNull(soonest)
    val minutes = countdown.remainingMinutes
    val subtitle = buildString {
        planRow?.let { append(it.destinationTag).append(' ') }
        append(
            when {
                minutes <= 0 -> "곧 출발"
                minutes > ShuttleWidgetDisplayPlanner.COUNTDOWN_WINDOW_MINUTES -> "${countdown.departure.time.format(TILE_CLOCK)} 출발"
                else -> "${minutes}분 후"
            },
        )
        if (planRow?.capsules?.firstOrNull()?.tone == ShuttleWidgetCapsuleTone.NEAREST_LAST) append(" · 막차")
    }
    return ShuttleTileText(subtitle, plan.spokenSummary)
}

private val TILE_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
