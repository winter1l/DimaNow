package com.example.dimanow.ui

import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.IconButtonDefaults
import android.provider.Settings
import com.example.dimanow.theme.DimaShapes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ProgressIndicatorDefaults
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
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.update.AppUpdatePhase
import com.example.dimanow.update.AppUpdateUiState
import com.example.dimanow.lms.CredentialState
import com.example.dimanow.ui.motion.dimaCollapseOut
import com.example.dimanow.ui.motion.dimaContentReveal
import com.example.dimanow.ui.motion.dimaContentSwap
import com.example.dimanow.ui.motion.dimaExpandIn

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
    academicReminders: Boolean = true,
    onAcademicRemindersChange: (Boolean) -> Unit = {},
    deadlineDigest: Boolean = true,
    onDeadlineDigestChange: (Boolean) -> Unit = {},
    outingReminders: Boolean = true,
    onOutingRemindersChange: (Boolean) -> Unit = {},
    noticeKeywordAlerts: Boolean = false,
    noticeKeywords: List<String> = emptyList(),
    onNoticeKeywordAlertsChange: (Boolean) -> Unit = {},
    onNoticeKeywordsChange: (List<String>) -> Unit = {},
    homeBase: HomeBase,
    onHomeBaseChange: (HomeBase) -> Unit,
    shuttleData: ShuttleData?,
    mealData: MealData?,
    onShowNowBarSetup: () -> Unit,
    lmsCredentialState: CredentialState,
    onDeleteLmsAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    updateState: AppUpdateUiState? = null,
    onCheckUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onContinueInstall: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
) {
    var confirmDeleteLmsAccount by remember { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as? com.example.dimanow.DimaNowApplication
    val guidanceSetup = rememberGuidanceSetupController(liveSurfaceController) { app?.refreshGuidancePermissions() }
    // D-102: 꺼진 권한이 있으면 펼친 채로 열어, 안내가 왜 안 오는지 바로 보이게 한다
    var setupExpanded by rememberSaveable { mutableStateOf(guidanceSetup.state.missingCount > 0) }

    ScreenScaffold(
        title = "설정",
        modifier = modifier,
        // 설정은 하단 내비가 없는 하위 화면이라 닫기 대신 앞쪽 뒤로 버튼을 둔다.
        // 뒤로는 설정을 연 탭으로 돌아간다 (D-035, D-043, D-094)
        navigationIcon = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack, modifier = Modifier.testTag("close_settings")) {
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
                colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("귀가 방향", style = MaterialTheme.typography.titleMedium)
                    Text("수업이 끝난 뒤 안내할 셔틀 방향이에요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 설정의 단일 선택은 모두 같은 SettingsChoice를 쓴다 (D-094(6))
                    SettingsChoice(
                        options = listOf(HomeBase.YEIN to "엔터관 방향", HomeBase.ONE_ROOM to "원룸촌 방향", HomeBase.COMMUTE to "통학"),
                        selected = homeBase,
                        onSelect = onHomeBaseChange,
                        itemTag = { "home_base_${it.name}" },
                    )
                }
            }
        }

        // D-102: 알림 묶음은 '안내가 오는가(권한) → 무엇을 받는가(종류) → 어떻게 보이는가(표시)' 순서다
        item(key = "alerts_header") { SettingsSectionHeader("알림과 위치") }
        // D-137: the checklist is part of its expander's item and grows in and out on the spring the chevron turns on
        // (D-118(4)), the gap above it inside the fold, instead of a list item that appeared at once.
        item(key = "guidance_setup_toggle") {
            Column {
                val missing = guidanceSetup.state.missingCount
                SettingsExpander(
                    title = "자동 안내 권한",
                    supporting = if (missing == 0) "모두 켜져 있어요" else "${missing}개가 꺼져 있어요 · 켜면 안내가 더 정확해져요",
                    supportingColor = if (missing == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    expanded = setupExpanded,
                    onToggle = { setupExpanded = !setupExpanded },
                    modifier = Modifier.testTag("open_guidance_setup"),
                )
                AnimatedVisibility(
                    visible = setupExpanded,
                    enter = dimaExpandIn(),
                    exit = dimaCollapseOut(),
                    label = "guidance_setup",
                ) {
                    GuidanceSetupChecklist(guidanceSetup, Modifier.padding(top = DimaLayout.sectionGap))
                }
            }
        }
        item(key = "notification_guidance") {
            NotificationGuidanceSettings(
                policy = notificationPolicy,
                onModeChange = onNotificationModeChange,
                academicReminders = academicReminders,
                onAcademicRemindersChange = onAcademicRemindersChange,
                deadlineDigest = deadlineDigest,
                onDeadlineDigestChange = onDeadlineDigestChange,
                outingReminders = outingReminders,
                onOutingRemindersChange = onOutingRemindersChange,
                noticeKeywordAlerts = noticeKeywordAlerts,
                noticeKeywords = noticeKeywords,
                onNoticeKeywordAlertsChange = onNoticeKeywordAlertsChange,
                onNoticeKeywordsChange = onNoticeKeywordsChange,
            )
        }
        item(key = "live_display") {
            LiveDisplaySettings(
                options = displayOptions,
                onChipContentChange = onChipContentChange,
                onClassOrderChange = onClassOrderChange,
            )
        }

        item(key = "account_header") { SettingsSectionHeader("계정과 데이터") }
        item(key = "lms_account") {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = DimaShapes.Card,
                colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("수업 계정", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (lmsCredentialState == CredentialState.SAVED) "자동 로그인 사용 중" else "연결된 계정이 없어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // D-099: 계정을 저장하지 않고 로그인한 경우에도 로그아웃과 수업 정보 삭제를 할 수 있다
                    OutlinedButton(shapes = ButtonDefaults.shapes(), 
                        onClick = { confirmDeleteLmsAccount = true },
                        modifier = Modifier.fillMaxWidth().testTag("delete_lms_account"),
                    ) {
                        Text(if (lmsCredentialState == CredentialState.SAVED) "저장된 계정 삭제" else "로그아웃하고 수업 정보 지우기")
                    }
                }
            }
        }
        item(key = "app_data") { AppDataCard() }
        // D-122: 앱 정보 is back in 설정 (D-119 had moved it to its own 전체 page); 전체 keeps 문의·오류 제보.
        item(key = "app_info_header") { SettingsSectionHeader("앱 정보") }
        if (updateState != null) {
            item(key = "app_update") {
                AppUpdateCard(
                    state = updateState,
                    onCheck = onCheckUpdate,
                    onDownload = onDownloadUpdate,
                    onContinueInstall = onContinueInstall,
                    onCancelDownload = onCancelDownload,
                )
            }
        }
        item(key = "about") { AboutCard() }
        // D-137: the same fold for 고급 및 진단; the page does not scroll by itself once it has opened.
        item(key = "diagnostics_toggle") {
            Column {
                SettingsExpander(
                    title = "고급 및 진단",
                    supporting = "기기별 알림 도움말, 위치 테스트, 데이터 원문",
                    expanded = diagnosticsExpanded,
                    onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                    modifier = Modifier.testTag("toggle_diagnostics"),
                )
                AnimatedVisibility(
                    visible = diagnosticsExpanded,
                    enter = dimaExpandIn(),
                    exit = dimaCollapseOut(),
                    label = "diagnostics",
                ) {
                    Column(
                        Modifier.padding(top = DimaLayout.sectionGap),
                        verticalArrangement = Arrangement.spacedBy(DimaLayout.sectionGap),
                    ) {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = onShowNowBarSetup) { Text("기기별 알림 도움말") }
                        // 3) GPS 비반영 테스트 모드 — 네 구역을 가로 스크롤 없이 모두 보여 준다 (D-094(6))
                        LocationTestCard(
                            testMode = locationMode == LocationMode.TEST,
                            testZone = testZone,
                            onTestModeChange = onTestModeChange,
                            onTestZone = onTestZone,
                            testTransitStopNumber = testTransitStopNumber,
                            onTestTransitStop = onTestTransitStop,
                        )
                        DataAndSourcesCard(shuttleData, mealData)
                    }
                }
            }
        }
    }
    if (confirmDeleteLmsAccount) {
        AlertDialog(
            icon = { DimaDialogIcon(Icons.Default.PersonRemove, DialogTone.DANGER) },
            onDismissRequest = { confirmDeleteLmsAccount = false },
            title = { Text(if (lmsCredentialState == CredentialState.SAVED) "저장된 계정을 삭제할까요?" else "로그아웃할까요?") },
            text = { Text("저장된 계정과 로그인 상태, 이 기기에 받아 둔 수업 정보를 지워요.") },
            confirmButton = {
                // 되돌릴 수 없는 삭제는 error 색 글자로 구분한다 (D-094(6)); 대화상자 동작은 텍스트 버튼이다 (D-104)
                TextButton(shapes = ButtonDefaults.shapes(), 
                    onClick = { confirmDeleteLmsAccount = false; onDeleteLmsAccount() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_lms_account"),
                ) { Text("삭제") }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDeleteLmsAccount = false }) { Text("취소") } },
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
        icon = { DimaDialogIcon(Icons.Default.NotificationsActive, DialogTone.INFO) },
        onDismissRequest = onComplete,
        title = { Text("나우바 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("잠금화면에서도 안내가 보이게 '알림 내용 표시'를 항상 표시로 바꿔 주세요.")
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onOpenLockScreenNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text("잠금화면 알림 설정 열기")
                }
                Text(
                    "(선택) Samsung 나우바에 띄우려면 개발자 옵션의 ‘모든 앱의 실시간 정보 보기’가 필요할 수 있어요. " +
                        "켜지 않아도 일반 알림으로 안내돼요. 일부 앱은 개발자 옵션이 켜져 있으면 실행되지 않을 수 있어요.",
                )
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onOpenDeveloperOptions, modifier = Modifier.fillMaxWidth()) {
                    Text("개발자 옵션 열기")
                }
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onComplete, modifier = Modifier.testTag("nowbar_setup_complete")) { Text("완료") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
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
    // D-137: read only in the bar's draw lambda; it glides to each new percent (reduced motion: it jumps).
    val shownProgress by animateFloatAsState(
        targetValue = (state.downloadProgress ?: 0) / 100f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "settings_update_progress",
    )
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
    ) {
        // D-137: the card moves with the update as Home's card does. The status line turns over when the phase changes
        // (never for a percent), the bar folds in and out with the gap above it, and the buttons cross-fade between
        // phase groups. The gaps live in the moving parts.
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("앱 업데이트", style = MaterialTheme.typography.titleMedium)
            Text("현재 버전 ${state.currentVersion.ifBlank { "확인 중" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedContent(
                targetState = state,
                contentKey = { it.phase },
                transitionSpec = { dimaContentSwap() },
                label = "settings_update_status",
            ) { shown ->
                val statusText = appUpdateStatusText(shown)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (shown.phase == AppUpdatePhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    shown.message?.takeIf { it != statusText }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column {
                AnimatedVisibility(
                    visible = state.phase == AppUpdatePhase.DOWNLOADING,
                    enter = dimaExpandIn(),
                    exit = dimaCollapseOut(),
                    label = "settings_update_progress_bar",
                ) {
                    // The flat bar: the wavy indicator's travelling wave would be a loop (D-121).
                    LinearProgressIndicator(
                        progress = { shownProgress },
                        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                    )
                }
                AnimatedContent(
                    targetState = state.phase.actionGroup(),
                    transitionSpec = { dimaContentReveal() },
                    contentAlignment = Alignment.TopStart,
                    label = "settings_update_actions",
                ) { group ->
                    // D-134: the action and 릴리스 보기 side by side, wrapping only when they do not fit.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (group) {
                            UpdateActionGroup.AVAILABLE -> Button(shapes = ButtonDefaults.shapes(), onClick = onDownload) { Text("다운로드 및 설치") }
                            UpdateActionGroup.DOWNLOADING -> OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onCancelDownload) { Text("취소") }
                            UpdateActionGroup.READY -> Button(shapes = ButtonDefaults.shapes(), onClick = onContinueInstall) { Text("설치 계속") }
                            UpdateActionGroup.CHECK -> OutlinedButton(
                                shapes = ButtonDefaults.shapes(),
                                enabled = state.phase != AppUpdatePhase.CHECKING,
                                onClick = onCheck,
                            ) { Text("업데이트 확인") }
                        }
                        if (group == UpdateActionGroup.AVAILABLE || group == UpdateActionGroup.READY) {
                            OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onCheck) { Text("업데이트 확인") }
                        }
                        state.latestRelease?.let { release ->
                            OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { openUrl(context, release.releasePageUrl) }) { Text("릴리스 보기") }
                        }
                    }
                }
            }
        }
    }
}

