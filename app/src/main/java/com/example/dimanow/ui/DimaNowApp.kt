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
import android.webkit.CookieManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
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
import androidx.compose.animation.togetherWith
import com.example.dimanow.ui.meal.groupDormitorySections
import com.example.dimanow.ui.motion.AnimatedCountText
import com.example.dimanow.ui.motion.expressiveBounceClick
import com.example.dimanow.ui.motion.pulseBreath
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.dimanow.ui.motion.entrance
import com.example.dimanow.ui.motion.staggeredEntrance
import com.example.dimanow.ui.onboarding.OnboardingRoute
import com.example.dimanow.ui.onboarding.shouldShowOnboarding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.data.AppPreferences
import com.example.dimanow.data.CampusDataRepository
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.DefaultCampusZones
import com.example.dimanow.domain.DefaultSchedule
import com.example.dimanow.domain.DisplayVocabulary
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
import com.example.dimanow.live.LiveSettingsDestination
import com.example.dimanow.live.LiveSurfaceController
import com.example.dimanow.live.GuidanceKind
import com.example.dimanow.live.NotificationGuidanceMode
import com.example.dimanow.live.NotificationGuidancePolicy
import com.example.dimanow.meal.MealData
import com.example.dimanow.meal.DormitoryMealData
import com.example.dimanow.meal.DormitoryMealImage
import com.example.dimanow.meal.DormitoryMealSubmissionResult
import com.example.dimanow.meal.MealSource
import com.example.dimanow.meal.mealServiceStatus
import com.example.dimanow.notice.NoticeData
import com.example.dimanow.notice.NoticeSource
import com.example.dimanow.notice.OFFICIAL_NOTICE_SOURCE_URL
import com.example.dimanow.meal.OFFICIAL_MEAL_SOURCE_URL
import com.example.dimanow.shuttle.OFFICIAL_SHUTTLE_SOURCE_URL
import com.example.dimanow.shuttle.ShuttleData
import com.example.dimanow.shuttle.ShuttleSource
import com.example.dimanow.time.MinuteTicker
import com.example.dimanow.location.LocationMode
import com.example.dimanow.update.AppUpdateCoordinator
import com.example.dimanow.update.AppUpdatePhase
import com.example.dimanow.update.AppUpdateUiState
import com.example.dimanow.lms.LmsAutoLoginCoordinator
import com.example.dimanow.lms.LmsCredentialStore
import com.example.dimanow.lms.CredentialState
import com.example.dimanow.lms.LmsSessionState
import com.example.dimanow.lms.LmsLoginBridge
import com.example.dimanow.lms.LmsRenderedPageBridge
import com.example.dimanow.lms.LmsRoute
import com.example.dimanow.lms.LmsSessionController
import com.example.dimanow.lms.LmsSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

internal enum class AppPage(val title: String, val icon: ImageVector) {
    // D-044: 기존 4탭(홈·시간표·셔틀·식단)의 손가락 기억을 보존하기 위해 수업은 맨 뒤에 둔다
    DASHBOARD("홈", Icons.Default.Home),
    TIMETABLE("시간표", Icons.Default.Schedule),
    SHUTTLE("셔틀", Icons.Default.DirectionsBus),
    MEAL("식단", Icons.Default.Restaurant),
    COURSES("수업", Icons.Default.School),
    SETTINGS("설정", Icons.Default.Settings),
    ;

    val usesMinuteTicker: Boolean
        get() = this == DASHBOARD || this == SHUTTLE || this == MEAL || this == COURSES
}

