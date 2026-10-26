package com.example.dimanow.ui

import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.school.SchoolReadCache
import com.example.dimanow.update.AppUpdatePhase
import com.example.dimanow.update.AppUpdateUiState

/**
 * 전체 (D-110), the last bottom-bar destination as in the Toss app: what the student opens now and then rather
 * than every day — the timetable (kept mainly for class alerts and the Now Bar), 기숙사, 장비 대여, the DIMA
 * portal and Settings. Each row opens a page with a Back action; Back returns here. The foot of the list says the
 * app is unofficial and which version runs, with the update action when one is waiting (D-122).
 */
@Composable
internal fun AllScreen(
    onOpen: (AppPage) -> Unit,
    modifier: Modifier = Modifier,
    updateState: AppUpdateUiState? = null,
    onDownloadUpdate: () -> Unit = {},
    onContinueInstall: () -> Unit = {},
) {
    val context = LocalContext.current
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    val dorm by SchoolReadCache.dormPoints.collectAsStateWithLifecycle()
    ScreenScaffold(title = AppPage.ALL.title, modifier = modifier, listTag = "all_list") {
        item(key = "school_header") { SettingsSectionHeader("학교 생활") }
        item(key = "school") {
            DimaSegmentedList {
                AllRow(
                    icon = Icons.Default.Schedule,
                    title = "시간표",
                    supporting = "수업 알림과 Now Bar 안내에 쓰는 시간표예요.",
                    index = 0, count = 5, tag = "all_timetable",
                    onClick = { onOpen(AppPage.TIMETABLE) },
                )
                AllRow(
                    icon = Icons.Default.Apartment,
                    title = "기숙사",
                    supporting = dorm?.summary?.let { "상점 ${it.merit}점 · 벌점 ${it.demerit}점 · 외출·외박 신청" }
                        ?: "상벌점과 외출·외박 신청",
                    index = 1, count = 5, tag = "all_dorm",
                    onClick = { onOpen(AppPage.DORM) },
                )
                AllRow(
                    icon = Icons.Default.Videocam,
                    title = "장비 대여",
                    supporting = "DIMA C 대여 현황과 신청",
                    index = 2, count = 5, tag = "all_equipment",
                    onClick = { onOpen(AppPage.EQUIPMENT) },
                )
                // D-131: 도서관 (대출·반납일, 이용 시간) and the campus map
                AllRow(
                    icon = Icons.Default.LocalLibrary,
                    title = "도서관",
                    supporting = "대출·반납일과 이용 시간",
                    index = 3, count = 5, tag = "all_library",
                    onClick = { onOpen(AppPage.LIBRARY) },
                )
                AllRow(
                    icon = Icons.Default.Map,
                    title = "캠퍼스 지도",
                    supporting = "셔틀·4402 정류장과 건물 위치",
                    index = 4, count = 5, tag = "all_map",
                    onClick = { onOpen(AppPage.MAP) },
                )
            }
        }
        item(key = "links_header") { SettingsSectionHeader("바로가기") }
        item(key = "links") {
            DimaSegmentedList {
                // D-126(6): the library gate takes the library app's 모바일 열람증; 전자출결 works only in 헤이영.
                AllRow(
                    icon = Icons.Default.Badge,
                    title = "모바일 열람증",
                    supporting = "도서관 앱을 열어요. 열람증 탭을 눌러 게이트에 대요.",
                    index = 0, count = 3, tag = "all_library_card",
                    external = true, externalLabel = "도서관 앱 열기",
                    onClick = { openApp(context, LIBRARY_APP_PACKAGE) },
                )
                AllRow(
                    icon = Icons.Default.EventAvailable,
                    title = "헤이영 전자출결",
                    supporting = "헤이영 앱을 열어요.",
                    index = 1, count = 3, tag = "all_heyoung",
                    external = true, externalLabel = "헤이영 열기",
                    onClick = { openApp(context, HEYOUNG_APP_PACKAGE) },
                )
                AllRow(
                    icon = Icons.Default.Language,
                    title = "DIMA 포털",
                    supporting = "브라우저에서 열어요.",
                    index = 2, count = 3, tag = "all_portal",
                    external = true,
                    onClick = { openUrl(context, "https://portal.dima.ac.kr/") },
                )
            }
        }
        item(key = "app_header") { SettingsSectionHeader("앱") }
        item(key = "app") {
            DimaSegmentedList {
                AllRow(
                    icon = Icons.Default.Settings,
                    title = "설정",
                    supporting = "알림, 위치, 계정, 업데이트와 앱 정보",
                    index = 0, count = 2, tag = "all_settings",
                    onClick = { onOpen(AppPage.SETTINGS) },
                )
                // D-119: 문의·오류 제보 has its own row below 설정 (앱 정보 went back into 설정, D-122).
                AllRow(
                    icon = Icons.Default.BugReport,
                    title = "문의·오류 제보",
                    supporting = "앱에서 바로 보내면 공개 GitHub 이슈로 올라가요",
                    index = 1, count = 2, tag = "all_feedback",
                    onClick = { showFeedback = true },
                )
            }
        }
        item(key = "footer") {
            AllFooter(updateState, onDownloadUpdate, onContinueInstall)
        }
    }
    if (showFeedback) FeedbackDialog(onDismiss = { showFeedback = false })
}

