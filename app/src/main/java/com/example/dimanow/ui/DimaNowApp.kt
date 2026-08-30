package com.example.dimanow.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.example.dimanow.ui.motion.AnimatedCountText
import com.example.dimanow.ui.motion.expressiveBounceClick
import com.example.dimanow.ui.motion.pulseBreath
import com.example.dimanow.ui.motion.staggeredEntrance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.activity.compose.BackHandler
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.Switch
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.data.AppPreferences
import com.example.dimanow.data.CampusDataRepository
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.DefaultCampusZones
import com.example.dimanow.domain.DefaultSchedule
import com.example.dimanow.domain.DisplayVocabulary
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.GuidanceSnapshot
import com.example.dimanow.domain.GuidancePause
import com.example.dimanow.domain.MealValidationState
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.domain.TermSchedule
import com.example.dimanow.guidance.GuidanceEngine
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.guidance.AnnotatedServiceDeparture
import com.example.dimanow.guidance.ShuttleBoardPurpose
import com.example.dimanow.live.LiveChipContent
import com.example.dimanow.live.LiveClassOrder
import com.example.dimanow.live.LiveDisplayOptions
import com.example.dimanow.live.LivePromotionReadiness
import com.example.dimanow.live.LiveSettingsDestination
import com.example.dimanow.live.LiveSurfaceController
import com.example.dimanow.meal.MealData
import com.example.dimanow.meal.MealSource
import com.example.dimanow.meal.mealServiceStatus
import com.example.dimanow.notice.NoticeData
import com.example.dimanow.notice.NoticeSource
import com.example.dimanow.notice.OFFICIAL_NOTICE_SOURCE_URL
import com.example.dimanow.meal.OFFICIAL_MEAL_SOURCE_URL
import com.example.dimanow.shuttle.OFFICIAL_SHUTTLE_SOURCE_URL
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.shuttle.ShuttleSource
import com.example.dimanow.theme.SuccessGreen
import com.example.dimanow.theme.WarningAmber
import com.example.dimanow.time.MinuteTicker
import com.example.dimanow.location.LocationMode
import com.example.dimanow.update.AppUpdateCoordinator
import com.example.dimanow.update.AppUpdatePhase
import com.example.dimanow.update.AppUpdateUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.launch

internal enum class AppPage(val title: String, val icon: ImageVector) {
    DASHBOARD("홈", Icons.Default.Home),
    TIMETABLE("시간표", Icons.Default.Schedule),
    SHUTTLE("셔틀", Icons.Default.DirectionsBus),
    MEAL("식단", Icons.Default.Restaurant),
    SETTINGS("설정", Icons.Default.Settings),
    ;

    val usesMinuteTicker: Boolean
        get() = this == DASHBOARD || this == SHUTTLE || this == MEAL
}

@Composable
private fun rememberMinuteNow(): ZonedDateTime {
    val context = LocalContext.current.applicationContext
    val ticker = remember { MinuteTicker() }
    val flow = remember(context, ticker) { ticker.ticksWithSystemChanges(context) }
    val now by flow.collectAsStateWithLifecycle(
        initialValue = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE),
    )
    return now
}

@Composable
fun DimaNowApp(
    repository: CampusDataRepository,
    preferences: AppPreferences,
    shuttleSource: ShuttleSource,
    mealSource: MealSource,
    liveSurfaceController: LiveSurfaceController,
    appUpdateCoordinator: AppUpdateCoordinator? = null,
    noticeSource: NoticeSource? = null,
    initialTargetPage: String? = null,
) {
    var page by remember { mutableStateOf(AppPage.DASHBOARD) }
    val homeBaseConfirmed by preferences.homeBaseSelectionConfirmed.collectAsStateWithLifecycle(initialValue = false)
    val nowBarSetupCompleted by preferences.nowBarSetupCompleted.collectAsStateWithLifecycle(initialValue = false)
    var showNowBarSetup by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val initialUpdateState = remember { AppUpdateUiState(currentVersion = "") }
    val updateState by (appUpdateCoordinator?.state ?: remember { kotlinx.coroutines.flow.flowOf(initialUpdateState) })
        .collectAsStateWithLifecycle(initialValue = initialUpdateState)
    LaunchedEffect(initialTargetPage) {
        when (initialTargetPage) {
            "SHUTTLE" -> page = AppPage.SHUTTLE
            "MEAL" -> page = AppPage.MEAL
            "TIMETABLE" -> page = AppPage.TIMETABLE
            "SETTINGS" -> page = AppPage.SETTINGS
        }
    }

    BackHandler(enabled = page != AppPage.DASHBOARD) {
        page = AppPage.DASHBOARD
    }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        // 상태바 인셋을 바깥 패딩이 아닌 각 화면의 contentPadding으로 처리해
        // 스크롤 콘텐츠가 상태바 뒤까지 흐르게 한다 (상단 빈 띠 제거)
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 8.dp,
            ) {
                AppPage.entries.forEach { item ->
                    val selected = page == item
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "nav_icon_scale_${item.name}",
                    )
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav_${item.name}"),
                        selected = selected,
                        onClick = { page = item },
                        icon = {
                            Icon(
                                item.icon,
                                contentDescription = item.title,
                                modifier = Modifier.scale(iconScale),
                            )
                        },
                        label = { Text(item.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val slideOffset = { width: Int -> if (forward) width / 4 else -width / 4 }
                slideInHorizontally(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                    initialOffsetX = slideOffset,
                ).togetherWith(
                    slideOutHorizontally(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        targetOffsetX = { width -> -slideOffset(width) },
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 180),
                    ),
                ).apply { targetContentZIndex = 1f }
            },
            label = "tab_transition",
        ) { targetPage ->
            val minuteNow = if (targetPage.usesMinuteTicker) rememberMinuteNow() else null
            when (targetPage) {
                AppPage.DASHBOARD -> DashboardRoute(
                    repository = repository,
                    preferences = preferences,
                    shuttleSource = shuttleSource,
                    mealSource = mealSource,
                    noticeSource = noticeSource,
                    onNavigateToPage = { page = it },
                    modifier = Modifier.padding(padding),
                    now = requireNotNull(minuteNow),
                )
                AppPage.TIMETABLE -> TimetableRoute(repository, Modifier.padding(padding))
                AppPage.SHUTTLE -> ShuttleRoute(
                    preferences,
                    shuttleSource,
                    Modifier.padding(padding),
                    requireNotNull(minuteNow),
                )
                AppPage.MEAL -> MealScreen(
                    mealSource,
                    Modifier.padding(padding),
                    requireNotNull(minuteNow).toLocalDate(),
                    minuteNow.toLocalTime(),
                )
                AppPage.SETTINGS -> SettingsRoute(
                    preferences = preferences,
                    shuttleSource = shuttleSource,
                    mealSource = mealSource,
                    liveSurfaceController = liveSurfaceController,
                    onShowNowBarSetup = { showNowBarSetup = true },
                    updateState = updateState,
                    onCheckUpdate = { appUpdateCoordinator?.checkManually() },
                    onDownloadUpdate = { appUpdateCoordinator?.downloadAndInstall() },
                    onContinueInstall = { appUpdateCoordinator?.continueInstall() },
                    onCancelDownload = { appUpdateCoordinator?.cancelDownload() },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (!homeBaseConfirmed) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("귀가 기준지 선택") },
            text = { Text("수업 중·수업 후 본관에서 안내할 셔틀 방향을 선택하세요. 설정에서 언제든 바꿀 수 있습니다.") },
            confirmButton = {
                Button(
                    onClick = { scope.launch { preferences.setHomeBase(HomeBase.YEIN) } },
                    modifier = Modifier.testTag("home_base_yein"),
                ) { Text("예인관") }
            },
            dismissButton = {
                OutlinedButton(onClick = { scope.launch { preferences.setHomeBase(HomeBase.ONE_ROOM) } }) { Text("원룸촌") }
            },
        )
    }
    if (updateState.promptVersion != null) {
        AlertDialog(
            onDismissRequest = { appUpdateCoordinator?.dismissPrompt() },
            title = { Text("업데이트 가능") },
            text = { Text("DIMA Now ${updateState.promptVersion} 버전을 설치할 수 있습니다.") },
            confirmButton = {
                Button(onClick = { appUpdateCoordinator?.downloadAndInstall() }) { Text("다운로드 및 설치") }
            },
            dismissButton = {
                TextButton(onClick = { appUpdateCoordinator?.dismissPrompt() }) { Text("나중에") }
            },
        )
    }
    if (showNowBarSetup || (homeBaseConfirmed && !nowBarSetupCompleted)) {
        NowBarSetupDialog(
            onOpenLockScreenNotifications = { liveSurfaceController.openPromotionSettings() },
            onOpenDeveloperOptions = { openDeveloperOptions(appContext) },
            onComplete = {
                scope.launch { preferences.setNowBarSetupCompleted(true) }
                showNowBarSetup = false
            },
        )
    }
}

