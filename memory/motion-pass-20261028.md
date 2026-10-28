# M3 Expressive motion pass — 2026-10-28 (D-137)

Request (confirmed, 2026-10-28): "현재 앱의 에니메이션을 점검하고, M3Expressive UI UX 모션을 적용할 수 있는 곳을 확인하고 채워서 더욱 생동감 넘치게 해줘." Decision: D-137. Deferred proposals: Q-045.

## How it was done

- Observed (code reading, 2026-10-28): an inventory of every animation API use, then six read-only audits (one per screen group) against the UX guide motion section, m3.material.io motion and the standing decisions; a synthesis turned them into 13 rules (R1–R13 below) and per-package item lists.
- Shared helpers were written first in `ui/motion/ExpressiveMotion.kt`; six packages with disjoint files were then implemented in isolated worktrees (two waves of three), each compiled and unit-tested there, and merged as patches. The only clash was two identical `rememberLastNonNull` helpers; the Home copy was removed.
- An adversarial review (three lenses: runtime, decisions, tests; each finding re-checked by a skeptic) found 17 items, 14 confirmed; the confirmed ones were fixed (list below).

## Rules used (R1–R13)

- R1 whole list replaced (day, venue, mode) → `ScreenScaffold` `contentKey`/`pageIndex`: one fade or slide for the list, item motion off meanwhile.
- R2 placeholder → content: `dimaContentReveal`.
- R3 one block replaced by the next of its kind: `dimaContentSwap`.
- R4 list data change: `dimaItemChange` (fade in/out; placement only where nothing above animates its own height).
- R5 folds carry their gap inside; pinned-header folds settle without bounce.
- R6 small element or icon swap: `dimaPopIn`/`dimaPopOut`/`dimaIconSwap`.
- R7 selected chip or cell: `FilterChipDefaults.shapes` morph + `animateColorAsState`.
- R8 tone change: `animateColorAsState` on an effects spring.
- R9 changing number or short status: `AnimatedCountText`/`dimaTextRoll`.
- R10 known progress: flat bar, `animateFloatAsState(ProgressIndicatorDefaults.ProgressAnimationSpec)`.
- R11 busy after a tap: `rememberShownAfter` (200 ms) then `DimaLoadingIndicator`.
- R12 sub-page: `dimaPageTransform` + `rememberPredictiveBackPreview`.
- R13 sheet closed by a button: `sheetState.hide()`, then remove.

## What each package did

### Shared parts (ScreenScaffold, loading line, day selector, dialogs, settings rows, 전체 footer)

- `meal-1+shell-7 (ScreenScaffold side)`: ScreenScaffold gets a new `contentKey: Any? = null` parameter next to pageIndex. shownPage and shownKey are mutableStateOf, and `switching` is computed during composition.
- `shell-4+meal-6+timetable-dorm-10`: The FAB slot is now AnimatedVisibility(floatingActionButton != null, dimaPopIn(reduced), dimaPopOut(reduced)) and shows the last non-null FAB lambda, held by a new internal helper rememberLastNonNull in ScreenScaffold.kt (a plain field, not state), so a FAB t…
- `shell-9+meal-4`: DimaDayCellSelector uses the FilterChip(selected, onClick, label, shapes: ChipShapes, ...) overload with FilterChipDefaults.shapes(shape = DimaShapes.Tile, selectedShape = CircleShape, pressedShape = DimaShapes.Small). javap confirmed that this overload anima…
- `shell-6+meal-2+shell-14`: LoadingLine's Row uses Modifier.loadingGrace().
- `shell-10+lms-17`: DimaDialogIcon draws a tone polygon: INFO Cookie9Sided, WARNING SoftBurst, DANGER Burst, in the existing D-120 container colour, with the icon in the tone's content colour.
- `misc-9 (SettingsExpander part)`: SettingsExpander's supporting line is AnimatedContent(targetState = supporting, dimaContentSwap()), shown only when supporting is non-null so the null layout is unchanged. supportingColor goes through animateColorAsState(DimaMotion.effectsDefault()).
- `shell-13`: LocationTestCard: the supporting line ('GPS 반영 안 함' / '현재 위치 자동 판정') is AnimatedContent(testMode, dimaContentReveal()).
- `shell-12`: 전체 footer: the text colour goes through animateColorAsState(effectsDefault).
- Fix in `ScreenScaffold.kt`: Mode-switch blink: the start state (hidden, at the pending ±18% offset) now comes from `switching`, which is computed in the composition that already holds the new list.
- Fix in `ScreenScaffold.kt`: Header tint read per frame in composition: headerColor is kept as a State, the header Surface is Color.Transparent with Modifier.drawBehind { drawRect(headerColor.value) }, and DimaTopAppBar gets transparent container and scrolled-container colours.
- Not done `meal-10`: The premise is wrong for the material3 version in use. javap -c of androidx.compose.material3.ScaffoldKt in material3-android 1.5.0-beta02 (the measure and placement lambdas of ScaffoldLayout) shows that fabOffsetFromBottom is fabHeight + FabSpacing + insets.…

