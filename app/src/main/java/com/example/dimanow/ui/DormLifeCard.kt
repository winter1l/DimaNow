package com.example.dimanow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.school.DormPointSummary
import com.example.dimanow.school.INTRANET_DORM_OUTING_URL
import com.example.dimanow.school.INTRANET_DORM_POINTS_SCRIPT
import com.example.dimanow.school.INTRANET_DORM_POINTS_URL
import com.example.dimanow.school.LocalSchoolServiceBridge
import com.example.dimanow.school.SchoolReadCache
import com.example.dimanow.school.SchoolService
import com.example.dimanow.school.SchoolServiceResult
import com.example.dimanow.school.parseDormPoints
import com.example.dimanow.school.summarizeDormPoints
import com.example.dimanow.theme.DimaShapes
import kotlinx.coroutines.launch

/**
 * 예인관 생활 (D-107), under the dormitory meals: this term's merit and demerit points read from the intranet
 * when the student asks, and the intranet's outing application, which stays on the school's own page.
 */
@Composable
internal fun DormLifeCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bridge = LocalSchoolServiceBridge.current
    val scope = rememberCoroutineScope()
    val read by SchoolReadCache.dormPoints.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        val service = bridge ?: return
        loading = true
        error = null
        scope.launch {
            when (val result = service.read(SchoolService.INTRANET, INTRANET_DORM_POINTS_URL, INTRANET_DORM_POINTS_SCRIPT, "상벌점")) {
                is SchoolServiceResult.Success -> runCatching { summarizeDormPoints(parseDormPoints(result.json)) }
                    .onSuccess { SchoolReadCache.setDormPoints(SchoolReadCache.DormPointsRead(it)) }
                    .onFailure { error = "상벌점 표를 읽지 못했어요." }
                is SchoolServiceResult.Failure -> error = result.message
                SchoolServiceResult.Cancelled -> Unit
            }
            loading = false
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("dorm_life_card"),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeCardHeader(icon = Icons.Default.Apartment, title = "예인관 생활", showChevron = false)
            val summary = read?.summary
            when {
                loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    DimaLoadingIndicator(size = 32.dp)
                    Text("인트라넷에서 상벌점을 불러오는 중이에요", style = MaterialTheme.typography.bodyMedium)
                }
                summary != null -> DormPointSummaryView(summary)
                read != null -> Text("기록된 상벌점이 없어요.", style = MaterialTheme.typography.bodyMedium)
                else -> Text(
                    "인트라넷의 상벌점을 이 기기로 불러와요. 불러온 내용은 앱을 닫으면 지워져요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (bridge != null) {
                    FilledTonalButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = ::load,
                        enabled = !loading,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("dorm_points_load"),
                    ) { Text(if (read == null) "상벌점 불러오기" else "다시 불러오기") }
                }
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { openUrl(context, INTRANET_DORM_OUTING_URL) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("dorm_outing_open"),
                ) { Text("외출·외박 신청") }
            }
        }
    }
}

@Composable
private fun DormPointSummaryView(summary: DormPointSummary) {
    val termLabel = listOfNotNull(summary.year?.let { "${it}년" }, summary.term?.let { "${it}학기" }).joinToString(" ")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${termLabel.ifBlank { "이번 학기" }} · 상점 ${summary.merit}점 · 벌점 ${summary.demerit}점",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("dorm_points_total"),
        )
        summary.entries.take(5).forEach { entry ->
            val points = buildList {
                if (entry.merit > 0) add("상점 ${entry.merit}")
                if (entry.demerit > 0) add("벌점 ${entry.demerit}")
            }.joinToString(" · ")
            val date = entry.date?.let { koreanDateLabel(it) }
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(date, entry.content, points.ifBlank { null }).joinToString(", ")
                },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.content.ifBlank { "내용 없음" }, style = MaterialTheme.typography.bodyMedium)
                    date?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(
                    points,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (entry.demerit > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

