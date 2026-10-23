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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.dimanow.dorm.RollCallStore
import com.example.dimanow.live.GuidanceKind
import com.example.dimanow.live.NotificationGuidanceMode
import com.example.dimanow.live.NotificationGuidancePolicy

@Composable
fun NotificationGuidanceSettings(
    policy: NotificationGuidancePolicy,
    onModeChange: (GuidanceKind, NotificationGuidanceMode) -> Unit,
    modifier: Modifier = Modifier,
    academicReminders: Boolean = true,
    onAcademicRemindersChange: (Boolean) -> Unit = {},
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
            // D-107: 학사 일정은 실시간 안내가 아니라 아침 한 번의 일반 알림이라 나우바 선택지가 없다
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("학사 일정", style = MaterialTheme.typography.labelLarge)
                SettingsChoice(
                    options = listOf(true to "일반 알림", false to "끔"),
                    selected = academicReminders,
                    onSelect = onAcademicRemindersChange,
                    itemTag = { "notification_academic_${if (it) "ON" else "OFF"}" },
                )
                Text(
                    "수강신청 정정·수업평가·시험 같은 기간이 시작하거나 끝나는 날 아침에 알려 줘요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            KakaoRollCallSetting()
        }
    }
}

/**
 * D-112: 카톡 점호 공지 읽기 follows Android's notification access for DIMA Now; this row shows whether it is on
 * and opens the system page that turns it on or off. The state is read again whenever the screen resumes.
 */
@Composable
private fun KakaoRollCallSetting() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(RollCallStore.accessGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = RollCallStore.accessGranted(context)
        if (!granted) RollCallStore.clear()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("kakao_roll_call_setting")) {
        Text("카톡 점호 공지", style = MaterialTheme.typography.labelLarge)
        Text(
            if (granted) {
                "켜져 있어요. 카카오톡 알림 중 점호 공지만 읽어 기숙사 화면에 보여 주고, 공지 내용은 저장하지 않아요."
            } else {
                "꺼져 있어요. 켜면 카카오톡 알림 중 점호 공지만 읽어 기숙사 화면에 오늘 점호 여부를 보여 줘요."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            shapes = ButtonDefaults.shapes(),
            onClick = { context.startActivity(RollCallStore.accessSettingsIntent(context)) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("kakao_roll_call_access"),
        ) { Text(if (granted) "설정에서 끄기" else "알림 접근 허용하기") }
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