### Shell and Home

- `shell-1+lms-18`: The bottom bar sits in AnimatedVisibility with expandVertically/shrinkVertically(DimaMotion.settle, Top) plus fadeIn(effectsDefault)/fadeOut(effectsFast).
- `shell-8`: navIcon crossfades the filled and outlined glyphs (Crossfade, DimaMotion.effectsFast).
- `home-2+home-3`: Adds a private BriefingBody { Class(course) | Free(title, supporting) } that drives the hero AnimatedContent, keyed on id to start or on the title, with dimaContentSwap.
- `home-1`: Inside the briefing AnimatedContent, the badge fill (primary/secondary) and ink (onPrimary/onSecondary) go through animateColorAsState(DimaMotion.effectsDefault), the same spec as HeroMoodMark.
- `home-4`: The shuttle body is AnimatedContent over a private HomeShuttleBody(phase, zone, message) with contentKey = phase to zone.
- `home-5`: The location-prompt card and the summary card are wrapped in AnimatedContent(needsLocationPrompt, dimaContentReveal, TopCenter).
- `home-7`: The meal body is AnimatedContent over a private HomeMealBody(kind, dormitory, date, dormDay, menu) with contentKey = Triple(kind, dormitory, date).
- `home-8`: The meal status badge is in AnimatedVisibility(showStatus, dimaPopIn/dimaPopOut(reduced)).
- `home-9`: The notices body is AnimatedContent(phase to list, contentKey = phase, dimaContentReveal) with phases LOADING, ERROR, WAITING_FOR_NETWORK, EMPTY and LIST.
- `home-10`: (1) The prompt and the card switch through AnimatedContent(prompt text or null, contentKey = it != null, dimaContentReveal); the external modifier (animateItem) is on that root.
- `home-11`: DashboardScreen gets separate updateNotice and whatsNewNotice slots, and DashboardRoute passes the two lambdas.
- `home-12`: These items get dimaItemChange() (fade in and out, no placement): test_mode, favorites, learning, academic, shuttle, roll_call (in a Box) and meal.
- `home-13+home-14`: Progress uses animateFloatAsState(ProgressIndicatorDefaults.ProgressAnimationSpec), read only in progress = {}, on the flat LinearProgressIndicator.
- `home-15`: The editor body is a LazyColumn (tag home_editor_list) with items cards_header, the cards (keyed by card), favorites_header, the favourites (keyed favorite_<key>) and reset.
- `home-16`: HomeCompactPrompt shares one MutableInteractionSource between Surface(onClick) and Modifier.pressScale.
- `R7 notice keyword chips`: Adds a private NoticeKeywordChip: FilterChip(shapes = FilterChipDefaults.shapes(shape = FilterChipDefaults.shape, selectedShape = CircleShape, pressedShape = DimaShapes.ExtraSmall)).
- Fix in `DimaNowApp.kt`: Replaced the inline predictive back with rememberPredictiveBackPreview(enabled = page != DASHBOARD, currentKey = page, onBack = { page = backTarget(page) }).
- Fix in `DimaNowApp.kt`: Added a gate to DashboardRoute. schedule, LMS snapshot, home base, card order, hidden cards and favourites are collected with collectLoadedAsState(); I also gated effectiveZone and locationMode, whose OUTSIDE/GPS defaults would animate the shuttle card at lau…
- Fix in `HomeHeroMark.kt`: A mood change while the launch intro is still running now keeps fromMood = null and retargets the morph from its current progress instead of snapping to 0.
- Fix in `WhatsNew.kt`: 확인했어요 runs scope.launch { sheetState.hide() }.invokeOnCompletion { showSheet = false; onDismiss() }, so the sheet slides down.
- Fix in `NoticeKeywords.kt`: The sheet state and a scope are hoisted.

