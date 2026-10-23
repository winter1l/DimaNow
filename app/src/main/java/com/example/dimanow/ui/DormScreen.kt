package com.example.dimanow.ui

import androidx.compose.material.icons.filled.Forum
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.calendar.ServiceCalendarStore
import com.example.dimanow.dorm.DormHours
import com.example.dimanow.dorm.OutingWarning
import com.example.dimanow.dorm.RollCallRules
import com.example.dimanow.dorm.RollCallStatus
import com.example.dimanow.dorm.RollCallStore
import com.example.dimanow.school.DormPointEntry
import com.example.dimanow.school.DormPointSummary
import com.example.dimanow.school.INTRANET_DORM_POINTS_SCRIPT
import com.example.dimanow.school.INTRANET_DORM_POINTS_URL
import com.example.dimanow.school.LocalSchoolServiceBridge
import com.example.dimanow.school.SchoolReadCache
import com.example.dimanow.school.SchoolService
import com.example.dimanow.school.SchoolServiceResult
import com.example.dimanow.school.parseDormPoints
import com.example.dimanow.school.summarizeDormPoints
import com.example.dimanow.sync.ServiceCalendar
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.DimaTheme
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** The pages behind the 기숙사 first screen (D-113): each opens over it with Back. */
private enum class DormTopic(val title: String) {
    POINTS("상벌점"),
    FACILITIES("시설 이용 시간"),
    DELIVERY("택배"),
    MEALS("식권·식당"),
    RULES("생활 규칙과 입·퇴사"),
    LINKS("공지와 수칙"),
}

/**
 * 기숙사 (D-110–D-113), opened from 전체. The first screen keeps what a resident checks every day — 오늘 (점호 and
 * the front door), this term's 상벌점 at a glance, 외출·외박 신청 — and a short 생활 정보 list whose rows open their
 * own pages (points in full, opening hours, deliveries, meal tickets, rules, links), so details arrive one step at
 * a time. Reading KakaoTalk 점호 notices is offered as one button that explains itself in a dialog.
 */
