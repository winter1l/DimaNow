package com.example.dimanow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dimanow.theme.DimaShapes

/** Public pages and channels shown in 앱 정보 (D-099 clause 5). */
internal object AppLinks {
    const val PRIVACY_POLICY = "https://winter1l.github.io/DimaNow/privacy.html"
    const val DOWNLOAD_PAGE = "https://winter1l.github.io/DimaNow/"
    const val SOURCE_CODE = "https://github.com/winter1l/DimaNow"
    const val REPORT_PROBLEM = "https://github.com/winter1l/DimaNow/issues/new/choose"
}

internal const val UNOFFICIAL_NOTICE =
    "DIMA Now는 학생이 만든 비공식 앱이에요. 동아방송예술대학교가 만들거나 운영하지 않아요."

internal const val DATA_ACCURACY_NOTICE =
    "셔틀·식단·공지는 학교 공개 자료를 옮긴 것이고, 식단은 AI가 사진을 읽거나 학생이 올린 사진으로 만들어져 틀릴 수 있어요. " +
        "중요한 일정은 학교 공지와 공식 시간표로 꼭 확인해 주세요."

/**
 * 앱 정보 card: unofficial notice, accuracy note, then the links as list rows (D-102): one row per
 * destination with a leading icon, a supporting line where it helps, and a trailing mark that says whether
 * it opens the browser or a page inside the app (M3 lists, Toss ListRow).
 */
@Composable
internal fun AboutCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("about_card"),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("이 앱에 대해", style = MaterialTheme.typography.titleMedium)
                Text(UNOFFICIAL_NOTICE, style = MaterialTheme.typography.bodySmall)
                Text(DATA_ACCURACY_NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                AboutLinkRow(
                    icon = Icons.Default.PrivacyTip, title = "개인정보 처리방침",
                    onClick = { openUrl(context, AppLinks.PRIVACY_POLICY) }, tag = "open_privacy_policy",
                )
                AboutLinkRow(
                    icon = Icons.Default.BugReport, title = "문의·오류 제보",
                    supporting = "공개 GitHub 이슈로 남아요. 시간표·위치·학번 같은 개인정보는 적지 말아 주세요.",
                    onClick = { openUrl(context, AppLinks.REPORT_PROBLEM) }, tag = "open_report_problem",
                )
                AboutLinkRow(
                    icon = Icons.Default.Description, title = "오픈소스 라이선스",
                    onClick = { showLicenses = true }, tag = "open_licenses", external = false,
                )
                AboutLinkRow(
                    icon = Icons.Default.Code, title = "소스 코드",
                    supporting = "Apache License 2.0",
                    onClick = { openUrl(context, AppLinks.SOURCE_CODE) }, tag = "open_source_code",
                )
            }
        }
    }
    if (showLicenses) OpenSourceLicensesDialog(onDismiss = { showLicenses = false })
}

@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    tag: String,
    supporting: String? = null,
    external: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = if (external) "브라우저에서 열기" else "열기", role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            if (external) Icons.AutoMirrored.Filled.OpenInNew else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

private data class LicensedComponent(val name: String, val owner: String, val license: String)

private val LICENSED_COMPONENTS = listOf(
    LicensedComponent("AndroidX · Jetpack Compose · Material 3 · Room · WorkManager · DataStore", "The Android Open Source Project", "Apache License 2.0"),
    LicensedComponent("Kotlin · kotlinx.coroutines · kotlinx.serialization", "JetBrains s.r.o.", "Apache License 2.0"),
    LicensedComponent("Guava (ListenableFuture)", "The Guava Authors", "Apache License 2.0"),
    LicensedComponent("Okio", "Square, Inc.", "Apache License 2.0"),
    LicensedComponent("JSpecify", "The JSpecify Authors", "Apache License 2.0"),
    LicensedComponent("jsoup", "Jonathan Hedley", "MIT License"),
    LicensedComponent("Google Play services (위치)", "Google LLC", "Android Software Development Kit License"),
    LicensedComponent("캠퍼스 구역 경계 데이터", "© OpenStreetMap contributors", "Open Database License (ODbL) 1.0"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val texts = remember {
        listOf("licenses/apache-2.0.txt", "licenses/mit-jsoup.txt").associateWith { path ->
            runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("")
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("licenses_screen"),
            topBar = {
                TopAppBar(
                    title = { Text("오픈소스 라이선스") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("DIMA Now에 포함된 구성 요소예요.", style = MaterialTheme.typography.bodyMedium) }
                items(LICENSED_COMPONENTS) { component ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(component.name, style = MaterialTheme.typography.titleSmall)
                        Text("${component.owner} · ${component.license}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                texts.forEach { (path, text) ->
                    item(key = path) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider()
                            SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