### 수업, 도서관, 장비 대여, school sites

- `lms-1`: LmsRoute now models what the tab shows as a private sealed LmsSurface: SignIn, Authenticating, Items, Detail(presented), Official(page) and Rendered.
- `lms-2`: LmsRoute keeps openingKey.
- `lms-3`: One always-present first item, 'lms_status', holds AnimatedContent(contentKey = kind) over Elsewhere / Failed(message) / Updated(label) / None.
- `lms-4`: One 'lms_state' item holds AnimatedContent(contentKey = key) over Loading / Failed / Empty(message).
- `lms-6`: DimaConnectedChoice and the filter row sit in one inner Column.
- `lms-7`: Two-pane rows use the selectable SegmentedListItem(selected, onClick, ...) overload with segmentedColors(containerColor = card or cardMuted, selectedContainerColor = secondaryContainer).
- `lms-8`: Section-heading and fold-header counts ('N개') go through AnimatedCountText.
- `lms-9`: LmsAuthenticationFrame fades in once through a graphicsLayer alpha Animatable on effectsDefault over the whole frame.
- `lms-10`: The attachment button has a fixed Box(ButtonDefaults.IconSize) slot with AnimatedContent(IDLE / DOWNLOADING / SAVED) on dimaIconSwap(reduced): a Download icon, a CircularProgressIndicator at IconSize, or DownloadDone.
- `lms-11`: LmsDetailPane uses AnimatedContent(presented, contentKey = entryKey): dimaContentReveal between the placeholder and a detail, dimaFadeThrough(scale = !reduced) between two items.
- `lms-12`: The on-screen school page Column gets webPageEntrance(): a draw-phase fade plus a forward slide.
- `lms-13`: The shared sign-in form sits in AnimatedVisibility(MutableTransitionState): fadeIn(effectsDefault) plus slideInHorizontally(spatialSlow) { PageSlideDistance px, RTL-signed }, fade only under reduced motion, and fadeOut(effectsFast) on exit.
- `lms-15`: The filter chip's ArrowDropDown turns 180 degrees while its menu is open, via animateFloatAsState(spatialFast) read in graphicsLayer.rotationZ; no rotation under reduced motion.
- `misc-11`: LibraryCountTile numbers go through AnimatedCountText, and the tile container and content colours through animateColorAsState(effectsDefault).
- `misc-12`: The hours list and the '이용 시간 공지' header and list are now one 'library_hours' item: AnimatedContent(LibraryHoursView, contentKey = LOADING / LIST / EMPTY, dimaContentReveal).
- `misc-13`: equipment_apply_error is now always present: AnimatedContent(applyError, dimaContentReveal) shows the ErrorState with the top gap, or a Spacer. library_open_error, the last item, uses dimaItemChange(fadeIn = true, fadeOut = true).
- Fix in `LmsScreen.kt`: The snapshot is collected with collectRetainedAsState(initialValue = LmsSnapshot()).
- Fix in `LmsScreen.kt`: On phones the list surface sits in SaveableStateProvider("items"), so todayMode, openSections and ScreenScaffold's LazyListState survive a detail.
- Fix in `LmsDetailScreen.kt`: The attachment icon and the progress spinner now share a fixed ButtonDefaults.IconSize slot, with the icon sized to IconSize, so the file name no longer moves 6dp when a download starts or ends.
- Fix in `LmsItemsScreen.kt`: LmsFoldedSection uses the shared dimaExpandIn()/dimaCollapseOut() instead of inline specs.
- Not done `lms-5`: Rows gliding to new places would look broken here. dimaItemChange(placement = true) puts a settle spring on every position change, but the 과목별 rows sit below folds that animate their own height (D-118 AnimatedVisibility), and all rows sit below the status and…

