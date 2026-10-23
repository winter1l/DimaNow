package com.example.dimanow.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.dimanow.update.AppUpdateUiState

/**
 * 앱 정보 (D-119): updates and 이 앱에 대해 with its links, moved out of 설정 into their own page under 전체,
 * opened with a Back action like the other 전체 pages. 문의·오류 제보 has its own row in 전체.
 */
@Composable
internal fun AppInfoScreen(
    updateState: AppUpdateUiState,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onContinueInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        title = AppPage.APP_INFO.title,
        modifier = modifier,
        navigationIcon = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack, modifier = Modifier.testTag("close_app_info")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        itemSpacing = DimaLayout.sectionGap,
        listTag = "app_info_list",
    ) {
        item(key = "app_update") {
            AppUpdateCard(
                state = updateState,
                onCheck = onCheckUpdate,
                onDownload = onDownloadUpdate,
                onContinueInstall = onContinueInstall,
                onCancelDownload = onCancelDownload,
            )
        }
        item(key = "about") { AboutCard() }
    }
}