/** The update card's status line for [state] (D-134); a percent is part of the words, never of the phase. */
private fun appUpdateStatusText(state: AppUpdateUiState): String = when (state.phase) {
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

/** The Settings update card's button groups (D-137): its buttons cross-fade only between these. */
private enum class UpdateActionGroup { CHECK, AVAILABLE, DOWNLOADING, READY }

private fun AppUpdatePhase.actionGroup(): UpdateActionGroup = when (this) {
    AppUpdatePhase.AVAILABLE -> UpdateActionGroup.AVAILABLE
    AppUpdatePhase.DOWNLOADING -> UpdateActionGroup.DOWNLOADING
    AppUpdatePhase.READY_TO_INSTALL, AppUpdatePhase.PERMISSION_REQUIRED -> UpdateActionGroup.READY
    else -> UpdateActionGroup.CHECK
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
        colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
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
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { openUrl(context, shuttleData.sourceUrl) }) { Text("셔틀 원문") }
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
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { openUrl(context, mealData.sourceUrl) }) { Text("식단 원문") }
                    mealData.sourceImageUrl?.let { imageUrl ->
                        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { openUrl(context, imageUrl) }) { Text("식단 이미지") }
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
        colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
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
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text("나우바 설정 안내")
                }
            }
        }
    }
}
