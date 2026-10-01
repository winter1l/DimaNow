package com.example.dimanow.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.dimanow.live.LiveSettingsDestination
import com.example.dimanow.live.LiveSurfaceController

internal data class GuidanceSetupState(
    val notifications: Boolean = false,
    val preciseLocation: Boolean = false,
    val approximateLocation: Boolean = false,
    val backgroundLocation: Boolean = false,
    val exactAlarms: Boolean = false,
    val liveSupported: Boolean = false,
    val liveAllowed: Boolean = false,
)

internal enum class GuidanceSetupAction { NOTIFICATIONS, LOCATION, BACKGROUND_LOCATION, EXACT_ALARM, LIVE }

/** Both first-run setup and Settings read permissions from Android, including on return from Settings. */
@Composable
fun GuidanceSetup(
    liveSurfaceController: LiveSurfaceController,
    modifier: Modifier = Modifier,
    onPermissionsChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    fun readState() = GuidanceSetupState(
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        preciseLocation = context.hasSetupPermission(Manifest.permission.ACCESS_FINE_LOCATION),
        approximateLocation = context.hasSetupPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
        backgroundLocation = context.hasSetupPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        exactAlarms = (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms(),
        liveSupported = Build.VERSION.SDK_INT >= 36,
        liveAllowed = Build.VERSION.SDK_INT >= 36 && liveSurfaceController.diagnostics().canPostPromotedNotifications,
    )
    var state by remember(context, liveSurfaceController) { mutableStateOf(readState()) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var backgroundExplanation by rememberSaveable { mutableStateOf(false) }
    var notificationDenied by rememberSaveable { mutableStateOf(false) }
    var locationDenied by rememberSaveable { mutableStateOf(false) }
    fun refresh() {
        val actual = readState()
        if (actual != state) {
            state = actual
            onPermissionsChanged()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationDenied = !granted
        refresh()
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        locationDenied = !state.preciseLocation
    }
    fun openSettings(vararg intents: Intent) {
        message = if (intents.any { intent ->
                runCatching { context.startActivity(intent); true }.getOrDefault(false)
            }) null else "설정을 열지 못했어요. 휴대폰 설정에서 DIMA Now를 찾아 주세요."
    }
    fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GuidanceSetupContent(state, onAction = { action ->
            message = null
            when (action) {
                GuidanceSetupAction.NOTIFICATIONS -> {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        !context.hasSetupPermission(Manifest.permission.POST_NOTIFICATIONS) && !notificationDenied
                    ) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openSettings(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        appDetails(),
                    )
                }
                GuidanceSetupAction.LOCATION -> {
                    if (locationDenied) openSettings(appDetails())
                    else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                GuidanceSetupAction.BACKGROUND_LOCATION -> backgroundExplanation = true
                GuidanceSetupAction.EXACT_ALARM -> openSettings(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    appDetails(),
                )
                GuidanceSetupAction.LIVE -> {
                    if (liveSurfaceController.openPromotionSettings() == LiveSettingsDestination.UNAVAILABLE) {
                        message = "실시간 알림 설정을 열지 못했어요. 휴대폰의 앱 알림 설정을 확인해 주세요."
                    }
                }
            }
        })
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (backgroundExplanation) {
        val optionLabel = context.packageManager.backgroundPermissionOptionLabel
        AlertDialog(
            onDismissRequest = { backgroundExplanation = false },
            title = { Text("앱을 닫아도 위치 확인") },
            text = { Text("이동에 맞춰 셔틀 안내를 바꾸려면 위치 권한이 필요해요. 다음 화면에서 권한 → 위치 → '$optionLabel'을 선택해 주세요. 나중에 설정해도 앱을 사용할 수 있어요.") },
            confirmButton = { TextButton(onClick = { backgroundExplanation = false; openSettings(appDetails()) }) { Text("설정") } },
            dismissButton = { TextButton(onClick = { backgroundExplanation = false }) { Text("나중에") } },
        )
    }
}

@Composable
internal fun GuidanceSetupContent(
    state: GuidanceSetupState,
    onAction: (GuidanceSetupAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetupRow("알림 받기", "수업 시작과 셔틀 출발을 알려줘요.", state.notifications, GuidanceSetupAction.NOTIFICATIONS, onAction)
        SetupRow(
            "현재 위치 사용", "가까운 정류장의 셔틀을 안내해요.", state.preciseLocation,
            GuidanceSetupAction.LOCATION, onAction,
            status = if (!state.preciseLocation && state.approximateLocation) "대략적 위치 허용됨 · 정확한 위치가 필요해요" else null,
        )
        SetupRow(
            "앱을 닫아도 위치 확인", "이동에 맞춰 자동 안내를 바꿔요.", state.backgroundLocation,
            GuidanceSetupAction.BACKGROUND_LOCATION, onAction,
            enabled = state.preciseLocation || state.approximateLocation,
            status = if (!state.preciseLocation && !state.approximateLocation) "현재 위치를 먼저 허용해 주세요" else null,
        )
        SetupRow("알림 시각 맞추기", "수업 시작·종료 시각에 맞춰 안내해요.", state.exactAlarms, GuidanceSetupAction.EXACT_ALARM, onAction)
        if (state.liveSupported) {
            SetupRow(
                "실시간 알림", "잠금화면과 상단에서 안내를 확인해요.", state.liveAllowed && state.notifications,
                GuidanceSetupAction.LIVE, onAction, enabled = state.notifications,
                status = if (!state.notifications) "알림 받기를 먼저 허용해 주세요" else null,
            )
        }
    }
}

@Composable
private fun SetupRow(
    title: String,
    description: String,
    completed: Boolean,
    action: GuidanceSetupAction,
    onAction: (GuidanceSetupAction) -> Unit,
    enabled: Boolean = true,
    status: String? = null,
) {
    Column {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status ?: if (completed) "설정 완료" else "설정 전",
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!completed) {
                    TextButton(
                        onClick = { onAction(action) }, enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("guidance_setup_${action.name}")
                            .semantics { contentDescription = "$title 설정" },
                    ) { Text("설정") }
                }
            }
        }
    }
}

private fun Context.hasSetupPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
