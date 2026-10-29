package com.example.dimanow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.LocalContentColor
import com.example.dimanow.ui.motion.AnimatedCountText
import com.example.dimanow.ui.motion.dimaContentReveal
import com.example.dimanow.ui.motion.pressScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.lms.LmsCompletionState
import com.example.dimanow.lms.LmsItem
import com.example.dimanow.lms.LmsItemKind
import com.example.dimanow.lms.LmsSessionState
import com.example.dimanow.lms.LmsSnapshot
import com.example.dimanow.lms.LmsSyncState
import com.example.dimanow.theme.DimaTheme
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

    // Signed out with nothing cached, the card is a one-row prompt. D-137: signing in cross-fades the prompt into the
    // card; the prompt keeps its own line while it leaves.
    val compactPrompt = stateMessage.takeIf {
        !hasCache && sessionState == LmsSessionState.SIGNED_OUT &&
            snapshot.syncState != LmsSyncState.ERROR && snapshot.syncState != LmsSyncState.SYNCING
    }
    // D-137: under the fixed header the body cross-fades between the list, the empty line and a state message; the
    // phase never holds the time, so the D-2 -> D-1 labels change in place.
    val body = HomeLearningContent(
        phase = when {
            overview.courses.isNotEmpty() -> HomeLearningPhase.LIST
            hasCache && stateMessage == null && snapshot.syncState != LmsSyncState.SYNCING -> HomeLearningPhase.EMPTY
            else -> HomeLearningPhase.MESSAGE
        },
        overview = overview,
        message = stateMessage,
    )
    AnimatedContent(
        targetState = compactPrompt,
        contentKey = { it != null },
        transitionSpec = { dimaContentReveal() },
        label = "home_learning_form",
        modifier = modifier,
    ) { prompt ->
    if (prompt != null) {
        HomeCompactPrompt(
            icon = Icons.Default.School,
            text = prompt,
            onClick = onOpenCourses,
            onClickLabel = COURSES_CLICK_LABEL,
            modifier = Modifier.testTag("dashboard_learning_prompt"),
        )
    } else HomeSummaryCard(
        onClick = onOpenCourses,
        onClickLabel = COURSES_CLICK_LABEL,
        modifier = Modifier.testTag("dashboard_learning_card"),
    ) {
      // The body brings its own gap below the header, so a body with nothing to say (a sync with nothing ahead) leaves
      // the card at its header alone, as before.
      Column {
        HomeCardHeader(icon = Icons.Default.School, title = "수업")
        AnimatedContent(
            targetState = body,
            // A message replaced by another one fades as well; the list and the empty line change in place, and a
            // leaving body keeps its own rows.
            contentKey = { if (it.phase == HomeLearningPhase.MESSAGE) it.message else it.phase },
            transitionSpec = { dimaContentReveal() },
            label = "home_learning_body",
        ) { shown ->
        if (shown.phase == HomeLearningPhase.MESSAGE && shown.message == null) {
            Spacer(Modifier.fillMaxWidth())
            return@AnimatedContent
        }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (shown.phase) {
            HomeLearningPhase.LIST -> {
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shown.overview.courses.forEach { course -> HomeCourseCountChip(course) }
                }
                shown.overview.items.forEach { item ->
                    val dueAt = requireNotNull(item.dueAt)
                    // D-126(2): a far deadline (often a video or a download open for weeks) still shows, in a quieter line.
                    val near = homeTaskIsNear(dueAt, now.toInstant())
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            homeTaskDeadlineLabel(dueAt, now.toInstant()),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (near) androidx.compose.ui.graphics.Color.Unspecified else secondaryColor,
                        )
                    }
                }
            }
            HomeLearningPhase.EMPTY -> Text("다가오는 마감이 없어요", style = MaterialTheme.typography.bodyMedium, color = secondaryColor)
            HomeLearningPhase.MESSAGE -> Unit
        }
        shown.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
            )
        }
        }
        }
      }
    }
    }
}

/** What the Home 수업 card shows under its header (D-137). */
private enum class HomeLearningPhase { LIST, EMPTY, MESSAGE }

/** The Home 수업 card's body with the rows and message it shows, so a leaving body keeps them (D-137). */
private data class HomeLearningContent(val phase: HomeLearningPhase, val overview: HomeTaskOverview, val message: String?)

/**
 * One course's chip on the Home 수업 card: its name, then the count of open tasks, which rolls when it changes
 * (D-137). Screen readers and tests read it as the one phrase it was before (`영상제작 · 2개`).
 */
