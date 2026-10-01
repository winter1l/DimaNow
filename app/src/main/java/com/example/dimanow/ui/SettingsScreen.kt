package com.example.dimanow.ui

import android.provider.Settings
import com.example.dimanow.theme.DimaShapes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.DefaultCampusZones
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.live.LiveChipContent
import com.example.dimanow.live.LiveClassOrder
import com.example.dimanow.live.LiveDisplayOptions
import com.example.dimanow.live.LiveSurfaceController
import com.example.dimanow.live.GuidanceKind
import com.example.dimanow.live.NotificationGuidanceMode
import com.example.dimanow.live.NotificationGuidancePolicy
import com.example.dimanow.meal.MealData
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.location.LocationMode
import com.example.dimanow.update.AppUpdatePhase
import com.example.dimanow.update.AppUpdateUiState
import com.example.dimanow.lms.CredentialState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    locationMode: LocationMode,
    testZone: CampusZoneId,
    onTestModeChange: (Boolean) -> Unit,
    onTestZone: (CampusZoneId) -> Unit,
    testTransitStopNumber: String?,
    onTestTransitStop: (String?) -> Unit,
    liveSurfaceController: LiveSurfaceController,
    displayOptions: LiveDisplayOptions,
    onChipContentChange: (LiveChipContent) -> Unit,
    onClassOrderChange: (LiveClassOrder) -> Unit,
    notificationPolicy: NotificationGuidancePolicy,
    onNotificationModeChange: (GuidanceKind, NotificationGuidanceMode) -> Unit,
    homeBase: HomeBase,
    onHomeBaseChange: (HomeBase) -> Unit,
    shuttleData: ShuttleData?,
    mealData: MealData?,
    onShowNowBarSetup: () -> Unit,
    updateState: AppUpdateUiState,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onContinueInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    lmsCredentialState: CredentialState,
    onDeleteLmsAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDeleteLmsAccount by remember { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    var setupExpanded by rememberSaveable { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as? com.example.dimanow.DimaNowApplication

    ScreenScaffold(
        title = "설정",
        modifier = modifier,
        // 설정은 하단 내비가 없는 하위 화면이라 닫기 대신 앞쪽 뒤로 버튼을 둔다.
        // 뒤로는 설정을 연 탭으로 돌아간다 (D-035, D-043, D-094)
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("close_settings")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        itemSpacing = DimaLayout.sectionGap,
        listTag = "settings_list",
    ) {
        // 1) 귀가 기준지 — 가장 자주 바꾸는 개인 설정을 최상단에
        item(key = "home_base") {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = DimaShapes.Card,
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("귀가 방향", style = MaterialTheme.typography.titleMedium)
                    Text("수업이 끝난 뒤 안내할 셔틀 방향이에요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 설정의 단일 선택은 모두 같은 SettingsChoice를 쓴다 (D-094(6))
                    SettingsChoice(
                        options = listOf(HomeBase.YEIN to "엔터관 방향", HomeBase.ONE_ROOM to "원룸촌 방향"),
                        selected = homeBase,
                        onSelect = onHomeBaseChange,
                        itemTag = { "home_base_${it.name}" },
                    )
                }
            }
        }

        item(key = "alerts_header") { Text("알림과 위치", style = MaterialTheme.typography.titleMedium) }
        item(key = "live_display") {
            LiveDisplaySettings(
                options = displayOptions,
                onChipContentChange = onChipContentChange,
                onClassOrderChange = onClassOrderChange,
            )
        }
        item(key = "notification_guidance") {
            NotificationGuidanceSettings(
                policy = notificationPolicy,
                onModeChange = onNotificationModeChange,
            )
        }
        item(key = "guidance_setup_toggle") {
            SettingsExpander(
                title = "자동 안내 설정",
                supporting = "위치·알림 권한과 기기 설정",
                expanded = setupExpanded,
                onToggle = { setupExpanded = !setupExpanded },
                modifier = Modifier.testTag("open_guidance_setup"),
            )
        }
        if (setupExpanded) item(key = "guidance_setup") {
            GuidanceSetup(liveSurfaceController = liveSurfaceController, onPermissionsChanged = { app?.refreshGuidancePermissions() })
        }

        item(key = "lms_account") {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = DimaShapes.Card,
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("수업 계정", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (lmsCredentialState == CredentialState.SAVED) "자동 로그인 사용 중" else "연결된 계정이 없어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (lmsCredentialState == CredentialState.SAVED) {
                        OutlinedButton(onClick = { confirmDeleteLmsAccount = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("저장된 계정 삭제")
                        }
                    }
                }
            }
        }
        item(key = "app_info_header") { Text("앱 정보", style = MaterialTheme.typography.titleMedium) }
        item(key = "app_update") {
            AppUpdateCard(
                state = updateState,
                onCheck = onCheckUpdate,
                onDownload = onDownloadUpdate,
                onContinueInstall = onContinueInstall,
                onCancelDownload = onCancelDownload,
            )
        }
        item(key = "diagnostics_toggle") {
            SettingsExpander(
                title = "고급 및 진단",
                supporting = "기기별 알림 도움말, 위치 테스트, 데이터 원문",
                expanded = diagnosticsExpanded,
                onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                modifier = Modifier.testTag("toggle_diagnostics"),
            )
        }
        if (diagnosticsExpanded) {
            item(key = "nowbar_help") {
                TextButton(onClick = onShowNowBarSetup) { Text("기기별 알림 도움말") }
            }
            // 3) GPS 비반영 테스트 모드 — 네 구역을 가로 스크롤 없이 모두 보여 준다 (D-094(6))
            item(key = "location_test") {
                LocationTestCard(
                    testMode = locationMode == LocationMode.TEST,
                    testZone = testZone,
                    onTestModeChange = onTestModeChange,
                    onTestZone = onTestZone,
                    testTransitStopNumber = testTransitStopNumber,
                    onTestTransitStop = onTestTransitStop,
                )
            }

            item(key = "data_sources") { DataAndSourcesCard(shuttleData, mealData) }
        }
    }
    if (confirmDeleteLmsAccount) {
        AlertDialog(
            onDismissRequest = { confirmDeleteLmsAccount = false },
            title = { Text("저장된 계정을 삭제할까요?") },
            text = { Text("저장된 계정과 이 기기의 수업 정보가 삭제돼요.") },
            confirmButton = {
                // 되돌릴 수 없는 삭제는 error 색으로 구분한다 (D-094(6))
                Button(
                    onClick = { confirmDeleteLmsAccount = false; onDeleteLmsAccount() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.testTag("confirm_delete_lms_account"),
                ) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteLmsAccount = false }) { Text("취소") } },
        )
    }
}

