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
        DepartureCapsule(label = departureCountdownLabel(8, emptyList()), clock = "21:30", containerColor = nearest, contentColor = onNearest)
        val (last, onLast) = shuttleCapsuleColors(isNearest = false, isLastService = true)
        DepartureCapsule(label = departureCountdownLabel(28, serviceMarkers(isFirst = false, isLast = true)), clock = "21:50", containerColor = last, contentColor = onLast)
    }
    val clocks = listOf("20:50", "21:10", "21:30", "21:50")
    TimetableChipRow(
        chips = clocks.mapIndexed { index, clock ->
            val isLast = index == clocks.lastIndex
            val isPast = index < 2
            val isNext = index == 2
            val markers = serviceMarkers(isFirst = index == 0, isLast = isLast)
            TimetableChipSpec(
                key = index,
                text = clock,
                description = timetableChipDescription(clock, isPast, isNext, markers),
                tone = timetableChipTone(isFirst = index == 0, isLast = isLast, isPast = isPast, isNext = isNext, isSecond = isLast),
            )
        },
        initialIndex = 2,
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