@Composable
private fun DashboardRoute(
    repository: CampusDataRepository,
    preferences: AppPreferences,
    shuttleSource: ShuttleSource,
    mealSource: MealSource,
    noticeSource: NoticeSource?,
    onNavigateToPage: (AppPage) -> Unit,
    modifier: Modifier,
    now: ZonedDateTime,
) {
    val schedule by repository.schedule.collectAsStateWithLifecycle(initialValue = DefaultSchedule.create())
    val zones by repository.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val resolvedZone by preferences.effectiveZone.collectAsStateWithLifecycle(initialValue = CampusZoneId.OUTSIDE)
    val locationMode by preferences.locationMode.collectAsStateWithLifecycle(initialValue = LocationMode.GPS)
    val shuttle by shuttleSource.data.collectAsStateWithLifecycle(
        initialValue = ShuttleData(emptyList(), null, null, null, OFFICIAL_SHUTTLE_SOURCE_URL, null),
    )
    val meal by mealSource.data.collectAsStateWithLifecycle(
        initialValue = MealData(emptyList(), null, null, null, OFFICIAL_MEAL_SOURCE_URL, null, null),
    )
    val homeBase by preferences.homeBase.collectAsStateWithLifecycle(initialValue = HomeBase.YEIN)
    val emptyNotices = remember { NoticeData(emptyList(), null, null, null, OFFICIAL_NOTICE_SOURCE_URL) }
    val notices by (noticeSource?.data ?: remember { kotlinx.coroutines.flow.flowOf(emptyNotices) })
        .collectAsStateWithLifecycle(initialValue = emptyNotices)

    DashboardScreen(
        schedule = schedule,
        zone = resolvedZone,
        hasSavedOrigin = zones.any { it.id == resolvedZone },
        testMode = locationMode == LocationMode.TEST,
        automatic = true,
        shuttle = shuttle,
        meal = meal,
        homeBase = homeBase,
        notices = notices,
        onNavigateToPage = onNavigateToPage,
        modifier = modifier,
        now = now,
    )
}

@Composable
private fun TimetableRoute(repository: CampusDataRepository, modifier: Modifier) {
    val schedule by repository.schedule.collectAsStateWithLifecycle(initialValue = DefaultSchedule.create())
    TimetableScreen(repository, schedule, modifier)
}

@Composable
private fun ShuttleRoute(
    preferences: AppPreferences,
    shuttleSource: ShuttleSource,
    modifier: Modifier,
    now: ZonedDateTime,
) {
    val resolvedZone by preferences.effectiveZone.collectAsStateWithLifecycle(initialValue = CampusZoneId.OUTSIDE)
    ShuttleScreen(shuttleSource, resolvedZone, modifier, now)
}

@Composable
private fun SettingsRoute(
    preferences: AppPreferences,
    shuttleSource: ShuttleSource,
    mealSource: MealSource,
    liveSurfaceController: LiveSurfaceController,
    onShowNowBarSetup: () -> Unit,
    updateState: AppUpdateUiState,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onContinueInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val locationMode by preferences.locationMode.collectAsStateWithLifecycle(initialValue = LocationMode.GPS)
    val testZone by preferences.testZone.collectAsStateWithLifecycle(initialValue = CampusZoneId.OUTSIDE)
    val resolvedZone by preferences.effectiveZone.collectAsStateWithLifecycle(initialValue = CampusZoneId.OUTSIDE)
    val displayOptions by preferences.liveDisplayOptions.collectAsStateWithLifecycle(initialValue = LiveDisplayOptions())
    val homeBase by preferences.homeBase.collectAsStateWithLifecycle(initialValue = HomeBase.YEIN)
    val shuttle by shuttleSource.data.collectAsStateWithLifecycle(
        initialValue = ShuttleData(emptyList(), null, null, null, OFFICIAL_SHUTTLE_SOURCE_URL, null),
    )
    val meal by mealSource.data.collectAsStateWithLifecycle(
        initialValue = MealData(emptyList(), null, null, null, OFFICIAL_MEAL_SOURCE_URL, null, null),
    )

    SettingsScreen(
        locationMode = locationMode,
        testZone = testZone,
        onTestModeChange = { enabled ->
            scope.launch { preferences.setTestLocationMode(enabled, if (enabled) resolvedZone else testZone) }
        },
        onTestZone = { scope.launch { preferences.setTestZone(it) } },
        liveSurfaceController = liveSurfaceController,
        displayOptions = displayOptions,
        onChipContentChange = { scope.launch { preferences.setLiveChipContent(it) } },
        onClassOrderChange = { scope.launch { preferences.setLiveClassOrder(it) } },
        homeBase = homeBase,
        onHomeBaseChange = { scope.launch { preferences.setHomeBase(it) } },
        shuttleData = shuttle,
        mealData = meal,
        onShowNowBarSetup = onShowNowBarSetup,
        updateState = updateState,
        onCheckUpdate = onCheckUpdate,
        onDownloadUpdate = onDownloadUpdate,
        onContinueInstall = onContinueInstall,
        onCancelDownload = onCancelDownload,
        modifier = modifier,
    )
}