internal val primaryAppPages = listOf(
    AppPage.DASHBOARD,
    AppPage.TIMETABLE,
    AppPage.SHUTTLE,
    AppPage.MEAL,
    AppPage.COURSES,
)

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
    lmsCredentialStore: LmsCredentialStore,
    lmsSessionController: LmsSessionController,
    lmsLoginBridge: LmsLoginBridge,
    lmsRenderedPageBridge: LmsRenderedPageBridge? = null,
    lmsAutoLoginCoordinator: LmsAutoLoginCoordinator,
    lmsSource: LmsSource,
    targetPageEvent: Pair<String, Long>? = null,
) {
    var page by remember { mutableStateOf(AppPage.DASHBOARD) }
    var settingsReturnPage by remember { mutableStateOf(AppPage.DASHBOARD) }
    // 수업 탭이 로그인 WebView/글 상세를 전체화면으로 띄우는 동안 하단 내비를 숨긴다 (D-044)
    var lmsFullScreen by remember { mutableStateOf(false) }
    val primaryPages = remember { primaryAppPages }
    val homeBaseConfirmed by preferences.homeBaseSelectionConfirmed.collectAsStateWithLifecycle(initialValue = true)
    val onboardingCompleted by preferences.onboardingCompleted.collectAsStateWithLifecycle(initialValue = true)
    var showNowBarSetup by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val initialUpdateState = remember { AppUpdateUiState(currentVersion = "") }
    val updateState by (appUpdateCoordinator?.state ?: remember { kotlinx.coroutines.flow.flowOf(initialUpdateState) })
        .collectAsStateWithLifecycle(initialValue = initialUpdateState)
    // nonce가 붙은 이벤트라 같은 위젯을 연달아 탭해도 매번 다시 이동한다 (#7)
    LaunchedEffect(targetPageEvent) {
        when (targetPageEvent?.first) {
            "DASHBOARD" -> page = AppPage.DASHBOARD
            "SHUTTLE" -> page = AppPage.SHUTTLE
            "MEAL" -> page = AppPage.MEAL
            // 아래 두 값은 현재 어떤 위젯도 보내지 않지만, 향후 위젯용으로 유지한다
            "TIMETABLE" -> page = AppPage.TIMETABLE
            "COURSES" -> page = AppPage.COURSES
            "SETTINGS" -> {
                settingsReturnPage = page
                page = AppPage.SETTINGS
            }
        }
    }

    BackHandler(enabled = page != AppPage.DASHBOARD) {
        page = if (page == AppPage.SETTINGS) settingsReturnPage else AppPage.DASHBOARD
    }

    // D-056: 최초 설치에는 앱 셸 대신 전체화면 온보딩이 권한을 순서대로 설명하고 요청한다.
    // 온보딩이 끝나기 전에는 셸과 다른 다이얼로그를 아예 구성하지 않는다.
    // 귀가 기준지가 이미 확정된 기존 설치는 업데이트만으로 온보딩을 다시 보지 않는다.
    if (shouldShowOnboarding(onboardingCompleted, homeBaseConfirmed)) {
        OnboardingRoute(
            preferences = preferences,
            liveSurfaceController = liveSurfaceController,
            onPermissionsChanged = { (appContext as? com.example.dimanow.DimaNowApplication)?.refreshGuidancePermissions() },
        )
        return
    }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        // 상태바 인셋을 바깥 패딩이 아닌 각 화면의 contentPadding으로 처리해
        // 스크롤 콘텐츠가 상태바 뒤까지 흐르게 한다 (상단 빈 띠 제거)
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (page != AppPage.SETTINGS && !(page == AppPage.COURSES && lmsFullScreen)) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 8.dp,
                ) {
                    primaryPages.forEach { item ->
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
            }
        },
    ) { padding ->
        // page가 바뀔 때만 람다를 재생성해 분 틱마다 모든 ScreenColumn이 무효화되지 않게 한다 (#15)
        val openSettingsAction = remember(page) {
            if (page == AppPage.SETTINGS) {
                null
            } else {
                { settingsReturnPage = page; page = AppPage.SETTINGS }
            }
        }
        CompositionLocalProvider(LocalOpenSettings provides openSettingsAction) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { dimaTabContentTransform(targetState.ordinal > initialState.ordinal) },
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
                    lmsSource = lmsSource,
                    lmsSessionController = lmsSessionController,
                    onNavigateToPage = { page = it },
                    modifier = Modifier.padding(padding),
                    now = requireNotNull(minuteNow),
                )
                AppPage.TIMETABLE -> TimetableRoute(repository, Modifier.padding(padding))
                AppPage.COURSES -> LmsRoute(
                    credentialStore = lmsCredentialStore,
                    sessionController = lmsSessionController,
                    loginBridge = lmsLoginBridge,
                    renderedPageBridge = lmsRenderedPageBridge,
                    autoLoginCoordinator = lmsAutoLoginCoordinator,
                    source = lmsSource,
                    now = requireNotNull(minuteNow).toInstant(),
                    onFullScreenChange = { lmsFullScreen = it },
                    modifier = Modifier.padding(padding),
                )
                AppPage.SHUTTLE -> ShuttleRoute(
                    preferences,
                    shuttleSource,
                    Modifier.padding(padding),
                    requireNotNull(minuteNow),
                )
                AppPage.MEAL -> MealRoute(
                    preferences = preferences,
                    mealSource = mealSource,
                    modifier = Modifier.padding(padding),
                    now = requireNotNull(minuteNow),
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
                    lmsCredentialStore = lmsCredentialStore,
                    lmsSessionController = lmsSessionController,
                    lmsSource = lmsSource,
                    onBack = { page = settingsReturnPage },
                    modifier = Modifier.padding(padding),
                )
            }
            }
        }
    }

    if (updateState.promptVersion != null) {
        AlertDialog(
            onDismissRequest = { appUpdateCoordinator?.dismissPrompt() },
            title = { Text("업데이트 가능") },
            text = { Text("DIMA Now ${updateState.promptVersion} 버전을 설치할 수 있어요.") },
            confirmButton = {
                Button(onClick = { appUpdateCoordinator?.downloadAndInstall() }) { Text("다운로드 및 설치") }
            },
            dismissButton = {
                TextButton(onClick = { appUpdateCoordinator?.dismissPrompt() }) { Text("나중에") }
            },
        )
    }
    if (showNowBarSetup) {
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

internal fun dimaTabContentTransform(forward: Boolean): ContentTransform {
    val slideOffset = { width: Int -> if (forward) width / 4 else -width / 4 }
    return (
        slideInHorizontally(
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            initialOffsetX = slideOffset,
        ) + fadeIn(animationSpec = tween(durationMillis = 180))
    ).togetherWith(
        slideOutHorizontally(
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            targetOffsetX = { width -> -slideOffset(width) },
        ) + fadeOut(animationSpec = tween(durationMillis = 180)),
    ).apply { targetContentZIndex = 1f }
}

@Composable
private fun DashboardRoute(
    repository: CampusDataRepository,
    preferences: AppPreferences,
    shuttleSource: ShuttleSource,
    mealSource: MealSource,
    noticeSource: NoticeSource?,
    lmsSource: LmsSource,
    lmsSessionController: LmsSessionController,
    onNavigateToPage: (AppPage) -> Unit,
    modifier: Modifier,
    now: ZonedDateTime,
) {
    val schedule by repository.schedule.collectAsStateWithLifecycle(initialValue = DefaultSchedule.create())
    val homeLms by lmsSource.snapshot.collectAsStateWithLifecycle(initialValue = com.example.dimanow.lms.LmsSnapshot())
    val homeLmsSession by lmsSessionController.state.collectAsStateWithLifecycle()
    val resolvedZone by preferences.effectiveZone.collectAsStateWithLifecycle(initialValue = CampusZoneId.OUTSIDE)
    val locationMode by preferences.locationMode.collectAsStateWithLifecycle(initialValue = LocationMode.GPS)
    val shuttle by shuttleSource.data.collectAsStateWithLifecycle(
        initialValue = ShuttleData(emptyList(), null, null, null, OFFICIAL_SHUTTLE_SOURCE_URL, null),
    )
    val meal by mealSource.data.collectAsStateWithLifecycle(
        initialValue = MealData(emptyList(), null, null, null, OFFICIAL_MEAL_SOURCE_URL, null, null),
    )
    val dormitoryMeal by mealSource.dormitoryData.collectAsStateWithLifecycle(
        initialValue = DormitoryMealData(emptyList(), null, null, null),
    )
    val homeBase by preferences.homeBase.collectAsStateWithLifecycle(initialValue = HomeBase.YEIN)
    val emptyNotices = remember { NoticeData(emptyList(), null, null, null, OFFICIAL_NOTICE_SOURCE_URL) }
    val notices by (noticeSource?.data ?: remember { kotlinx.coroutines.flow.flowOf(emptyNotices) })
        .collectAsStateWithLifecycle(initialValue = emptyNotices)

    DashboardScreen(
        schedule = schedule,
        lmsSnapshot = homeLms,
        lmsSessionState = homeLmsSession,
        zone = resolvedZone,
        testMode = locationMode == LocationMode.TEST,
        automatic = true,
        shuttle = shuttle,
        meal = meal,
        dormitoryMeal = dormitoryMeal,
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
    val nearbyTransitStopNumber by preferences.effectiveTransitStopNumber.collectAsStateWithLifecycle(initialValue = null)
    ShuttleScreen(
        shuttleSource = shuttleSource,
        currentZone = resolvedZone,
        nearbyTransitStopNumber = nearbyTransitStopNumber,
        modifier = modifier,
        now = now,
    )
}

@Composable
private fun MealRoute(
    preferences: AppPreferences,
    mealSource: MealSource,
    modifier: Modifier,
    now: ZonedDateTime,
) {
    val resolvedZone by preferences.effectiveZone.collectAsStateWithLifecycle(initialValue = CampusZoneId.OUTSIDE)
    MealScreen(
        mealSource = mealSource,
        modifier = modifier,
        today = now.toLocalDate(),
        nowTime = now.toLocalTime(),
        initialVenue = if (resolvedZone == CampusZoneId.YEIN) MealVenue.DORMITORY else MealVenue.MAIN_CAFETERIA,
    )
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
    lmsCredentialStore: LmsCredentialStore,
    lmsSessionController: LmsSessionController,
    lmsSource: LmsSource,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val locationMode by preferences.locationMode.collectAsStateWithLifecycle(initialValue = LocationMode.GPS)
    val testZone by preferences.testZone.collectAsStateWithLifecycle(initialValue = CampusZoneId.OUTSIDE)
    val testTransitStopNumber by preferences.testTransitStopNumber.collectAsStateWithLifecycle(initialValue = null)
    val resolvedZone by preferences.effectiveZone.collectAsStateWithLifecycle(initialValue = CampusZoneId.OUTSIDE)
    val displayOptions by preferences.liveDisplayOptions.collectAsStateWithLifecycle(initialValue = LiveDisplayOptions())
    val notificationPolicy by preferences.notificationGuidancePolicy.collectAsStateWithLifecycle(
        initialValue = NotificationGuidancePolicy(),
    )
    val homeBase by preferences.homeBase.collectAsStateWithLifecycle(initialValue = HomeBase.YEIN)
    val shuttle by shuttleSource.data.collectAsStateWithLifecycle(
        initialValue = ShuttleData(emptyList(), null, null, null, OFFICIAL_SHUTTLE_SOURCE_URL, null),
    )
    val meal by mealSource.data.collectAsStateWithLifecycle(
        initialValue = MealData(emptyList(), null, null, null, OFFICIAL_MEAL_SOURCE_URL, null, null),
    )
    val credentialState by lmsCredentialStore.state.collectAsStateWithLifecycle()

    SettingsScreen(
        locationMode = locationMode,
        testZone = testZone,
        onTestModeChange = { enabled ->
            scope.launch { preferences.setTestLocationMode(enabled, if (enabled) resolvedZone else testZone) }
        },
        onTestZone = { scope.launch { preferences.setTestZone(it) } },
        testTransitStopNumber = testTransitStopNumber,
        onTestTransitStop = { scope.launch { preferences.setTestTransitStop(it) } },
        liveSurfaceController = liveSurfaceController,
        displayOptions = displayOptions,
        onChipContentChange = { scope.launch { preferences.setLiveChipContent(it) } },
        onClassOrderChange = { scope.launch { preferences.setLiveClassOrder(it) } },
        notificationPolicy = notificationPolicy,
        onNotificationModeChange = { kind, mode ->
            scope.launch { preferences.setNotificationGuidanceMode(kind, mode) }
        },
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
        lmsCredentialState = credentialState,
        onDeleteLmsAccount = {
            scope.launch {
                lmsCredentialStore.delete()
                lmsSource.clearPrivateData()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                lmsSessionController.transition(LmsSessionState.SIGNED_OUT)
            }
        },
        onBack = onBack,
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
    testMode: Boolean = false,
    automatic: Boolean,
    shuttle: ShuttleData,
    meal: MealData,
    dormitoryMeal: DormitoryMealData = DormitoryMealData(emptyList(), null, null, null),
    homeBase: HomeBase = HomeBase.YEIN,
    notices: NoticeData = NoticeData(emptyList(), null, null, null, OFFICIAL_NOTICE_SOURCE_URL),
    onNavigateToPage: (AppPage) -> Unit = {},
    modifier: Modifier = Modifier,
    now: ZonedDateTime = ZonedDateTime.now(),
    lmsSnapshot: com.example.dimanow.lms.LmsSnapshot = com.example.dimanow.lms.LmsSnapshot(),
    lmsSessionState: LmsSessionState = LmsSessionState.SIGNED_OUT,
) {
    val context = LocalContext.current
    val nowTime = now.toLocalTime()
    val guidancePaused = now.toLocalDate() in schedule.noClassDates || schedule.guidancePause?.contains(now.toLocalDate()) == true
    val todayCourses = schedule.coursesOn(now.toLocalDate())
    val remainingCourses = todayCourses.filter { !it.end.isBefore(nowTime) }
    val nextCourse = remainingCourses.firstOrNull()
    val upcomingAfterCourse = remainingCourses.getOrNull(1)

    // 엔진과 무거운 계산은 입력이 바뀔 때만 다시 수행한다 (#20)
    val guidanceEngine = remember { GuidanceEngine() }
    val shuttleBoard = remember(now, zone, shuttle.departures) {
        guidanceEngine.shuttleBoard(
            now = now,
            originZone = zone,
            departures = shuttle.departures,
            purpose = ShuttleBoardPurpose.GENERAL,
        )
    }
    val guidanceSnapshot = remember(now, schedule, zone, automatic, shuttle.departures, homeBase) {
        guidanceEngine.snapshot(
            now = now,
            termStart = schedule.termStart,
            termEnd = schedule.termEnd,
            courses = schedule.coursesOn(now.toLocalDate()),
            noClassDates = schedule.noClassDates,
            resolvedZone = zone,
            automaticClassGuidance = automatic,
            shuttleDepartures = shuttle.departures,
            homeBase = homeBase,
            guidancePause = schedule.guidancePause,
        )
    }

    val todayMeal = meal.days.firstOrNull { it.date == now.toLocalDate() }
    val homeDormitoryMeal = dormitoryMeal.homeServiceAt(now)
    val useDormitoryMeal = zone == CampusZoneId.YEIN
    val mealStatusNow = meal.serviceStatusAt(now)
    val homeMealOpen = if (useDormitoryMeal) {
        homeDormitoryMeal?.let { day ->
            groupDormitorySections(day.sections).any { mealServiceStatus(day.date, it.hours, now).state == MealServiceState.OPEN }
        } == true
    } else mealStatusNow.state == MealServiceState.OPEN
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
                    .entrance(),
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
                .entrance(),
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
                        // 셔틀 출발 정보는 바로 아래 셔틀 카드가 캡슐로 보여주므로
                        // 히어로 카드에서 같은 내용을 문장으로 반복하지 않는다 (D-056)
                    }

                    // 오늘의 다음 수업 표시 (있을 경우)
                    if (upcomingAfterCourse != null) {
                        HeroClassPreviewRow(
                            label = "다음 수업 · ${upcomingAfterCourse.start.format(TIME)}",
                            detail = "${upcomingAfterCourse.name} (${upcomingAfterCourse.room})",
                        )
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
                                schedule.coursesOn(date)
                                    .minByOrNull { it.start }
                                    ?.let { date to it }
                            }
                            .firstOrNull()
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = when {
                                schedule.courses.isEmpty() -> "시간표에 수업을 추가해 주세요"
                                guidancePaused -> "오늘은 휴강이에요"
                                schedule.coursesOn(now.toLocalDate()).isEmpty() -> "오늘은 수업이 없어요"
                                else -> "오늘 수업이 모두 끝났어요"
                            },
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
                            HeroClassPreviewRow(
                                label = "$dayLabel · ${course.start.format(TIME)}",
                                detail = "${course.name} (${course.room})",
                            )
                        }
                    }
                }

            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth().entrance().testTag("dashboard_learning_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            HomeTodaySummary(
                snapshot = lmsSnapshot, now = now,
                onOpenCourses = { onNavigateToPage(AppPage.COURSES) },
                sessionState = lmsSessionState,
                modifier = Modifier.padding(18.dp),
            )
        }

        // 2. 실시간 셔틀 카드
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .entrance()
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
                        Text("전체 시간표", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                                // 목적지 루프 안에서 엔진을 새로 만들지 않고 위에서 remember한 것을 쓴다
                                val annotated = guidanceEngine.annotatedServiceDepartures(
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
                                                                append(when {
                                                                    minutesLeft > 60 -> "다음 출발"
                                                                    minutesLeft <= 0 -> "곧 출발"
                                                                    else -> "${minutesLeft}분 후"
                                                                })
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
                        Text("오늘은 운행하지 않아요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 3. 현재 구역의 오늘 식단 카드
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_meal_card")
                .semantics { stateDescription = if (homeMealOpen) "운영 중" else "운영 시간 아님" }
                .entrance()
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
                        Text(text = if (useDormitoryMeal) "기숙사" else "학생식당", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!useDormitoryMeal) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (homeMealOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (homeMealOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    text = mealStatusNow.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }

                when {
                    useDormitoryMeal && homeDormitoryMeal != null -> {
                        if (homeDormitoryMeal.date != now.toLocalDate()) {
                            Text(
                                text = "${homeDormitoryMeal.date.monthValue}월 ${homeDormitoryMeal.date.dayOfMonth}일 · 다음 식단",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        groupDormitorySections(homeDormitoryMeal.sections).forEachIndexed { index, block ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            val status = mealServiceStatus(homeDormitoryMeal.date, block.hours, now)
                            DormitoryMealPeriodContent(block, status)
                        }
                    }
                    useDormitoryMeal -> {
                        Text("예정된 식단이 없어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    todayMeal != null && todayMeal.menuLines.isNotEmpty() -> {
                        Text(
                            text = todayMeal.menuLines.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp,
                        )
                    }
                    now.dayOfWeek.value >= 6 -> {
                        Text("주말에는 학생식당을 쉬어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Text("오늘 등록된 식단이 없어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 4. 학교 공지 카드 (최근 3건, 행 탭 시 해당 공지로 이동)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .entrance(),
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("전체보기", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }

                val latestNotices = notices.notices.take(3)
                if (latestNotices.isEmpty()) {
                    Text(
                        text = when {
                            notices.error != null -> "공지를 불러오지 못했어요"
                            notices.lastSuccess == null -> "공지를 확인하고 있어요"
                            else -> "등록된 공지가 없어요"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        latestNotices.forEach { notice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .expressiveBounceClick { openUrl(context, notice.url) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = notice.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Text(
                                        text = "${notice.date.monthValue}.${notice.date.dayOfMonth}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. 학교 서비스 바로가기 (DIMA Portal · 수업 탭)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .entrance(),
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
            // 네이티브 수업 탭이 있으므로 외부 브라우저 대신 탭으로 이동한다 (#28)
            FilledTonalButton(
                onClick = { onNavigateToPage(AppPage.COURSES) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("수업 (LMS)", fontWeight = FontWeight.Bold)
            }
        }

    }
}

/**
 * 히어로 수업 카드 안의 보조 수업 줄(오늘의 다음 수업 / 다음 수업일 첫 수업).
 * 두 분기가 같은 문법을 쓰도록 하나로 모은다 (D-056).
 */
@Composable
private fun HeroClassPreviewRow(label: String, detail: String) {
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
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    var changingOnce by remember { mutableStateOf<Course?>(null) }
    var changeError by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now(MinuteTicker.CAMPUS_ZONE)

    BackHandler(
        enabled = changingOnce != null || showEditor || showTermEditor || showPauseChoice || pausePickerMode != null || pendingDelete != null,
    ) {
        when {
            changingOnce != null -> changingOnce = null
            pendingDelete != null -> pendingDelete = null
            pausePickerMode != null -> pausePickerMode = null
            showPauseChoice -> showPauseChoice = false
            showTermEditor -> showTermEditor = false
            showEditor -> showEditor = false
        }
    }

    ScreenColumn(
        title = "시간표", modifier = modifier,
        topAction = { TextButton(onClick = { editing = null; showEditor = true }) { Text("수업 추가") } },
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .entrance(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = { showTermEditor = true },
                        modifier = Modifier.testTag("edit_term"),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("학기 ${schedule.termStart} ~ ${schedule.termEnd}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                GuidancePauseSetting(
                    pause = schedule.guidancePause,
                    today = today,
                    onConfigure = { showPauseChoice = true },
                    onClear = { scope.launch { repository.clearGuidancePause() } },
                )
            }
        }

        changeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val upcomingChanges = schedule.courseOverrides.filter { it.date >= today }
        if (upcomingChanges.isNotEmpty()) {
            Text("한 번만 바꾼 수업", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            upcomingChanges.forEach { change ->
                val course = schedule.courses.firstOrNull { it.id == change.courseId }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${change.date} · ${com.example.dimanow.ui.schedule.overrideKindLabel(change.kind)}")
                            Text(course?.name.orEmpty(), fontWeight = FontWeight.SemiBold)
                            if (change.kind == com.example.dimanow.domain.CourseOverrideKind.CHANGED) {
                                Text("${change.start ?: course?.start}–${change.end ?: course?.end} · ${change.room ?: course?.room}")
                            }
                        }
                        TextButton(onClick = { scope.launch { repository.removeCourseOverride(change.courseId, change.date) } }) { Text("되돌리기") }
                    }
                }
            }
        }

        val grouped = schedule.courses
            .sortedWith(compareBy<Course> { it.weekday.value }.thenBy { it.start })
            .groupBy { it.weekday }

        if (grouped.isEmpty()) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(text = "등록된 수업이 없어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
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
                        .entrance(),
                )
                courses.forEach { course ->
                    CourseSummaryCard(
                        course = course,
                        onEdit = { editing = course; showEditor = true },
                        onDelete = { pendingDelete = course },
                        onChangeOnce = { changingOnce = course },
                        modifier = Modifier.entrance(),
                    )
                }
            }
        }

        if (schedule.noClassDates.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("지정한 휴강일", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        if (schedule.noClassDates.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.entrance(),
            ) {
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

    changingOnce?.let { course ->
        com.example.dimanow.ui.schedule.CourseOverrideDialog(
            course, today, schedule.termStart, schedule.termEnd,
            onDismiss = { changingOnce = null },
            onSave = { change -> scope.launch {
                try { repository.setCourseOverride(change); changingOnce = null; changeError = null }
                catch (error: IllegalArgumentException) { changeError = error.message; changingOnce = null }
            } },
        )
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
                showPauseChoice = false; pausePickerMode = "RANGE"
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
                ) { Text("휴강을 해제할 때까지") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
internal fun GuidancePauseSetting(
    pause: GuidancePause?,
    today: LocalDate,
    onConfigure: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("휴강 모드", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            text = pause?.let { pauseLabel(it, today) } ?: "수업 안내 켜짐",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onConfigure) {
                Text(if (pause == null) "설정" else "변경")
            }
            if (pause != null) {
                TextButton(onClick = onClear, modifier = Modifier.testTag("clear_guidance_pause")) {
                    Text("해제")
                }
            }
        }
    }
}

private fun pauseLabel(pause: GuidancePause, today: LocalDate): String = when {
    pause.isUntilDisabled -> "휴강을 해제할 때까지 휴강"
    pause.startDate == pause.endDateInclusive -> "${pause.endDateInclusive.monthValue}월 ${pause.endDateInclusive.dayOfMonth}일 휴강"
    today.isBefore(pause.startDate) -> "${pause.startDate.monthValue}월 ${pause.startDate.dayOfMonth}일부터 ${pause.endDateInclusive.monthValue}월 ${pause.endDateInclusive.dayOfMonth}일까지 휴강"
    else -> "${pause.endDateInclusive.monthValue}월 ${pause.endDateInclusive.dayOfMonth}일까지 휴강"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseSummaryCard(
    course: Course,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onChangeOnce: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
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
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.testTag("course_menu_${course.id}")) {
                    Icon(Icons.Default.MoreVert, contentDescription = "${course.name} 더보기")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    onChangeOnce?.let { action ->
                        DropdownMenuItem(text = { Text("이번 수업만 변경") }, onClick = { menuExpanded = false; action() })
                    }
                    DropdownMenuItem(text = { Text("수정") }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("삭제") }, onClick = { menuExpanded = false; onDelete() })
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
internal fun TermEditorDialog(start: LocalDate, end: LocalDate, onDismiss: () -> Unit, onSave: (LocalDate, LocalDate) -> Unit) {
    var startText by remember(start) { mutableStateOf(start.toString()) }
    var endText by remember(end) { mutableStateOf(end.toString()) }
    val parsedStart = runCatching { LocalDate.parse(startText) }.getOrNull()
    val parsedEnd = runCatching { LocalDate.parse(endText) }.getOrNull()
    val reversedDates = parsedStart != null && parsedEnd != null && parsedEnd.isBefore(parsedStart)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("학기 기간 설정", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startText, onValueChange = { startText = it },
                    modifier = Modifier.testTag("term_start"),
                    label = { Text("시작 (YYYY-MM-DD)") }, shape = RoundedCornerShape(12.dp),
                    isError = parsedStart == null,
                    supportingText = if (parsedStart == null) ({ Text("2026-09-01 형식으로 입력해 주세요") }) else null,
                )
                OutlinedTextField(
                    value = endText, onValueChange = { endText = it },
                    modifier = Modifier.testTag("term_end"),
                    label = { Text("종료 (YYYY-MM-DD)") }, shape = RoundedCornerShape(12.dp),
                    isError = parsedEnd == null || reversedDates,
                    supportingText = when {
                        parsedEnd == null -> ({ Text("2026-09-01 형식으로 입력해 주세요") })
                        reversedDates -> ({ Text("종료일은 시작일과 같거나 늦어야 해요") })
                        else -> null
                    },
                )
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
                    Text("종료 시간을 시작 시간보다 늦게 설정해 주세요", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
private enum class ShuttleView { CAMPUS, BUS_4402 }

@Composable
fun ShuttleScreen(
    shuttleSource: ShuttleSource,
    currentZone: CampusZoneId,
    nearbyTransitStopNumber: String? = null,
    modifier: Modifier = Modifier,
    now: ZonedDateTime = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE),
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val shuttle by shuttleSource.data.collectAsStateWithLifecycle(
        initialValue = ShuttleData(emptyList(), null, null, null, OFFICIAL_SHUTTLE_SOURCE_URL, null),
    )
    val guidanceEngine = remember { GuidanceEngine() }
    var refreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    // 날짜가 바뀌면(자정) 선택 요일도 새 오늘로 재설정된다 (#14)
    var selectedDay by remember(now.toLocalDate()) { mutableStateOf(now.dayOfWeek) }
    var shuttleView by rememberSaveable { mutableStateOf(ShuttleView.CAMPUS) }
    val nowTime = now.toLocalTime()

    LaunchedEffect(refreshMessage) {
        refreshMessage?.let {
            snackbarHostState.showSnackbar(it)
            refreshMessage = null
        }
    }

    fun refreshShuttle() {
        if (refreshing) return
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
    }

    Box(modifier = modifier.fillMaxSize()) {
    ScreenColumn(
        title = "셔틀버스",
        modifier = Modifier.fillMaxSize(),
        // 새로고침은 목록을 당겨서 실행한다 (D-058)
        onRefresh = ::refreshShuttle,
        refreshing = refreshing,
        listTag = "shuttle_list",
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = shuttleView == ShuttleView.CAMPUS,
                onClick = { shuttleView = ShuttleView.CAMPUS },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("교내 셔틀") }
            SegmentedButton(
                selected = shuttleView == ShuttleView.BUS_4402,
                onClick = { shuttleView = ShuttleView.BUS_4402 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("4402") }
        }
        if (shuttleView == ShuttleView.BUS_4402) {
            Bus4402ScheduleContent(now = now, nearbyStopNumber = nearbyTransitStopNumber)
            return@ScreenColumn
        }
        // M3 Expressive 요일 선택 (DimaDaySelector)
        DimaDaySelector(
            days = DayOfWeek.entries,
            selected = selectedDay,
            onSelect = { selectedDay = it },
            today = now.dayOfWeek,
            itemTag = { "shuttle_day_${it.name}" },
            modifier = Modifier.staggeredEntrance(0),
        )

        // 선택된 요일의 노선별 셔틀 목록 (현재 위치 출발 우선 정렬)
        AnimatedContent(
            targetState = selectedDay,
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { 40 })
                    .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessMediumLow)))
            },
            label = "shuttle_day_list",
            modifier = Modifier.staggeredEntrance(1),
        ) { activeDay ->
            // 전환 중 나가는 패널이 새 요일의 카운트다운을 잘못 그리지 않도록 activeDay 기준으로 판정 (#12)
            val isToday = activeDay == now.dayOfWeek
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
                    text = "${koreanWeekdayLabel(activeDay)}은 운행하지 않아요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
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

                val upcomingCountdowns = remember(shuttle.departures, now, activeDay, isToday, originZone, destinationZone) {
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isCurrentLocation) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
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
                                        text = "첫차 ${sortedTimes.first().format(TIME)} · 막차 ${sortedTimes.last().format(TIME)}",
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
                                        text = "출발 시각",
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
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    AnimatedCountText(
                                                        text = buildString {
                                                            append(when {
                                                                minutesLeft > 60 -> "다음 출발"
                                                                minutesLeft <= 0 -> "곧 출발"
                                                                else -> "${minutesLeft}분 후"
                                                            })
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
                                        text = "오늘 운행이 끝났어요",
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
                            text = "전체 시간표 · ${sortedTimes.size}회",
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
    val meal by mealSource.data.collectAsStateWithLifecycle(
        initialValue = MealData(emptyList(), null, null, null, OFFICIAL_MEAL_SOURCE_URL, null, null),
    )
    val dormitoryMeal by mealSource.dormitoryData.collectAsStateWithLifecycle(
        initialValue = DormitoryMealData(emptyList(), null, null, null),
    )
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
            } catch (failure: Exception) {
                refreshMessage = failure.message ?: "식단 사진을 읽지 못했어요."
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
                    refreshMessage = "식단 확인 중"
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
                                refreshMessage = status.message
                                return
                            }
                            else -> Unit
                        }
                    }
                    refreshMessage = "식단 확인이 계속되고 있어요. 잠시 후 새로고침해 주세요."
                }
                is DormitoryMealSubmissionResult.Failure -> refreshMessage = submitted.message
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
                refreshMessage = when (result) {
                    null -> null
                    is com.example.dimanow.meal.MealRefreshResult.Success -> "${result.weekStart} 주간 식단 저장 완료"
                    com.example.dimanow.meal.MealRefreshResult.NotPublishedYet -> "아직 새 식단이 올라오지 않았어요"
                    is com.example.dimanow.meal.MealRefreshResult.NeedsReview -> "확인 필요: ${result.reason}"
                    is com.example.dimanow.meal.MealRefreshResult.Failure -> "실패: ${result.message}"
                }
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
    var selectedDay by remember(today) {
        mutableStateOf(if (today.dayOfWeek.value in 1..5) today else weekStart)
    }
    val listState = rememberLazyListState()
    val now = ZonedDateTime.of(today, nowTime, MinuteTicker.CAMPUS_ZONE)
    val dormitoryBlocks = remember(dormitoryMeal.days, selectedDay) {
        dormitoryMeal.days.firstOrNull { it.date == selectedDay }?.sections
            ?.let(::groupDormitorySections).orEmpty()
    }
    val dormitoryStates = dormitoryBlocks.map { mealServiceStatus(selectedDay, it.hours, now) }
    val focusBlock = if (selectedDay == today) {
        dormitoryStates.indexOfFirst { it.state == MealServiceState.OPEN }.takeIf { it >= 0 }
            ?: dormitoryStates.indexOfFirst { it.state == MealServiceState.BEFORE_OPEN }.takeIf { it >= 0 }
            ?: dormitoryStates.indexOfLast { it.state == MealServiceState.CLOSED }.takeIf { it >= 0 }
    } else null
    // Only a change of meal/venue/day moves the list; ordinary minute ticks preserve manual scrolling.
    LaunchedEffect(venue, selectedDay, focusBlock, uploadingDormitoryMeal) {
        val target = if (venue == MealVenue.DORMITORY && focusBlock != null) {
            1 + focusBlock + if (uploadingDormitoryMeal) 1 else 0
        } else 0
        listState.scrollToItem(target)
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
                WeekdaySelector(
                    weekStart = weekStart,
                    selected = selectedDay,
                    today = today,
                    onSelect = { selectedDay = it },
                )
            },
        ) {
            if (venue == MealVenue.DORMITORY) {
                // 업로드는 15분까지 폴링되므로 진행 상태를 상시 카드로 보여준다 (D-056)
                if (uploadingDormitoryMeal) {
                    item { DormitoryUploadProgressCard() }
                }
                if (preparingDormitoryImage) {
                    item {
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
            } else {
                if (!meal.hasCurrentStudentWeek(today) || meal.error != null) {
                    item { StudentMealSyncStatus(meal, today) }
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

/**
 * 월~금 요일 선택기 (D-057). 셔틀 화면의 커넥티드 버튼 문법을 그대로 써서
 * 앱 안에서 요일을 고르는 방식이 한 가지로 유지된다. 오늘에는 점 표식이 붙는다.
 */
@Composable
private fun WeekdaySelector(
    weekStart: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0L..4L).forEach { offset ->
            val date = weekStart.plusDays(offset)
            val isSelected = date == selected
            val isToday = date == today
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "meal_day_bg_$offset",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "meal_day_content_$offset",
            )
            Surface(
                onClick = { onSelect(date) },
                shape = RoundedCornerShape(16.dp),
                color = bgColor,
                contentColor = contentColor,
                border = if (isToday && !isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("meal_day_${date.dayOfWeek.name}"),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = koreanWeekdayLabel(date.dayOfWeek).take(1),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        )
                        Text(
                            text = "${date.dayOfMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = contentColor.copy(alpha = if (isSelected) 0.9f else 0.7f),
                        )
                    }
                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

/** 선택한 날짜의 메뉴 줄을 읽기 쉬운 목록으로 그린다 (D-057). */
@Composable
private fun MenuLineList(lines: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.filter { it.isNotBlank() }.forEach { rawLine ->
            val line = rawLine.trim()
            val isOriginOrNote = line.contains("원산지") || line.contains("국내산") || line.contains("호주산") ||
                line.contains("미국산") || line.startsWith("-") || line.startsWith("*") ||
                (line.contains(":") && !line.contains("kcal", ignoreCase = true))

            if (isOriginOrNote) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
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
                fontWeight = FontWeight.Bold,
            )
            if (date == today) {
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
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
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
        item(key = "main-empty-$date") { MealEmptyDayCard("등록된 식단이 없어요") }
    } else {
        item(key = "main-menu-$date") {
            MealPeriodCard(
                status = status,
                modifier = Modifier.testTag("main_meal_card"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("중식", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            item(key = "dorm-empty-$date") { MealEmptyDayCard("이날 식단이 없어요") }
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
private fun DormitoryMealPeriodContent(block: DormitoryMealBlock, status: MealServiceStatus) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(dormitoryBlockIcon(block.name), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(block.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        MealHoursChip(block.hours, status.state, includeStatus = true)
    }
    MenuLineList(block.menuLines)
    block.extras.forEach { extra ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = extra.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
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
    val shape = RoundedCornerShape(20.dp)
    val background = when {
        isOpen -> colors.primaryContainer
        isClosed -> lerp(colors.surfaceContainerLow, Color.Black, 0.12f)
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
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun MealPeriodStatus(status: MealServiceStatus) {
    if (status.state == MealServiceState.UNKNOWN_HOURS) return
    Text(
        status.label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (status.state == MealServiceState.OPEN) FontWeight.Bold else FontWeight.Medium,
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
    Surface(shape = RoundedCornerShape(8.dp), color = background) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = foreground,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun MealEmptyDayCard(message: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().entrance(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }
}

/** 이번 주 기숙사 식단이 하나도 없을 때의 단일 안내 카드 (D-056). */
@Composable
private fun DormitoryWeekEmptyCard(
    modifier: Modifier = Modifier,
    onUpload: (() -> Unit)? = null,
    uploadEnabled: Boolean = true,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .entrance()
            .testTag("dormitory_week_empty"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Text(
                text = "이번 주 기숙사 식단이 아직 없어요",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "식단표를 올리면 함께 볼 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onUpload != null) {
                FilledTonalButton(
                    onClick = onUpload,
                    enabled = uploadEnabled,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("dormitory_week_empty_upload"),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("식단표 사진 올리기", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 사진 업로드 후 서버 확인이 끝날 때까지 남아 있는 진행 카드 (D-056). */
@Composable
private fun DormitoryUploadProgressCard(modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dormitory_upload_progress"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp).pulseBreath(),
                strokeWidth = 2.5.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "식단표를 확인하고 있어요",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "앱을 닫아도 계속 진행돼요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 5. 설정 및 진단 화면 (Settings) - 위치, 데이터, 권한 통합 (슬라이더 삭제)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    locationMode: LocationMode,
    testZone: CampusZoneId,
    onTestModeChange: (Boolean) -> Unit,
    onTestZone: (CampusZoneId) -> Unit,
    testTransitStopNumber: String?,
    onTestTransitStop: (String?) -> Unit,
    liveSurfaceController: LiveSurfaceController,
    displayOptions: LiveDisplayOptions,
    onChipContentChange: (LiveChipContent) -> Unit,
    onClassOrderChange: (LiveClassOrder) -> Unit,
    notificationPolicy: NotificationGuidancePolicy,
    onNotificationModeChange: (GuidanceKind, NotificationGuidanceMode) -> Unit,
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
    lmsCredentialState: CredentialState,
    onDeleteLmsAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDeleteLmsAccount by remember { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    var setupExpanded by rememberSaveable { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as? com.example.dimanow.DimaNowApplication

    ScreenColumn(
        title = "설정",
        topAction = { TextButton(onClick = onBack, modifier = Modifier.testTag("close_settings")) { Text("닫기") } },
        modifier = modifier,
    ) {
        // 1) 귀가 기준지 — 가장 자주 바꾸는 개인 설정을 최상단에
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .entrance(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("귀가 방향", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("수업이 끝난 뒤 안내할 셔틀 방향이에요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpressiveToggleButton(
                        label = "엔터관 방향",
                        selected = homeBase == HomeBase.YEIN,
                        onClick = { onHomeBaseChange(HomeBase.YEIN) },
                    )
                    ExpressiveToggleButton(
                        label = "원룸촌 방향",
                        selected = homeBase == HomeBase.ONE_ROOM,
                        onClick = { onHomeBaseChange(HomeBase.ONE_ROOM) },
                    )
                }
            }
        }

        Text("알림과 위치", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LiveDisplaySettings(
            options = displayOptions,
            onChipContentChange = onChipContentChange,
            onClassOrderChange = onClassOrderChange,
            modifier = Modifier.entrance(),
        )

        NotificationGuidanceSettings(
            policy = notificationPolicy,
            onModeChange = onNotificationModeChange,
            modifier = Modifier.entrance(),
        )

        TextButton(onClick = { setupExpanded = !setupExpanded }, modifier = Modifier.testTag("open_guidance_setup")) {
            Text(if (setupExpanded) "자동 안내 설정 접기" else "자동 안내 설정")
        }
        if (setupExpanded) {
            GuidanceSetup(liveSurfaceController = liveSurfaceController, onPermissionsChanged = { app?.refreshGuidancePermissions() })
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth().entrance(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("수업 계정", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (lmsCredentialState == CredentialState.SAVED) "자동 로그인 사용 중" else "연결된 계정이 없어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lmsCredentialState == CredentialState.SAVED) {
                    OutlinedButton(onClick = { confirmDeleteLmsAccount = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("저장된 계정 삭제")
                    }
                }
            }
        }
        Text("앱 정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        AppUpdateCard(
            state = updateState,
            onCheck = onCheckUpdate,
            onDownload = onDownloadUpdate,
            onContinueInstall = onContinueInstall,
            onCancelDownload = onCancelDownload,
            modifier = Modifier.entrance(),
        )
        TextButton(onClick = { diagnosticsExpanded = !diagnosticsExpanded }, modifier = Modifier.testTag("toggle_diagnostics")) {
            Text(if (diagnosticsExpanded) "고급 및 진단 접기" else "고급 및 진단")
        }
        if (diagnosticsExpanded) {
            TextButton(onClick = onShowNowBarSetup) { Text("기기별 알림 도움말") }
        // 3) GPS 비반영 테스트 모드
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .entrance(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("위치 테스트", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

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
                if (locationMode == LocationMode.TEST) {
                    TransitStopTestControls(
                        testStopNumber = testTransitStopNumber,
                        onChange = onTestTransitStop,
                    )
                }
            }
        }

            DataAndSourcesCard(shuttleData, mealData)
        }
    }
    if (confirmDeleteLmsAccount) {
        AlertDialog(
            onDismissRequest = { confirmDeleteLmsAccount = false },
            title = { Text("저장된 계정을 삭제할까요?") },
            text = { Text("저장된 계정과 이 기기의 수업 정보가 삭제돼요.") },
            confirmButton = {
                Button(onClick = { confirmDeleteLmsAccount = false; onDeleteLmsAccount() }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteLmsAccount = false }) { Text("취소") } },
        )
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
                AppUpdatePhase.IDLE -> "업데이트를 아직 확인하지 않았어요"
                AppUpdatePhase.CHECKING -> "업데이트 확인 중"
                AppUpdatePhase.UP_TO_DATE -> "최신 버전입니다"
                AppUpdatePhase.AVAILABLE -> "새 버전 ${state.latestRelease?.versionName}"
                AppUpdatePhase.DOWNLOADING -> "다운로드 중 ${state.downloadProgress ?: 0}%"
                AppUpdatePhase.READY_TO_INSTALL -> "설치 준비 완료"
                AppUpdatePhase.PERMISSION_REQUIRED -> "설치 권한이 필요해요"
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
    val fieldOverrideCount = shuttleData.departures.count { it.sourceRouteId == "A-field-extra" }
    val officialDepartureCount = shuttleData.departures.size - fieldOverrideCount
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
            Text(
                buildString {
                    append("공식 주간 시간표 ${officialDepartureCount}행")
                    if (fieldOverrideCount > 0) append(" · 현장 추가 ${fieldOverrideCount}행")
                    append(" · 사용자 출발 슬롯 ${shuttleSlots}개")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

@OptIn(ExperimentalLayoutApi::class)
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
            Text("실시간 알림", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("상단 알림 표시", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

/**
 * 제목 행과 선택 컨트롤을 화면 상단에 고정하는 스캐폴드 (D-057).
 *
 * 기존 [ScreenColumn]은 헤더를 리스트의 첫 항목으로 넣어, 긴 목록을 스크롤하면 제목과
 * 새로고침·설정은 물론 탭 전환 컨트롤까지 함께 사라졌다. 이 스캐폴드는 헤더와 `subHeader`를
 * 리스트 밖에 두고, 내용이 스크롤되면 헤더에 컨테이너 배경과 경계선을 입혀 분리를 만든다.
 */
@Composable
internal fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    topAction: (@Composable RowScope.() -> Unit)? = null,
    subHeader: (@Composable ColumnScope.() -> Unit)? = null,
    listTag: String? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val openSettings = LocalOpenSettings.current
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val headerColor by animateColorAsState(
        targetValue = if (scrolled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "screen_header_color",
    )

    Column(modifier = modifier.fillMaxSize()) {
        Surface(color = headerColor) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = 16.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        topAction?.invoke(this)
                        openSettings?.let { open ->
                            IconButton(onClick = open, modifier = Modifier.testTag("open_settings")) {
                                Icon(Icons.Default.Settings, contentDescription = "설정")
                            }
                        }
                    }
                }
                subHeader?.let { pinned ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = pinned,
                    )
                }
                if (scrolled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
        // 새로고침은 아이콘 버튼이 아니라 M3 당겨서 새로고침이 1차 동작이다 (D-058)
        PullToRefresh(onRefresh = onRefresh, refreshing = refreshing, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (listTag != null) Modifier.testTag(listTag) else Modifier),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/**
 * M3 당겨서 새로고침 래퍼 (D-058).
 *
 * [onRefresh]가 없으면 인디케이터와 제스처 처리를 아예 붙이지 않고 [content]만 그린다.
 * 새로고침이 없는 화면에까지 중첩 스크롤 노드를 추가하지 않기 위해서다.
 */
@Composable
private fun PullToRefresh(
    onRefresh: (() -> Unit)?,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onRefresh == null) {
        Box(modifier = modifier) { content() }
        return
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
internal fun ScreenColumn(
    modifier: Modifier = Modifier,
    title: String? = null,
    topAction: (@Composable () -> Unit)? = null,
    customTopBar: (@Composable () -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    listTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val openSettings = LocalOpenSettings.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefresh(onRefresh = onRefresh, refreshing = refreshing, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (listTag != null) Modifier.testTag(listTag) else Modifier),
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
                            openSettings?.let { open ->
                                IconButton(
                                    onClick = open,
                                    modifier = Modifier.align(Alignment.CenterEnd).testTag("open_settings"),
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = "설정")
                                }
                            }
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    topAction?.invoke()
                                    openSettings?.let { open ->
                                        IconButton(onClick = open, modifier = Modifier.testTag("open_settings")) {
                                            Icon(Icons.Default.Settings, contentDescription = "설정")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        // 상단 시스템 상태바 영역 반투명 그라데이션 스크림 (스크롤 시 텍스트/아이콘 겹침 방지 및 부드러운 페이드)
        // 헤더 행(콘텐츠 시작 statusBarTop+8dp)과 겹치지 않도록 스크림은 8dp까지만 내려온다 (#5)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarTop + 8.dp)
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

internal val LocalOpenSettings = compositionLocalOf<(() -> Unit)?> { null }

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
