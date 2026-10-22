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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
) {
    /** The two permissions the first run asks for in one step (D-102): guidance needs both to work at all. */
    val essentialsGranted: Boolean get() = notifications && (preciseLocation || approximateLocation)

    /** How many applicable setup items are still off, for the Settings summary. */
    val missingCount: Int
        get() = listOf(notifications, preciseLocation, backgroundLocation, exactAlarms).count { !it } +
            if (liveSupported && !(liveAllowed && notifications)) 1 else 0
}

internal enum class GuidanceSetupAction { NOTIFICATIONS, LOCATION, BACKGROUND_LOCATION, EXACT_ALARM, LIVE }

/**
 * The permission and device-setting state shared by first-run setup and Settings, read from Android again
 * on every resume (the settings-only items change outside the app, D-059).
 *
 * [requestEssentials] asks for notifications and location in one go: Android shows its prompts one after
 * another, and every refusal leaves the app usable (D-056). [perform] handles one checklist row; a prompt
 * Android no longer shows falls back to this app's system settings page.
 */
@Stable
internal class GuidanceSetupController internal constructor() {
    var state by mutableStateOf(GuidanceSetupState())
        internal set
    var message by mutableStateOf<String?>(null)
        internal set
    internal var backgroundExplanation by mutableStateOf(false)
    internal var performAction: (GuidanceSetupAction) -> Unit = {}
    internal var essentials: () -> Unit = {}
    internal var openAppSettings: () -> Unit = {}

    fun perform(action: GuidanceSetupAction) = performAction(action)
    fun requestEssentials() = essentials()
}

@Composable
internal fun rememberGuidanceSetupController(
    liveSurfaceController: LiveSurfaceController,
    onPermissionsChanged: () -> Unit = {},
): GuidanceSetupController {
    val context = LocalContext.current
    val changed by rememberUpdatedState(onPermissionsChanged)
    fun readState() = GuidanceSetupState(
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        preciseLocation = context.hasSetupPermission(Manifest.permission.ACCESS_FINE_LOCATION),
        approximateLocation = context.hasSetupPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
        backgroundLocation = context.hasSetupPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        exactAlarms = (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms(),
        liveSupported = Build.VERSION.SDK_INT >= 36,
        liveAllowed = Build.VERSION.SDK_INT >= 36 && liveSurfaceController.diagnostics().canPostPromotedNotifications,
    )
    val controller = remember(context, liveSurfaceController) { GuidanceSetupController().apply { state = readState() } }
    var notificationDenied by rememberSaveable { mutableStateOf(false) }
    var locationDenied by rememberSaveable { mutableStateOf(false) }
    fun refresh() {
        val actual = readState()
        if (actual != controller.state) {
            controller.state = actual
            changed()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationDenied = !granted
        refresh()
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        locationDenied = !controller.state.preciseLocation
    }
    val essentialsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        notificationDenied = !controller.state.notifications
        locationDenied = !controller.state.preciseLocation
    }
    fun openSettings(vararg intents: Intent) {
        controller.message = if (intents.any { intent ->
                runCatching { context.startActivity(intent); true }.getOrDefault(false)
            }) null else "설정을 열지 못했어요. 휴대폰 설정에서 DIMA Now를 찾아 주세요."
    }
    fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    controller.openAppSettings = { openSettings(appDetails()) }
    controller.essentials = {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= 33 && !context.hasSetupPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!controller.state.preciseLocation) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (permissions.isNotEmpty()) essentialsLauncher.launch(permissions.toTypedArray())
    }
    controller.performAction = { action ->
        controller.message = null
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
            GuidanceSetupAction.BACKGROUND_LOCATION -> controller.backgroundExplanation = true
            GuidanceSetupAction.EXACT_ALARM -> openSettings(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                appDetails(),
            )
            GuidanceSetupAction.LIVE -> {
                if (liveSurfaceController.openPromotionSettings() == LiveSettingsDestination.UNAVAILABLE) {
                    controller.message = "실시간 알림 설정을 열지 못했어요. 휴대폰의 앱 알림 설정을 확인해 주세요."
                }
            }
        }
    }
    return controller
}

/** Settings' checklist: every row with its state, read from Android, including on return from Settings. */
@Composable
fun GuidanceSetup(
    liveSurfaceController: LiveSurfaceController,
    modifier: Modifier = Modifier,
    onPermissionsChanged: () -> Unit = {},
) {
    val controller = rememberGuidanceSetupController(liveSurfaceController, onPermissionsChanged)
    GuidanceSetupChecklist(controller, modifier)
}