@Composable
internal fun DormScreen(onBack: () -> Unit, onOpenMeals: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bridge = LocalSchoolServiceBridge.current
    val scope = rememberCoroutineScope()
    val saved by SchoolReadCache.dormPoints.collectAsStateWithLifecycle()
    val notices by RollCallStore.notices.collectAsStateWithLifecycle()
    val calendar by ServiceCalendarStore.calendar.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var kakaoAccess by remember { mutableStateOf(RollCallStore.accessGranted(context)) }
    var showKakaoDialog by rememberSaveable { mutableStateOf(false) }
    var topic by rememberSaveable { mutableStateOf<DormTopic?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        kakaoAccess = RollCallStore.accessGranted(context)
        // Turning notification access off also drops what was read (D-112).
        if (!kakaoAccess) RollCallStore.clear()
    }
    // The door, 점호 and opening times change through the day; a minute is fine enough.
    // The shared minute ticker: lifecycle-aware, and it follows clock and time-zone changes.
    val now = rememberMinuteNow()
    // Back from a detail page returns to the first screen before leaving 기숙사.
    BackHandler(enabled = topic != null) { topic = null }

    fun load() {
        val service = bridge ?: return
        if (loading) return
        loading = true
        error = null
        scope.launch {
            when (val result = service.read(SchoolService.INTRANET, INTRANET_DORM_POINTS_URL, INTRANET_DORM_POINTS_SCRIPT, "상벌점")) {
                is SchoolServiceResult.Success -> runCatching { summarizeDormPoints(parseDormPoints(result.json)) }
                    .onSuccess { SchoolReadCache.setDormPoints(SchoolReadCache.DormPointsRead(it, Instant.now())) }
                    .onFailure { error = "상벌점 표를 읽지 못했어요." }
                is SchoolServiceResult.Failure -> error = result.message
                SchoolServiceResult.Cancelled -> Unit
            }
            loading = false
        }
    }

    val today = now.toLocalDate()
    val rollCall = todayRollCall(today, notices, calendar)
    val announcedApplyBy = (rollCall as? RollCallStatus.Announced)?.notice?.takeIf { it.held }?.applyBy
    // D-119: past the 점호 deadline or the overnight cut-off, ask before opening the application page (shared with Home).
    val outing = rememberOutingLauncher(rollCall)
    fun requestOuting() = outing.request()
    val read = saved
    val summary = read?.summary
    val shown = topic

    ScreenScaffold(
        title = shown?.title ?: AppPage.DORM.title,
        modifier = modifier,
        navigationIcon = {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = { if (shown != null) topic = null else onBack() },
                modifier = Modifier.testTag(if (shown != null) "close_dorm_topic" else "close_dorm"),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        listTag = if (shown != null) "dorm_topic_list" else "dorm_list",
        // The points are what refreshes: on the first screen and on the 상벌점 page.
        onRefresh = if (bridge != null && (shown == null || shown == DormTopic.POINTS)) ::load else null,
        refreshing = loading && saved != null,
        refreshLabel = "상벌점 새로고침",
    ) {
        when (shown) {
            null -> {
                item(key = "today") {
                    DormTodayCard(
                        rollCall = rollCall,
                        time = now.toLocalTime(),
                        kakaoAccess = kakaoAccess,
                        onAskKakao = { showKakaoDialog = true },
                    )
                }
                item(key = "points") {
                    when {
                        read == null && loading -> LoadingLine("인트라넷에서 상벌점을 불러오는 중이에요")
                        read == null -> HomeContainerCard(Modifier.testTag("dorm_points_card")) {
                            HomeCardHeader(icon = Icons.Default.Apartment, title = "상벌점", showChevron = false)
                            Text(
                                "인트라넷의 상벌점을 불러와 이 기기에 저장해 둬요.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (bridge != null) {
                                FilledTonalButton(
                                    shapes = ButtonDefaults.shapes(),
                                    onClick = ::load,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("dorm_points_load"),
                                ) { Text("상벌점 불러오기") }
                            }
                        }
                        else -> PointsSummaryCard(summary, read.readAt) { topic = DormTopic.POINTS }
                    }
                }
                error?.let { message -> item(key = "points_error") { ErrorState(message, onRetry = if (bridge != null) ::load else null) } }
                item(key = "outing") {
                    DimaSegmentedList {
                        SegmentedListItem(
                            onClick = ::requestOuting,
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                            modifier = Modifier.testTag("dorm_outing_open").semantics { onClick(label = "열기") { requestOuting(); true } },
                            leadingContent = { DimaIconBadge(Icons.Default.Luggage) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            supportingContent = {
                                Text(
                                    announcedApplyBy?.let { "오늘은 ${it.format(CLOCK)}까지 신청할 수 있어요." }
                                        ?: "점호가 있는 날은 21:00 전까지 신청해요.",
                                )
                            },
                            colors = dimaSegmentedListColors(),
                        ) {
                            Text("외출·외박 신청")
                        }
                    }
                }
                outing.error?.let { message -> item(key = "outing_error") { ErrorState(message) } }
                item(key = "info_header") { SettingsSectionHeader("생활 정보") }
                item(key = "info") {
                    val openNow = DormGuide.facilities.count { facility ->
                        facility.opens == null || facility.closes == null || DormHours.isOpen(now.toLocalTime(), facility.opens, facility.closes)
                    }
                    val topics = listOf(
                        Triple(DormTopic.FACILITIES, Icons.Default.LocalLaundryService, "지금 ${openNow}곳 이용 가능 · 세탁실·휴게실·편의점"),
                        Triple(DormTopic.DELIVERY, Icons.Default.Inventory2, "09:00~02:00 · 받는 주소 복사"),
                        Triple(DormTopic.MEALS, Icons.Default.Restaurant, "하루 2장 · 한 끼에 1장"),
                        Triple(DormTopic.RULES, Icons.AutoMirrored.Filled.Rule, "금지 사항, 퇴사 일정과 보증금"),
                        Triple(DormTopic.LINKS, Icons.Default.Groups, "자치회 인스타그램, 예인관 수칙"),
                    )
                    DimaSegmentedList(Modifier.testTag("dorm_info")) {
                        topics.forEachIndexed { index, (item, icon, supporting) ->
                            SegmentedListItem(
                                onClick = { topic = item },
                                shapes = ListItemDefaults.segmentedShapes(index = index, count = topics.size),
                                modifier = Modifier.testTag("dorm_topic_${item.name}").semantics { onClick(label = "열기") { topic = item; true } },
                                leadingContent = { DimaIconBadge(icon) },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                supportingContent = { Text(supporting) },
                                colors = dimaSegmentedListColors(),
                            ) {
                                Text(item.title)
                            }
                        }
                    }
                }
            }
            DormTopic.POINTS -> {
                when {
                    read == null && loading -> item(key = "points_loading") { LoadingLine("인트라넷에서 상벌점을 불러오는 중이에요") }
                    read != null -> {
                        item(key = "points_totals") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                summary?.let { termLabel(it) }?.let { SettingsSectionHeader("$it 상벌점") }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("dorm_points_total")) {
                                    PointTotal("상점", summary?.merit ?: 0, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                    PointTotal("벌점", summary?.demerit ?: 0, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                                }
                                Text(
                                    dormPointsReadAtLabel(read.readAt, Instant.now()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp).testTag("dorm_points_read_at"),
                                )
                            }
                        }
                        val entries = summary?.entries.orEmpty()
                        item(key = "entries_header") { SettingsSectionHeader("이번 학기 내역") }
                        if (entries.isEmpty()) {
                            item(key = "entries_empty") { EmptyState("이번 학기에 기록된 상벌점이 없어요") }
                        } else {
                            item(key = "entries") {
                                DimaSegmentedList(Modifier.testTag("dorm_points_entries")) {
                                    entries.forEachIndexed { index, entry -> PointEntryRow(entry, index, entries.size) }
                                }
                            }
                        }
                    }
                }
                error?.let { message -> item(key = "points_error") { ErrorState(message, onRetry = if (bridge != null) ::load else null) } }
                item(key = "point_rules_header") { SettingsSectionHeader("벌점과 상점") }
                item(key = "point_rules") {
                    GuideCard {
                        Text(
                            "벌점이 20점 쌓이면 즉시 퇴사예요. 상점으로 상쇄할 수 있고, 벌점이 있으면 다음 학기 선발에 불리해요.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        DormGuide.pointRules.forEach { (points, what) -> GuideLine(marker = "벌점 $points", text = what) }
                        Text("상점 받는 법", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                        DormGuide.meritWays.forEach { GuideLine(text = it) }
                    }
                }
                item(key = "source") { SourceLine() }
            }
            DormTopic.FACILITIES -> {
                item(key = "facilities") {
                    DimaSegmentedList(Modifier.testTag("dorm_facilities")) {
                        DormGuide.facilities.forEachIndexed { index, facility ->
                            val open = facility.opens == null || facility.closes == null ||
                                DormHours.isOpen(now.toLocalTime(), facility.opens, facility.closes)
                            HoursRow(facility.name, facility.detail, open, index, DormGuide.facilities.size)
                        }
                    }
                }
                item(key = "microwaves") {
                    Text(
                        DormGuide.MICROWAVES,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                item(key = "source") { SourceLine() }
            }
            DormTopic.DELIVERY -> {
                item(key = "delivery") {
                    val open = DormHours.isOpen(now.toLocalTime(), DormGuide.DELIVERY_OPENS, DormGuide.DELIVERY_CLOSES)
                    GuideCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("운영실 택배 업무", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            OpenBadge(open)
                        }
                        Text(DormGuide.DELIVERY_HOURS, style = MaterialTheme.typography.bodyMedium)
                        DormGuide.delivery.forEach { GuideLine(text = it) }
                    }
                }
                item(key = "address") {
                    GuideCard {
                        Text("받는 주소", style = MaterialTheme.typography.titleSmall)
                        Text("${DormGuide.ADDRESS}(호수) (이름)", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = { copyAddress(context) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("dorm_copy_address"),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Text("주소 복사", modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                        }
                    }
                }
                item(key = "source") { SourceLine() }
            }
            DormTopic.MEALS -> {
                item(key = "meal") {
                    GuideCard {
                        DormGuide.mealTickets.forEach { GuideLine(text = it) }
                        FilledTonalButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = onOpenMeals,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("dorm_open_meals"),
                        ) {
                            Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Text("기숙사 식단 보기", modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                        }
                    }
                }
                item(key = "source") { SourceLine() }
            }
            DormTopic.RULES -> {
                item(key = "living_header") { SettingsSectionHeader("생활 규칙") }
                item(key = "living") { GuideCard { DormGuide.livingRules.forEach { GuideLine(text = it) } } }
                item(key = "move_header") { SettingsSectionHeader("입·퇴사") }
                item(key = "move") { GuideCard { DormGuide.moveRules.forEach { GuideLine(text = it) } } }
                item(key = "outing_rules_header") { SettingsSectionHeader("외출·외박") }
                item(key = "outing_rules") { GuideCard { DormGuide.outingRules.forEach { GuideLine(text = it) } } }
                item(key = "source") { SourceLine() }
            }
            DormTopic.LINKS -> {
                item(key = "links") {
                    DimaSegmentedList {
                        LinkRow(Icons.Default.Groups, "예인관 자치회 인스타그램", "@dima_dorm", 0, 2, "dorm_instagram") {
                            openUrl(context, DormGuide.INSTAGRAM_URL)
                        }
                        LinkRow(Icons.AutoMirrored.Filled.MenuBook, "예인관 수칙 안내", "학교 홈페이지", 1, 2, "dorm_rules_page") {
                            openUrl(context, DormGuide.RULES_URL)
                        }
                    }
                }
                item(key = "source") { SourceLine() }
            }
        }
    }

    if (showKakaoDialog) {
        // One explanation, one action that opens Android's notification-access page; back or a tap outside closes it.
        AlertDialog(
            icon = { DimaDialogIcon(Icons.Default.Forum, DialogTone.INFO) },
            onDismissRequest = { showKakaoDialog = false },
            title = { Text("카톡 공지로 점호 확인하기") },
            text = {
                Text(
                    "카카오톡 알림 중 점호 공지 문구가 있는 알림만 읽어 오늘 점호가 있는지 알려 드려요. " +
                        "날짜와 점호 여부만 이 기기에 2주 동안 보관해요. 자치회 공지방 알림이 꺼져 있으면 읽을 수 없어요.",
                )
            },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        showKakaoDialog = false
                        RollCallStore.openAccessSettings(context)
                    },
                    modifier = Modifier.testTag("dorm_kakao_open_settings"),
                ) { Text("설정 열기") }
            },
            modifier = Modifier.testTag("dorm_kakao_dialog"),
        )
    }
}

/**
 * The warning before 외출·외박 신청 (D-119, styled by D-120): an amber clock, a title that says what happened, one
 * sentence on why the application may not count, and a small time line from the deadline to now (and, at night, to
 * when the front door opens). `신청 화면 열기` still opens the page; `닫기` keeps the student here.
 */
@Composable
internal fun OutingWarningDialog(warning: OutingWarning, now: LocalTime, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val nowText = now.format(CLOCK)
    val (title, text, points) = when (warning) {
        is OutingWarning.AfterRollCallDeadline -> Triple(
            "점호 신청 시간이 지났어요",
            "점호가 있는 날은 ${warning.deadlineText} 신청해야 해요. 지금 신청하면 반영되지 않을 수 있어요.",
            listOf("신청 마감" to warning.deadlineText.substringBefore(' ').removeSuffix("까지"), "지금" to nowText),
        )
        OutingWarning.OvernightClosed -> Triple(
            "외박 신청 시간이 지났어요",
            "외박은 전날 23:59까지 신청해야 해요. 마감이 지나 신청하면 신청 날짜가 자동으로 다음 날로 넘어가요.",
            listOf("신청 마감" to "23:59", "지금" to nowText, "출입문 열림" to DormHours.DOOR_OPENS.format(CLOCK)),
        )
    }
    DimaAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Default.Schedule,
        tone = DialogTone.WARNING,
        title = title,
        confirmLabel = "신청 화면 열기",
        onConfirm = onOpen,
        dismissLabel = "닫기",
        confirmTag = "dorm_outing_warning_open",
        dismissTag = "dorm_outing_warning_close",
        modifier = Modifier.testTag("dorm_outing_warning"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text)
            DialogTimeline(points = points, highlight = 1, tone = DialogTone.WARNING)
        }
    }
}

/** This term's points at a glance; the whole card opens the 상벌점 page. */
@Composable
private fun PointsSummaryCard(summary: DormPointSummary?, readAt: Instant, onOpen: () -> Unit) {
    val merit = summary?.merit ?: 0
    val demerit = summary?.demerit ?: 0
    HomeSummaryCard(
        onClick = onOpen,
        onClickLabel = "상벌점 자세히 보기",
        modifier = Modifier.testTag("dorm_points_card"),
    ) {
        HomeCardHeader(icon = Icons.Default.Apartment, title = summary?.let { termLabel(it) }?.let { "$it 상벌점" } ?: "상벌점")
        // D-122: two number tiles read faster than one sentence; the line stays for screen readers.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("dorm_points_total").semantics(mergeDescendants = true) {},
        ) {
            PointTile("상점", merit, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f))
            PointTile(
                "벌점",
                demerit,
                if (demerit > 0) DimaTheme.colors.warningContainer else DimaTheme.colors.cardInner,
                if (demerit > 0) DimaTheme.colors.onWarningContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                Modifier.weight(1f),
            )
        }
        Text(
            dormPointsReadAtLabel(readAt, Instant.now()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("dorm_points_read_at"),
        )
    }
}

