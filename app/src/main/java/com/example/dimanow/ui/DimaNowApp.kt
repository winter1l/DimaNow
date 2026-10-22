package com.example.dimanow.ui

import android.provider.Settings
import android.webkit.CookieManager
import androidx.compose.animation.AnimatedContent
import com.example.dimanow.ui.motion.LocalEntranceMotion
import com.example.dimanow.ui.motion.dimaFadeThrough
import com.example.dimanow.ui.onboarding.OnboardingRoute
import com.example.dimanow.ui.onboarding.shouldShowOnboarding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.data.AppPreferences
import com.example.dimanow.data.CampusDataRepository
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.emptyTermSchedule
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.live.LiveDisplayOptions
import com.example.dimanow.live.LiveSurfaceController
import com.example.dimanow.live.NotificationGuidancePolicy
import com.example.dimanow.meal.MealSource
import com.example.dimanow.notice.NoticeData
import com.example.dimanow.notice.NoticeSource
import com.example.dimanow.notice.OFFICIAL_NOTICE_SOURCE_URL
import com.example.dimanow.shuttle.ShuttleSource
import com.example.dimanow.time.MinuteTicker
import com.example.dimanow.location.LocationMode
import com.example.dimanow.update.AppUpdateCoordinator
import com.example.dimanow.update.AppUpdateUiState
import com.example.dimanow.lms.LmsAutoLoginCoordinator
import com.example.dimanow.lms.LmsCredentialStore
import com.example.dimanow.lms.LmsSessionState
import com.example.dimanow.lms.LmsLoginBridge
import com.example.dimanow.lms.LmsRenderedPageBridge
import com.example.dimanow.lms.LmsRoute
import com.example.dimanow.lms.LmsSessionController
import com.example.dimanow.lms.LmsSource
import java.time.ZonedDateTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/** [icon] marks the selected destination and [outlinedIcon] the others, as the M3 navigation bar does (D-102). */
internal enum class AppPage(val title: String, val icon: ImageVector, val outlinedIcon: ImageVector = icon) {
    // D-044: 기존 4탭(홈·시간표·셔틀·식단)의 손가락 기억을 보존하기 위해 수업은 맨 뒤에 둔다
    DASHBOARD("홈", Icons.Default.Home, Icons.Outlined.Home),
    TIMETABLE("시간표", Icons.Default.Schedule, Icons.Outlined.Schedule),
    SHUTTLE("셔틀", Icons.Default.DirectionsBus, Icons.Outlined.DirectionsBus),
    MEAL("식단", Icons.Default.Restaurant, Icons.Outlined.Restaurant),
    COURSES("수업", Icons.Default.School, Icons.Outlined.School),
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
    // D-094(12): the current tab survives configuration changes and process death
    var page by rememberSaveable { mutableStateOf(AppPage.DASHBOARD) }
    var settingsReturnPage by rememberSaveable { mutableStateOf(AppPage.DASHBOARD) }
    // After the first tab change the tab transition is the only motion; lists no longer rise in.
    var tabChanged by rememberSaveable { mutableStateOf(false) }
    val initialPage = remember { page }
    val entranceMotion = !tabChanged && page == initialPage
    SideEffect { if (!entranceMotion) tabChanged = true }
    // Each tab keeps its saveable state (list scroll, selected day/view/venue) while another is shown.
    val tabStateHolder = rememberSaveableStateHolder()
    val retainedFlowValues = remember { RetainedFlowValues() }
    // 수업 탭이 로그인 WebView/글 상세를 전체화면으로 띄우는 동안 하단 내비를 숨긴다 (D-044)
    var lmsFullScreen by remember { mutableStateOf(false) }
    val primaryPages = remember { primaryAppPages }
    val homeBaseConfirmed by preferences.homeBaseSelectionConfirmed.collectAsStateWithLifecycle(initialValue = true)
    val onboardingCompleted by preferences.onboardingCompleted.collectAsStateWithLifecycle(initialValue = true)
    var showNowBarSetup by remember { mutableStateOf(false) }
    // D-102: 홈의 '수업 추가하기'가 시간표 탭의 추가 화면을 바로 열도록 건네는 일회성 요청
    var addCourseRequest by rememberSaveable { mutableLongStateOf(0L) }
    // D-102: 지금 보고 있는 탭을 다시 누르면 그 화면이 맨 위로 올라간다 (M3 내비게이션 바)
    val reselections = remember { mutableStateMapOf<AppPage, Int>() }
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

