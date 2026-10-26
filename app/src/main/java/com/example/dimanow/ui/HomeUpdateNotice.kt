package com.example.dimanow.ui

import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.update.AppUpdatePhase
import com.example.dimanow.update.AppUpdateUiState

/**
 * Home shows an update only while it can be acted on: a newer version that was not put off, or a download
 * or install in progress. A failed background check never appears here; Settings keeps that detail.
 */
internal fun AppUpdateUiState.showsHomeNotice(): Boolean =
    promptVersion != null || phase == AppUpdatePhase.DOWNLOADING ||
        phase == AppUpdatePhase.READY_TO_INSTALL || phase == AppUpdatePhase.PERMISSION_REQUIRED

/** D-134: a version not put off with 나중에 that can still be fetched or installed asks once at launch. */
internal fun launchesUpdatePrompt(state: AppUpdateUiState): Boolean =
    state.promptVersion != null && state.phase in setOf(
        AppUpdatePhase.AVAILABLE,
        AppUpdatePhase.READY_TO_INSTALL,
        AppUpdatePhase.PERMISSION_REQUIRED,
    )

/**
 * D-134: the launch pop-up for a new version (the student asked for it, superseding D-102's card-only offer). It says
 * which version, then 업데이트 (download and install, or install what is already downloaded) or 나중에.
 */
@Composable
internal fun UpdateLaunchPrompt(
    state: AppUpdateUiState,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onClose: () -> Unit,
) {
    val version = state.promptVersion ?: state.latestRelease?.versionName.orEmpty()
    DimaAlertDialog(
        onDismissRequest = onClose,
        icon = Icons.Default.SystemUpdate,
        tone = DialogTone.INFO,
        title = "새 버전이 나왔어요",
        text = {
            Text(
                if (state.preparedUpdate != null) {
                    "받아 둔 DIMA Now $version 버전을 설치할 수 있어요."
                } else {
                    "DIMA Now $version 버전으로 업데이트할 수 있어요. 받는 동안에도 앱을 쓸 수 있어요."
                },
            )
        },
        confirmLabel = if (state.preparedUpdate != null) "설치하기" else "업데이트",
        onConfirm = onUpdate,
        dismissLabel = "나중에",
        onDismiss = onLater,
        confirmTag = "update_prompt_confirm",
        dismissTag = "update_prompt_later",
        modifier = Modifier.testTag("update_prompt"),
    )
}

/**
 * The new-version card at the top of Home (D-102). It replaces the dialog that opened over the app at launch:
 * Apple HIG and the Toss guide both rule out interrupting people on entry, so the offer waits in the feed,
 * with 나중에 putting this version off the same way the dialog did.
 */
@Composable
internal fun HomeUpdateNotice(
    state: AppUpdateUiState,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onCancel: () -> Unit,
    onContinueInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("home_update_notice"),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(
                    text = when (state.phase) {
                        AppUpdatePhase.DOWNLOADING -> "새 버전을 내려받고 있어요 · ${state.downloadProgress ?: 0}%"
                        AppUpdatePhase.READY_TO_INSTALL, AppUpdatePhase.PERMISSION_REQUIRED -> "새 버전을 설치할 준비가 됐어요"
                        else -> "DIMA Now ${state.promptVersion ?: state.latestRelease?.versionName.orEmpty()} 버전이 나왔어요"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (state.phase == AppUpdatePhase.PERMISSION_REQUIRED) {
                Text("설치하려면 이 앱의 설치 허용을 켜 주세요.", style = MaterialTheme.typography.bodySmall)
            }
            if (state.phase == AppUpdatePhase.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { (state.downloadProgress ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                when (state.phase) {
                    AppUpdatePhase.DOWNLOADING -> TextButton(shapes = ButtonDefaults.shapes(), onClick = onCancel) { Text("취소") }
                    AppUpdatePhase.READY_TO_INSTALL, AppUpdatePhase.PERMISSION_REQUIRED ->
                        Button(shapes = ButtonDefaults.shapes(), onClick = onContinueInstall, modifier = Modifier.testTag("home_update_continue")) { Text("설치 계속") }
                    else -> {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = onLater, modifier = Modifier.testTag("home_update_later")) { Text("나중에") }
                        FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onInstall, modifier = Modifier.testTag("home_update_install")) { Text("업데이트하기") }
                    }
                }
            }
        }
    }
}
