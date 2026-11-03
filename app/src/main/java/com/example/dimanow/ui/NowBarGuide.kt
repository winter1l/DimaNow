package com.example.dimanow.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.dimanow.live.NowBarAvailability

/**
 * D-143: the fallback for a Now Bar that does not show. DIMA Now asks One UI for the card itself (Samsung's undocumented
 * extras), so most phones need nothing; when a phone still does not draw it, the two steps of D-140 turn it on: 개발자
 * 옵션 (빌드번호 seven times, opens the device info page) and `모든 앱의 실시간 정보 보기` under 추가 설정 (opens 개발자
 * 옵션 with that row highlighted). Each step shows as done once the phone reports it on, read again on return.
 * Before Android 16 it only says why the Now Bar cannot be used. Opened from 설정's "나우바가 안 보이면" and from a
 * dimmed 나우바 choice.
 */
@Composable
internal fun NowBarGuideDialog(unsupported: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var developerOptionsOn by remember { mutableStateOf(NowBarAvailability.developerOptionsOn(context)) }
    var allAppsOn by remember { mutableStateOf(NowBarAvailability.samsungOptionOn(context) == true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        developerOptionsOn = NowBarAvailability.developerOptionsOn(context)
        allAppsOn = NowBarAvailability.samsungOptionOn(context) == true
    }
    AlertDialog(
        icon = { DimaDialogIcon(Icons.Default.NotificationsActive, DialogTone.INFO) },
        onDismissRequest = onDismiss,
        title = { Text(if (unsupported) "이 휴대폰은 나우바를 쓸 수 없어요" else "나우바가 안 보이면") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).testTag("now_bar_guide"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (unsupported) {
                    KoreanText("나우바는 Android 16 이상에서 쓸 수 있어요. 이 휴대폰에서는 일반 알림으로 안내해요.")
                } else {
                    KoreanText(
                        "DIMA Now는 따로 설정하지 않아도 나우바에 떠요. Samsung이 공개하지 않은 방식이라 기기나 업데이트에 따라 " +
                            "안 보일 수 있는데, 그럴 땐 아래 두 가지를 켜 주세요.",
                    )
                    GuideStep(
                        number = 1,
                        done = developerOptionsOn,
                        title = "개발자 옵션 켜기",
                        description = if (developerOptionsOn) "켜져 있어요." else "열리는 화면에서 소프트웨어 정보 → 빌드번호를 7번 눌러요.",
                        action = "휴대폰 정보 열기".takeUnless { developerOptionsOn },
                        onAction = { openDeviceInfo(context) },
                        tag = "now_bar_guide_device_info",
                    )
                    GuideStep(
                        number = 2,
                        done = allAppsOn,
                        title = "모든 앱의 실시간 정보 보기 켜기",
                        description = if (allAppsOn) "켜져 있어요." else "개발자 옵션 맨 아래 ‘추가 설정’에 있어요. 열면 그 줄이 강조돼요.",
                        action = "개발자 옵션 열기".takeUnless { allAppsOn },
                        actionEnabled = developerOptionsOn,
                        onAction = { openDeveloperOptions(context, SAMSUNG_MORE_DEVELOPER_SETTINGS_KEY) },
                        tag = "now_bar_guide_developer_options",
                    )
                    KoreanText(
                        "일부 금융 앱은 개발자 옵션이 켜져 있으면 실행되지 않을 수 있어요. 나우바가 잘 보이면 켜지 않아도 돼요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss, modifier = Modifier.testTag("now_bar_guide_close")) {
                Text("닫기")
            }
        },
    )
}

@Composable
private fun GuideStep(
    number: Int,
    done: Boolean,
    title: String,
    description: String,
    action: String?,
    onAction: () -> Unit,
    tag: String,
    actionEnabled: Boolean = true,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (done) {
                Icon(Icons.Default.CheckCircle, contentDescription = "완료", tint = MaterialTheme.colorScheme.primary)
            } else {
                PreviewMarker(number)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            KoreanText(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.let {
                OutlinedButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onAction,
                    enabled = actionEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag),
                ) { Text(it) }
            }
        }
    }
}

/** The phone's device information page, where Samsung keeps 소프트웨어 정보 and its 빌드번호. */
private fun openDeviceInfo(context: android.content.Context) {
    listOf(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS), Intent(Settings.ACTION_SETTINGS)).firstOrNull { intent ->
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }
}