    // Settings is a transient destination: it opens from the top each time.
    LaunchedEffect(page) {
        if (page != AppPage.SETTINGS) tabStateHolder.removeState(AppPage.SETTINGS.name)
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

    val showNavigation = page != AppPage.SETTINGS && !(page == AppPage.COURSES && lmsFullScreen)
    // D-104: compact windows keep the bottom bar; from the M3 medium width (tablets, unfolded foldables)
    // the same destinations move to a navigation rail at the start edge.
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val useRail = windowWidth >= DimaLayout.railMinWidth
    fun navigate(item: AppPage) {
        if (page == item) reselections[item] = (reselections[item] ?: 0) + 1 else page = item
    }
    val navIcon: @Composable (AppPage) -> Unit = { item ->
        Icon(if (page == item) item.icon else item.outlinedIcon, contentDescription = item.title)
    }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        // 상태바 인셋을 바깥 패딩이 아닌 각 화면의 contentPadding으로 처리해
        // 스크롤 콘텐츠가 상태바 뒤까지 흐르게 한다 (상단 빈 띠 제거)
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showNavigation && !useRail) {
                // D-104: the M3 Expressive flexible (short) navigation bar — 64dp on surfaceContainer with the
                // secondaryContainer indicator; the selected destination is marked by the indicator and a
                // filled icon (others outlined), with one label weight so labels never reflow (D-094(12)).
                ShortNavigationBar {
                    primaryPages.forEach { item ->
                        ShortNavigationBarItem(
                            modifier = Modifier.testTag("nav_${item.name}"),
                            selected = page == item,
                            onClick = { navigate(item) },
                            icon = { navIcon(item) },
                            label = { Text(item.title) },
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
        // 하단 내비가 차지한 인셋을 소비해, 각 화면의 스캐폴드는 내비가 없을 때(설정)만
        // 제스처 바 인셋을 목록 아래 여백으로 더한다 (D-094)
        val pageModifier = Modifier.padding(padding).consumeWindowInsets(padding)
      Row(Modifier.fillMaxSize()) {
        if (showNavigation && useRail) {
            // D-104: the M3 Expressive collapsed navigation rail — icons over labels, the same indicator and
            // filled/outlined icons as the bar; it takes the status bar, cutout and start insets itself.
            WideNavigationRail(modifier = Modifier.testTag("nav_rail")) {
                primaryPages.forEach { item ->
                    WideNavigationRailItem(
                        modifier = Modifier.testTag("nav_${item.name}"),
                        selected = page == item,
                        onClick = { navigate(item) },
                        icon = { navIcon(item) },
                        label = { Text(item.title) },
                        railExpanded = false,
                    )
                }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .then(
                    if (showNavigation && useRail) {
                        Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    } else {
                        Modifier
                    },
                ),
        ) {
        CompositionLocalProvider(
            LocalOpenSettings provides openSettingsAction,
            LocalEntranceMotion provides entranceMotion,
            LocalRetainedFlowValues provides retainedFlowValues,
        ) {
            // One motion per tab change: an M3 fade-through (D-103); the lists do not also rise in.
            AnimatedContent(
                targetState = page,
                transitionSpec = { dimaFadeThrough() },
                label = "tab_transition",
            ) { targetPage ->
              tabStateHolder.SaveableStateProvider(targetPage.name) {
               CompositionLocalProvider(LocalScrollToTopSignal provides (reselections[targetPage] ?: 0)) {
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
                    onAddCourse = { addCourseRequest = System.currentTimeMillis(); page = AppPage.TIMETABLE },
                    updateState = updateState,
                    appUpdateCoordinator = appUpdateCoordinator,
                    modifier = pageModifier,
                    now = requireNotNull(minuteNow),
                )
                AppPage.TIMETABLE -> TimetableRoute(
                    repository,
                    pageModifier,
                    addCourseRequest = addCourseRequest,
                    onAddCourseRequestHandled = { addCourseRequest = 0L },
                )
                AppPage.COURSES -> LmsRoute(
                    credentialStore = lmsCredentialStore,
                    sessionController = lmsSessionController,
                    loginBridge = lmsLoginBridge,
                    renderedPageBridge = lmsRenderedPageBridge,
                    autoLoginCoordinator = lmsAutoLoginCoordinator,
                    source = lmsSource,
                    now = requireNotNull(minuteNow).toInstant(),
                    onFullScreenChange = { lmsFullScreen = it },
                    modifier = pageModifier,
                )
                AppPage.SHUTTLE -> ShuttleRoute(
                    preferences,
                    shuttleSource,
                    pageModifier,
                    requireNotNull(minuteNow),
                )
                AppPage.MEAL -> MealRoute(
                    preferences = preferences,
                    mealSource = mealSource,
                    modifier = pageModifier,
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
                    modifier = pageModifier,
                )
                }
               }
              }
            }
        }
        }
      }
    }

    // D-102: 새 버전 안내는 앱을 열자마자 뜨는 대화상자가 아니라 홈의 카드(HomeUpdateNotice)로 보여 준다
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
    onAddCourse: () -> Unit,
    updateState: AppUpdateUiState,
    appUpdateCoordinator: AppUpdateCoordinator?,
    modifier: Modifier,
    now: ZonedDateTime,
) {
    val schedule by repository.schedule.collectRetainedAsState(initialValue = remember { emptyTermSchedule() })
    val homeLms by lmsSource.snapshot.collectRetainedAsState(initialValue = com.example.dimanow.lms.LmsSnapshot())
    val homeLmsSession by lmsSessionController.state.collectAsStateWithLifecycle()
    val resolvedZone by preferences.effectiveZone.collectRetainedAsState(initialValue = CampusZoneId.OUTSIDE)
    val locationMode by preferences.locationMode.collectRetainedAsState(initialValue = LocationMode.GPS)
    // D-094: null until each source emits its first cached value, so cold start shows loading, not empty
    val shuttle by shuttleSource.data.collectLoadedAsState()
    val meal by mealSource.data.collectLoadedAsState()
    val dormitoryMeal by mealSource.dormitoryData.collectLoadedAsState()
    val emptyNotices = remember { NoticeData(emptyList(), null, null, null, OFFICIAL_NOTICE_SOURCE_URL) }
    val notices by (noticeSource?.data ?: remember { kotlinx.coroutines.flow.flowOf(emptyNotices) })
        .collectLoadedAsState()
    // D-106: exam weeks and the next term start for Home's title follow the published calendar
    val serviceCalendar by com.example.dimanow.calendar.ServiceCalendarStore.calendar.collectAsStateWithLifecycle()

    DashboardScreen(
        schedule = schedule,
        serviceCalendar = serviceCalendar,
        lmsSnapshot = homeLms,
        lmsSessionState = homeLmsSession,
        zone = resolvedZone,
        testMode = locationMode == LocationMode.TEST,
        shuttle = shuttle,
        meal = meal,
        dormitoryMeal = dormitoryMeal,
        notices = notices,
        onNavigateToPage = onNavigateToPage,
        onAddCourse = onAddCourse,
        updateNotice = if (appUpdateCoordinator != null && updateState.showsHomeNotice()) {
            {
                HomeUpdateNotice(
                    state = updateState,
                    onInstall = appUpdateCoordinator::downloadAndInstall,
                    onLater = appUpdateCoordinator::dismissPrompt,
                    onCancel = appUpdateCoordinator::cancelDownload,
                    onContinueInstall = appUpdateCoordinator::continueInstall,
                )
            }
        } else null,
        modifier = modifier,
        now = now,
    )
}

@Composable
private fun TimetableRoute(
    repository: CampusDataRepository,
    modifier: Modifier,
    addCourseRequest: Long,
    onAddCourseRequestHandled: () -> Unit,
) {
    val schedule by repository.schedule.collectRetainedAsState(initialValue = remember { emptyTermSchedule() })
    TimetableScreen(
        repository, schedule, modifier,
        addCourseRequest = addCourseRequest,
        onAddCourseRequestHandled = onAddCourseRequestHandled,
    )
}

@Composable
private fun ShuttleRoute(
    preferences: AppPreferences,
    shuttleSource: ShuttleSource,
    modifier: Modifier,
    now: ZonedDateTime,
) {
    val resolvedZone by preferences.effectiveZone.collectRetainedAsState(initialValue = CampusZoneId.OUTSIDE)
    val nearbyTransitStopNumber by preferences.effectiveTransitStopNumber.collectRetainedAsState(initialValue = null)
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
    val resolvedZone by preferences.effectiveZone.collectRetainedAsState(initialValue = CampusZoneId.OUTSIDE)
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
    // D-094: null until the cache emits, so the diagnostics card shows loading rather than "기록 없음"
    val shuttle by shuttleSource.data.collectLoadedAsState()
    val meal by mealSource.data.collectLoadedAsState()
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

internal val LocalOpenSettings = compositionLocalOf<(() -> Unit)?> { null }

/** How many times the visible tab was selected again; [ScreenScaffold] scrolls to the top on each new value. */
internal val LocalScrollToTopSignal = compositionLocalOf { 0 }