@Composable
internal fun GuidanceSetupChecklist(
    controller: GuidanceSetupController,
    modifier: Modifier = Modifier,
    onlyIncomplete: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GuidanceSetupContent(controller.state, onAction = controller::perform, onlyIncomplete = onlyIncomplete)
        controller.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (controller.backgroundExplanation) {
        val optionLabel = LocalContext.current.packageManager.backgroundPermissionOptionLabel
        AlertDialog(
            onDismissRequest = { controller.backgroundExplanation = false },
            title = { Text("앱을 닫아도 위치 확인") },
            text = { Text("앱이 닫혀 있을 때도 예인관·본관·원룸촌·4402 정류장에 들어섰는지 확인해 셔틀 안내를 바꾸려면 위치 권한이 필요해요. 위치는 이 기기 안에서만 쓰고 저장하거나 보내지 않아요. 다음 화면에서 권한 → 위치 → '$optionLabel'을 선택해 주세요. 나중에 설정해도 앱을 사용할 수 있어요.") },
            confirmButton = {
                TextButton(onClick = { controller.backgroundExplanation = false; controller.openAppSettings() }) { Text("설정 열기") }
            },
            dismissButton = { TextButton(onClick = { controller.backgroundExplanation = false }) { Text("나중에") } },
        )
    }
}

/**
 * One row per setup item: icon, what it does in one sentence, and either a check mark or the action that
 * turns it on. The button names what happens (D-102): `허용하기` asks Android, `설정 열기` opens a system page.
 */
@Composable
internal fun GuidanceSetupContent(
    state: GuidanceSetupState,
    onAction: (GuidanceSetupAction) -> Unit,
    modifier: Modifier = Modifier,
    onlyIncomplete: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        @Composable
        fun row(
            icon: ImageVector, title: String, description: String, completed: Boolean, action: GuidanceSetupAction,
            enabled: Boolean = true, status: String? = null,
        ) {
            if (!(onlyIncomplete && completed)) SetupRow(icon, title, description, completed, action, onAction, enabled, status)
        }
        row(Icons.Default.Notifications, "알림 받기", "수업 시작과 셔틀 출발을 알려 줘요.", state.notifications, GuidanceSetupAction.NOTIFICATIONS)
        row(
            Icons.Default.LocationOn, "현재 위치 사용", "가까운 정류장의 셔틀을 안내해요. 위치는 이 기기 안에서만 쓰고 어디에도 보내지 않아요.",
            state.preciseLocation, GuidanceSetupAction.LOCATION,
            status = if (!state.preciseLocation && state.approximateLocation) "대략적 위치 허용됨 · 정확한 위치가 필요해요" else null,
        )
        row(
            Icons.Default.MyLocation, "앱을 닫아도 위치 확인", "이동에 맞춰 자동 안내를 바꿔요.", state.backgroundLocation,
            GuidanceSetupAction.BACKGROUND_LOCATION,
            enabled = state.preciseLocation || state.approximateLocation,
            status = if (!state.preciseLocation && !state.approximateLocation) "현재 위치를 먼저 허용해 주세요" else null,
        )
        row(Icons.Default.AccessAlarm, "알림 시각 맞추기", "수업 시작·종료 시각에 맞춰 안내해요.", state.exactAlarms, GuidanceSetupAction.EXACT_ALARM)
        if (state.liveSupported) {
            row(
                Icons.Default.ViewDay, "실시간 알림", "잠금화면과 상단에서 안내를 확인해요.", state.liveAllowed && state.notifications,
                GuidanceSetupAction.LIVE, enabled = state.notifications,
                status = if (!state.notifications) "알림 받기를 먼저 허용해 주세요" else null,
            )
        }
    }
}

@Composable
private fun SetupRow(
    icon: ImageVector,
    title: String,
    description: String,
    completed: Boolean,
    action: GuidanceSetupAction,
    onAction: (GuidanceSetupAction) -> Unit,
    enabled: Boolean,
    status: String?,
) {
    val actionLabel = when (action) {
        GuidanceSetupAction.NOTIFICATIONS, GuidanceSetupAction.LOCATION -> "허용하기"
        else -> "설정 열기"
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            status?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (completed) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "켜져 있음",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        } else {
            TextButton(
                onClick = { onAction(action) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("guidance_setup_${action.name}")
                    .semantics { contentDescription = "$title $actionLabel" },
            ) { Text(actionLabel) }
        }
    }
}

private fun Context.hasSetupPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
