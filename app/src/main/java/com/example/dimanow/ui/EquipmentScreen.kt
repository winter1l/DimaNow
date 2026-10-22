package com.example.dimanow.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.school.DIMA_C_EQUIPMENT_CATALOG_SCRIPT
import com.example.dimanow.school.DIMA_C_EQUIPMENT_URL
import com.example.dimanow.school.EquipmentDay
import com.example.dimanow.school.EquipmentModel
import com.example.dimanow.school.LocalSchoolServiceBridge
import com.example.dimanow.school.SchoolReadCache
import com.example.dimanow.school.SchoolService
import com.example.dimanow.school.SchoolServiceResult
import com.example.dimanow.school.dimaCModelAvailabilityScript
import com.example.dimanow.school.parseEquipmentAvailability
import com.example.dimanow.school.parseEquipmentCatalog
import com.example.dimanow.theme.DimaShapes
import kotlinx.coroutines.launch

/**
 * 장비 대여 (D-107): DIMA C's general-rental equipment, read with the student's sign-in when the screen opens.
 * Each model opens to the next two weeks with the units free each day; renting itself stays on DIMA C.
 */
@Composable
internal fun EquipmentScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bridge = LocalSchoolServiceBridge.current
    val scope = rememberCoroutineScope()
    val catalog by SchoolReadCache.equipmentCatalog.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    val availability = remember { mutableStateMapOf<Long, List<EquipmentDay>>() }
    val availabilityErrors = remember { mutableStateMapOf<Long, String>() }
    var loadingModel by remember { mutableStateOf<Long?>(null) }

    fun loadCatalog() {
        val service = bridge ?: return
        if (loading) return
        loading = true
        error = null
        scope.launch {
            when (val result = service.read(SchoolService.DIMA_C, DIMA_C_EQUIPMENT_URL, DIMA_C_EQUIPMENT_CATALOG_SCRIPT, "장비 대여 현황")) {
                is SchoolServiceResult.Success -> runCatching { parseEquipmentCatalog(result.json) }
                    .onSuccess {
                        SchoolReadCache.setEquipmentCatalog(it)
                        availability.clear()
                    }
                    .onFailure { error = "장비 목록을 읽지 못했어요." }
                is SchoolServiceResult.Failure -> error = result.message
                SchoolServiceResult.Cancelled -> Unit
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
                    .onSuccess { availability[model.no] = it }
                    .onFailure { availabilityErrors[model.no] = "대여 현황을 읽지 못했어요." }
                is SchoolServiceResult.Failure -> availabilityErrors[model.no] = result.message
                SchoolServiceResult.Cancelled -> Unit
            }
            loadingModel = null
        }
    }

    LaunchedEffect(Unit) { if (catalog == null) loadCatalog() }

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
        item(key = "equipment_intro") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "DIMA C 일반 대여 장비예요. 장비를 누르면 앞으로 2주 동안 날마다 빌릴 수 있는 대수를 보여 줘요. " +
                        "신청은 DIMA C에서 하고, 당일 대여는 낮 12시 전까지만 돼요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { openUrl(context, DIMA_C_EQUIPMENT_URL) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("equipment_open_dimac"),
                ) {
                    Text("DIMA C에서 신청하기")
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                }
            }
        }
        val groups = catalog
        when {
            groups == null && loading -> item(key = "equipment_loading") { LoadingLine("DIMA C에서 장비 목록을 불러오는 중이에요") }
            groups == null && error != null -> item(key = "equipment_error") {
                ErrorState(requireNotNull(error), onRetry = if (bridge != null) ::loadCatalog else null)
            }
            groups == null -> if (bridge != null) item(key = "equipment_start") {
                // After the sign-in was closed: nothing is read until the student asks again.
                EmptyState(
                    message = "학교 계정으로 로그인하면 대여 현황을 볼 수 있어요",
                    supporting = "수업 탭과 같은 학교 포털 계정을 써요.",
                    actionLabel = "대여 현황 불러오기",
                    onAction = ::loadCatalog,
                    modifier = Modifier.testTag("equipment_start"),
                )
            }
            groups.isEmpty() -> item(key = "equipment_empty") { EmptyState("지금 빌릴 수 있는 일반 대여 장비가 없어요") }
            else -> {
                error?.let { message -> item(key = "equipment_refresh_error") { ErrorState(message, onRetry = ::loadCatalog) } }
                groups.forEach { group ->
                    item(key = "group_${group.name}") {
                        Text(group.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    group.models.forEach { model ->
                        item(key = "model_${model.no}") {
                            EquipmentModelCard(
                                model = model,
                                expanded = expanded == model.no,
                                days = availability[model.no],
                                loading = loadingModel == model.no,
                                error = availabilityErrors[model.no],
                                onToggle = {
                                    expanded = if (expanded == model.no) null else model.no
                                    if (expanded == model.no && availability[model.no] == null) loadModel(model)
                                },
                                onRetry = { loadModel(model) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentModelCard(
    model: EquipmentModel,
    expanded: Boolean,
    days: List<EquipmentDay>?,
    loading: Boolean,
    error: String?,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
) {
    ElevatedCard(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("equipment_model_${model.no}")
            .semantics { stateDescription = if (expanded) "펼침" else "접힘" },
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                    Text("대여용 ${model.units}대", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                when {
                    loading -> LoadingLine("대여 현황을 불러오는 중이에요")
                    error != null -> ErrorState(error, onRetry = onRetry)
                    days != null -> EquipmentDayStrip(days)
                }
            }
        }
    }
}

@Composable
private fun EquipmentDayStrip(days: List<EquipmentDay>) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { day ->
            val label = when {
                !day.open -> "휴무"
                day.freeUnits == 0 -> "마감"
                else -> "${day.freeUnits}대"
            }
            val available = day.open && day.freeUnits > 0
            Surface(
                shape = DimaShapes.Tile,
                color = if (available) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .widthIn(min = 56.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${koreanDateLabel(day.date)}, " + when {
                            !day.open -> "대여하지 않는 날"
                            day.freeUnits == 0 -> "빌릴 수 있는 장비 없음"
                            else -> "${day.totalUnits}대 중 ${day.freeUnits}대 빌릴 수 있음"
                        }
                    },
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${day.date.monthValue}/${day.date.dayOfMonth}", style = MaterialTheme.typography.labelMedium)
                    Text(koreanWeekdayLabel(day.date.dayOfWeek).take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        color = if (available) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
