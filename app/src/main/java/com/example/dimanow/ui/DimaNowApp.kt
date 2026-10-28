package com.example.dimanow.ui

import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.LocalLibrary
import android.provider.Settings
import android.webkit.CookieManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.motion.LocalEntranceMotion
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.LocalReducedMotion
import com.example.dimanow.ui.motion.PageMotion
import com.example.dimanow.ui.motion.PageSlideDistance
import com.example.dimanow.ui.motion.PredictiveBackPage
import com.example.dimanow.ui.motion.dimaPageTransform
import com.example.dimanow.ui.motion.pageMotionFor
import com.example.dimanow.ui.motion.predictiveBackBackdrop
import com.example.dimanow.ui.motion.rememberPredictiveBackPreview
import com.example.dimanow.ui.motion.rememberShownAfter
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
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
    DASHBOARD("홈", Icons.Default.Home, Icons.Outlined.Home),
    SHUTTLE("셔틀", Icons.Default.DirectionsBus, Icons.Outlined.DirectionsBus),
    MEAL("식단", Icons.Default.Restaurant, Icons.Outlined.Restaurant),
    COURSES("수업", Icons.Default.School, Icons.Outlined.School),
    // D-110: Toss-style 전체 holds what students open now and then; its pages open over it with Back.
    ALL("전체", Icons.Default.Menu),
    TIMETABLE("시간표", Icons.Default.Schedule, Icons.Outlined.Schedule),
    DORM("기숙사", Icons.Default.Apartment),
    EQUIPMENT("장비 대여", Icons.Default.Videocam),
    // D-131: phase 3 pages opened from 전체
    LIBRARY("도서관", Icons.Default.LocalLibrary),
    MAP("캠퍼스 지도", Icons.Default.Map),
    SETTINGS("설정", Icons.Default.Settings),
    ;

    val usesMinuteTicker: Boolean
        get() = this == DASHBOARD || this == SHUTTLE || this == MEAL || this == COURSES

    /** Pages opened from 전체 (or Home's timetable buttons): no navigation bar, a Back action instead. */
    val isSubPage: Boolean
        get() = this == TIMETABLE || this == DORM || this == EQUIPMENT || this == LIBRARY || this == MAP || this == SETTINGS
}

/**
 * The M3 transition pattern for a page change (D-121): opening a 전체 page is forward, Back to [backTarget] (where
 * Back from a sub-page returns) is backward, and everything else is the top-level fade-through (D-103).
 */
internal fun pageMotion(from: AppPage, to: AppPage, backTarget: AppPage): PageMotion =
    pageMotionFor(fromSubPage = from.isSubPage, toSubPage = to.isSubPage, toIsBackTarget = to == backTarget)

internal val primaryAppPages = listOf(
    AppPage.DASHBOARD,
    AppPage.SHUTTLE,
    AppPage.MEAL,
    AppPage.COURSES,
    AppPage.ALL,
)

