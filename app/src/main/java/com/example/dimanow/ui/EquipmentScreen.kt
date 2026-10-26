package com.example.dimanow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.school.DIMA_C_EQUIPMENT_CATALOG_SCRIPT
import com.example.dimanow.school.DIMA_C_EQUIPMENT_URL
import com.example.dimanow.school.EquipmentDay
import com.example.dimanow.school.EquipmentGroup
import com.example.dimanow.school.EquipmentModel
import com.example.dimanow.school.LocalSchoolServiceBridge
import com.example.dimanow.school.SchoolReadCache
import com.example.dimanow.school.SchoolService
import com.example.dimanow.school.SchoolServiceResult
import com.example.dimanow.school.dimaCModelAvailabilityScript
import com.example.dimanow.school.parseEquipmentAvailability
import com.example.dimanow.school.equipmentCatalogAutoReadAllowed
import com.example.dimanow.school.parseEquipmentCatalog
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.motion.dimaCollapseOut
import com.example.dimanow.ui.motion.dimaContentReveal
import com.example.dimanow.ui.motion.dimaExpandIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * 장비 대여 (D-107): DIMA C's general-rental equipment, read with the student's sign-in when the screen opens.
 * The rental notice sits on top as a short guide with the full text under 자세히 보기; below, each equipment
 * group is a segmented list whose models open to the next two weeks, weekday by weekday, with the units free
 * each day. Renting itself happens on DIMA C, shown signed in inside the app.
 */