### 셔틀, 4402, 식단

- `shuttle-1`: The subHeader is now one inner Column with no spacing.
- `shuttle-6+day change`: Added a private ShuttlePhase enum (LOADING/ERROR/NO_DATA/SUSPENDED/CONTENT), computed once.
- `meal-1+meal-2`: ScreenScaffold(contentKey = Triple(selectedDay, venueLoaded, dayHasContent)). venueLoaded means the shown venue's flow has emitted. dayHasContent is computed per venue: dormitoryBlocks.isNotEmpty() for 기숙사, the VALID cafeteria day having menu lines for 본관.
- `meal-3`: MealPeriodCard: container, content colour and defaultElevation now go through animateColorAsState / animateDpAsState on DimaMotion.effectsDefault.
- `meal-5`: The scroll key is split into a place part (venue|day|upload|venueLoaded|dayHasContent) and a focus part.
- `meal-7`: The item is emitted only when studentMealSyncLine != NONE.
- `meal-8`: The 사진을 준비하고 있어요 row and DormitoryUploadProgressCard use dimaItemChange(fadeIn = true, fadeOut = true, placement = false).
- `meal-9`: EmptyState gets actionBusy: Boolean = false, passed to the tonal StateAction.
- `shuttle-4`: The back-to-next arrow's AnimatedVisibility uses dimaPopIn(reducedMotion) and dimaPopOut(reducedMotion).
- `shuttle-5`: The arrow Surface takes a remembered MutableInteractionSource, with Modifier.pressScale(source, scaleDown = 0.9f) before .rotate().
- `shuttle-7`: EndedCell gets Modifier.fillMaxHeight().then(dimaItemChange(fadeIn = true, fadeOut = false, placement = false)).
- `shuttle-3`: Added internal rememberPlaceEmphasis(active) plus a PlaceEmphasis data class (container, border, title) in Bus4402Screen.kt, each through animateColorAsState(effectsDefault).
- `shuttle-2`: Covered by placement = true on the origin header and route card items.
- Fix in `ShuttleScreen.kt`: Fixed the double fade on 4402 -> 교내 셔틀.
- Fix in `TimetableChips.kt`: Removed the competing springs on one cell width.
- Fix in `TimetableChips.kt`: The cell-kind cross-fade is now (fadeIn(effectsDefault) togetherWith fadeOut(effectsFast)).using(null).
- Fix in `TimetableChips.kt`: Moved per-frame reads out of composition with the same springs and the same layering.
- Fix in `Shape.kt`: Reworded the tileToPill and TileToPillShape KDoc.
- Fix in `MealScreen.kt`: student_meal_sync is emitted only when studentMealSyncLine(meal, today) != NONE.
- Fix in `MealScreen.kt`: DormitoryUploadProgressCard drops the looping LoadingIndicator.
- Fix in `MealScreen.kt`: Instant jumps for a new venue, day, upload or loaded content now use listState.requestScrollToItem(target) from a SideEffect.

### 시간표, 기숙사, timetable dialogs, intranet import

