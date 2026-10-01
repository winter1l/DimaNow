package com.example.dimanow.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.dimanow.ui.meal.groupDormitorySections
import androidx.compose.material.icons.filled.PhotoCamera
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.emphasized
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.semantics.stateDescription
import com.example.dimanow.meal.MealServiceState
import com.example.dimanow.meal.MealServiceStatus
import com.example.dimanow.meal.hasCurrentStudentWeek
import com.example.dimanow.ui.meal.DormitoryMealBlock
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.dimanow.domain.MealValidationState
import com.example.dimanow.meal.MealData
import com.example.dimanow.meal.DormitoryMealData
import com.example.dimanow.meal.DormitoryMealImage
import com.example.dimanow.meal.DormitoryMealSubmissionResult
import com.example.dimanow.meal.MealSource
import com.example.dimanow.meal.mealServiceStatus
import com.example.dimanow.time.MinuteTicker
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

enum class MealVenue { MAIN_CAFETERIA, DORMITORY }

@Composable
fun MealScreen(
    mealSource: MealSource,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(MinuteTicker.CAMPUS_ZONE),
    nowTime: LocalTime = LocalTime.now(MinuteTicker.CAMPUS_ZONE),
    initialVenue: MealVenue = MealVenue.MAIN_CAFETERIA,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // D-094: null until the cache emits, so cold start shows loading instead of "등록된 식단이 없어요"
    val loadedMeal by mealSource.data.collectLoadedAsState()
    val loadedDormitoryMeal by mealSource.dormitoryData.collectLoadedAsState()
    // 사용자가 직접 선택하기 전에는 현재 구역을 따라가고, 선택한 뒤에는 존 변경이 덮어쓰지 않는다 (#11)
    var userVenue by rememberSaveable { mutableStateOf<String?>(null) }
    val venue = userVenue?.let(MealVenue::valueOf) ?: initialVenue
    var refreshing by remember { mutableStateOf(false) }
    var uploadPreflight by remember { mutableStateOf(false) }
    var showDormitoryPhotoSource by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<DormitoryMealImage?>(null) }
    var preparingDormitoryImage by remember { mutableStateOf(false) }
    var uploadingDormitoryMeal by remember { mutableStateOf(false) }
    var cameraOutput by remember { mutableStateOf<Uri?>(null) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }

    fun loadImage(uri: Uri) {
        if (preparingDormitoryImage || uploadingDormitoryMeal) return
        preparingDormitoryImage = true
        scope.launch {
            try {
                pendingImage = com.example.dimanow.meal.DormitoryMealImageReader(context).read(uri)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: IllegalArgumentException) {
                // 사진 확인 메시지는 무엇을 고르면 되는지 알려 주는 문장이라 그대로 보여 준다
                refreshMessage = failure.message ?: "식단 사진을 읽지 못했어요. 다른 사진을 선택해 주세요."
            } catch (failure: IllegalStateException) {
                refreshMessage = failure.message ?: "식단 사진을 읽지 못했어요. 다른 사진을 선택해 주세요."
            } catch (failure: Exception) {
                refreshMessage = "식단 사진을 읽지 못했어요. 다른 사진을 선택해 주세요."
            } finally {
                preparingDormitoryImage = false
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::loadImage)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) cameraOutput?.let(::loadImage)
    }

    suspend fun submitAndWatch(image: DormitoryMealImage) {
        uploadingDormitoryMeal = true
        try {
            when (val submitted = mealSource.submitDormitoryMeal(image)) {
                is DormitoryMealSubmissionResult.Submitted -> {
                    refreshMessage = "사진을 올렸어요. 식단표를 확인하고 있어요"
                    repeat(90) {
                        delay(10_000)
                        when (val status = mealSource.dormitorySubmissionStatus(submitted.submissionId)) {
                            DormitoryMealSubmissionResult.Processing -> Unit
                            DormitoryMealSubmissionResult.Published -> {
                                mealSource.refreshDormitory()
                                refreshMessage = "기숙사 식단을 등록했어요"
                                return
                            }
                            DormitoryMealSubmissionResult.Duplicate -> {
                                mealSource.refreshDormitory()
                                refreshMessage = "이미 이번 주 식단이 등록되어 있어요"
                                return
                            }
                            is DormitoryMealSubmissionResult.Rejected -> {
                                refreshMessage = status.reason
                                return
                            }
                            is DormitoryMealSubmissionResult.Failure -> {
                                refreshMessage = "식단표를 확인하지 못했어요. 잠시 후 다시 올려 주세요."
                                return
                            }
                            else -> Unit
                        }
                    }
                    refreshMessage = "식단 확인이 계속되고 있어요. 잠시 후 새로고침해 주세요."
                }
                is DormitoryMealSubmissionResult.Failure -> refreshMessage = "식단 사진을 올리지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요."
                else -> Unit
            }
        } finally {
            uploadingDormitoryMeal = false
        }
    }

    LaunchedEffect(refreshMessage) {
        refreshMessage?.let {
            snackbarHostState.showSnackbar(it)
            refreshMessage = null
        }
    }

    fun refreshMeal() {
        if (refreshing) return
        refreshing = true
        refreshMessage = null
        scope.launch {
            try {
                val result = if (venue == MealVenue.DORMITORY) mealSource.refreshDormitory() else
                    com.example.dimanow.work.StudentMealSync.refresh(context, mealSource, com.example.dimanow.meal.MealRefreshTrigger.MANUAL)
                // D-094: 스낵바는 사용자가 직접 당긴 새로고침의 결과만 쉬운 말로 알린다.
                // 탭 진입 시의 자동 새로고침(아래 FOREGROUND)은 결과를 알리지 않는다.
                refreshMessage = mealRefreshMessage(result, venue, today)
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(mealSource, venue) {
        if (venue == MealVenue.MAIN_CAFETERIA) {
            com.example.dimanow.work.StudentMealSync.refresh(context, mealSource, com.example.dimanow.meal.MealRefreshTrigger.FOREGROUND)
        }
    }

    // 주간 5일 중 보고 있는 하루. 날짜가 바뀌면 오늘로 다시 맞춘다 (D-057)
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    // 선택은 고른 날(오늘)과 함께 저장해 탭 전환에도 유지하고, 날짜가 바뀐 뒤에는 새 기본값을 쓴다 (D-094(12))
    val defaultDay = if (today.dayOfWeek.value in 1..5) today else weekStart
    var daySelection by rememberSaveable { mutableStateOf(today to defaultDay) }
    val selectedDay = if (daySelection.first == today) daySelection.second else defaultDay
    val listState = rememberLazyListState()
    // The last venue/day/focus the list was moved for; returning to the tab restores the user's scroll.
    var lastAutoScrollKey by rememberSaveable { mutableStateOf<String?>(null) }
    val now = ZonedDateTime.of(today, nowTime, MinuteTicker.CAMPUS_ZONE)
    val dormitoryBlocks = remember(loadedDormitoryMeal, selectedDay) {
        loadedDormitoryMeal?.days.orEmpty().firstOrNull { it.date == selectedDay }?.sections
            ?.let(::groupDormitorySections).orEmpty()
    }
    val dormitoryStates = dormitoryBlocks.map { mealServiceStatus(selectedDay, it.hours, now) }
    val focusBlock = if (selectedDay == today) {
        dormitoryStates.indexOfFirst { it.state == MealServiceState.OPEN }.takeIf { it >= 0 }
            ?: dormitoryStates.indexOfFirst { it.state == MealServiceState.BEFORE_OPEN }.takeIf { it >= 0 }
            ?: dormitoryStates.indexOfLast { it.state == MealServiceState.CLOSED }.takeIf { it >= 0 }
    } else null
    // Only a change of meal/venue/day moves the list; ordinary minute ticks preserve manual scrolling.
    val autoScrollKey = "$venue|$selectedDay|$focusBlock|$uploadingDormitoryMeal"
    LaunchedEffect(autoScrollKey) {
        if (autoScrollKey == lastAutoScrollKey) return@LaunchedEffect
        val target = if (venue == MealVenue.DORMITORY && focusBlock != null) {
            1 + focusBlock + if (uploadingDormitoryMeal) 1 else 0
        } else 0
        listState.scrollToItem(target)
        lastAutoScrollKey = autoScrollKey
    }

    Box(modifier = modifier.fillMaxSize()) {
        ScreenScaffold(
            title = "식단",
            listState = listState,
            modifier = Modifier.fillMaxSize(),
            // 새로고침은 목록을 당겨서 실행한다 — 상단 아이콘 버튼은 제거했다 (D-058)
            onRefresh = ::refreshMeal,
            refreshing = refreshing,
            listTag = "meal_list",
            // 식당 전환과 요일 선택은 스크롤해도 항상 닿을 수 있도록 헤더에 고정한다 (D-057)
            subHeader = {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = venue == MealVenue.MAIN_CAFETERIA,
                        onClick = { userVenue = MealVenue.MAIN_CAFETERIA.name },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("본관 학생식당") }
                    SegmentedButton(
                        selected = venue == MealVenue.DORMITORY,
                        onClick = { userVenue = MealVenue.DORMITORY.name },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("기숙사") }
                }
                // 셔틀 요일 선택과 같은 접근성 선택기: 선택 상태와 '오늘'을 읽어 준다 (D-094(11))
                DimaDateSelector(
                    dates = (0L..4L).map { weekStart.plusDays(it) },
                    selected = selectedDay,
                    today = today,
                    onSelect = { daySelection = today to it },
                    itemTag = { "meal_day_${it.dayOfWeek.name}" },
                )
            },
        ) {
            val dormitoryMeal = loadedDormitoryMeal
            val meal = loadedMeal
            if (venue == MealVenue.DORMITORY && dormitoryMeal == null || venue == MealVenue.MAIN_CAFETERIA && meal == null) {
                item(key = "meal_loading") {
                    LoadingLine(
                        text = "식단을 불러오고 있어요",
                        modifier = Modifier.padding(16.dp).testTag("meal_loading"),
                    )
                }
            } else if (venue == MealVenue.DORMITORY && dormitoryMeal != null) {
                // 업로드는 15분까지 폴링되므로 진행 상태를 상시 카드로 보여준다 (D-056)
                if (uploadingDormitoryMeal) {
                    item(key = "dormitory_upload") { DormitoryUploadProgressCard() }
                }
                if (preparingDormitoryImage) {
                    item(key = "dormitory_image_preparing") {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text("사진을 준비하고 있어요", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                }
                dormitoryDayContent(
                    meal = dormitoryMeal,
                    date = selectedDay,
                    today = today,
                    blocks = dormitoryBlocks,
                    statuses = dormitoryStates,
                    onUpload = {
                        uploadPreflight = true
                        scope.launch {
                            try {
                                val result = mealSource.refreshDormitory()
                                val currentWeekExists = mealSource.dormitoryData.first().hasCurrentWeek(today)
                                if (result is com.example.dimanow.meal.MealRefreshResult.Success && currentWeekExists) {
                                    refreshMessage = "이번 주 기숙사 식단을 불러왔어요"
                                } else {
                                    showDormitoryPhotoSource = true
                                }
                            } finally {
                                uploadPreflight = false
                            }
                        }
                    },
                    uploadEnabled = !refreshing && !uploadPreflight && !uploadingDormitoryMeal && !preparingDormitoryImage,
                )
            } else if (meal != null) {
                if (!meal.hasCurrentStudentWeek(today) || meal.error != null) {
                    item(key = "student_meal_sync") { StudentMealSyncStatus(meal, today, onRetry = ::refreshMeal) }
                }
                mainCafeteriaDayContent(meal = meal, date = selectedDay, today = today, nowTime = nowTime)
            }
        }
    SnackbarHost(
        snackbarHostState,
        Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    }
    if (showDormitoryPhotoSource) {
        AlertDialog(
            onDismissRequest = { showDormitoryPhotoSource = false },
            title = { Text("기숙사 식단표") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showDormitoryPhotoSource = false
                            photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    ) { Text("사진 선택") }
                    TextButton(
                        onClick = {
                            showDormitoryPhotoSource = false
                            val file = File(context.cacheDir, "dorm-meals/capture-${System.currentTimeMillis()}.jpg").apply {
                                parentFile?.mkdirs()
                            }
                            val outputUri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                            cameraOutput = outputUri
                            cameraLauncher.launch(outputUri)
                        },
                    ) { Text("카메라 촬영") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDormitoryPhotoSource = false }) { Text("취소") }
            },
        )
    }
    pendingImage?.let { image ->
        AlertDialog(
            onDismissRequest = { pendingImage = null },
            title = { Text("기숙사 식단 올리기") },
            text = { Text("사진은 식단 확인 후 공개 데이터로 공유돼요.") },
            confirmButton = {
                Button(
                    enabled = !uploadingDormitoryMeal,
                    onClick = {
                        pendingImage = null
                        scope.launch { submitAndWatch(image) }
                    },
                ) { Text("올리기") }
            },
            dismissButton = { TextButton(onClick = { pendingImage = null }) { Text("취소") } },
        )
    }
}

/** 선택한 날짜의 메뉴 줄을 읽기 쉬운 목록으로 그린다 (D-057). */
@Composable
private fun MenuLineList(lines: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // OCR can attach an origin note to a dish on the next line; classify each line separately
        // so the dish keeps its menu style and only the note becomes supporting text.
        lines.flatMap { it.split('\n') }.filter { it.isNotBlank() }.forEach { rawLine ->
            val line = rawLine.trim()
            val isOriginOrNote = line.contains("원산지") || line.contains("국내산") || line.contains("호주산") ||
                line.contains("미국산") || line.startsWith("-") || line.startsWith("*") ||
                (line.contains(":") && !line.contains("kcal", ignoreCase = true))

            if (isOriginOrNote) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current,
                    )
                }
            }
        }
    }
}

