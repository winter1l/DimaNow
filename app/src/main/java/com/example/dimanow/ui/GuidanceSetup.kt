package com.example.dimanow.ui

import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
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
            icon = { DimaDialogIcon(Icons.Default.LocationOn, DialogTone.INFO) },
            onDismissRequest = { controller.backgroundExplanation = false },
            title = { Text("앱을 닫아도 셔틀 안내를 받으려면") },
            text = { Text("예인관·본관·원룸촌·4402 정류장에 들어섰는지 알려면 위치를 '$optionLabel'으로 허용해야 해요. 다음 화면에서 권한 → 위치를 눌러 바꿔 주세요. 위치는 이 기기 안에서만 쓰고 저장하거나 보내지 않아요.") },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { controller.backgroundExplanation = false; controller.openAppSettings() }) { Text("설정 열기") }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { controller.backgroundExplanation = false }) { Text("나중에") } },
        )
    }
}

/**
 * One segmented list row per setup item (D-104): icon, what it does in one sentence, and either a check mark
 * or the action that turns it on. The button names what happens (D-102): `허용하기` asks Android,
 * `설정 열기` opens a system page.
 */
@Composable
internal fun GuidanceSetupContent(
    state: GuidanceSetupState,
    onAction: (GuidanceSetupAction) -> Unit,
    modifier: Modifier = Modifier,
    onlyIncomplete: Boolean = false,
) {
    val items = buildList {
        add(SetupItem(Icons.Default.Notifications, "알림 받기", "수업 시작과 셔틀 출발을 알려 줘요.", state.notifications, GuidanceSetupAction.NOTIFICATIONS))
        add(
            SetupItem(
                Icons.Default.LocationOn, "현재 위치 사용", "가까운 정류장의 셔틀을 안내해요. 위치는 이 기기 안에서만 쓰고 어디에도 보내지 않아요.",
                state.preciseLocation, GuidanceSetupAction.LOCATION,
                status = if (!state.preciseLocation && state.approximateLocation) "대략적 위치 허용됨 · 정확한 위치가 필요해요" else null,
            ),
        )
        add(
            SetupItem(
                Icons.Default.MyLocation, "앱을 닫아도 위치 확인", "이동에 맞춰 자동 안내를 바꿔요.", state.backgroundLocation,
                GuidanceSetupAction.BACKGROUND_LOCATION,
                enabled = state.preciseLocation || state.approximateLocation,
                status = if (!state.preciseLocation && !state.approximateLocation) "현재 위치를 먼저 허용해 주세요" else null,
            ),
        )
        add(SetupItem(Icons.Default.AccessAlarm, "알림 시각 맞추기", "수업 시작·종료 시각에 맞춰 안내해요.", state.exactAlarms, GuidanceSetupAction.EXACT_ALARM))
        if (state.liveSupported) {
            add(
                SetupItem(
                    Icons.Default.ViewDay, "실시간 알림", "잠금화면과 상단에서 안내를 확인해요.", state.liveAllowed && state.notifications,
                    GuidanceSetupAction.LIVE, enabled = state.notifications,
                    status = if (!state.notifications) "알림 받기를 먼저 허용해 주세요" else null,
                ),
            )
        }
    }.filterNot { onlyIncomplete && it.completed }
    DimaSegmentedList(modifier) {
        items.forEachIndexed { index, item -> SetupRow(item, index, items.size, onAction) }
    }
}

private class SetupItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val completed: Boolean,
    val action: GuidanceSetupAction,
    val enabled: Boolean = true,
    val status: String? = null,
)

@Composable
private fun SetupRow(item: SetupItem, index: Int, count: Int, onAction: (GuidanceSetupAction) -> Unit) {
    val actionLabel = when (item.action) {
        GuidanceSetupAction.NOTIFICATIONS, GuidanceSetupAction.LOCATION -> "허용하기"
        else -> "설정 열기"
    }
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        leadingContent = { DimaIconBadge(item.icon) },
        trailingContent = {
            if (item.completed) {
                Icon(Icons.Default.CheckCircle, contentDescription = "켜져 있음", tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { onAction(item.action) },
                    enabled = item.enabled,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("guidance_setup_${item.action.name}")
                        .semantics { contentDescription = "${item.title} $actionLabel" },
                ) { Text(actionLabel) }
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.description)
                item.status?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            }
        },
        colors = dimaSegmentedListColors(),
    ) {
        Text(item.title)
    }
}

private fun Context.hasSetupPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
