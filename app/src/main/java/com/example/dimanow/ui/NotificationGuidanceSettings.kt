package com.example.dimanow.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("알림 종류", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                NotificationGuidanceMode.LIVE_UPDATE to "나우바",
                NotificationGuidanceMode.STANDARD to "일반 알림",
                NotificationGuidanceMode.OFF to "끔",
            ).forEach { (mode, label) ->
                ExpressiveToggleButton(
                    label = label,
                    selected = selected == mode,
                    onClick = { onModeChange(kind, mode) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("notification_mode_${kind.name}_${mode.name}"),
                )
            }
        }
    }
}

@Composable
fun TransitStopTestControls(
    testStopNumber: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("4402 정류장", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                null to "선택 안 함",
                "34710" to "대학 셔틀 정류장",
                "33243" to "원룸촌 앞",
            ).forEach { (stopNumber, label) ->
                ExpressiveToggleButton(
                    label = label,
                    selected = testStopNumber == stopNumber,
                    onClick = { onChange(stopNumber) },
                )
            }
        }
    }
}