@Composable
internal fun NowBarSetupDialog(
    onOpenLockScreenNotifications: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onComplete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("나우바 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("'잠긴 상태에서 알림 내용 표시' 옵션을 항상 표시로 변경해주세요.")
                OutlinedButton(onClick = onOpenLockScreenNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text("잠금화면 알림 설정 열기")
                }
                Text("개발자 옵션에서 ‘모든 앱의 실시간 정보 보기’를 켜세요.")
                OutlinedButton(onClick = onOpenDeveloperOptions, modifier = Modifier.fillMaxWidth()) {
                    Text("개발자 옵션 열기")
                }
            }
        },
        confirmButton = {
            Button(onClick = onComplete, modifier = Modifier.testTag("nowbar_setup_complete")) { Text("완료") }
        },
    )
}

@Composable
internal fun AppUpdateCard(
    state: AppUpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onContinueInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("앱 업데이트", style = MaterialTheme.typography.titleMedium)
            Text("현재 버전 ${state.currentVersion.ifBlank { "확인 중" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val statusText = when (state.phase) {
                AppUpdatePhase.IDLE -> "업데이트를 아직 확인하지 않았어요"
                AppUpdatePhase.CHECKING -> "업데이트 확인 중"
                AppUpdatePhase.UP_TO_DATE -> "최신 버전이에요"
                AppUpdatePhase.AVAILABLE -> "새 버전 ${state.latestRelease?.versionName}"
                AppUpdatePhase.DOWNLOADING -> "다운로드 중 ${state.downloadProgress ?: 0}%"
                AppUpdatePhase.READY_TO_INSTALL -> "설치 준비 완료"
                AppUpdatePhase.PERMISSION_REQUIRED -> "설치 권한이 필요해요"
                AppUpdatePhase.INSTALLER_OPENED -> "Android 설치 화면을 확인해 주세요"
                AppUpdatePhase.ERROR -> state.message ?: "업데이트하지 못했어요. 잠시 후 다시 시도해 주세요"
            }
            Text(
                statusText,
                style = MaterialTheme.typography.titleSmall,
                color = if (state.phase == AppUpdatePhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            state.message?.takeIf { it != statusText }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.phase == AppUpdatePhase.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { (state.downloadProgress ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.phase) {
                    AppUpdatePhase.AVAILABLE -> Button(onClick = onDownload) { Text("다운로드 및 설치") }
                    AppUpdatePhase.DOWNLOADING -> OutlinedButton(onClick = onCancelDownload) { Text("취소") }
                    AppUpdatePhase.READY_TO_INSTALL, AppUpdatePhase.PERMISSION_REQUIRED -> Button(onClick = onContinueInstall) { Text("설치 계속") }
                    else -> OutlinedButton(enabled = state.phase != AppUpdatePhase.CHECKING, onClick = onCheck) { Text("업데이트 확인") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.phase == AppUpdatePhase.AVAILABLE || state.phase == AppUpdatePhase.READY_TO_INSTALL || state.phase == AppUpdatePhase.PERMISSION_REQUIRED) {
                        OutlinedButton(onClick = onCheck) { Text("업데이트 확인") }
                    }
                    state.latestRelease?.let { release ->
                        OutlinedButton(onClick = { openUrl(context, release.releasePageUrl) }) { Text("릴리스 보기") }
                    }
                }
            }
        }
    }
}