@Composable
private fun AllRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    index: Int,
    count: Int,
    tag: String,
    onClick: () -> Unit,
    external: Boolean = false,
    externalLabel: String = "브라우저에서 열기",
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        // The row says where it goes, which SegmentedListItem has no parameter for.
        modifier = Modifier.testTag(tag).semantics { onClick(label = if (external) externalLabel else "열기") { onClick(); true } },
        leadingContent = { DimaIconBadge(icon) },
        trailingContent = {
            Icon(
                if (external) Icons.AutoMirrored.Filled.OpenInNew else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        supportingContent = { KoreanText(supporting) },
        colors = dimaSegmentedListColors(),
    ) {
        Text(title)
    }
}

/**
 * What the foot of 전체 says about updates, and which action its button runs (D-122). D-134: the app checks every
 * day by itself, so there is no check button; `최신 버전이에요` appears only once a check said so, and a newer
 * version brings its button.
 */
internal data class AllFooterUpdate(val text: String, val action: Action? = null, val actionLabel: String? = null) {
    enum class Action { DOWNLOAD, CONTINUE_INSTALL }
}

internal fun allFooterUpdate(state: AppUpdateUiState): AllFooterUpdate {
    val version = "버전 ${state.currentVersion}"
    val latest = state.latestRelease?.versionName
    return when (state.phase) {
        AppUpdatePhase.IDLE, AppUpdatePhase.ERROR, AppUpdatePhase.CHECKING -> AllFooterUpdate(version)
        AppUpdatePhase.UP_TO_DATE -> AllFooterUpdate("$version · 최신 버전이에요")
        AppUpdatePhase.AVAILABLE -> AllFooterUpdate(
            "$version · ${latest ?: "새"} 버전이 나왔어요",
            AllFooterUpdate.Action.DOWNLOAD,
            "업데이트 받기",
        )
        AppUpdatePhase.DOWNLOADING -> AllFooterUpdate(
            "$version · 새 버전을 받고 있어요${state.downloadProgress?.let { " ($it%)" }.orEmpty()}",
        )
        AppUpdatePhase.READY_TO_INSTALL, AppUpdatePhase.PERMISSION_REQUIRED -> AllFooterUpdate(
            "$version · ${latest ?: "새"} 버전을 설치할 수 있어요",
            AllFooterUpdate.Action.CONTINUE_INSTALL,
            "설치하기",
        )
        AppUpdatePhase.INSTALLER_OPENED -> AllFooterUpdate("$version · 설치 화면을 열었어요")
    }
}

/** The unofficial notice and the running version, quiet and centred at the end of 전체 (D-122). */
@Composable
private fun AllFooter(
    updateState: AppUpdateUiState?,
    onDownloadUpdate: () -> Unit,
    onContinueInstall: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp).testTag("all_footer"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            UNOFFICIAL_NOTICE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (updateState != null) {
            val footer = allFooterUpdate(updateState)
            Text(
                footer.text,
                style = MaterialTheme.typography.labelMedium,
                color = if (footer.action == AllFooterUpdate.Action.DOWNLOAD || footer.action == AllFooterUpdate.Action.CONTINUE_INSTALL) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("all_version"),
            )
            val run = when (footer.action) {
                AllFooterUpdate.Action.DOWNLOAD -> onDownloadUpdate
                AllFooterUpdate.Action.CONTINUE_INSTALL -> onContinueInstall
                null -> null
            }
            if (run != null && footer.actionLabel != null) {
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = run, modifier = Modifier.testTag("all_update_action")) {
                    Text(footer.actionLabel)
                }
            }
        }
    }
}
