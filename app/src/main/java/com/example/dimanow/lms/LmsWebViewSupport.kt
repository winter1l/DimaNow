package com.example.dimanow.lms

import android.webkit.WebResourceResponse
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.dimanow.ui.DimaTopAppBar
import java.io.ByteArrayInputStream

/**
 * 수업 탭의 전체화면 페인(공식 강의·글 상세·로그인·렌더링) 공용 셸 (D-056).
 *
 * 네 화면이 각자 복사해 쓰던 인셋·헤더 패딩·뒤로가기 아이콘을 한곳으로 모아
 * 같은 상단 문법과 창 인셋을 쓰게 한다.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun LmsFullScreenPane(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backDescription: String = "뒤로",
    titleMaxLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // 탭 화면과 같은 상단 바를 쓰되, 전체화면 페인은 설정 톱니 대신 뒤로 버튼만 둔다 (D-094)
        DimaTopAppBar(
            title = { Text(title, style = com.example.dimanow.theme.dimaPageTitleStyle(), maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDescription)
                }
            },
            actions = { trailing?.invoke() },
            showSettings = false,
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        content()
    }
}

internal fun shouldBlockLmsWebResource(url: String, loginFlow: Boolean): Boolean {
    val scheme = runCatching { java.net.URI.create(url).scheme?.lowercase() }.getOrNull()
    if (scheme !in setOf("http", "https")) return false
    return if (loginFlow) {
        !LmsUrlPolicy.isAllowedLoginNavigation(url)
    } else {
        !LmsUrlPolicy.isAllowed(url)
    }
}

internal fun blockedLmsWebResourceResponse(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    "UTF-8",
    403,
    "Blocked",
    mapOf("Cache-Control" to "no-store"),
    ByteArrayInputStream(ByteArray(0)),
)
