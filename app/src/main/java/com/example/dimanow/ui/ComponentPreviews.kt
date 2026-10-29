package com.example.dimanow.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DIMANowTheme

/** Design-time previews of the shared components described in DESIGN.md; no app data needed. */

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    DIMANowTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Preview(name = "Departures", widthDp = 360)
@Preview(name = "Departures dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeparturePreview() = PreviewSurface {
    DepartureCapsuleRow {
        val (nearest, onNearest) = shuttleCapsuleColors(isNearest = true, isLastService = false)
        DepartureCapsule(countdown = "8분 후", clock = "21:30", markers = emptyList(), containerColor = nearest, contentColor = onNearest, isNext = true)
        val (last, onLast) = shuttleCapsuleColors(isNearest = false, isLastService = true)
        DepartureCapsule(countdown = "28분 후", clock = "21:50", markers = listOf("막차"), containerColor = last, contentColor = onLast)
    }
    val clocks = listOf("20:50", "21:10", "21:30", "21:50")
    DepartureStrip(
        chips = clocks.mapIndexed { index, clock ->
            val isLast = index == clocks.lastIndex
            val isPast = index < 1
            val isNext = index == 1
            val isSecond = index == 2
            val markers = serviceMarkers(isFirst = index == 0, isLast = isLast)
            val countdown = when { isNext -> "8분 후"; isSecond -> "28분 후"; else -> null }
            TimetableChipSpec(
                key = index,
                clock = clock,
                markers = markers,
                description = timetableChipDescription(clock, isPast, isNext, markers, countdown),
                tone = timetableChipTone(isFirst = index == 0, isLast = isLast, isPast = isPast, isNext = isNext, isSecond = isSecond),
                isPast = isPast,
                countdown = countdown,
            )
        },
        stateKey = Unit,
    )
}

@Preview(name = "States", widthDp = 360)
@Composable
private fun StatePreview() = PreviewSurface {
    LoadingLine("셔틀 시간표를 불러오는 중이에요")
    EmptyState(message = "오늘은 운행하는 셔틀이 없어요", icon = Icons.Default.DirectionsBus)
    ErrorState(message = "시간표를 새로 받지 못했어요", supporting = "잠시 후 다시 시도해 주세요.", onRetry = {})
}