/** 날짜 제목 + 상태 배지를 담은 하루 머리글 (D-057). */
@Composable
private fun MealDayHeading(date: LocalDate, today: LocalDate, trailing: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${date.monthValue}월 ${date.dayOfMonth}일 ${koreanWeekdayLabel(date.dayOfWeek)}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (date == today) {
                Surface(
                    shape = DimaShapes.Badge,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "오늘",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 본관 학생식당의 선택한 하루. */
private fun androidx.compose.foundation.lazy.LazyListScope.mainCafeteriaDayContent(
    meal: MealData,
    date: LocalDate,
    today: LocalDate,
    nowTime: LocalTime,
) {
    val day = meal.days.firstOrNull { it.date == date && it.validationState == MealValidationState.VALID }
    val status = mealServiceStatus(date, day?.hours, ZonedDateTime.of(today, nowTime, MinuteTicker.CAMPUS_ZONE))
    item(key = "main-heading-$date") {
        MealDayHeading(
            date = date,
            today = today,
            trailing = null,
        )
    }
    if (day == null || day.menuLines.isEmpty()) {
        item(key = "main-empty-$date") { EmptyState("등록된 식단이 없어요", modifier = Modifier.testTag("meal_empty_day")) }
    } else {
        item(key = "main-menu-$date") {
            MealPeriodCard(
                status = status,
                modifier = Modifier.testTag("main_meal_card"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("중식", style = MaterialTheme.typography.titleMedium)
                    MealHoursChip(day.hours, status.state)
                }
                MealPeriodStatus(status)
                MenuLineList(day.menuLines)
            }
        }
    }
}

/** 기숙사의 선택한 하루. 식사 시간대별 카드로 나눠 긴 목록을 끊어 읽게 한다 (D-057). */
private fun androidx.compose.foundation.lazy.LazyListScope.dormitoryDayContent(
    meal: DormitoryMealData,
    date: LocalDate,
    today: LocalDate,
    blocks: List<DormitoryMealBlock>,
    statuses: List<MealServiceStatus>,
    onUpload: (() -> Unit)?,
    uploadEnabled: Boolean,
) {
    val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = meal.days.filter { it.date in weekStart..weekStart.plusDays(4) }

    item(key = "dorm-heading-$date") { MealDayHeading(date = date, today = today, trailing = null) }

    if (blocks.isEmpty()) {
        if (week.none { it.sections.isNotEmpty() }) {
            // 주간 전체가 비었을 때만 이유와 업로드 CTA를 담은 안내 카드를 보여준다 (D-056)
            item(key = "dorm-week-empty") {
                DormitoryWeekEmptyCard(onUpload = onUpload, uploadEnabled = uploadEnabled)
            }
        } else {
            item(key = "dorm-empty-$date") { EmptyState("이날 식단이 없어요", modifier = Modifier.testTag("meal_empty_day")) }
        }
        return
    }

    itemsIndexed(blocks, key = { index, _ -> "dorm-$date-$index" }) { index, block ->
        val status = statuses[index]
        MealPeriodCard(
            status = status,
            modifier = Modifier.testTag("dorm_meal_card_$index"),
        ) {
            DormitoryMealPeriodContent(block, status)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DormitoryMealPeriodContent(block: DormitoryMealBlock, status: MealServiceStatus) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(dormitoryBlockIcon(block.name), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(block.name, style = MaterialTheme.typography.titleMedium)
        }
        MealHoursChip(block.hours, status.state, includeStatus = true)
    }
    MenuLineList(block.menuLines)
    block.extras.forEach { extra ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = extra.name,
                style = MaterialTheme.typography.labelLarge,
                color = LocalContentColor.current,
            )
            MenuLineList(extra.menuLines)
        }
    }
}

@Composable
private fun MealPeriodCard(
    status: MealServiceStatus,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isOpen = status.state == MealServiceState.OPEN
    val isClosed = status.state == MealServiceState.CLOSED
    val shape = DimaShapes.Card
    val background = when {
        isOpen -> colors.primaryContainer
        isClosed -> colors.surfaceContainerHighest
        else -> colors.surfaceContainerLow
    }
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
            .semantics { stateDescription = status.label },
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = background,
            contentColor = when {
                isOpen -> colors.onPrimaryContainer
                isClosed -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isClosed) 0.dp else 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun MealPeriodStatus(status: MealServiceStatus) {
    if (status.state == MealServiceState.UNKNOWN_HOURS) return
    Text(
        status.label,
        style = if (status.state == MealServiceState.OPEN) MaterialTheme.typography.labelMedium.emphasized() else MaterialTheme.typography.labelMedium,
        color = if (status.state == MealServiceState.OPEN) MaterialTheme.colorScheme.primary else LocalContentColor.current,
    )
}

private fun dormitoryBlockIcon(name: String): ImageVector = when {
    name.contains("조식") -> Icons.Default.WbSunny
    name.contains("석식") -> Icons.Default.NightsStay
    else -> Icons.Default.Restaurant
}

@Composable
private fun MealHoursChip(
    hours: String?,
    state: MealServiceState = MealServiceState.UNKNOWN_HOURS,
    includeStatus: Boolean = false,
) {
    val hoursText = hours?.takeIf { it.isNotBlank() } ?: return
    val statusText = if (includeStatus) when (state) {
        MealServiceState.BEFORE_OPEN -> "운영 전"
        MealServiceState.OPEN -> "운영 중"
        MealServiceState.CLOSED -> "운영 종료"
        else -> null
    } else null
    val text = listOfNotNull(hoursText, statusText).joinToString(" · ")
    val colors = MaterialTheme.colorScheme
    val background = when (state) {
        MealServiceState.OPEN -> colors.primary
        MealServiceState.CLOSED -> colors.surfaceContainer
        else -> colors.secondaryContainer
    }
    val foreground = when (state) {
        MealServiceState.OPEN -> colors.onPrimary
        MealServiceState.CLOSED -> colors.onSurfaceVariant
        else -> colors.onSecondaryContainer
    }
    Surface(shape = DimaShapes.Badge, color = background) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** 이번 주 기숙사 식단이 하나도 없을 때의 단일 안내 카드 (D-056), 공용 EmptyState 문법 (D-094(14)). */
@Composable
private fun DormitoryWeekEmptyCard(
    modifier: Modifier = Modifier,
    onUpload: (() -> Unit)? = null,
    uploadEnabled: Boolean = true,
) {
    EmptyState(
        message = "이번 주 기숙사 식단이 아직 없어요",
        supporting = "식단표를 올리면 함께 볼 수 있어요.",
        icon = Icons.Default.Restaurant,
        actionLabel = "식단표 사진 올리기",
        onAction = onUpload,
        actionIcon = Icons.Default.PhotoCamera,
        actionEnabled = uploadEnabled,
        actionTag = "dormitory_week_empty_upload",
        modifier = modifier.testTag("dormitory_week_empty"),
    )
}

/** 사진 업로드 후 서버 확인이 끝날 때까지 남아 있는 진행 카드 (D-056). */
@Composable
private fun DormitoryUploadProgressCard(modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dormitory_upload_progress"),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "식단표를 확인하고 있어요",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "앱을 닫아도 계속 진행돼요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
