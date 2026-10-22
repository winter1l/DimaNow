package com.example.dimanow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.example.dimanow.theme.DimaShapes
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dimanow.live.GuidanceKind
import com.example.dimanow.live.NotificationGuidanceMode
import com.example.dimanow.live.NotificationGuidancePolicy

@Composable
fun NotificationGuidanceSettings(
    policy: NotificationGuidancePolicy,
    onModeChange: (GuidanceKind, NotificationGuidanceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("알림 종류", style = MaterialTheme.typography.titleMedium)
            GuidanceModeRow("수업 안내", GuidanceKind.CLASS, policy.classGuidance, onModeChange)
            GuidanceModeRow("교내 셔틀", GuidanceKind.CAMPUS_SHUTTLE, policy.campusShuttle, onModeChange)
            GuidanceModeRow("4402 강남행", GuidanceKind.BUS_4402, policy.bus4402, onModeChange)
        }
    }
}

@Composable
private fun GuidanceModeRow(
    title: String,
    kind: GuidanceKind,
    selected: NotificationGuidanceMode,
    onModeChange: (GuidanceKind, NotificationGuidanceMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        SettingsChoice(
            options = listOf(
                NotificationGuidanceMode.LIVE_UPDATE to "나우바",
                NotificationGuidanceMode.STANDARD to "일반 알림",
                NotificationGuidanceMode.OFF to "끔",
            ),
            selected = selected,
            onSelect = { onModeChange(kind, it) },
            itemTag = { "notification_mode_${kind.name}_${it.name}" },
        )
    }
}

@Composable
fun TransitStopTestControls(
    testStopNumber: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("4402 정류장", style = MaterialTheme.typography.labelLarge)
        SettingsChoice(
            options = listOf(
                null to "선택 안 함",
                "34710" to "대학 셔틀 정류장",
                "33243" to "원룸촌 앞",
            ),
            selected = testStopNumber,
            onSelect = onChange,
        )
    }
}