// -----------------------------------------------------------------------------
// 1. 홈 화면 (Dashboard)
// -----------------------------------------------------------------------------
@Composable
internal fun DashboardScreen(
    schedule: TermSchedule,
    zone: CampusZoneId,
    hasSavedOrigin: Boolean,
    testMode: Boolean = false,
    automatic: Boolean,
    shuttle: ShuttleData,
    meal: MealData,
    homeBase: HomeBase = HomeBase.YEIN,
    notices: NoticeData = NoticeData(emptyList(), null, null, null, OFFICIAL_NOTICE_SOURCE_URL),
    onNavigateToPage: (AppPage) -> Unit = {},
    modifier: Modifier = Modifier,
    now: ZonedDateTime = ZonedDateTime.now(),
) {
    val context = LocalContext.current
    val nowTime = now.toLocalTime()
    val guidancePaused = now.toLocalDate() in schedule.noClassDates || schedule.guidancePause?.contains(now.toLocalDate()) == true
    val todayCourses = schedule.courses
        .filter { it.weekday == now.dayOfWeek }
        .filterNot { guidancePaused }
        .sortedBy { it.start }
    val remainingCourses = todayCourses.filter { !it.end.isBefore(nowTime) }
    val nextCourse = remainingCourses.firstOrNull()
    val upcomingAfterCourse = remainingCourses.getOrNull(1)

    val shuttleBoard = GuidanceEngine().shuttleBoard(
        now = now,
        originZone = zone,
        departures = shuttle.departures,
        purpose = ShuttleBoardPurpose.GENERAL,
    )
    val guidanceSnapshot = GuidanceEngine().snapshot(
        now = now,
        termStart = schedule.termStart,
        termEnd = schedule.termEnd,
        courses = schedule.courses,
        noClassDates = schedule.noClassDates,
        resolvedZone = zone,
        automaticClassGuidance = automatic,
        shuttleDepartures = shuttle.departures,
        homeBase = homeBase,
        guidancePause = schedule.guidancePause,
    )

    val todayMeal = meal.days.firstOrNull { it.date == now.toLocalDate() }
    val mealServiceStatus = meal.serviceStatusAt(now)
    val originName = DisplayVocabulary.originName(zone)

    ScreenColumn(
        modifier = modifier,
        customTopBar = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "위치: $originName",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
    ) {
        if (testMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(0),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = "테스트 모드 · GPS 미반영",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        // 1. M3 Expressive Hero 수업 브리핑 카드
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(1)
                .expressiveBounceClick { onNavigateToPage(AppPage.TIMETABLE) },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 브리핑 본문 (수업 일정)
                if (nextCourse != null) {
                    val isOngoing = !nowTime.isBefore(nextCourse.start)
                    val remainingMillis = java.time.Duration.between(now, now.toLocalDate().atTime(nextCourse.start).atZone(now.zone)).toMillis()
                    val mins = ((remainingMillis.coerceAtLeast(0) + 59_999L) / 60_000L)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isOngoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = if (isOngoing) Modifier.pulseBreath() else Modifier,
                            ) {
                                AnimatedCountText(
                                    text = if (isOngoing) "수업 중" else "시작까지 ${mins}분",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOngoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            Text(
                                text = "${nextCourse.start.format(TIME)} ~ ${nextCourse.end.format(TIME)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                        Text(
                            text = nextCourse.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = if (nextCourse.professor.isNotBlank()) "${nextCourse.room} · ${nextCourse.professor}" else nextCourse.room,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                        )
                        guidanceSnapshot.shuttleLines.forEach { line ->
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // 오늘의 다음 수업 표시 (있을 경우)
                    if (upcomingAfterCourse != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        text = "다음 수업 · ${upcomingAfterCourse.start.format(TIME)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text(
                                    text = "${upcomingAfterCourse.name} (${upcomingAfterCourse.room})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                } else {
                    // 다음 수업일(내일부터 최대 7일, 휴강일 제외)의 첫 수업 미리보기
                    val nextClassPreview = remember(schedule, now) {
                        (1L..7L).asSequence()
                            .map { now.toLocalDate().plusDays(it) }
                            .filterNot { date ->
                                date in schedule.noClassDates || schedule.guidancePause?.contains(date) == true
                            }
                            .filter { !it.isBefore(schedule.termStart) && !it.isAfter(schedule.termEnd) }
                            .mapNotNull { date ->
                                schedule.courses
                                    .filter { it.weekday == date.dayOfWeek }
                                    .minByOrNull { it.start }
                                    ?.let { date to it }
                            }
                            .firstOrNull()
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (guidancePaused) "오늘은 휴강입니다" else "오늘 남은 수업이 없습니다",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (nextClassPreview != null) {
                            val (date, course) = nextClassPreview
                            val dayLabel = if (date == now.toLocalDate().plusDays(1)) {
                                "내일 첫 수업"
                            } else {
                                "${koreanWeekdayLabel(date.dayOfWeek)} 첫 수업"
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    ) {
                                        Text(
                                            text = "$dayLabel · ${course.start.format(TIME)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Text(
                                        text = "${course.name} (${course.room})",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                // 하단 바로가기 힌트
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "시간표 전체보기",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // 2. 실시간 셔틀 카드
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(2)
                .expressiveBounceClick { onNavigateToPage(AppPage.SHUTTLE) },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text(
                            text = "셔틀 ($originName 출발)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("시간표", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }

                val todayZoneDepartures = shuttle.departures.filter { it.serviceDay == now.dayOfWeek && it.originZone == zone }
                val destinations = todayZoneDepartures.mapNotNull { it.destinationZone }.distinct().sortedBy { it.ordinal }

                when {
                    shuttle.departures.isEmpty() -> {
                        Text("셔틀 데이터를 새로고침해 주세요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    zone == CampusZoneId.OUTSIDE -> {
                        Text("캠퍼스 외부 — 교내 진입 시 자동 안내", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    destinations.isNotEmpty() -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            destinations.forEach { destZone ->
                                val destName = DisplayVocabulary.originName(destZone)
                                val annotated = GuidanceEngine().annotatedServiceDepartures(
                                    serviceDay = now.dayOfWeek,
                                    originZone = zone,
                                    destinationZone = destZone,
                                    departures = shuttle.departures,
                                )
                                val upcoming = shuttleBoard.rows.firstOrNull { it.destinationZone == destZone }?.departures.orEmpty()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    // 캡슐 좌측에 항상 크게 표시되는 목적지 태그
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    ) {
                                        Text(
                                            text = "${destName}행",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        )
                                    }

                                    if (upcoming.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            upcoming.forEachIndexed { index, countdown ->
                                                val time = countdown.departure.time
                                                val minutesLeft = countdown.remainingMinutes
                                                val isFirst = index == 0
                                                val serviceDeparture = annotated.firstOrNull { it.departure.time == time }
                                                val isLastService = serviceDeparture?.isLast == true
                                                val serviceLabel = when {
                                                    serviceDeparture?.isFirst == true && isLastService -> "첫차·막차"
                                                    serviceDeparture?.isFirst == true -> "첫차"
                                                    isLastService -> "막차"
                                                    else -> null
                                                }
                                                val boardingStopLabel = when {
                                                    serviceDeparture?.isBoardingStopTransition == true -> "운동장 전환"
                                                    serviceDeparture?.isStadiumStop == true -> "운동장"
                                                    else -> null
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = when {
                                                        isLastService -> MaterialTheme.colorScheme.errorContainer
                                                        isFirst -> MaterialTheme.colorScheme.primaryContainer
                                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        AnimatedCountText(
                                                            text = buildString {
                                                                append(if (minutesLeft <= 0) "곧 출발" else "${minutesLeft}분 후")
                                                                serviceLabel?.let { append(" · $it") }
                                                                boardingStopLabel?.let { append(" · $it") }
                                                            },
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = when {
                                                                isLastService -> MaterialTheme.colorScheme.onErrorContainer
                                                                isFirst -> MaterialTheme.colorScheme.onPrimaryContainer
                                                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                            },
                                                        )
                                                        Text(
                                                            text = time.format(TIME),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = when {
                                                                isLastService -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                                                isFirst -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                                else -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // 운행 종료 상태 (첫차 및 막차 시각 함께 표시)
                                        val firstDepartureTime = annotated.firstOrNull()?.departure?.time
                                        val lastDepartureTime = annotated.lastOrNull()?.departure?.time
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = "운행 종료",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                if (firstDepartureTime != null && lastDepartureTime != null) {
                                                    Text(
                                                        text = "첫차 ${firstDepartureTime.format(TIME)} · 막차 ${lastDepartureTime.format(TIME)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Text("오늘 운행 일정이 없습니다", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 3. 오늘의 학생식당 카드
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(3)
                .expressiveBounceClick { onNavigateToPage(AppPage.MEAL) },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text(text = "학생식당", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = mealServiceStatus.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }

                when {
                    todayMeal != null && todayMeal.menuLines.isNotEmpty() -> {
                        Text(
                            text = todayMeal.menuLines.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp,
                        )
                    }
                    now.dayOfWeek.value >= 6 -> {
                        Text("주말은 학생식당을 운영하지 않습니다", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Text("오늘 등록된 식단 정보가 없습니다", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 4. 학교 공지 카드 (최근 3건, 행 탭 시 해당 공지로 이동)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(4),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveBounceClick { openUrl(context, notices.sourceUrl) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text(text = "학교 공지", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("전체보기", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }

                val latestNotices = notices.notices.take(3)
                if (latestNotices.isEmpty()) {
                    Text(
                        text = "공지를 불러오는 중이거나 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        latestNotices.forEach { notice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .expressiveBounceClick { openUrl(context, notice.url) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = notice.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${notice.date.monthValue}.${notice.date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. 학교 서비스 바로가기 (DIMA Portal · LMS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(5),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { openUrl(context, "https://portal.dima.ac.kr/") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("DIMA Portal", fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(
                onClick = { openUrl(context, "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("LMS", fontWeight = FontWeight.Bold)
            }
        }

    }
}

@Composable
fun GuidanceCard(snapshot: GuidanceSnapshot, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            snapshot.classContent?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(text = it.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(text = it.detail, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            if (snapshot.shuttleLines.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                snapshot.shuttleLines.forEach { line ->
                    Text(text = line.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 2. 시간표 화면 (Timetable)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimetableScreen(repository: CampusDataRepository, schedule: TermSchedule, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Course?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showTermEditor by remember { mutableStateOf(false) }
    var showPauseChoice by remember { mutableStateOf(false) }
    var pausePickerMode by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Course?>(null) }
    val today = LocalDate.now(MinuteTicker.CAMPUS_ZONE)

    BackHandler(
        enabled = showEditor || showTermEditor || showPauseChoice || pausePickerMode != null || pendingDelete != null,
    ) {
        when {
            pendingDelete != null -> pendingDelete = null
            pausePickerMode != null -> pausePickerMode = null
            showPauseChoice -> showPauseChoice = false
            showTermEditor -> showTermEditor = false
            showEditor -> showEditor = false
        }
    }

    ScreenColumn(title = "시간표", modifier = modifier) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(0),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "학기 기간", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${schedule.termStart} ~ ${schedule.termEnd}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(onClick = { showTermEditor = true }, shape = RoundedCornerShape(12.dp)) {
                    Text("기간 변경")
                }
            }
        }

        Button(
            onClick = { editing = null; showEditor = true },
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(1),
            shape = RoundedCornerShape(14.dp),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("수업 추가", fontWeight = FontWeight.Bold)
        }

        val grouped = schedule.courses
            .sortedWith(compareBy<Course> { it.weekday.value }.thenBy { it.start })
            .groupBy { it.weekday }

        // 요일 헤더·수업 카드·휴강 카드까지 순차 입장하도록 실행 인덱스를 이어간다
        var entranceIndex = 2
        if (grouped.isEmpty()) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(entranceIndex++),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(text = "등록된 수업이 없습니다", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            }
        } else {
            grouped.forEach { (weekday, courses) ->
                Text(
                    text = koreanWeekdayLabel(weekday),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp, start = 4.dp)
                        .staggeredEntrance(entranceIndex++),
                )
                courses.forEach { course ->
                    CourseSummaryCard(
                        course = course,
                        onEdit = { editing = course; showEditor = true },
                        onDelete = { pendingDelete = course },
                        modifier = Modifier.staggeredEntrance(entranceIndex++),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(entranceIndex),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(text = "휴강 모드", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = schedule.guidancePause?.let { pauseLabel(it, today) } ?: "정상 수업 안내 중",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = schedule.guidancePause?.contains(today) == true,
                        onCheckedChange = { enabled ->
                            if (enabled) showPauseChoice = true else scope.launch { repository.clearGuidancePause() }
                        },
                    )
                }
                if (schedule.noClassDates.isNotEmpty()) {
                    Text("기존 개별 휴강일", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (schedule.noClassDates.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                schedule.noClassDates.sorted().forEach { date ->
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = date.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { scope.launch { repository.removeNoClassDate(date) } }) {
                                Text("삭제", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        CourseEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { course -> scope.launch { repository.saveCourse(course) }; showEditor = false },
        )
    }
    if (showTermEditor) {
        TermEditorDialog(
            start = schedule.termStart,
            end = schedule.termEnd,
            onDismiss = { showTermEditor = false },
            onSave = { start, end ->
                scope.launch { repository.setTerm(start, end) }
                showTermEditor = false
            },
        )
    }
    if (showPauseChoice) {
        PauseDurationDialog(
            today = today,
            termEnd = schedule.termEnd,
            onSelection = { pause ->
                scope.launch { repository.setGuidancePause(pause) }
                showPauseChoice = false
            },
            onRangeRequested = {
                showPauseChoice = false
                pausePickerMode = "RANGE"
            },
            onDismiss = { showPauseChoice = false },
        )
    }
    if (pausePickerMode == "RANGE") {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pausePickerMode = null },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                    onClick = {
                        val start = Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        val end = Instant.ofEpochMilli(state.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        scope.launch { repository.setGuidancePause(GuidancePause(start, end)) }
                        pausePickerMode = null
                    },
                ) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { pausePickerMode = null }) { Text("취소") } },
        ) { DateRangePicker(state = state, modifier = Modifier.fillMaxSize()) }
    }
    pendingDelete?.let { course ->
        CourseDeleteConfirmationDialog(
            course = course,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                scope.launch { repository.deleteCourse(course.id) }
                pendingDelete = null
            },
        )
    }
}

@Composable
internal fun CourseDeleteConfirmationDialog(
    course: Course,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("수업을 삭제할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.name, fontWeight = FontWeight.Bold)
                Text("${koreanWeekdayLabel(course.weekday)} · ${course.start.format(TIME)}–${course.end.format(TIME)}")
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
internal fun PauseDurationDialog(
    today: LocalDate,
    termEnd: LocalDate,
    onSelection: (GuidancePause) -> Unit,
    onRangeRequested: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("휴강 기간") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onSelection(GuidancePause(today, today)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("오늘만") }
                FilledTonalButton(
                    onClick = onRangeRequested,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("기간 지정") }
                FilledTonalButton(
                    onClick = { onSelection(GuidancePause(today, termEnd)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("학기 종료일까지") }
                FilledTonalButton(
                    onClick = { onSelection(GuidancePause.untilDisabled(today)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("휴강모드를 다시 끌 때까지") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun pauseLabel(pause: GuidancePause, today: LocalDate): String = when {
    pause.isUntilDisabled -> "휴강모드를 다시 끌 때까지 휴강"
    pause.startDate == pause.endDateInclusive -> "${pause.endDateInclusive.monthValue}월 ${pause.endDateInclusive.dayOfMonth}일 휴강"
    today.isBefore(pause.startDate) -> "${pause.startDate.monthValue}월 ${pause.startDate.dayOfMonth}일부터 ${pause.endDateInclusive.monthValue}월 ${pause.endDateInclusive.dayOfMonth}일까지 휴강"
    else -> "${pause.endDateInclusive.monthValue}월 ${pause.endDateInclusive.dayOfMonth}일까지 휴강"
}

@Composable
fun CourseSummaryCard(
    course: Course,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .expressiveBounceClick { onEdit() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${course.start.format(TIME)} – ${course.end.format(TIME)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        text = course.room,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (course.professor.isNotBlank()) {
                    Text(
                        text = course.professor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "수업 수정",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "수업 삭제",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

fun koreanWeekdayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "월요일"
    DayOfWeek.TUESDAY -> "화요일"
    DayOfWeek.WEDNESDAY -> "수요일"
    DayOfWeek.THURSDAY -> "목요일"
    DayOfWeek.FRIDAY -> "금요일"
    DayOfWeek.SATURDAY -> "토요일"
    DayOfWeek.SUNDAY -> "일요일"
}

@Composable
private fun TermEditorDialog(start: LocalDate, end: LocalDate, onDismiss: () -> Unit, onSave: (LocalDate, LocalDate) -> Unit) {
    var startText by remember(start) { mutableStateOf(start.toString()) }
    var endText by remember(end) { mutableStateOf(end.toString()) }
    val parsedStart = runCatching { LocalDate.parse(startText) }.getOrNull()
    val parsedEnd = runCatching { LocalDate.parse(endText) }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("학기 기간 설정", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = startText, onValueChange = { startText = it }, label = { Text("시작 (YYYY-MM-DD)") }, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = endText, onValueChange = { endText = it }, label = { Text("종료 (YYYY-MM-DD)") }, shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = {
            Button(
                enabled = parsedStart != null && parsedEnd != null && !parsedEnd.isBefore(parsedStart),
                onClick = { onSave(requireNotNull(parsedStart), requireNotNull(parsedEnd)) },
                shape = RoundedCornerShape(10.dp),
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun CourseEditorDialog(initial: Course?, onDismiss: () -> Unit, onSave: (Course) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var room by remember(initial) { mutableStateOf(initial?.room.orEmpty()) }
    var professor by remember(initial) { mutableStateOf(initial?.professor.orEmpty()) }
    var day by remember(initial) { mutableStateOf(initial?.weekday ?: DayOfWeek.MONDAY) }
    var start by remember(initial) { mutableStateOf(initial?.start ?: LocalTime.of(10, 0)) }
    var end by remember(initial) { mutableStateOf(initial?.end ?: LocalTime.of(11, 0)) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && end.isAfter(start)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "수업 추가" else "수업 수정", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("수업명") }, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("강의실") }, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = professor, onValueChange = { professor = it }, label = { Text("담당 교수") }, shape = RoundedCornerShape(12.dp))

                Text("요일 선택", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DayOfWeek.entries.forEach { option ->
                        FilterChip(
                            selected = day == option,
                            onClick = { day = option },
                            label = { Text(koreanWeekdayLabel(option).removeSuffix("요일")) },
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("시작 ${start.format(TIME)}")
                    }
                    FilledTonalButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("종료 ${end.format(TIME)}")
                    }
                }
                if (!end.isAfter(start)) {
                    Text("종료 시간은 시작 시간보다 늦어야 합니다", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        Course(
                            weekday = day, start = start, end = end,
                            name = name, room = room, professor = professor, zone = CampusZoneId.MAIN, id = initial?.id ?: 0,
                        ),
                    )
                },
                shape = RoundedCornerShape(10.dp),
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )

    if (showStartPicker) {
        CourseTimePickerDialog(title = "시작 시간", initial = start, onDismiss = { showStartPicker = false }, onConfirm = { start = it; showStartPicker = false })
    }
    if (showEndPicker) {
        CourseTimePickerDialog(title = "종료 시간", initial = end, onDismiss = { showEndPicker = false }, onConfirm = { end = it; showEndPicker = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseTimePickerDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { TimePicker(state = state) },
        confirmButton = {
            Button(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }, shape = RoundedCornerShape(10.dp)) {
                Text("확인")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

// -----------------------------------------------------------------------------
// 3. 셔틀 전용 화면 (Shuttle)
// -----------------------------------------------------------------------------
@Composable
fun ShuttleScreen(
    shuttleSource: ShuttleSource,
    currentZone: CampusZoneId,
    modifier: Modifier = Modifier,
    now: ZonedDateTime = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE),
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val shuttle by shuttleSource.data.collectAsStateWithLifecycle(
        initialValue = ShuttleData(emptyList(), null, null, null, OFFICIAL_SHUTTLE_SOURCE_URL, null),
    )
    var refreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf(now.dayOfWeek) }
    val isToday = selectedDay == now.dayOfWeek
    val nowTime = now.toLocalTime()

    LaunchedEffect(refreshMessage) {
        refreshMessage?.let {
            snackbarHostState.showSnackbar(it)
            refreshMessage = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    ScreenColumn(
        title = "셔틀버스",
        modifier = Modifier.fillMaxSize(),
        topAction = {
            IconButton(
                enabled = !refreshing,
                onClick = {
                    refreshing = true
                    refreshMessage = null
                    scope.launch {
                        try {
                            refreshMessage = when (val result = shuttleSource.refresh()) {
                                is com.example.dimanow.shuttle.ShuttleRefreshResult.Success -> "${result.departureCount}건 저장 완료"
                                is com.example.dimanow.shuttle.ShuttleRefreshResult.Failure -> "실패: ${result.message}"
                            }
                        } finally {
                            refreshing = false
                        }
                    }
                },
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).semantics { contentDescription = "셔틀 새로고침 중" },
                        strokeWidth = 2.5.dp,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "셔틀 새로고침")
                }
            }
        },
    ) {
        // M3 Expressive Connected Button Group (월~일 요일 선택)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val weekdays = DayOfWeek.entries
            weekdays.forEachIndexed { index, day ->
                val isSelected = selectedDay == day
                // M3 Expressive shape morphing: 선택된 항목은 완전한 필 형태로 부풀어 오른다
                val innerRadius by animateDpAsState(
                    targetValue = if (isSelected) 18.dp else 4.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "btn_shape_${day.name}",
                )
                val itemShape = when (index) {
                    0 -> RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = innerRadius, bottomEnd = innerRadius)
                    weekdays.lastIndex -> RoundedCornerShape(topStart = innerRadius, bottomStart = innerRadius, topEnd = 18.dp, bottomEnd = 18.dp)
                    else -> RoundedCornerShape(innerRadius)
                }
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "btn_bg_${day.name}",
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "btn_content_${day.name}",
                )
                Surface(
                    onClick = { selectedDay = day },
                    shape = itemShape,
                    color = bgColor,
                    contentColor = contentColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = koreanWeekdayLabel(day).take(1),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        )
                        // 다른 요일을 보고 있어도 '오늘' 위치를 알 수 있는 점 마커
                        if (day == now.dayOfWeek) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 5.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    ),
                            )
                        }
                    }
                }
            }
        }

        // 선택된 요일의 노선별 셔틀 목록 (현재 위치 출발 우선 정렬)
        AnimatedContent(
            targetState = selectedDay,
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { 40 })
                    .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessMediumLow)))
            },
            label = "shuttle_day_list",
        ) { activeDay ->
            val dayDepartures = remember(shuttle.departures, activeDay) {
                shuttle.departures.filter { it.serviceDay == activeDay }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
        if (dayDepartures.isEmpty()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text(
                    text = "${koreanWeekdayLabel(activeDay)}은 운행 일정이 없습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            val guidanceEngine = remember { GuidanceEngine() }
            val originGroups = remember(dayDepartures, currentZone) {
                dayDepartures
                    .groupBy { it.originZone }
                    .toList()
                    .sortedWith(
                        compareByDescending<Pair<CampusZoneId, List<ShuttleDeparture>>> {
                            it.first == currentZone && currentZone != CampusZoneId.OUTSIDE
                        }.thenBy { it.first.ordinal }
                    )
            }

            originGroups.forEach { (originZone, originDepartures) ->
                val isCurrentLocation = originZone == currentZone && currentZone != CampusZoneId.OUTSIDE
                val origin = DisplayVocabulary.originName(originZone)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = origin,
                        style = if (isCurrentLocation) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (isCurrentLocation) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.pulseBreath(minAlpha = 0.7f),
                        ) {
                            Text(
                                text = "현재 위치",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                val routes = originDepartures
                    .groupBy { it.destinationZone }
                    .toList()
                    .sortedBy { it.first?.ordinal ?: Int.MAX_VALUE }
                routes.forEach { (destinationZone, routeDepartures) ->
                val destination = destinationZone?.let(DisplayVocabulary::originName)
                val annotatedDepartures = remember(shuttle.departures, activeDay, originZone, destinationZone) {
                    if (destinationZone != null) {
                        guidanceEngine.annotatedServiceDepartures(
                            serviceDay = activeDay,
                            originZone = originZone,
                            destinationZone = destinationZone,
                            departures = shuttle.departures,
                        )
                    } else {
                        val slots = routeDepartures.sortedBy { it.time }.distinctBy { it.time }
                        slots.mapIndexed { index, departure ->
                            AnnotatedServiceDeparture(
                                departure = departure,
                                isFirst = index == 0,
                                isLast = index == slots.lastIndex,
                            )
                        }
                    }
                }
                val sortedTimes = annotatedDepartures.map { it.departure.time }

                val upcomingCountdowns = remember(shuttle.departures, now, isToday, originZone, destinationZone) {
                    if (isToday && destinationZone != null) {
                        guidanceEngine.shuttleBoard(
                            now = now,
                            originZone = originZone,
                            departures = shuttle.departures,
                            purpose = ShuttleBoardPurpose.GENERAL,
                        ).rows.firstOrNull { it.destinationZone == destinationZone }?.departures.orEmpty()
                    } else emptyList()
                }
                val upcomingTimes = upcomingCountdowns.map { it.departure.time }

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isCurrentLocation) {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                            } else {
                                Modifier
                            },
                        ),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isCurrentLocation) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = destination?.let { "${it}행" } ?: "기타 목적지",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrentLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (sortedTimes.isNotEmpty()) {
                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                    Text(
                                        text = "총 ${sortedTimes.size}회 (첫차 ${sortedTimes.first().format(TIME)} · 막차 ${sortedTimes.last().format(TIME)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }

                        // 실시간 다가오는 셔틀 2개 남은 시간 뱃지 (오늘 요일일 때만)
                        if (isToday) {
                            if (upcomingTimes.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "다음 출발 예정",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        upcomingCountdowns.forEachIndexed { index, countdown ->
                                            val time = countdown.departure.time
                                            val minutesLeft = countdown.remainingMinutes
                                            val isFirst = index == 0
                                            val serviceDeparture = annotatedDepartures.firstOrNull { it.departure.time == time }
                                            val isLastService = serviceDeparture?.isLast == true
                                            val serviceLabel = when {
                                                serviceDeparture?.isFirst == true && isLastService -> "첫차·막차"
                                                serviceDeparture?.isFirst == true -> "첫차"
                                                isLastService -> "막차"
                                                else -> null
                                            }
                                            val boardingStopLabel = when {
                                                serviceDeparture?.isBoardingStopTransition == true -> "운동장 전환"
                                                serviceDeparture?.isStadiumStop == true -> "운동장"
                                                else -> null
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = when {
                                                    isFirst && isLastService -> MaterialTheme.colorScheme.error
                                                    isLastService -> MaterialTheme.colorScheme.errorContainer
                                                    isFirst -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("next_departure_$index"),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    AnimatedCountText(
                                                        text = buildString {
                                                            append(if (minutesLeft <= 0) "곧 출발" else "${minutesLeft}분 후")
                                                            serviceLabel?.let { append(" · $it") }
                                                            boardingStopLabel?.let { append(" · $it") }
                                                        },
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = when {
                                                            isFirst && isLastService -> MaterialTheme.colorScheme.onError
                                                            isLastService -> MaterialTheme.colorScheme.onErrorContainer
                                                            isFirst -> MaterialTheme.colorScheme.onPrimary
                                                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                        },
                                                    )
                                                    Text(
                                                        text = time.format(TIME),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = when {
                                                            isFirst && isLastService -> MaterialTheme.colorScheme.onError.copy(alpha = 0.9f)
                                                            isLastService -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                                            isFirst -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                                            else -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "오늘 운행이 모두 종료되었습니다",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }

                        // 전체 시간표 칩 (지나간 시간은 딤 처리)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "전체 시간표",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowTimeChips(
                                departures = annotatedDepartures,
                                isToday = isToday,
                                nowTime = nowTime,
                                upcomingTimes = upcomingTimes,
                                contextKey = "$activeDay|$originZone|$destinationZone|${sortedTimes.hashCode()}",
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
    SnackbarHost(
        snackbarHostState,
        Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    }
}

@Composable
private fun FlowTimeChips(
    departures: List<AnnotatedServiceDeparture>,
    isToday: Boolean,
    nowTime: LocalTime,
    upcomingTimes: List<LocalTime>,
    contextKey: String,
) {
    val times = departures.map { it.departure.time }
    // 요일 전환 애니메이션 도중 scrollToItem이 재레이아웃 프레임 드랍을 일으키지 않도록
    // 처음부터 현재 시각 인덱스로 리스트 상태를 생성한다.
    val state = remember(contextKey) {
        val initialIndex = when {
            times.isEmpty() -> 0
            !isToday -> 0
            else -> times.indexOfFirst { !it.isBefore(nowTime) }.takeIf { it >= 0 } ?: times.lastIndex
        }
        LazyListState(firstVisibleItemIndex = (initialIndex - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(departures, key = { _, item -> item.departure.time.toSecondOfDay() }) { _, item ->
            val time = item.departure.time
            val isPast = isToday && time.isBefore(nowTime)
            val isFirstUpcoming = isToday && upcomingTimes.getOrNull(0) == time
            val isSecondUpcoming = isToday && upcomingTimes.getOrNull(1) == time

            val bgColor = when {
                isFirstUpcoming && item.isLast -> MaterialTheme.colorScheme.error
                item.isLast -> MaterialTheme.colorScheme.errorContainer
                isFirstUpcoming -> MaterialTheme.colorScheme.primary
                isSecondUpcoming -> MaterialTheme.colorScheme.secondaryContainer
                item.isFirst -> MaterialTheme.colorScheme.tertiaryContainer
                isPast -> MaterialTheme.colorScheme.surfaceContainerLowest
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }

            val textColor = when {
                isFirstUpcoming && item.isLast -> MaterialTheme.colorScheme.onError
                item.isLast -> MaterialTheme.colorScheme.onErrorContainer
                isFirstUpcoming -> MaterialTheme.colorScheme.onPrimary
                isSecondUpcoming -> MaterialTheme.colorScheme.onSecondaryContainer
                item.isFirst -> MaterialTheme.colorScheme.onTertiaryContainer
                isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.onSurface
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = bgColor,
                modifier = Modifier.height(30.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = item.displayText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (item.isFirst || item.isLast || isFirstUpcoming || isSecondUpcoming) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}


// -----------------------------------------------------------------------------
// 4. 식단 전용 화면 (Meal) - 월~금 5일만 표시
// -----------------------------------------------------------------------------
@Composable
fun MealScreen(
    mealSource: MealSource,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(MinuteTicker.CAMPUS_ZONE),
    nowTime: LocalTime = LocalTime.now(MinuteTicker.CAMPUS_ZONE),
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val meal by mealSource.data.collectAsStateWithLifecycle(
        initialValue = MealData(emptyList(), null, null, null, OFFICIAL_MEAL_SOURCE_URL, null, null),
    )
    var refreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshMessage) {
        refreshMessage?.let {
            snackbarHostState.showSnackbar(it)
            refreshMessage = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    ScreenColumn(
        title = "식단표",
        modifier = Modifier.fillMaxSize(),
        topAction = {
            IconButton(
                enabled = !refreshing,
                onClick = {
                    refreshing = true
                    refreshMessage = null
                    scope.launch {
                        try {
                            refreshMessage = when (val result = mealSource.refresh()) {
                                is com.example.dimanow.meal.MealRefreshResult.Success -> "${result.weekStart} 주간 식단 저장 완료"
                                com.example.dimanow.meal.MealRefreshResult.NotPublishedYet -> "아직 새 식단이 올라오지 않았어요"
                                is com.example.dimanow.meal.MealRefreshResult.NeedsReview -> "확인 필요: ${result.reason}"
                                is com.example.dimanow.meal.MealRefreshResult.Failure -> "실패: ${result.message}"
                            }
                        } finally {
                            refreshing = false
                        }
                    }
                },
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).semantics { contentDescription = "식단 새로고침 중" },
                        strokeWidth = 2.5.dp,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "식단 새로고침")
                }
            }
        },
    ) {
        // 주간 식단: 월요일부터 금요일까지 5일만 표시 (토, 일 제외)
        WeeklyMealMenu(meal = meal, today = today, nowTime = nowTime)
    }
    SnackbarHost(
        snackbarHostState,
        Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    }
}

@Composable
fun WeeklyMealMenu(
    meal: MealData,
    today: LocalDate,
    nowTime: LocalTime = LocalTime.now(MinuteTicker.CAMPUS_ZONE),
    modifier: Modifier = Modifier,
) {
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekEnd = weekStart.plusDays(4) // 금요일까지
    val validDays = meal.days
        .filter { it.validationState == MealValidationState.VALID && !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
        .associateBy { it.date }
    val dateFormat = DateTimeFormatter.ofPattern("M/d")

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 0부터 4까지 5일간만 반복 (월~금)
        (0L..4L).forEach { offset ->
            val date = weekStart.plusDays(offset)
            val day = validDays[date]
            val isToday = date == today
            val isPast = date.isBefore(today)
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(offset.toInt())
                    .alpha(if (isPast) 0.5f else 1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = when {
                        isToday -> MaterialTheme.colorScheme.primaryContainer
                        isPast -> MaterialTheme.colorScheme.surfaceContainerLowest
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${date.format(dateFormat)} (${koreanWeekdayLabel(date.dayOfWeek).take(1)})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                            if (isToday) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.pulseBreath(minAlpha = 0.75f),
                                ) {
                                    Text(
                                        text = "오늘",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        if (day != null) {
                            Text(
                                text = if (isToday) mealServiceStatus(day, nowTime).label else day.hours,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = if (day == null || day.menuLines.isEmpty()) "등록된 식단 없음" else day.menuLines.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 5. 설정 및 진단 화면 (Settings) - 위치, 데이터, 권한 통합 (슬라이더 삭제)
// -----------------------------------------------------------------------------
@Composable
private fun SettingsScreen(
    locationMode: LocationMode,
    testZone: CampusZoneId,
    onTestModeChange: (Boolean) -> Unit,
    onTestZone: (CampusZoneId) -> Unit,
    liveSurfaceController: LiveSurfaceController,
    displayOptions: LiveDisplayOptions,
    onChipContentChange: (LiveChipContent) -> Unit,
    onClassOrderChange: (LiveClassOrder) -> Unit,
    homeBase: HomeBase,
    onHomeBaseChange: (HomeBase) -> Unit,
    shuttleData: ShuttleData,
    mealData: MealData,
    onShowNowBarSetup: () -> Unit,
    updateState: AppUpdateUiState,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onContinueInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var liveSettingsMessage by remember { mutableStateOf<String?>(null) }

    val notifications = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exact = (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val background = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    val promoted = if (Build.VERSION.SDK_INT >= 36) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canPostPromotedNotifications()
    } else false
    val liveDiagnostics = remember(notifications, promoted) { liveSurfaceController.diagnostics() }

    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        locationMessage = if (it) "정확한 위치 허용됨" else "정확한 위치 필요"
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        locationMessage = if (it) "백그라운드 위치 허용됨" else "백그라운드 위치 미허용"
    }

    ScreenColumn(title = "설정 및 상태", modifier = modifier) {
        // 1) 귀가 기준지 — 가장 자주 바꾸는 개인 설정을 최상단에
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(0),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("귀가 기준지", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("수업 시작 30분 후와 수업 종료 뒤 안내할 셔틀 방향입니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveToggleButton(
                        label = "예인관",
                        selected = homeBase == HomeBase.YEIN,
                        onClick = { onHomeBaseChange(HomeBase.YEIN) },
                    )
                    ExpressiveToggleButton(
                        label = "원룸촌",
                        selected = homeBase == HomeBase.ONE_ROOM,
                        onClick = { onHomeBaseChange(HomeBase.ONE_ROOM) },
                    )
                }
            }
        }

        // 2) Live Update 표시 + 나우바 설정 안내
        LiveDisplaySettings(
            options = displayOptions,
            onChipContentChange = onChipContentChange,
            onClassOrderChange = onClassOrderChange,
            modifier = Modifier.staggeredEntrance(1),
            onShowNowBarSetup = onShowNowBarSetup,
        )

        // 3) GPS 비반영 테스트 모드
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(2),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("위치 및 테스트", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("테스트 모드", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (locationMode == LocationMode.TEST) "GPS 반영 안 함" else "현재 위치 자동 판정",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = locationMode == LocationMode.TEST,
                        onCheckedChange = onTestModeChange,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CampusZoneId.entries.forEach { zone ->
                        ExpressiveToggleButton(
                            label = DisplayVocabulary.zoneName(zone),
                            selected = locationMode == LocationMode.TEST && testZone == zone,
                            enabled = locationMode == LocationMode.TEST,
                            onClick = { onTestZone(zone) },
                        )
                    }
                }
            }
        }

        // 4) 시스템 권한 상태 — 미허용 항목은 행에서 바로 요청
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(3),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("시스템 권한 상태", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                PermissionStatusRow("알림 권한", notifications)
                PermissionStatusRow("정확한 알람", exact)
                PermissionStatusRow(
                    title = "정확한 위치",
                    granted = fine,
                    onRequest = { fineLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                )
                PermissionStatusRow(
                    title = "백그라운드 위치",
                    granted = background,
                    onRequest = { backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                )
                if (Build.VERSION.SDK_INT >= 36) {
                    PermissionStatusRow("Live Update 승격", liveDiagnostics.canPostPromotedNotifications)
                }

                locationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { openAppSettings(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("앱 권한 설정")
                    }
                    if (Build.VERSION.SDK_INT >= 36) {
                        OutlinedButton(
                            onClick = {
                                liveSettingsMessage = when (liveSurfaceController.openPromotionSettings()) {
                                    LiveSettingsDestination.PROMOTED_NOTIFICATION_SETTINGS -> "Live Update 설정 열림"
                                    LiveSettingsDestination.APP_NOTIFICATION_SETTINGS -> "앱 알림 설정 열림"
                                    LiveSettingsDestination.APP_DETAILS -> "앱 정보 설정 열림"
                                    LiveSettingsDestination.UNAVAILABLE -> "설정 화면 미지원"
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Live 설정")
                        }
                    }
                }
                liveSettingsMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }

        AppUpdateCard(
            state = updateState,
            onCheck = onCheckUpdate,
            onDownload = onDownloadUpdate,
            onContinueInstall = onContinueInstall,
            onCancelDownload = onCancelDownload,
            modifier = Modifier.staggeredEntrance(4),
        )
        DataAndSourcesCard(shuttleData, mealData, modifier = Modifier.staggeredEntrance(5))
    }
}

@Composable
internal fun NowBarSetupDialog(
    onOpenLockScreenNotifications: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onComplete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("나우바 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("'잠긴 상태에서 알림 내용 표시' 옵션을 항상 표시로 변경해주세요.")
                OutlinedButton(onClick = onOpenLockScreenNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text("잠금화면 알림 설정 열기")
                }
                Text("개발자 옵션에서 ‘모든 앱의 실시간 정보 보기’를 켜세요.")
                OutlinedButton(onClick = onOpenDeveloperOptions, modifier = Modifier.fillMaxWidth()) {
                    Text("개발자 옵션 열기")
                }
            }
        },
        confirmButton = {
            Button(onClick = onComplete, modifier = Modifier.testTag("nowbar_setup_complete")) { Text("완료") }
        },
    )
}

@Composable
internal fun AppUpdateCard(
    state: AppUpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onContinueInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("앱 업데이트", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("현재 버전 ${state.currentVersion.ifBlank { "확인 중" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val statusText = when (state.phase) {
                AppUpdatePhase.IDLE -> "업데이트를 확인하지 않았습니다"
                AppUpdatePhase.CHECKING -> "업데이트 확인 중"
                AppUpdatePhase.UP_TO_DATE -> "최신 버전입니다"
                AppUpdatePhase.AVAILABLE -> "새 버전 ${state.latestRelease?.versionName}"
                AppUpdatePhase.DOWNLOADING -> "다운로드 중 ${state.downloadProgress ?: 0}%"
                AppUpdatePhase.READY_TO_INSTALL -> "설치 준비 완료"
                AppUpdatePhase.PERMISSION_REQUIRED -> "설치 권한이 필요합니다"
                AppUpdatePhase.INSTALLER_OPENED -> "Android 설치 화면을 확인하세요"
                AppUpdatePhase.ERROR -> state.message ?: "업데이트 오류"
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (state.phase == AppUpdatePhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            state.message?.takeIf { it != statusText }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.phase == AppUpdatePhase.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { (state.downloadProgress ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.phase) {
                    AppUpdatePhase.AVAILABLE -> Button(onClick = onDownload) { Text("다운로드 및 설치") }
                    AppUpdatePhase.DOWNLOADING -> OutlinedButton(onClick = onCancelDownload) { Text("취소") }
                    AppUpdatePhase.READY_TO_INSTALL, AppUpdatePhase.PERMISSION_REQUIRED -> Button(onClick = onContinueInstall) { Text("설치 계속") }
                    else -> OutlinedButton(enabled = state.phase != AppUpdatePhase.CHECKING, onClick = onCheck) { Text("업데이트 확인") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.phase == AppUpdatePhase.AVAILABLE || state.phase == AppUpdatePhase.READY_TO_INSTALL || state.phase == AppUpdatePhase.PERMISSION_REQUIRED) {
                        OutlinedButton(onClick = onCheck) { Text("업데이트 확인") }
                    }
                    state.latestRelease?.let { release ->
                        OutlinedButton(onClick = { openUrl(context, release.releasePageUrl) }) { Text("릴리스 보기") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DataAndSourcesCard(
    shuttleData: ShuttleData,
    mealData: MealData,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shuttleSlots = shuttleData.departures.distinctBy {
        listOf(it.serviceDay, it.originZone, it.destinationZone, it.time)
    }.size
    val mealWeeks = mealData.cachedWeeks.joinToString { week ->
        "${week.weekStart.monthValue}/${week.weekStart.dayOfMonth}~${week.weekEnd.monthValue}/${week.weekEnd.dayOfMonth}"
    }.ifBlank { "검증된 주간 식단 없음" }
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("데이터 및 원문", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("셔틀", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("기기 동기화: ${shuttleData.lastSuccess?.let(::formatSourceSuccessTime) ?: "기록 없음"}", style = MaterialTheme.typography.bodySmall)
            shuttleData.serverPublishedAt?.let { Text("서버 게시: ${formatSourceSuccessTime(it)}", style = MaterialTheme.typography.bodySmall) }
            Text("공식 주간 시간표 ${shuttleData.departures.size}행 · 사용자 출발 슬롯 ${shuttleSlots}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            shuttleData.error?.let { Text("마지막 오류: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = { openUrl(context, shuttleData.sourceUrl) }) { Text("셔틀 원문") }
            HorizontalDivider()
            Text("식단", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("기기 동기화: ${mealData.lastSuccess?.let(::formatSourceSuccessTime) ?: "기록 없음"}", style = MaterialTheme.typography.bodySmall)
            mealData.serverPublishedAt?.let { Text("서버 게시: ${formatSourceSuccessTime(it)}", style = MaterialTheme.typography.bodySmall) }
            Text(mealWeeks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            mealData.error?.let { Text("마지막 오류: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openUrl(context, mealData.sourceUrl) }) { Text("식단 원문") }
                mealData.sourceImageUrl?.let { imageUrl ->
                    OutlinedButton(onClick = { openUrl(context, imageUrl) }) { Text("식단 이미지") }
                }
            }
            HorizontalDivider()
            Text(
                "캠퍼스 구역 ${DefaultCampusZones.VERSION} · © OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    onRequest: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        if (!granted && onRequest != null) {
            FilledTonalButton(
                onClick = onRequest,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text("요청", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        } else {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = if (granted) "허용됨" else "필요",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * M3 Expressive Toggle Button (S 사이즈 40dp, filled 스타일).
 * 미선택 시 완전한 라운드(필) 형태, 선택 시 12dp 사각형으로 코너가 spring 모핑되고
 * 색상은 surfaceContainer/onSurfaceVariant ↔ primary/onPrimary 로 전환된다.
 * 참고: https://m3.material.io/components/buttons/specs
 */
@Composable
internal fun ExpressiveToggleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 12.dp else 20.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "toggle_corner_$label",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "toggle_container_$label",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "toggle_content_$label",
    )
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.height(40.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun LiveDisplaySettings(
    options: LiveDisplayOptions,
    onChipContentChange: (LiveChipContent) -> Unit,
    onClassOrderChange: (LiveClassOrder) -> Unit,
    modifier: Modifier = Modifier,
    onShowNowBarSetup: (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Live Update 표시", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("상단 필", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveToggleButton(
                        label = "남은 시간",
                        selected = options.chipContent == LiveChipContent.COUNTDOWN,
                        onClick = { onChipContentChange(LiveChipContent.COUNTDOWN) },
                    )
                    ExpressiveToggleButton(
                        label = "강의실",
                        selected = options.chipContent == LiveChipContent.CLASSROOM,
                        onClick = { onChipContentChange(LiveChipContent.CLASSROOM) },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("잠금화면 첫 줄", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveToggleButton(
                        label = "수업명 먼저",
                        selected = options.classOrder == LiveClassOrder.COURSE_FIRST,
                        onClick = { onClassOrderChange(LiveClassOrder.COURSE_FIRST) },
                    )
                    ExpressiveToggleButton(
                        label = "강의실 먼저",
                        selected = options.classOrder == LiveClassOrder.CLASSROOM_FIRST,
                        onClick = { onClassOrderChange(LiveClassOrder.CLASSROOM_FIRST) },
                    )
                }
            }

            onShowNowBarSetup?.let { onClick ->
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text("나우바 설정 안내")
                }
            }
        }
    }
}

@Composable
private fun ScreenColumn(
    modifier: Modifier = Modifier,
    title: String? = null,
    topAction: (@Composable () -> Unit)? = null,
    customTopBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 상태바 높이를 contentPadding으로 주면 스크롤 시 콘텐츠가 상태바 뒤로 지나가
            // 상단에 죽은 여백이 생기지 않는다
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = statusBarTop + 8.dp,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (customTopBar != null) {
                        customTopBar()
                    } else if (title != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            topAction?.invoke()
                        }
                    }
                }
            }
            item { Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content) }
            item { Spacer(Modifier.height(16.dp)) }
        }

        // 상단 시스템 상태바 영역 반투명 그라데이션 스크림 (스크롤 시 텍스트/아이콘 겹침 방지 및 부드러운 페이드)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarTop + 20.dp)
                .background(
                    Brush.verticalGradient(
                        0.0f to surfaceColor.copy(alpha = 0.95f),
                        0.6f to surfaceColor.copy(alpha = 0.70f),
                        1.0f to surfaceColor.copy(alpha = 0.0f),
                    ),
                ),
        )
    }
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

private fun openDeveloperOptions(context: Context) {
    openFirstAvailableSettings(
        context,
        listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        ),
    )
}

private fun openFirstAvailableSettings(context: Context, intents: List<Intent>) {
    intents.firstOrNull { it.resolveActivity(context.packageManager) != null }?.let { intent ->
        val launchIntent = Intent(intent).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(launchIntent)
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