@Composable
internal fun EquipmentScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bridge = LocalSchoolServiceBridge.current
    val scope = rememberCoroutineScope()
    val catalog by SchoolReadCache.equipmentCatalog.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(false) }
    // A failure from an earlier visit shows again instead of reading DIMA C once more right away (Q-031).
    var error by remember { mutableStateOf(SchoolReadCache.equipmentCatalogFailure.value?.message) }
    var applyError by remember { mutableStateOf<String?>(null) }
    var guideExpanded by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    val availability by SchoolReadCache.equipmentAvailability.collectAsStateWithLifecycle()
    val availabilityErrors = remember { mutableStateMapOf<Long, String>() }
    var loadingModel by remember { mutableStateOf<Long?>(null) }
    val today = LocalDate.now()

    fun loadCatalog() {
        val service = bridge ?: return
        if (loading) return
        loading = true
        error = null
        scope.launch {
            val result = service.read(SchoolService.DIMA_C, DIMA_C_EQUIPMENT_URL, DIMA_C_EQUIPMENT_CATALOG_SCRIPT, "장비 대여 현황")
            when (result) {
                is SchoolServiceResult.Success -> runCatching { parseEquipmentCatalog(result.json) }
                    .onSuccess { SchoolReadCache.setEquipmentCatalog(it) }
                    .onFailure { error = "장비 목록을 읽지 못했어요." }
                is SchoolServiceResult.Failure -> error = result.message
                SchoolServiceResult.Cancelled -> Unit
            }
            if (result !is SchoolServiceResult.Cancelled) {
                SchoolReadCache.setEquipmentCatalogFailure(error?.let { SchoolReadCache.EquipmentCatalogFailure(it, Instant.now()) })
            }
            loading = false
        }
    }

    fun loadModel(model: EquipmentModel) {
        val service = bridge ?: return
        if (loadingModel != null) return
        loadingModel = model.no
        availabilityErrors.remove(model.no)
        scope.launch {
            when (val result = service.read(SchoolService.DIMA_C, DIMA_C_EQUIPMENT_URL, dimaCModelAvailabilityScript(model.no), "장비 대여 현황")) {
                is SchoolServiceResult.Success -> runCatching { parseEquipmentAvailability(result.json) }
                    .onSuccess { SchoolReadCache.setEquipmentAvailability(model.no, it) }
                    .onFailure { availabilityErrors[model.no] = "대여 현황을 읽지 못했어요." }
                is SchoolServiceResult.Failure -> availabilityErrors[model.no] = result.message
                SchoolServiceResult.Cancelled -> Unit
            }
            loadingModel = null
        }
    }

    // DIMA C opened in the browser would ask for the sign-in again, so it opens signed in inside the app.
    fun openDimaC() {
        val service = bridge ?: return openUrl(context, DIMA_C_EQUIPMENT_URL)
        applyError = null
        scope.launch {
            val result = service.open(SchoolService.DIMA_C, DIMA_C_EQUIPMENT_URL, "장비 대여 신청")
            if (result is SchoolServiceResult.Failure) applyError = result.message
        }
    }

    LaunchedEffect(Unit) {
        val failedAt = SchoolReadCache.equipmentCatalogFailure.value?.at
        if (catalog == null && equipmentCatalogAutoReadAllowed(failedAt, Instant.now())) loadCatalog()
    }
    // The open model is remembered across visits; read its days again if they are not cached any more
    // (a new catalog, or the app process was restarted), instead of showing an empty panel.
    LaunchedEffect(expanded, catalog, availability) {
        val open = expanded ?: return@LaunchedEffect
        if (open in availability || open in availabilityErrors || loadingModel != null) return@LaunchedEffect
        catalog?.flatMap { it.models }?.firstOrNull { it.no == open }?.let(::loadModel)
    }

    ScreenScaffold(
        title = "장비 대여",
        modifier = modifier,
        navigationIcon = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack, modifier = Modifier.testTag("close_equipment")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        listTag = "equipment_list",
        onRefresh = if (bridge != null) ::loadCatalog else null,
        refreshing = loading && catalog != null,
        refreshLabel = "장비 대여 현황 새로고침",
    ) {
        item(key = "equipment_guide") {
            EquipmentGuideCard(
                expanded = guideExpanded,
                onToggle = { guideExpanded = !guideExpanded },
                onApply = ::openDimaC,
                onOpenNotice = { openUrl(context, EquipmentRentalNotice.SEARCH_URL) },
            )
        }
        applyError?.let { message -> item(key = "equipment_apply_error") { ErrorState(message) } }
        item(key = "equipment_section") {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("일반 대여 장비", style = MaterialTheme.typography.titleMedium)
                Text(
                    "장비를 누르면 앞으로 2주 동안 날마다 빌릴 수 있는 대수를 보여 줘요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // One item whose content crossfades and resizes when the read finishes, so the list grows into the groups
        // instead of popping in under the loading line. The groups appear together, never one by one (D-058).
        val view = EquipmentCatalogView(
            phase = equipmentCatalogPhase(catalog?.size, loading, error != null, bridge != null),
            error = error,
            groups = catalog.orEmpty(),
        )
        item(key = "equipment_catalog") {
            AnimatedContent(
                targetState = view,
                transitionSpec = { dimaContentReveal() },
                contentKey = { it.phase },
                label = "equipment_catalog",
            ) { shown ->
                when (shown.phase) {
                    EquipmentCatalogPhase.LOADING -> LoadingLine("DIMA C에서 장비 목록을 불러오는 중이에요")
                    EquipmentCatalogPhase.ERROR ->
                        ErrorState(shown.error.orEmpty(), onRetry = if (bridge != null) ::loadCatalog else null)
                    // After the sign-in was closed: nothing is read until the student asks again.
                    EquipmentCatalogPhase.START -> EmptyState(
                        message = "학교 계정으로 로그인하면 대여 현황을 볼 수 있어요",
                        supporting = "수업 탭과 같은 학교 포털 계정을 써요.",
                        actionLabel = "대여 현황 불러오기",
                        onAction = ::loadCatalog,
                        modifier = Modifier.testTag("equipment_start"),
                    )
                    EquipmentCatalogPhase.EMPTY -> EmptyState("지금 빌릴 수 있는 일반 대여 장비가 없어요")
                    EquipmentCatalogPhase.NONE -> Spacer(Modifier.fillMaxWidth())
                    EquipmentCatalogPhase.READY -> Column {
                        // A failed refresh keeps the list and opens its message above it.
                        AnimatedContent(
                            targetState = shown.error,
                            transitionSpec = { dimaContentReveal() },
                            label = "equipment_refresh_error",
                        ) { message ->
                            if (message != null) {
                                ErrorState(message, modifier = Modifier.padding(bottom = 12.dp), onRetry = ::loadCatalog)
                            } else {
                                Spacer(Modifier.fillMaxWidth())
                            }
                        }
                        // The 12dp the groups had as separate list items.
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            shown.groups.forEach { group ->
                                key(group.name) {
                                    EquipmentGroupList(
                                        group = group,
                                        expanded = expanded,
                                        today = today,
                                        availability = availability,
                                        availabilityErrors = availabilityErrors,
                                        loadingModel = loadingModel,
                                        onToggle = { model ->
                                            expanded = if (expanded == model.no) null else model.no
                                            if (expanded == model.no && availability[model.no] == null) loadModel(model)
                                        },
                                        onRetry = ::loadModel,
                                        onApply = ::openDimaC,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What the equipment part of the page shows; the content changes with an animation only when this changes. */
internal enum class EquipmentCatalogPhase { LOADING, ERROR, START, EMPTY, READY, NONE }

/**
 * Pure choice of [EquipmentCatalogPhase]: a first read in progress, its failure, the sign-in prompt once the
 * sign-in was closed ([canSignIn]: the school bridge exists), an empty catalog, or the groups. [catalogSize] is null
 * before any catalog was read; a refresh or its failure with a catalog in hand keeps [EquipmentCatalogPhase.READY].
 */
internal fun equipmentCatalogPhase(catalogSize: Int?, loading: Boolean, failed: Boolean, canSignIn: Boolean): EquipmentCatalogPhase =
    when {
        catalogSize == null && loading -> EquipmentCatalogPhase.LOADING
        catalogSize == null && failed -> EquipmentCatalogPhase.ERROR
        catalogSize == null -> if (canSignIn) EquipmentCatalogPhase.START else EquipmentCatalogPhase.NONE
        catalogSize == 0 -> EquipmentCatalogPhase.EMPTY
        else -> EquipmentCatalogPhase.READY
    }

/** The catalog part's content, carried whole so the content fading out keeps showing what it showed. */
private data class EquipmentCatalogView(
    val phase: EquipmentCatalogPhase,
    val error: String?,
    val groups: List<EquipmentGroup>,
)

/** What an open model's panel shows. */
internal enum class EquipmentDaysPhase { LOADING, ERROR, DAYS, NONE }

/** Pure choice of [EquipmentDaysPhase], in the panel's order: a read in progress, then its failure, then the days. */
internal fun equipmentDaysPhase(loading: Boolean, failed: Boolean, hasDays: Boolean): EquipmentDaysPhase = when {
    loading -> EquipmentDaysPhase.LOADING
    failed -> EquipmentDaysPhase.ERROR
    hasDays -> EquipmentDaysPhase.DAYS
    else -> EquipmentDaysPhase.NONE
}

private data class EquipmentDaysView(
    val phase: EquipmentDaysPhase,
    val error: String?,
    val days: List<EquipmentDay>,
)

/** One group's heading and its models as an M3 Expressive segmented list; an open model adds its days below it. */
@Composable
private fun EquipmentGroupList(
    group: EquipmentGroup,
    expanded: Long?,
    today: LocalDate,
    availability: Map<Long, List<EquipmentDay>>,
    availabilityErrors: Map<Long, String>,
    loadingModel: Long?,
    onToggle: (EquipmentModel) -> Unit,
    onRetry: (EquipmentModel) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionHeader("${group.displayName} · ${group.models.size}종")
        // Each model's day panel opens and closes on the expressive spring (D-118's fold motion). A panel still
        // closing keeps its place in the segmented shapes until it is gone, so the rows below do not jump early.
        // A panel already open when the page is shown again (D-111) simply shows.
        val panels = remember(group) { group.models.associate { it.no to MutableTransitionState(it.no == expanded) } }
        panels.forEach { (no, state) -> state.targetState = no == expanded }
        val count = group.models.size + panels.values.count { it.currentState || it.targetState }
        DimaSegmentedList {
            var index = 0
            group.models.forEach { model ->
                val open = expanded == model.no
                val days = availability[model.no]
                EquipmentModelRow(
                    model = model,
                    open = open,
                    summary = days?.let { equipmentAvailabilitySummary(it, today) },
                    index = index++,
                    count = count,
                    onToggle = { onToggle(model) },
                )
                val panel = panels.getValue(model.no)
                if (panel.currentState || panel.targetState) {
                    val panelIndex = index++
                    AnimatedVisibility(visibleState = panel, enter = dimaExpandIn(), exit = dimaCollapseOut()) {
                        val message = availabilityErrors[model.no]
                        EquipmentDaysPanel(
                            view = EquipmentDaysView(
                                phase = equipmentDaysPhase(loadingModel == model.no, message != null, days != null),
                                error = message,
                                days = days.orEmpty(),
                            ),
                            today = today,
                            shape = ListItemDefaults.segmentedShapes(index = panelIndex, count = count).shape,
                            onRetry = { onRetry(model) },
                            onApply = onApply,
                            modifier = Modifier.testTag("equipment_days_${model.no}"),
                        )
                    }
                }
            }
        }
    }
}

/**
 * An open model's days. When its read finishes the loading line crossfades into the week grid and the panel grows
 * to it on the spatial spring ([dimaContentReveal]), instead of the grid popping in.
 */
@Composable
private fun EquipmentDaysPanel(
    view: EquipmentDaysView,
    today: LocalDate,
    shape: Shape,
    onRetry: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = shape, color = DimaTheme.colors.card, modifier = modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = view,
            transitionSpec = { dimaContentReveal() },
            contentKey = { it.phase },
            label = "equipment_days",
        ) { shown ->
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (shown.phase) {
                    EquipmentDaysPhase.LOADING -> LoadingLine("대여 현황을 불러오는 중이에요")
                    EquipmentDaysPhase.ERROR -> ErrorState(shown.error.orEmpty(), onRetry = onRetry)
                    EquipmentDaysPhase.DAYS -> {
                        EquipmentWeekGrid(shown.days, today)
                        Text(
                            "숫자는 그날 빌릴 수 있는 대수예요. 오픈 전은 아직 신청이 열리지 않은 날이고, 주말과 휴일에는 대여하지 않아요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = onApply,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("DIMA C에서 신청하기") }
                    }
                    EquipmentDaysPhase.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun EquipmentModelRow(
    model: EquipmentModel,
    open: Boolean,
    summary: String?,
    index: Int,
    count: Int,
    onToggle: () -> Unit,
) {
    SegmentedListItem(
        onClick = onToggle,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = Modifier
            .testTag("equipment_model_${model.no}")
            .expandableSemantics(open, onToggle),
        trailingContent = {
            ExpandChevron(open, Modifier.size(24.dp), label = "equipment_model_chevron")
        },
        // D-130: a model with no rentable unit stays listed, so the catalog matches the site.
        supportingContent = {
            Text(if (model.units == 0) "지금 빌릴 수 있는 장비가 없어요" else listOfNotNull("대여용 ${model.units}대", summary).joinToString(" · "))
        },
        colors = dimaSegmentedListColors(),
    ) {
        Text(model.name)
    }
}

/** The open days as a Monday-to-Friday grid, one row per week, so no day hides off screen. */
@Composable
private fun EquipmentWeekGrid(days: List<EquipmentDay>, today: LocalDate) {
    val weekdays = days.filter { it.date.dayOfWeek <= DayOfWeek.FRIDAY }
    if (weekdays.isEmpty()) return
    // The first cell and the first weekday of a new month show the month as well.
    val monthStarts = weekdays.filterIndexed { index, day -> index == 0 || day.date.month != weekdays[index - 1].date.month }
        .map { it.date }.toSet()
    // Closed days after the last bookable one are not open for applications yet (DIMA C opens a week at a time).
    val lastOpen = weekdays.lastOrNull { it.open }?.date
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("equipment_week_grid")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("월", "화", "수", "목", "금").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        weekdays.groupBy { it.date.with(DayOfWeek.MONDAY) }.toSortedMap().forEach { (monday, week) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0L until 5L).forEach { offset ->
                    val day = week.firstOrNull { it.date == monday.plusDays(offset) }
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        EquipmentDayCell(
                            day = day,
                            today = today,
                            showMonth = day.date in monthStarts,
                            notYetOpen = !day.open && !day.holiday && (lastOpen == null || day.date.isAfter(lastOpen)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentDayCell(
    day: EquipmentDay,
    today: LocalDate,
    showMonth: Boolean,
    notYetOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val available = day.open && day.freeUnits > 0
    val isToday = day.date == today
    Surface(
        shape = DimaShapes.Tile,
        color = when {
            available -> MaterialTheme.colorScheme.primaryContainer
            day.open -> DimaTheme.colors.cardInner
            else -> Color.Transparent
        },
        contentColor = if (available) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (day.open) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(if (isToday) "오늘" else null, koreanDateLabel(day.date)).joinToString(" ") + ", " + when {
                    day.holiday -> "휴일이라 대여하지 않음"
                    notYetOpen -> "아직 신청이 열리지 않음"
                    !day.open -> "대여하지 않는 날"
                    day.freeUnits == 0 -> "빌릴 수 있는 장비 없음"
                    else -> "${day.totalUnits}대 중 ${day.freeUnits}대 빌릴 수 있음"
                }
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Text(
                when {
                    isToday -> "오늘"
                    showMonth -> "${day.date.monthValue}/${day.date.dayOfMonth}"
                    else -> "${day.date.dayOfMonth}"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else null,
            )
            Text(
                when {
                    day.holiday -> "휴일"
                    notYetOpen -> "오픈 전"
                    !day.open -> "불가"
                    day.freeUnits == 0 -> "마감"
                    else -> "${day.freeUnits}대"
                },
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The soonest day with a free unit, in a few words for the model's row. */
internal fun equipmentAvailabilitySummary(days: List<EquipmentDay>, today: LocalDate): String {
    val soonest = days.firstOrNull { it.open && it.freeUnits > 0 } ?: return "2주 안에 빈 날이 없어요"
    val units = "${soonest.freeUnits}대 빌릴 수 있어요"
    return when (soonest.date) {
        today -> "오늘 $units"
        today.plusDays(1) -> "내일 $units"
        else -> "${koreanDateLabel(soonest.date, today)}에 $units"
    }
}
