package com.example.dimanow.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.school.SchoolReadCache

/**
 * 전체 (D-110), the last bottom-bar destination as in the Toss app: what the student opens now and then rather
 * than every day — the timetable (kept mainly for class alerts and the Now Bar), 기숙사, 장비 대여, the DIMA
 * portal and Settings. Each row opens a page with a Back action; Back returns here.
 */
@Composable
internal fun AllScreen(onOpen: (AppPage) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dorm by SchoolReadCache.dormPoints.collectAsStateWithLifecycle()
    ScreenScaffold(title = AppPage.ALL.title, modifier = modifier, listTag = "all_list") {
        item(key = "school_header") { SettingsSectionHeader("학교 생활") }
        item(key = "school") {
            DimaSegmentedList {
                AllRow(
                    icon = Icons.Default.Schedule,
                    title = "시간표",
                    supporting = "수업 알림과 Now Bar 안내에 쓰는 시간표예요.",
                    index = 0, count = 3, tag = "all_timetable",
                    onClick = { onOpen(AppPage.TIMETABLE) },
                )
                AllRow(
                    icon = Icons.Default.Apartment,
                    title = "기숙사",
                    supporting = dorm?.summary?.let { "상점 ${it.merit}점 · 벌점 ${it.demerit}점 · 외출·외박 신청" }
                        ?: "상벌점과 외출·외박 신청",
                    index = 1, count = 3, tag = "all_dorm",
                    onClick = { onOpen(AppPage.DORM) },
                )
                AllRow(
                    icon = Icons.Default.Videocam,
                    title = "장비 대여",
                    supporting = "DIMA C 대여 현황과 신청",
                    index = 2, count = 3, tag = "all_equipment",
                    onClick = { onOpen(AppPage.EQUIPMENT) },
                )
            }
        }
        item(key = "links_header") { SettingsSectionHeader("바로가기") }
        item(key = "links") {
            DimaSegmentedList {
                AllRow(
                    icon = Icons.Default.Language,
                    title = "DIMA 포털",
                    supporting = "브라우저에서 열어요.",
                    index = 0, count = 1, tag = "all_portal",
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
                    supporting = "알림, 위치, 데이터와 앱 정보",
                    index = 0, count = 1, tag = "all_settings",
                    onClick = { onOpen(AppPage.SETTINGS) },
                )
            }
        }
    }
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
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        // The row says where it goes, which SegmentedListItem has no parameter for.
        modifier = Modifier.testTag(tag).semantics { onClick(label = if (external) "브라우저에서 열기" else "열기") { onClick(); true } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Icon(
                if (external) Icons.AutoMirrored.Filled.OpenInNew else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        supportingContent = { Text(supporting) },
        colors = dimaSegmentedListColors(),
    ) {
        Text(title)
    }
}