/** The campus-time minute, ticking while the screen is started and on clock or time-zone changes. */
@Composable
internal fun rememberMinuteNow(): ZonedDateTime {
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
    schoolServiceBridge: com.example.dimanow.school.SchoolServiceBridge? = null,
) {
    // D-094(12): the current tab survives configuration changes and process death
    var page by rememberSaveable { mutableStateOf(AppPage.DASHBOARD) }
    // Where Back from a 전체 page goes: 전체 itself, or Home for the timetable opened from Home's buttons.
    var subPageReturn by rememberSaveable { mutableStateOf(AppPage.ALL) }
    // After the first tab change the tab transition is the only motion; lists no longer rise in.
    var tabChanged by rememberSaveable { mutableStateOf(false) }
    val initialPage = remember { page }
    val entranceMotion = !tabChanged && page == initialPage
    SideEffect { if (!entranceMotion) tabChanged = true }
    // Each tab keeps its saveable state (list scroll, selected day/view/venue) while another is shown.
    val tabStateHolder = rememberSaveableStateHolder()
    val retainedFlowValues = remember { RetainedFlowValues() }
    // D-137: one instance for the shell, so Home's gate finds the favourites it last read when the tab comes back.
    val savedHomeFavorites = remember(preferences) { preferences.homeFavorites.map(::SavedHomeFavorites) }
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
    // D-132: the room 캠퍼스 지도 opens on (a class's location button) and, when that was a page over 전체 such as
    // 시간표, the page Back from the map returns to.
    var mapFocusRoom by rememberSaveable { mutableStateOf<String?>(null) }
    var mapReturn by rememberSaveable { mutableStateOf<AppPage?>(null) }
    fun open(target: AppPage) {
        if (target.isSubPage && !page.isSubPage) subPageReturn = page
        if (target == AppPage.MAP) {
            mapFocusRoom = null
            mapReturn = null
        }
        page = target
    }
    fun openMapFor(room: String) {
        val from = page
        open(AppPage.MAP)
        mapFocusRoom = room
        mapReturn = from.takeIf { it.isSubPage }
    }
    /** Where Back from [from] goes: the map back to the page that opened it, other pages as before. */
    fun backTarget(from: AppPage): AppPage = when {
        from == AppPage.MAP -> mapReturn ?: subPageReturn
        from.isSubPage -> subPageReturn
        else -> AppPage.DASHBOARD
    }
    // nonce가 붙은 이벤트라 같은 위젯을 연달아 탭해도 매번 다시 이동한다 (#7)
    LaunchedEffect(targetPageEvent) {
        when (targetPageEvent?.first) {
            "DASHBOARD" -> open(AppPage.DASHBOARD)
            "SHUTTLE" -> open(AppPage.SHUTTLE)
            "MEAL" -> open(AppPage.MEAL)
            // 아래 세 값은 현재 어떤 위젯도 보내지 않지만, 향후 위젯용으로 유지한다
            "TIMETABLE" -> open(AppPage.TIMETABLE)
            "COURSES" -> open(AppPage.COURSES)
            "SETTINGS" -> open(AppPage.SETTINGS)
            // D-128: app shortcuts and the keyword alert open these too
            "DORM" -> open(AppPage.DORM)
            "LIBRARY_CARD" -> openApp(appContext, LIBRARY_APP_PACKAGE)
            // D-131(1): a morning digest with only books due opens the 도서관 page
            "LIBRARY" -> open(AppPage.LIBRARY)
            // D-126(4): the outing reminder opens 외출·외박 신청 over the 기숙사 page
            "OUTING" -> {
                open(AppPage.DORM)
                val bridge = schoolServiceBridge
                if (bridge == null) {
                    openUrl(appContext, com.example.dimanow.school.INTRANET_DORM_OUTING_URL)
                } else {
                    bridge.open(
                        com.example.dimanow.school.SchoolService.INTRANET,
                        com.example.dimanow.school.INTRANET_DORM_OUTING_URL,
                        "외출·외박 신청",
                    )
                }
            }
        }
    }

    // Settings is a transient destination: it opens from the top each time.
    LaunchedEffect(page) {
        if (page != AppPage.SETTINGS) tabStateHolder.removeState(AppPage.SETTINGS.name)
        // D-132: so is 캠퍼스 지도, so each location button opens it on its own room.
        if (page != AppPage.MAP) tabStateHolder.removeState(AppPage.MAP.name)
    }

    // D-121: M3 predictive back. While the gesture is held the page being left shrinks and rounds; letting go goes
    // back as before, cancelling springs the page back. Inner Back handlers (a 기숙사 topic page, the LMS detail) still
    // take Back first. Under reduced motion there is no preview. D-137: only the page being left takes the preview
    // (PredictiveBackPage), so after letting go it leaves as it is while the next page comes in at full size; before,
    // both pages sprang back to full size during the slide (two geometry animations for one change, D-094(12)).
    val reducedMotion = LocalReducedMotion.current
    val backPreview = rememberPredictiveBackPreview(
        enabled = page != AppPage.DASHBOARD,
        currentKey = page,
        onBack = { page = backTarget(page) },
    )

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

    // D-134 (supersedes D-102's card-only offer): a newer version also asks once per launch, over the app;
    // 나중에 puts this version off as the Home card does, and closing the pop-up only waits for the next launch.
    var updatePromptHandled by rememberSaveable { mutableStateOf(false) }
    if (appUpdateCoordinator != null && !updatePromptHandled && launchesUpdatePrompt(updateState)) {
        UpdateLaunchPrompt(
            state = updateState,
            onUpdate = {
                updatePromptHandled = true
                if (updateState.preparedUpdate != null) appUpdateCoordinator.continueInstall() else appUpdateCoordinator.downloadAndInstall()
            },
            onLater = {
                updatePromptHandled = true
                appUpdateCoordinator.dismissPrompt()
            },
            onClose = { updatePromptHandled = true },
        )
    }

    val showNavigation = !page.isSubPage && !(page == AppPage.COURSES && lmsFullScreen)
    // D-104: compact windows keep the bottom bar; from the M3 medium width (tablets, unfolded foldables)
    // the same destinations move to a navigation rail at the start edge.
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val useRail = windowWidth >= DimaLayout.railMinWidth
    fun navigate(item: AppPage) {
        if (page == item) reselections[item] = (reselections[item] ?: 0) + 1 else page = item
    }
    val navIcon: @Composable (AppPage) -> Unit = { item ->
        if (item.icon == item.outlinedIcon) {
            Icon(item.icon, contentDescription = item.title)
        } else {
            // D-137: the filled glyph fades in with the indicator pill instead of switching in one frame.
            Crossfade(targetState = page == item, animationSpec = DimaMotion.effectsFast(), label = "nav_icon") { selected ->
                Icon(if (selected) item.icon else item.outlinedIcon, contentDescription = item.title)
            }
        }
    }
    // D-137 (adopting D-121's left-over "slide the navigation bar away on sub-pages"): the bar's slot grows and shrinks
    // on the non-bouncy spring, so the Scaffold's bottom padding (every page's bottom, the 시간표 FAB) follows it instead
    // of jumping in one frame, and the bar, anchored to the slot's top, slides down past the screen edge. The rail does
    // the same toward the start edge. Under reduced motion they appear and disappear at once, as before.
    val barEnter = if (reducedMotion) {
        EnterTransition.None
    } else {
        expandVertically(DimaMotion.settle(), expandFrom = Alignment.Top) + fadeIn(DimaMotion.effectsDefault())
    }
    val barExit = if (reducedMotion) {
        ExitTransition.None
    } else {
        shrinkVertically(DimaMotion.settle(), shrinkTowards = Alignment.Top) + fadeOut(DimaMotion.effectsFast())
    }
    val railEnter = if (reducedMotion) {
        EnterTransition.None
    } else {
        expandHorizontally(DimaMotion.settle(), expandFrom = Alignment.End) + fadeIn(DimaMotion.effectsDefault())
    }
    val railExit = if (reducedMotion) {
        ExitTransition.None
    } else {
        shrinkHorizontally(DimaMotion.settle(), shrinkTowards = Alignment.End) + fadeOut(DimaMotion.effectsFast())
    }

    // D-107: the school-site host lies over the whole window, navigation bar and rail included, so a page or
    // sign-in it shows is full screen.
    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
    Scaffold(
        containerColor = DimaTheme.colors.page,
        // 상태바 인셋을 바깥 패딩이 아닌 각 화면의 contentPadding으로 처리해
        // 스크롤 콘텐츠가 상태바 뒤까지 흐르게 한다 (상단 빈 띠 제거)
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(visible = showNavigation && !useRail, enter = barEnter, exit = barExit, label = "navigation_bar") {
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
        // 하단 내비가 차지한 인셋을 소비해, 각 화면의 스캐폴드는 내비가 없을 때(설정)만
        // 제스처 바 인셋을 목록 아래 여백으로 더한다 (D-094)
        val pageModifier = Modifier.padding(padding).consumeWindowInsets(padding)
      Row(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = showNavigation && useRail, enter = railEnter, exit = railExit, label = "navigation_rail") {
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
        // D-110: Settings opens from 전체, so no screen carries the settings gear any more.
        CompositionLocalProvider(
            com.example.dimanow.school.LocalSchoolServiceBridge provides schoolServiceBridge,
            LocalEntranceMotion provides entranceMotion,
            LocalRetainedFlowValues provides retainedFlowValues,
        ) {
            // One motion per page change; the lists do not also rise in. Navigation-bar destinations use the M3
            // fade-through (D-103); a page opened from 전체 slides in forward and back out backward (D-121).
            val slidePx = with(LocalDensity.current) { PageSlideDistance.roundToPx() } *
                (if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1)
            val backdrop = MaterialTheme.colorScheme.surfaceContainerHighest
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    dimaPageTransform(pageMotion(initialState, targetState, subPageReturn), slidePx, reducedMotion)
                },
                label = "tab_transition",
                // The shrinking page needs a backdrop one tone apart, or its edges vanish into the same page color.
                modifier = Modifier.predictiveBackBackdrop(backPreview, backdrop),
            ) { targetPage ->
             PredictiveBackPage(backPreview, key = targetPage) {
              tabStateHolder.SaveableStateProvider(targetPage.name) {
               CompositionLocalProvider(LocalScrollToTopSignal provides (reselections[targetPage] ?: 0)) {
                val minuteNow = if (targetPage.usesMinuteTicker) rememberMinuteNow() else null
                when (targetPage) {
                AppPage.DASHBOARD -> DashboardRoute(
                    repository = repository,
                    preferences = preferences,
                    savedFavoritesFlow = savedHomeFavorites,
                    shuttleSource = shuttleSource,
                    mealSource = mealSource,
                    noticeSource = noticeSource,
                    lmsSource = lmsSource,
                    lmsSessionController = lmsSessionController,
                    onNavigateToPage = ::open,
                    onAddCourse = { addCourseRequest = System.currentTimeMillis(); open(AppPage.TIMETABLE) },
                    onShowRoom = ::openMapFor,
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
                    onBack = { page = subPageReturn },
                    onShowRoom = ::openMapFor,
                )
                AppPage.ALL -> AllScreen(
                    onOpen = ::open,
                    updateState = updateState,
                    onDownloadUpdate = { appUpdateCoordinator?.downloadAndInstall() },
                    onContinueInstall = { appUpdateCoordinator?.continueInstall() },
                    modifier = pageModifier,
                )
                AppPage.DORM -> DormScreen(onBack = { page = subPageReturn }, onOpenMeals = { page = AppPage.MEAL }, modifier = pageModifier)
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
                    lmsCredentialStore = lmsCredentialStore,
                    lmsSessionController = lmsSessionController,
                    lmsSource = lmsSource,
                    updateState = updateState,
                    appUpdateCoordinator = appUpdateCoordinator,
                    onBack = { page = subPageReturn },
                    modifier = pageModifier,
                )
                AppPage.EQUIPMENT -> EquipmentScreen(
                    onBack = { page = subPageReturn },
                    modifier = pageModifier,
                )
                AppPage.LIBRARY -> LibraryScreen(
                    onBack = { page = subPageReturn },
                    modifier = pageModifier,
                )
                AppPage.MAP -> CampusMapScreen(
                    onBack = { page = backTarget(AppPage.MAP) },
                    modifier = pageModifier,
                    focusRoom = mapFocusRoom,
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
    // D-107: 인트라넷·DIMA C 읽기는 셸 위의 숨은 WebView가 맡고, 로그인이 필요할 때만 공식 포털을 화면에 띄운다
    if (schoolServiceBridge != null) {
        com.example.dimanow.school.SchoolServiceHost(schoolServiceBridge, lmsCredentialStore)
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
    savedFavoritesFlow: Flow<SavedHomeFavorites>,
    shuttleSource: ShuttleSource,
    mealSource: MealSource,
    noticeSource: NoticeSource?,
    lmsSource: LmsSource,
    lmsSessionController: LmsSessionController,
    onNavigateToPage: (AppPage) -> Unit,
    onAddCourse: () -> Unit,
    onShowRoom: (String) -> Unit,
    updateState: AppUpdateUiState,
    appUpdateCoordinator: AppUpdateCoordinator?,
    modifier: Modifier,
    now: ZonedDateTime,
) {
    // D-137: Home's own inputs are null until they are read, and Home is drawn only once all of them are. Their
    // stand-in defaults (no classes, an empty LMS cache, outside campus, COMMUTE, the default card order with nothing
    // hidden, the default favourites) drew the wrong briefing and cards for the first cold-start frames and corrected
    // them a frame later, which the card and list motion would now animate at every launch.
    val schedule = repository.schedule.collectLoadedAsState().value
    val homeLms = lmsSource.snapshot.collectLoadedAsState().value
    val homeLmsSession by lmsSessionController.state.collectAsStateWithLifecycle()
    val resolvedZone = preferences.effectiveZone.collectLoadedAsState().value
    val locationMode = preferences.locationMode.collectLoadedAsState().value
    // D-094: null until each source emits its first cached value, so cold start shows loading, not empty
    val shuttle by shuttleSource.data.collectLoadedAsState()
    val meal by mealSource.data.collectLoadedAsState()
    val dormitoryMeal by mealSource.dormitoryData.collectLoadedAsState()
    val emptyNotices = remember { NoticeData(emptyList(), null, null, null, OFFICIAL_NOTICE_SOURCE_URL) }
    val notices by (noticeSource?.data ?: remember { kotlinx.coroutines.flow.flowOf(emptyNotices) })
        .collectLoadedAsState()
    // D-106: exam weeks and the next term start for Home's title follow the published calendar
    val serviceCalendar by com.example.dimanow.calendar.ServiceCalendarStore.calendar.collectAsStateWithLifecycle()
    // D-122: 엔터관 방향(예인관) 학생에게 점호 날 점호 카드를 보여 준다
    val homeBase = preferences.homeBase.collectLoadedAsState().value
    val rollCallNotices by com.example.dimanow.dorm.RollCallStore.notices.collectAsStateWithLifecycle()
    val todayRollCallStatus = todayRollCall(now.toLocalDate(), rollCallNotices, serviceCalendar)
    // D-128(1): the student's card order, hidden cards and favourites row
    val cardOrder = preferences.homeCardOrder.collectLoadedAsState().value
    val hiddenCards = preferences.homeHiddenCards.collectLoadedAsState().value
    val savedFavorites = savedFavoritesFlow.collectLoadedAsState().value
    var editingHome by rememberSaveable { mutableStateOf(false) }
    val homeContext = LocalContext.current
    val outingLauncher = rememberOutingLauncher(todayRollCallStatus)
    // D-128(3): keyword alerts are offered once, when the student comes back from 학교 공지 opened on Home
    val noticeKeywordSuggested by preferences.noticeKeywordSuggested.collectAsStateWithLifecycle(initialValue = true)
    var noticeOpenedFromHome by rememberSaveable { mutableStateOf(false) }
    var showNoticeSuggestion by rememberSaveable { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        if (noticeOpenedFromHome && !noticeKeywordSuggested) showNoticeSuggestion = true
        noticeOpenedFromHome = false
    }
    // D-126(7): after an update Home offers what changed, once; the stored value starts as "seen" to avoid a flash.
    val whatsNewSeen by preferences.whatsNewSeenVersionCode
        .collectAsStateWithLifecycle(initialValue = com.example.dimanow.BuildConfig.VERSION_CODE)
    val whatsNewScope = rememberCoroutineScope()
    val updateCard: (@Composable () -> Unit)? = if (appUpdateCoordinator != null && updateState.showsHomeNotice()) {
        {
            HomeUpdateNotice(
                state = updateState,
                onInstall = appUpdateCoordinator::downloadAndInstall,
                onLater = appUpdateCoordinator::dismissPrompt,
                onCancel = appUpdateCoordinator::cancelDownload,
                onContinueInstall = appUpdateCoordinator::continueInstall,
            )
        }
    } else null
    val whatsNewCard: (@Composable () -> Unit)? =
        if (showsWhatsNew(whatsNewSeen, com.example.dimanow.BuildConfig.VERSION_CODE, onboardingCompleted = true)) {
            {
                HomeWhatsNewNotice(
                    versionName = com.example.dimanow.BuildConfig.VERSION_NAME,
                    onDismiss = {
                        whatsNewScope.launch { preferences.setWhatsNewSeenVersionCode(com.example.dimanow.BuildConfig.VERSION_CODE) }
                    },
                )
            }
        } else null

    if (schedule == null || homeLms == null || resolvedZone == null || locationMode == null || homeBase == null ||
        cardOrder == null || hiddenCards == null || savedFavorites == null
    ) {
        HomeWaiting(shown = rememberShownAfter(true), modifier = modifier)
        return
    }
    val rollCall = todayRollCallStatus.takeIf { showsHomeRollCall(homeBase == HomeBase.YEIN, it) }
    val favorites = homeFavorites(savedFavorites.keys, homeBase)
    DashboardScreen(
        schedule = schedule,
        serviceCalendar = serviceCalendar,
        lmsSnapshot = homeLms,
        lmsSessionState = homeLmsSession,
        zone = resolvedZone,
        livesInDorm = homeBase == HomeBase.YEIN,
        testMode = locationMode == LocationMode.TEST,
        shuttle = shuttle,
        meal = meal,
        dormitoryMeal = dormitoryMeal,
        notices = notices,
        onNavigateToPage = onNavigateToPage,
        onAddCourse = onAddCourse,
        onShowRoom = onShowRoom,
        updateNotice = updateCard,
        whatsNewNotice = whatsNewCard,
        modifier = modifier,
        now = now,
        rollCall = rollCall,
        cardOrder = cardOrder,
        hiddenCards = hiddenCards,
        favorites = favorites,
        onOpenShortcut = { shortcut ->
            when (shortcut) {
                HomeShortcut.TIMETABLE -> onNavigateToPage(AppPage.TIMETABLE)
                HomeShortcut.DORM -> onNavigateToPage(AppPage.DORM)
                HomeShortcut.EQUIPMENT -> onNavigateToPage(AppPage.EQUIPMENT)
                HomeShortcut.SETTINGS -> onNavigateToPage(AppPage.SETTINGS)
                HomeShortcut.OUTING -> outingLauncher.request()
                HomeShortcut.LIBRARY_CARD -> openApp(homeContext, LIBRARY_APP_PACKAGE)
                HomeShortcut.HEYOUNG -> openApp(homeContext, HEYOUNG_APP_PACKAGE)
                HomeShortcut.MEAL_TICKET -> openPaycoMealTicket(homeContext)
            }
        },
        onEditHome = { editingHome = true },
        onNoticeOpened = { if (!noticeKeywordSuggested) noticeOpenedFromHome = true },
    )
    outingLauncher.error?.let { message ->
        LaunchedEffect(message) { android.widget.Toast.makeText(homeContext, message, android.widget.Toast.LENGTH_SHORT).show() }
    }
    if (editingHome) {
        HomeEditorDialog(
            order = homeCardOrder(cardOrder, emptySet()),
            hidden = hiddenCards,
            favorites = favorites,
            onSave = { order, hidden, picked ->
                whatsNewScope.launch { preferences.setHomeLayout(order.map { it.key }, hidden, picked.map { it.key }) }
                editingHome = false
            },
            onDismiss = { editingHome = false },
        )
    }
    if (showNoticeSuggestion) {
        NoticeKeywordSuggestionSheet(
            onEnable = { keywords ->
                whatsNewScope.launch {
                    preferences.setNoticeKeywords(keywords)
                    preferences.setNoticeKeywordAlerts(true)
                    preferences.setNoticeKeywordSuggested()
                }
                showNoticeSuggestion = false
            },
            onLater = {
                whatsNewScope.launch { preferences.setNoticeKeywordSuggested() }
                showNoticeSuggestion = false
            },
        )
    }
}

/**
 * The saved favourites row as Home's gate reads it (D-137): `keys` null means nothing picked yet (the defaults of
 * [homeFavorites]), while the wrapper itself is null until the setting was read.
 */
private data class SavedHomeFavorites(val keys: List<String>?)

/**
 * Home while its own settings are read (D-137): nothing for a wait under the loading grace period, which is almost
 * always the case (a frame or two), and a loading line after it. The list's entrance covers Home's first appearance.
 */
@Composable
private fun HomeWaiting(shown: Boolean, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (shown) LoadingLine("홈을 불러오고 있어요", Modifier.testTag("dashboard_waiting"))
    }
}

@Composable
private fun TimetableRoute(
    repository: CampusDataRepository,
    modifier: Modifier,
    addCourseRequest: Long,
    onAddCourseRequestHandled: () -> Unit,
    onBack: () -> Unit,
    onShowRoom: (String) -> Unit,
) {
    val schedule by repository.schedule.collectRetainedAsState(initialValue = remember { emptyTermSchedule() })
    TimetableScreen(
        repository, schedule, modifier,
        addCourseRequest = addCourseRequest,
        onAddCourseRequestHandled = onAddCourseRequestHandled,
        onBack = onBack,
        onShowRoom = onShowRoom,
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
    lmsCredentialStore: LmsCredentialStore,
    lmsSessionController: LmsSessionController,
    lmsSource: LmsSource,
    updateState: AppUpdateUiState,
    appUpdateCoordinator: AppUpdateCoordinator?,
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
    val academicReminders by preferences.academicReminders.collectAsStateWithLifecycle(initialValue = true)
    val deadlineDigest by preferences.deadlineDigest.collectAsStateWithLifecycle(initialValue = true)
    val outingReminders by preferences.outingReminders.collectAsStateWithLifecycle(initialValue = true)
    val appContextForReminders = LocalContext.current.applicationContext
    val noticeKeywordAlerts by preferences.noticeKeywordAlerts.collectAsStateWithLifecycle(initialValue = false)
    val noticeKeywords by preferences.noticeKeywords.collectAsStateWithLifecycle(initialValue = emptyList())
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
        academicReminders = academicReminders,
        onAcademicRemindersChange = { scope.launch { preferences.setAcademicReminders(it) } },
        deadlineDigest = deadlineDigest,
        onDeadlineDigestChange = { scope.launch { preferences.setDeadlineDigest(it) } },
        noticeKeywordAlerts = noticeKeywordAlerts,
        noticeKeywords = noticeKeywords,
        onNoticeKeywordAlertsChange = {
            scope.launch {
                preferences.setNoticeKeywordAlerts(it)
                preferences.setNoticeKeywordSuggested()
            }
        },
        onNoticeKeywordsChange = { scope.launch { preferences.setNoticeKeywords(it) } },
        outingReminders = outingReminders,
        onOutingRemindersChange = {
            scope.launch {
                preferences.setOutingReminders(it)
                if (!it) com.example.dimanow.dorm.OutingReminder.cancel(appContextForReminders)
            }
        },
        homeBase = homeBase,
        onHomeBaseChange = { scope.launch { preferences.setHomeBase(it) } },
        shuttleData = shuttle,
        mealData = meal,
        onShowNowBarSetup = onShowNowBarSetup,
        lmsCredentialState = credentialState,
        onDeleteLmsAccount = {
            scope.launch {
                lmsCredentialStore.delete()
                lmsSource.clearPrivateData()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                com.example.dimanow.school.SchoolReadCache.clear()
                lmsSessionController.transition(LmsSessionState.SIGNED_OUT)
            }
        },
        onBack = onBack,
        modifier = modifier,
        updateState = updateState,
        onCheckUpdate = { appUpdateCoordinator?.checkManually() },
        onDownloadUpdate = { appUpdateCoordinator?.downloadAndInstall() },
        onContinueInstall = { appUpdateCoordinator?.continueInstall() },
        onCancelDownload = { appUpdateCoordinator?.cancelDownload() },
    )
}

/** How many times the visible tab was selected again; [ScreenScaffold] scrolls to the top on each new value. */
internal val LocalScrollToTopSignal = compositionLocalOf { 0 }
