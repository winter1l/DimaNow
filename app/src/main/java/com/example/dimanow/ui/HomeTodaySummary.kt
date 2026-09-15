package com.example.dimanow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dimanow.lms.LmsCompletionState
import com.example.dimanow.lms.LmsItem
import com.example.dimanow.lms.LmsItemKind
import com.example.dimanow.lms.LmsSessionState
import com.example.dimanow.lms.LmsSnapshot
import com.example.dimanow.lms.LmsSyncState
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** A view of existing LMS deadlines; opening Home does not initiate an LMS sign-in. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HomeTodaySummary(
    snapshot: LmsSnapshot,
    now: ZonedDateTime,
    onOpenCourses: () -> Unit,
    modifier: Modifier = Modifier,
    sessionState: LmsSessionState = LmsSessionState.SIGNED_OUT,
) {
    val secondaryColor = LocalContentColor.current.copy(alpha = 0.8f)
    val overview = remember(snapshot.items, now) { upcomingHomeTasks(snapshot.items, now.toInstant()) }
    val hasCache = snapshot.lastSuccessAt != null || snapshot.items.isNotEmpty()
    val stateMessage = when {
        snapshot.syncState == LmsSyncState.SYNCING -> if (hasCache) null else "수업 정보를 확인하고 있어요"
        snapshot.syncState == LmsSyncState.ERROR ->
            "수업 정보를 불러오지 못했어요. 다시 시도해 주세요"
        sessionState in setOf(LmsSessionState.EXPIRED, LmsSessionState.CREDENTIALS_NEED_REVIEW, LmsSessionState.INTERACTIVE_AUTH_REQUIRED) ->
            "수업 탭에서 다시 로그인해 주세요"
        !hasCache -> if (sessionState == LmsSessionState.ACTIVE) "수업 탭에서 정보를 불러오면 마감을 확인할 수 있어요" else "수업 탭에서 로그인하면 마감을 확인할 수 있어요"
        else -> null
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text("수업", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onOpenCourses, modifier = Modifier.requiredSize(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "수업 보기", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (overview.courses.isNotEmpty()) {
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                overview.courses.forEach { course ->
                    Surface(shape = RoundedCornerShape(8.dp), color = LocalContentColor.current.copy(alpha = 0.08f), contentColor = LocalContentColor.current) {
                        Text("${course.courseName} · ${course.totalCount}개", style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                }
            }
        }
        overview.items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    homeTaskDeadlineLabel(requireNotNull(item.dueAt), now.toInstant()),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if (overview.courses.isEmpty() && hasCache && stateMessage == null && snapshot.syncState != LmsSyncState.SYNCING) {
            Text("다가오는 마감이 없어요", style = MaterialTheme.typography.bodyMedium, color = secondaryColor)
        }
        stateMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
            )
        }
    }
}

internal data class HomeTaskCourse(val courseName: String, val totalCount: Int)
internal data class HomeTaskOverview(val courses: List<HomeTaskCourse>, val items: List<LmsItem>)

/** Count every upcoming task; only show titles due within the next two Korean calendar days. */
internal fun upcomingHomeTasks(items: List<LmsItem>, now: Instant): HomeTaskOverview {
    val upcoming = items
        .filter { it.kind in HOME_TASK_KINDS && it.completionState != LmsCompletionState.COMPLETE && it.dueAt?.isBefore(now) == false }
        .sortedWith(compareBy<LmsItem> { it.dueAt }.thenBy { it.courseId }.thenBy { it.id })
    val courses = upcoming.groupBy { it.courseId.ifBlank { it.courseName } }
        .values.map { HomeTaskCourse(it.first().courseName, it.size) }
    val lastVisibleDate = now.atZone(HOME_TASK_ZONE).toLocalDate().plusDays(2)
    val urgentItems = upcoming.filter { !requireNotNull(it.dueAt).atZone(HOME_TASK_ZONE).toLocalDate().isAfter(lastVisibleDate) }
    return HomeTaskOverview(courses, urgentItems.take(3))
}

internal fun homeTaskDeadlineLabel(dueAt: Instant, now: Instant): String {
    val due = dueAt.atZone(HOME_TASK_ZONE)
    val days = ChronoUnit.DAYS.between(now.atZone(HOME_TASK_ZONE).toLocalDate(), due.toLocalDate())
    val countdown = if (days == 0L) "D-day" else "D-$days"
    return "$countdown · ${due.format(HOME_TASK_CHECKED_AT)} 마감"
}

private val HOME_TASK_ZONE = ZoneId.of("Asia/Seoul")
private val HOME_TASK_CHECKED_AT = DateTimeFormatter.ofPattern("M/d HH:mm")
private val HOME_TASK_KINDS = setOf(
    LmsItemKind.ASSIGNMENT, LmsItemKind.CONTENT, LmsItemKind.QUESTION,
    LmsItemKind.DISCUSSION, LmsItemKind.TEAM_PROJECT, LmsItemKind.QUIZ, LmsItemKind.EXAM,
)