@Composable
private fun HomeCourseCountChip(course: HomeTaskCourse) {
    val content = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = DimaShapes.Badge, color = DimaTheme.colors.cardInner, contentColor = content) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clearAndSetSemantics { text = AnnotatedString("${course.courseName} · ${course.totalCount}개") },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${course.courseName} · ", style = MaterialTheme.typography.labelMedium)
            AnimatedCountText("${course.totalCount}개", style = MaterialTheme.typography.labelMedium, color = content)
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
    // D-121: the card gives slightly under the finger (the shared expressive press scale), so a whole-card tap is felt
    // as one target; it shares the card's own interaction source and is off under reduced motion.
    val interactionSource = remember { MutableInteractionSource() }
    ElevatedCard(
        onClick = open,
        interactionSource = interactionSource,
        // Outer semantics override the card's own click action, so this only labels the same action.
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .semantics { labelClickAction(onClickLabel, open) },
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/**
 * The same Home card surface when the card holds its own buttons (D-102): M3 cards are either one tap target
 * or a container of controls, never both, so this card itself does not respond to taps.
 */
@Composable
internal fun HomeContainerCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
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
    /** D-137: the title rolls when it changes (Home's 식단 card flipping between 학생식당 and 기숙사). */
    rollTitle: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DimaIconBadge(icon, size = 32.dp)
        if (rollTitle) {
            AnimatedCountText(title, style = MaterialTheme.typography.titleMedium, color = LocalContentColor.current, modifier = Modifier.weight(1f))
        } else {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
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
    // D-137: the same press scale as HomeSummaryCard (D-121(3)) on the prompt's own interaction source, so one tap
    // pattern gives one response on Home.
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = open,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .semantics { labelClickAction(onClickLabel, open) },
        shape = DimaShapes.Tile,
        color = DimaTheme.colors.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DimaIconBadge(icon, size = 32.dp)
            KoreanText(
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

/**
 * Count every upcoming task and show the three nearest, however far away (D-126(2)): videos and downloads are
 * often open for weeks, and a card that hid them looked empty while the Courses tab still listed work.
 */
internal fun upcomingHomeTasks(items: List<LmsItem>, now: Instant): HomeTaskOverview {
    val upcoming = openHomeTasks(items, now)
    val courses = upcoming.groupBy { it.courseId.ifBlank { it.courseName } }
        .values.map { HomeTaskCourse(it.first().courseName, it.size) }
    return HomeTaskOverview(courses, upcoming.take(3))
}

/** Open tasks due today in Korea, nearest first: the morning deadline notification's list (D-126(3)). */
internal fun todayHomeTasks(items: List<LmsItem>, now: Instant): List<LmsItem> {
    val today = now.atZone(HOME_TASK_ZONE).toLocalDate()
    return openHomeTasks(items, now).filter { requireNotNull(it.dueAt).atZone(HOME_TASK_ZONE).toLocalDate() == today }
}

/**
 * Unfinished work with a deadline still ahead. A 자료 counts only when LMS reports it as not yet opened, so a
 * download the student already has does not stay on Home until it closes.
 */
private fun openHomeTasks(items: List<LmsItem>, now: Instant): List<LmsItem> = items
    .filter { item ->
        val countsAsTask = when (item.kind) {
            in HOME_TASK_KINDS -> item.completionState != LmsCompletionState.COMPLETE
            LmsItemKind.MATERIAL -> item.completionState == LmsCompletionState.INCOMPLETE
            else -> false
        }
        countsAsTask && item.dueAt?.isBefore(now) == false
    }
    .sortedWith(compareBy<LmsItem> { it.dueAt }.thenBy { it.courseId }.thenBy { it.id })

/** Due within the next two Korean calendar days: shown at full strength with the exact time. */
internal fun homeTaskIsNear(dueAt: Instant, now: Instant): Boolean =
    !dueAt.atZone(HOME_TASK_ZONE).toLocalDate().isAfter(now.atZone(HOME_TASK_ZONE).toLocalDate().plusDays(2))

internal fun homeTaskDeadlineLabel(dueAt: Instant, now: Instant): String {
    val due = dueAt.atZone(HOME_TASK_ZONE)
    val days = ChronoUnit.DAYS.between(now.atZone(HOME_TASK_ZONE).toLocalDate(), due.toLocalDate())
    val countdown = if (days == 0L) "D-day" else "D-$days"
    val format = if (homeTaskIsNear(dueAt, now)) HOME_TASK_CHECKED_AT else HOME_TASK_FAR_DATE
    return "$countdown · ${due.format(format)} 마감"
}

private val HOME_TASK_ZONE = ZoneId.of("Asia/Seoul")
private val HOME_TASK_CHECKED_AT = DateTimeFormatter.ofPattern("M/d HH:mm")
private val HOME_TASK_FAR_DATE = DateTimeFormatter.ofPattern("M/d")
private val HOME_TASK_KINDS = setOf(
    LmsItemKind.ASSIGNMENT, LmsItemKind.CONTENT, LmsItemKind.QUESTION,
    LmsItemKind.DISCUSSION, LmsItemKind.TEAM_PROJECT, LmsItemKind.QUIZ, LmsItemKind.EXAM,
)
