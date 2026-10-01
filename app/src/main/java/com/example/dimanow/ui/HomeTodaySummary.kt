package com.example.dimanow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaShapes
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

/**
 * A view of existing LMS deadlines; opening Home does not initiate an LMS sign-in.
 * The whole card opens the Courses tab (D-094(9)). Signed out with nothing cached, it shrinks to a
 * single-row prompt instead of a full card.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HomeTodaySummary(
    snapshot: LmsSnapshot,
    now: ZonedDateTime,
    onOpenCourses: () -> Unit,
    modifier: Modifier = Modifier,
    sessionState: LmsSessionState = LmsSessionState.SIGNED_OUT,
) {
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
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

    if (!hasCache && sessionState == LmsSessionState.SIGNED_OUT && stateMessage != null &&
        snapshot.syncState != LmsSyncState.ERROR && snapshot.syncState != LmsSyncState.SYNCING
    ) {
        HomeCompactPrompt(
            icon = Icons.Default.School,
            text = stateMessage,
            onClick = onOpenCourses,
            onClickLabel = COURSES_CLICK_LABEL,
            modifier = modifier.testTag("dashboard_learning_prompt"),
        )
        return
    }

    HomeSummaryCard(
        onClick = onOpenCourses,
        onClickLabel = COURSES_CLICK_LABEL,
        modifier = modifier.testTag("dashboard_learning_card"),
    ) {
        HomeCardHeader(icon = Icons.Default.School, title = "수업")
        if (overview.courses.isNotEmpty()) {
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                overview.courses.forEach { course ->
                    Surface(shape = DimaShapes.Badge, color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                        Text("${course.courseName} · ${course.totalCount}개", style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        overview.items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

/** Spoken action of the Home course card and its compact prompt. */
internal const val COURSES_CLICK_LABEL = "수업 보기"

/**
 * One tap pattern for Home summary cards (D-094(9)): the whole card is the touch target and
 * announces what it opens; there are no separate small arrow buttons inside.
 */
@Composable
internal fun HomeSummaryCard(
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val open = onClick
    ElevatedCard(
        onClick = open,
        // Outer semantics override the card's own click action, so this only labels the same action.
        modifier = modifier
            .fillMaxWidth()
            .semantics { labelClickAction(onClickLabel, open) },
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Names the click action for screen readers ("double-tap to <label>") without adding a second action. */
private fun SemanticsPropertyReceiver.labelClickAction(label: String, action: () -> Unit) {
    onClick(label = label) { action(); true }
}

/** Icon + title + optional trailing status, ending in a decorative chevron that marks the card as openable. */
@Composable
internal fun HomeCardHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** A single-row Home prompt used where a full card would only say that nothing is there yet (D-094(9)). */
@Composable
internal fun HomeCompactPrompt(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
) {
    val open = onClick
    Surface(
        onClick = open,
        modifier = modifier
            .fillMaxWidth()
            .semantics { labelClickAction(onClickLabel, open) },
        shape = DimaShapes.Tile,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
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