- `timetable-dorm-1`: DormScreen wraps its pages in AnimatedContent(targetState = topic, dimaPageTransform(FORWARD when a topic opens, BACKWARD when it closes, slidePx, reduced), label 'dorm_topic').
- `timetable-dorm-2`: Every 시간표 item root (term, term_guide, intranet_import_loading, courses_empty, change_error, changes_header, change_*, day_*, course_*, academic_agenda, no_class_dates, no_class_date_list) gets dimaItemChange(placement = true), which keeps fadeIn/fadeOut at t…
- `timetable-dorm-3`: The 'points' item on the first screen is AnimatedContent(targetState = DormPointsView(phase, read), contentKey = { it.phase }, dimaContentReveal(), label 'dorm_points'), showing PROMPT (the card with 상벌점 불러오기), LOADING (LoadingLine) or SUMMARY (PointsSummaryC…
- `timetable-dorm-4`: points_error (first screen and 상벌점 page) and outing_error are no longer items of their own.
- `timetable-dorm-5`: PointTile and PointTotal numbers ('${points}점') go through AnimatedCountText.
- `timetable-dorm-6`: CourseOverrideDialog: the 시간·강의실 fields sit in AnimatedVisibility(changed, dimaExpandIn(), dimaCollapseOut()) { Column(Modifier.padding(top = 12.dp), spacedBy(12.dp)) }.
- `timetable-dorm-7`: Inline validation messages unfold in AnimatedVisibility(dimaExpandIn/dimaCollapseOut) with the gap as top padding inside, each grouped with the field above it in a gapless Column: TermEditorDialog's reversed range (12dp), CourseOverrideDialog's date error (12…
- `timetable-dorm-8`: The 본관/엔터관 picker in the course editor is DimaConnectedChoice(options = MAIN/YEIN to DisplayVocabulary.originName, selected = zone, onSelect, itemTag = { 'course_zone_${it.name}' }).
- `timetable-dorm-9 (chip variant)`: The seven weekday FilterChips are a private WeekdayChip: FilterChipDefaults.shapes(shape = FilterChipDefaults.shape, selectedShape = CircleShape, pressedShape = DimaShapes.Small), with container and label colours through animateColorAsState(DimaMotion.effects…
- `timetable-dorm-11`: The pause status line is Crossfade(statusText, DimaMotion.effectsDefault()).
- `timetable-dorm-12`: New package-internal AnimatedKeyedColumn(items, key, modifier, gap, content) in ui/AnimatedKeyedColumn.kt.
- `timetable-dorm-14`: '카톡 공지로 점호 확인하기' sits in AnimatedVisibility(!kakaoAccess, dimaExpandIn(), dimaCollapseOut()) with padding(top = 12.dp) inside, grouped with the chip row in a gapless Column.
- `timetable-dorm-16+home-17`: DormChip (DormHighlights) and OpenBadge (DormScreen) colours go through animateColorAsState(DimaMotion.effectsDefault()).
- `timetable-dorm-17`: The term pill text is AnimatedContent(targetState = termLabel, (fadeIn(effectsDefault) togetherWith fadeOut(effectsFast)).using(SizeTransform { spatialFast })).
- `timetable-dorm-13`: CourseTimePickerDialog content is AnimatedContent(targetState = displayMode, dimaContentReveal(), contentAlignment = TopCenter): TimePicker or TimeInput on the shared state.
- `timetable-dorm-18`: 주소 복사 is a private CopyAddressButton: AnimatedContent(copied, dimaIconSwap(reduced)) swaps ContentCopy + '주소 복사' for Check + '복사했어요'.
- Fix in `TimetableScreen.kt`: CourseSummaryCard now uses the ElevatedCard(onClick = onEdit, interactionSource = source, modifier = modifier.fillMaxWidth().pressScale(source)) pattern instead of expressiveBounceClick outside a non-clickable card.
- Fix in `TimetableScreen.kt`: The AddCourseFabMenu toggle icon reads checkedProgress through remember { derivedStateOf { checkedProgress > 0.5f } }, so the FAB content no longer recomposes every frame while the menu animates. animateIcon still reads progress in its lambda.
- Fix in `DormScreen.kt`: The 기숙사 first screen and the topic pages no longer share one LazyListState.
- Fix in `IntranetTimetableImport.kt`: The intranet import's confirm now calls a new CampusDataRepository.replaceCourses(remove, add).
- Not done `timetable-dorm-12 (reuse for the 상벌점 entries list)`: Does not fit.
- Not done `timetable-dorm-16+home-17 (pop for the past-deadline chip)`: Not skipped, changed: the chip folds in (dimaExpandIn/dimaCollapseOut, R5) instead of popping (dimaPopIn/dimaPopOut).

### 설정, first run, feedback, 캠퍼스 지도

- `misc-1`: Onboarding: OnboardingHero and the scrolling column stay outside the transition; `val scroll = key(draft.stage) { rememberScrollState() }`.
- `misc-2`: Permission rows (granted != null) always reserve a 24dp Box at the end.
- `misc-3`: The optional block ('더 정확한 안내 (선택)' plus setupContent) now sits in the same child as the two permission rows.
- `misc-4`: 귀가 방향 tiles: the fill uses animateColorAsState(primaryContainer/card, effectsDefault).
- `misc-5`: SetupRow trailing slot: AnimatedContent(item.completed, dimaIconSwap(reduced), contentAlignment = CenterEnd).
- `misc-6`: GuidanceSetupContent keeps every applicable item.
- `misc-7+shell-11`: Settings 자동 안내 권한: the checklist moved into the guidance_setup_toggle item as Column { SettingsExpander; AnimatedVisibility(setupExpanded, dimaExpandIn(), dimaCollapseOut()) { GuidanceSetupChecklist(guidanceSetup, Modifier.padding(top = DimaLayout.sectionGap)…
- `misc-8`: 고급 및 진단 uses the same fold.
- `misc-10`: AppUpdateCard: (a) shownProgress = animateFloatAsState(…, ProgressIndicatorDefaults.ProgressAnimationSpec), read only in the flat LinearProgressIndicator's progress lambda.
- `misc-15`: FeedbackDialog: the 오류-only parts (screenshots, log switch, preview) are in AnimatedVisibility(bug, dimaExpandIn, dimaCollapseOut), grouped with the version switch; gaps sit inside the folds as padding.
- `misc-16`: showSending = rememberShownAfter(sending).
- `misc-17`: The early return is replaced by AnimatedContent(posted, contentKey = { it != null }, dimaContentReveal()) choosing the form or FeedbackPosted.
- `misc-18`: Send failures: AnimatedContent(result?.takeIf { it !is Posted }, contentKey = { it?.let { r -> r::class } }, dimaContentReveal()).
- `misc-9 (Kakao part)`: KakaoRollCallSetting: the status sentence is in AnimatedContent(granted, dimaContentSwap()).
- `map-1`: One stable item(key = "focus") while a building is selected or a room is unknown.
- `map-2`: A private SelectionMotion holds target/outgoing state plus edge, fill and outgoingAlpha Animatables, driven by LaunchedEffect(selected).
- `map-3`: 확대, 축소 and 캠퍼스 전체 보기 use the FilledTonalIconButton(onClick, shapes = IconButtonDefaults.shapes(), …) overload (confirmed with javap on the 1.5.0-beta02 classes.jar).
- `map-4`: 캠퍼스 전체 보기 is in AnimatedVisibility(resetShown, Modifier.align(TopStart).padding(CONTROL_INSET), dimaPopIn(reduced), dimaPopOut(reduced)), driven by the new derived state; the map_reset tag stays on the button.
- `map-5`: LocationStatusLine: AnimatedContent(targetState = state to text, contentKey = state kind (the position object, or shownOnMap for Known), dimaContentReveal()).
- `map-6`: The student dot appears via Animatable (keyed on presence) 0 → 1 on DimaMotion.spatialFast, applied as graphicsLayer scale on StudentMarker (new scale lambda) and multiplied into the accuracy radius.
- `map-8`: CampusBuildingRow uses SegmentedListItem(selected = building == selected, onClick, shapes = segmentedShapes(index, count), colors = ListItemDefaults.segmentedColors(containerColor = card, selectedContainerColor = secondaryContainer, selectedContentColor = onS…
- Fix in `OnboardingScreen.kt`: Save flash (L308): OnboardingScreen drives '저장 중' and the disabled look of the primary, back, tiles, radios and skip from busy = rememberShownAfter(saving); taps in the grace period are still ignored by Route's `if (saving) return` and back()'s check.
- Fix in `OnboardingScreen.kt`: Initial null draft (L89): OnboardingRoute renders AnimatedContent(pending ?: draft, contentKey = { it != null }, dimaContentReveal()).
- Fix in `OnboardingHero.kt`: Intro alpha (L198): a separate introAlpha Animatable animates on DimaMotion.effectsDefault in a sibling launch of the same LaunchedEffect and is read in the same graphicsLayer; scale and turn stay on spatialSlow.
- Fix in `OnboardingHero.kt`: Mid-morph jump (L156): on a step change the turn sets off at once; if progress != 1 the running morph first finishes on DimaMotion.spatialFast, then from/to swap and the new morph runs.
- Fix in `OnboardingScreen.kt`: 개인정보 처리방침 보기 and 나중에 할게요 TextButtons now pass shapes = ButtonDefaults.shapes().
- Fix in `FeedbackDialog.kt`: The screenshot remove FilledTonalIconButton passes shapes = IconButtonDefaults.shapes().
- Fix in `CampusMapScreen.kt`: MapControls recomposed every frame (L997): the zoom/maxZoom/showReset lambdas are held with rememberUpdatedState and read only through remember { derivedStateOf { … } } (canZoomIn, canZoomOut, resetShown), so MapControls recomposes only when a flag flips.
- Fix in `CampusMapScreen.kt`: CameraMotion's hardcoded spring(DampingRatioNoBouncy, StiffnessMediumLow) is replaced by DimaMotion.settle<Float>() (same values); the Spring/spring imports are removed.
- Fix in `CampusBaseMap.kt`: Base-map Loading frame (L363): added CampusBaseMapAsset.cachedOrNull(). rememberCampusBaseMap seeds produceState with BaseMapLoad.Ready(cached) when present and skips the IO read, so only the first open per process goes through Loading.
- Not done `map-9`: The rules list it as never to implement (map pan fling with decay, deferred).

## Adversarial review (2026-10-28)

- confirmed, medium — `ExpressiveMotion.kt`: After a committed predictive back, the preview is cleared only by PredictiveBackPage's onDispose. (fixed)
- confirmed, medium — `TabStateRestorationTest.kt`: waitForHomeData no longer waits for Home. (fixed)
- confirmed, low — `MealScreen.kt`: Two different springs move the same layout change. (fixed)
- confirmed, low — `OnboardingHero.kt`: The new step-change logic swaps from/to only after the old morph finishes (lines 171-173). (fixed)
- confirmed, low — `MealScreen.kt`: Some items in the 식단 list fade out on their own while the whole list slides and fades during a 본관/기숙사 switch. (fixed)
- confirmed, medium — `LmsScreen.kt`: openingKey is cleared only when the open job completes, and on failure that job first waits for showSnackbar to finish. (fixed)
- uncertain, medium — `DimaDialogs.kt`: Every pop-up's tone mark now grows from a circle into a MaterialShapes polygon while turning 30 degrees on the spatial spring. (not changed)
- confirmed, low — `TabStateRestorationTest.kt`: The new Home gate in DashboardRoute (DimaNowApp.kt:648-651) shows HomeWaiting, which has no dashboard_* tags, until eight cold DataStore/Room flows emit. waitForHomeData's condition, 'no dashboard_shuttle_loading and no dashboard_meal_loading node', is theref… (fixed)
- confirmed, low — `FeedbackDialog.kt`: When a send that took over 200 ms is answered with Posted, the action slot's inner AnimatedContent (showSending true to false) cross-fades the loading indicator back to an enabled '보내기' button while the outer AnimatedVisibility (posted == null, line 200) fade… (fixed)
- confirmed, low — `HomeUpdateNotice.kt`: The polite liveRegion moved off the changing Text onto an unmerged wrapper Box around an AnimatedContent. (fixed)
- confirmed, low — `HomeScreen.kt`: The update and what's-new cards moved inside the 'hero' item, which now comes after the test_mode item (line 190). (fixed)
- confirmed, medium — `LmsScreen.kt`: openingKey is cleared only in invokeOnCompletion (line 301), after the open coroutine finishes. (fixed)
- confirmed, medium — `ExpressiveMotion.kt`: After a committed back gesture, the preview is cleared only when the leaving page's PredictiveBackPage is disposed (lines 333-339). (fixed)
- confirmed, medium — `TabStateRestorationTest.kt`: homeListScrollPositionSurvivesATripToShuttleAndBack can fail: with the new Home gate (DimaNowApp.kt:648-651), waitForHomeData's condition (no dashboard_shuttle_loading or dashboard_meal_loading nodes) is already true while Home still shows HomeWaiting, so the… (fixed)
- confirmed, low — `ExpressiveMotion.kt`: On commit, line 298 sets preview.active=false before back(), so predictiveBackBackdrop stops drawing in the same frame. (fixed)
- uncertain, low — `ShuttleScreen.kt`: rememberPlaceEmphasis reads three animateColorAsState values inside a composable that returns a value, so the reads belong to ShuttleRouteCard's restart scope. (not changed)
- uncertain, low — `DimaNowApp.kt`: The rail (and on phones the bottom bar) now leaves with a size animation on DimaMotion.settle, so the full-screen LMS WebView pages get a new size on every frame for about 0.4 s. (not changed)

Not changed and why: the pop-up mark morph is kept and recorded in D-137(4) as superseding D-120(1); the 셔틀 card rebuilding its cell list on each frame of a GPS-zone colour change and the LMS WebView resizing while the rail slides away are real but rare and showed no visible fault in the review; left as observed risks.

## Device checks still to make by eye

- 식단 tab: switch 본관 학생식당 → 기숙사 and back several times.
- 셔틀 tab: switch 교내 셔틀/4402 and change the weekday.
- 식단 day selector (dates): same round selected cell and fill fade.
- 수업 tab 할 일/과목별 switch (pageIndex): slide plus fade, no blink.
- 전체 → 설정 (or any 전체 page), then Back: the bottom bar slides down past the screen edge while the page slides in, and the page bottom and the 시간표 FAB follow it instead of jumping 80dp.
- Tablet or unfolded width (rail): opening a 전체 page slides the rail away toward the start edge.
- Tap between bottom tabs: the filled icon cross-fades in with the indicator pill.
- Predictive back on a 전체 page (Android 13+ gesture): while held the page shrinks and rounds over the backdrop.
- 수업 (phone): tap an item whose detail loads slowly.
- 수업 (phone): open a detail from 과목별 after scrolling and opening a fold, then press Back.
- 수업 (phone): hold a predictive back gesture on a detail.
- 수업: open a discussion or quiz, i.e. an item that shows the official LMS page.
- 셔틀 tab: switch 교내 셔틀 -> 4402 -> 교내 셔틀.
- 셔틀 tab, 교내 셔틀: tap another weekday.
- 셔틀 tab: wait for a departure hand-over on the S23 (D-124(5)).
- 셔틀 tab: scroll a strip away from the next departure.
- 전체 > 기숙사, scroll the first screen down, open 생활 정보 > 시설 이용 시간: the topic page slides in from the end edge with a fade and starts at its top with an untinted header.
- 기숙사 topic page, slow predictive back swipe from either edge: the topic page shrinks toward 0.9, shifts away from the edge and rounds its corners over a surfaceContainerHighest backdrop, with the first screen not scaled.
- 기숙사 with no saved 상벌점: tap 상벌점 불러오기.
- 기숙사 with the school read failing (airplane mode, then 상벌점 불러오기 or refresh): the error card opens under the 상벌점 card (12dp gap) and grows the item, with no separate item popping in; 다시 시도 clears it with a fade.
- Onboarding, fresh install or cleared data: the first step fades in with no loading-indicator flash.
- Onboarding 귀가 방향: tapping a tile changes its fill at once (on the tap, not after the save), the tile presses down slightly with the ripple, and the radio follows.
- Onboarding: tap 다음 then 뒤로 within half a second.
- Onboarding SETUP: tap 계속 and allow both prompts.