/**
 * Settings > 고급 및 진단 data card. This is the only place raw sync errors appear, labelled as
 * diagnostic detail (D-094(14)); `null` data shows loading until the cache emits (D-094(2)).
 */
@Composable
internal fun DataAndSourcesCard(
    shuttleData: ShuttleData?,
    mealData: MealData?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("data_sources_card"),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("데이터 및 원문", style = MaterialTheme.typography.titleMedium)
            Text(
                "문제를 알릴 때 참고하는 진단 정보예요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("셔틀", style = MaterialTheme.typography.titleSmall)
            if (shuttleData == null) {
                LoadingLine("셔틀 동기화 기록을 불러오고 있어요", Modifier.testTag("data_sources_shuttle_loading"))
            } else {
                val shuttleSlots = shuttleData.departures.distinctBy {
                    listOf(it.serviceDay, it.originZone, it.destinationZone, it.time)
                }.size
                val fieldOverrideCount = shuttleData.departures.count { it.sourceRouteId == "A-field-extra" }
                val officialDepartureCount = shuttleData.departures.size - fieldOverrideCount
                Text("기기 동기화: ${shuttleData.lastSuccess?.let(::formatSourceSuccessTime) ?: "기록 없음"}", style = MaterialTheme.typography.bodySmall)
                shuttleData.serverPublishedAt?.let { Text("서버 게시: ${formatSourceSuccessTime(it)}", style = MaterialTheme.typography.bodySmall) }
                Text(
                    buildString {
                        append("공식 주간 시간표 ${officialDepartureCount}행")
                        if (fieldOverrideCount > 0) append(" · 현장 추가 ${fieldOverrideCount}행")
                        append(" · 사용자 출발 슬롯 ${shuttleSlots}개")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                shuttleData.error?.let { DiagnosticErrorLine(it) }
                OutlinedButton(onClick = { openUrl(context, shuttleData.sourceUrl) }) { Text("셔틀 원문") }
            }
            HorizontalDivider()
            Text("식단", style = MaterialTheme.typography.titleSmall)
            if (mealData == null) {
                LoadingLine("식단 동기화 기록을 불러오고 있어요", Modifier.testTag("data_sources_meal_loading"))
            } else {
                val mealWeeks = mealData.cachedWeeks.joinToString { week ->
                    "${week.weekStart.monthValue}/${week.weekStart.dayOfMonth}~${week.weekEnd.monthValue}/${week.weekEnd.dayOfMonth}"
                }.ifBlank { "검증된 주간 식단 없음" }
                Text("기기 동기화: ${mealData.lastSuccess?.let(::formatSourceSuccessTime) ?: "기록 없음"}", style = MaterialTheme.typography.bodySmall)
                mealData.serverPublishedAt?.let { Text("서버 게시: ${formatSourceSuccessTime(it)}", style = MaterialTheme.typography.bodySmall) }
                Text(mealWeeks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                mealData.error?.let { DiagnosticErrorLine(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openUrl(context, mealData.sourceUrl) }) { Text("식단 원문") }
                    mealData.sourceImageUrl?.let { imageUrl ->
                        OutlinedButton(onClick = { openUrl(context, imageUrl) }) { Text("식단 이미지") }
                    }
                }
            }
            HorizontalDivider()
            Text(
                "캠퍼스 구역 ${DefaultCampusZones.VERSION} · © OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A raw sync error, kept verbatim for troubleshooting and marked as diagnostic detail. */
@Composable
private fun DiagnosticErrorLine(detail: String) {
    Text(
        text = "진단 · 마지막 동기화 오류: $detail",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveDisplaySettings(
    options: LiveDisplayOptions,
    onChipContentChange: (LiveChipContent) -> Unit,
    onClassOrderChange: (LiveClassOrder) -> Unit,
    modifier: Modifier = Modifier,
    onShowNowBarSetup: (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("실시간 알림", style = MaterialTheme.typography.titleMedium)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("상단 알림 표시", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsChoice(
                    options = listOf(LiveChipContent.COUNTDOWN to "남은 시간", LiveChipContent.CLASSROOM to "강의실"),
                    selected = options.chipContent,
                    onSelect = onChipContentChange,
                    itemTag = { "live_chip_${it.name}" },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("잠금화면 첫 줄", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsChoice(
                    options = listOf(LiveClassOrder.COURSE_FIRST to "수업명 먼저", LiveClassOrder.CLASSROOM_FIRST to "강의실 먼저"),
                    selected = options.classOrder,
                    onSelect = onClassOrderChange,
                    itemTag = { "live_order_${it.name}" },
                )
            }

            onShowNowBarSetup?.let { onClick ->
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text("나우바 설정 안내")
                }
            }
        }
    }
}