@Composable
private fun PointTile(label: String, points: Int, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = DimaShapes.Tile, color = container, contentColor = content) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text("${points}점", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun SourceLine() {
    Text(
        DormGuide.SOURCE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val RECEIVED = DateTimeFormatter.ofPattern("HH:mm")

internal fun rollCallText(status: RollCallStatus, zone: ZoneId = DormHours.CAMPUS_ZONE): Pair<String, String?> = when (status) {
    is RollCallStatus.Announced -> {
        val notice = status.notice
        val received = "카톡 공지 ${notice.receivedAt.atZone(zone).format(RECEIVED)}"
        if (notice.held) {
            val at = (notice.time ?: RollCallRules.USUAL_TIME).format(CLOCK)
            val applyBy = (notice.applyBy ?: RollCallRules.USUAL_APPLY_BY).let { by ->
                if (notice.applyBy != null) "외출·외박 신청은 ${by.format(CLOCK)}까지" else "외출·외박 신청은 ${by.format(CLOCK)} 전까지"
            }
            "오늘 $at 점호 · 문을 열고 기다려요" to "$applyBy · $received"
        } else {
            "오늘은 점호가 없어요" to listOfNotNull(notice.reason, received).joinToString(" · ")
        }
    }
    is RollCallStatus.Scheduled ->
        "오늘 ${status.time.format(CLOCK)} 점호 예정" to buildString {
            append("외출·외박 신청은 21:00 전까지")
            status.mayBeSkipped?.let { append(" · ${it} 기간이라 없을 수 있어요(카톡 공지 확인)") }
        }
    is RollCallStatus.Holiday -> "오늘은 점호가 없어요" to "${status.name} · 공휴일에는 점호를 하지 않아요"
    is RollCallStatus.NotToday -> "오늘은 점호가 없어요" to "다음 점호는 ${koreanDateLabel(status.next)} 23:00 예정이에요"
}

private fun copyAddress(context: Context) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("예인관 주소", DormGuide.ADDRESS))
    Toast.makeText(context, "주소를 복사했어요. 호수와 이름을 붙여 주세요.", Toast.LENGTH_SHORT).show()
}

@Composable
private fun GuideCard(content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun GuideLine(text: String, marker: String = "•") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            marker,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = if (marker.length > 1) 48.dp else 12.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun OpenBadge(open: Boolean) {
    Surface(
        shape = DimaShapes.Badge,
        color = if (open) MaterialTheme.colorScheme.primaryContainer else DimaTheme.colors.cardInner,
    ) {
        Text(
            if (open) "지금 이용 가능" else "지금은 닫힘",
            style = MaterialTheme.typography.labelMedium,
            color = if (open) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun HoursRow(name: String, detail: String, open: Boolean, index: Int, count: Int) {
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape,
        color = DimaTheme.colors.card,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "$name, $detail, ${if (open) "지금 이용 가능" else "지금은 닫힘"}"
        },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OpenBadge(open)
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, supporting: String, index: Int, count: Int, tag: String, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = Modifier.testTag(tag).semantics { onClick(label = "브라우저에서 열기") { onClick(); true } },
        leadingContent = { DimaIconBadge(icon) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp)) },
        supportingContent = { Text(supporting) },
        colors = dimaSegmentedListColors(),
    ) {
        Text(title)
    }
}

private fun termLabel(summary: DormPointSummary): String? =
    listOfNotNull(summary.year?.let { "${it}년" }, summary.term?.let { "${it}학기" }).joinToString(" ").ifBlank { null }

@Composable
private fun PointTotal(label: String, points: Int, accent: Color, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label ${points}점" },
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${points}점", style = MaterialTheme.typography.headlineMedium, color = accent)
        }
    }
}

@Composable
private fun PointEntryRow(entry: DormPointEntry, index: Int, count: Int) {
    val points = buildList {
        if (entry.merit > 0) add("상점 ${entry.merit}")
        if (entry.demerit > 0) add("벌점 ${entry.demerit}")
    }.joinToString(" · ")
    val date = entry.date?.let { koreanDateLabel(it) }
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape,
        color = DimaTheme.colors.card,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(date, entry.content.ifBlank { null }, points.ifBlank { null }).joinToString(", ")
        },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.content.ifBlank { "내용 없음" }, style = MaterialTheme.typography.bodyLarge)
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

private val READ_CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** When the saved 상벌점 were read: `오늘 09:12 기준`, `어제 21:40 기준`, `10월 20일 (화) 18:05 기준`. */
internal fun dormPointsReadAtLabel(readAt: Instant, now: Instant, zone: ZoneId = ZoneId.of("Asia/Seoul")): String {
    val at = readAt.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val day = when (at.toLocalDate()) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> koreanDateLabel(at.toLocalDate(), today)
    }
    return "$day ${at.format(READ_CLOCK)} 기준"
}
