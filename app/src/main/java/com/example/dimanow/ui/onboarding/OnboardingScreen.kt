package com.example.dimanow.ui.onboarding

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import com.example.dimanow.theme.DimaShapes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.data.AppPreferences
import com.example.dimanow.data.OnboardingDraft
import com.example.dimanow.data.OnboardingStage
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.live.LiveSurfaceController
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.GuidanceSetupChecklist
import com.example.dimanow.ui.GuidanceSetupState
import com.example.dimanow.ui.rememberGuidanceSetupController
import com.example.dimanow.ui.DimaLayout
import com.example.dimanow.ui.motion.AnimatedCountText
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.LocalReducedMotion
import com.example.dimanow.ui.motion.PageMotion
import com.example.dimanow.ui.motion.PageSlideDistance
import com.example.dimanow.ui.motion.dimaCollapseOut
import com.example.dimanow.ui.motion.dimaContentReveal
import com.example.dimanow.ui.motion.dimaExpandIn
import com.example.dimanow.ui.motion.dimaPageTransform
import com.example.dimanow.ui.motion.dimaPopIn
import com.example.dimanow.ui.motion.dimaPopOut
import com.example.dimanow.ui.motion.pressScale
import com.example.dimanow.ui.motion.rememberShownAfter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Established installs keep their existing completion or confirmed home direction after an update. */
internal fun shouldShowOnboarding(onboardingCompleted: Boolean, homeBaseConfirmed: Boolean): Boolean =
    !onboardingCompleted && !homeBaseConfirmed

@Composable
fun OnboardingRoute(
    preferences: AppPreferences,
    liveSurfaceController: LiveSurfaceController,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier,
    onPermissionsChanged: () -> Unit = {},
) {
    val draft by preferences.onboardingDraft.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // D-137: the step or direction just tapped shows from the tap on, so the step's motion and the tile's fill start
    // with the finger rather than after the DataStore round trip. It gives way once the stored draft has caught up and
    // is dropped when the save fails, so the screen falls back to what was stored.
    var pending by remember { mutableStateOf<OnboardingDraft?>(null) }
    LaunchedEffect(draft, pending) {
        if (draft != null && draft == pending) pending = null
    }
    fun save(next: OnboardingDraft?, action: suspend () -> Unit) {
        if (saving) return
        saving = true
        pending = next
        scope.launch {
            try {
                action()
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                pending = null
                error = "설정을 저장하지 못했어요. 다시 시도해 주세요."
            } finally {
                saving = false
            }
        }
    }
    val setup = rememberGuidanceSetupController(liveSurfaceController, onPermissionsChanged)
    // D-137: the stored draft is read from DataStore far within 200ms, so the screen waits on its plain surface and
    // shows the loading indicator only for a slow read; the first step then fades in instead of popping in after a
    // flash of the indicator.
    val slowRead = rememberShownAfter(draft == null)
    AnimatedContent(
        targetState = pending ?: draft,
        contentKey = { it != null },
        transitionSpec = { dimaContentReveal() },
        label = "onboarding_ready",
        modifier = modifier.fillMaxSize(),
    ) { current ->
        if (current == null) {
            Surface(Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    if (slowRead) com.example.dimanow.ui.DimaLoadingIndicator()
                }
            }
        } else {
            OnboardingScreen(
                draft = current,
                onDraftChange = { next -> save(next) { preferences.saveOnboardingDraft(next) } },
                onComplete = { homeBase -> save(null) { preferences.completeOnboarding(homeBase); onComplete() } },
                saving = saving,
                error = error,
                setup = setup.state,
                onRequestEssentials = setup::requestEssentials,
                setupContent = { GuidanceSetupChecklist(setup, onlyIncomplete = true) },
            )
        }
    }
}

/**
 * First run (D-075 order, D-102 content): what the app does, the home direction, then one permission step.
 * Each step opens with a small decorative graphic of Material shapes ([OnboardingHero]) that morphs between steps.
 *
 * The permission step follows Apple HIG and the Toss guide: it names the two permissions guidance needs with
 * one sentence each, and its single `계속` button lets Android ask for both in turn. Refusing never blocks the
 * app (D-056). Only after that does [setupContent] show the optional items that need a system settings page;
 * `나중에 할게요` finishes without asking at all.
 *
 * D-137: a step's heading and body move forward or back as a page does ([dimaPageTransform]) under the graphic, which
 * stays and turns with them; the back button grows in from the start edge so the step counter glides aside, and the
 * counter rolls. After `계속` the optional items grow in beneath the permissions, each permission's check mark pops
 * in where room was kept for it, and `나중에 할게요` folds away. A save shows `저장 중` and the disabled look only when
 * it takes longer than 200ms.
 */
@Composable
internal fun OnboardingScreen(
    draft: OnboardingDraft,
    onDraftChange: (OnboardingDraft) -> Unit,
    onComplete: (HomeBase) -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
    error: String? = null,
    setup: GuidanceSetupState = GuidanceSetupState(),
    onRequestEssentials: () -> Unit = {},
    setupContent: @Composable () -> Unit = {},
) {
    var essentialsAsked by rememberSaveable { mutableStateOf(false) }
    val essentialsHandled = essentialsAsked || setup.essentialsGranted
    // D-137: a DataStore save ends within a few frames, so the controls keep their look unless it takes longer than
    // the 200ms grace; a tap meanwhile is still ignored ([OnboardingRoute]'s guard and the checks below).
    val busy = rememberShownAfter(saving)
    val reduced = LocalReducedMotion.current
    fun back() {
        val previous = when (draft.stage) {
            OnboardingStage.SETUP -> OnboardingStage.HOME_BASE
            else -> OnboardingStage.WELCOME
        }
        if (!saving) onDraftChange(draft.copy(stage = previous))
    }
    BackHandler(enabled = draft.stage != OnboardingStage.WELCOME) { back() }
    Surface(modifier.fillMaxSize().testTag("onboarding_root")) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = DimaLayout.readingWidth).fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // D-137: the back button grows in from the start edge, so the counter beside it glides aside.
                AnimatedVisibility(
                    visible = draft.stage != OnboardingStage.WELCOME,
                    enter = if (reduced) EnterTransition.None else {
                        fadeIn(DimaMotion.effectsFast()) + expandHorizontally(DimaMotion.spatialDefault(), expandFrom = Alignment.Start)
                    },
                    exit = if (reduced) ExitTransition.None else {
                        fadeOut(DimaMotion.effectsFast()) + shrinkHorizontally(DimaMotion.spatialDefault(), shrinkTowards = Alignment.Start)
                    },
                    label = "onboarding_back",
                ) {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = ::back, enabled = !busy, modifier = Modifier.testTag("onboarding_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
                val counter = "${draft.stage.ordinal + 1} / 3"
                if (reduced) {
                    Text(
                        counter,
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AnimatedCountText(
                        text = counter,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            // Outside the step transition, so the graphic morphs from one step to the next instead of starting over.
            val hero = rememberOnboardingHeroState(draft.stage)
            // Each step still opens at the top; the scrolling column itself stays, so only the step below the graphic
            // changes (D-137).
            val scroll = key(draft.stage) { rememberScrollState() }
            val slidePx = with(LocalDensity.current) { PageSlideDistance.roundToPx() } *
                (if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                OnboardingHero(hero)
                // D-137: the heading and body move with the graphic's turn: forward slides in from the end edge, back
                // from the start (a fade under reduced motion), and the step's height follows on the slow spring.
                AnimatedContent(
                    targetState = draft.stage,
                    transitionSpec = {
                        val motion = if (targetState.ordinal > initialState.ordinal) PageMotion.FORWARD else PageMotion.BACKWARD
                        dimaPageTransform(motion, slidePx, reduced).using(SizeTransform(clip = false) { _, _ -> DimaMotion.spatialSlow() })
                    },
                    label = "onboarding_step",
                ) { stage ->
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    when (stage) {
                        OnboardingStage.WELCOME -> {
                            StepHeading("학교생활을 한눈에", "수업, 셔틀, 식단을 한곳에서 확인해요.")
                            // D-102: 무엇을 해 주는 앱인지 먼저 보여 준다 (가치 먼저, 설정은 나중에)
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                OnboardingRow(Icons.Default.Schedule, "수업 안내", "수업 시작 전에 강의실과 시간을 알려 드려요.")
                                OnboardingRow(Icons.Default.DirectionsBus, "셔틀 안내", "지금 있는 곳에서 곧 출발하는 셔틀을 보여 드려요.")
                                OnboardingRow(Icons.Default.Restaurant, "오늘 식단", "학생식당과 기숙사 식단을 바로 확인해요.")
                                OnboardingRow(Icons.Default.School, "과제 마감", "학교 계정을 연결하면 마감을 모아 보여 드려요.")
                            }
                            // D-099: 첫 화면에서 비공식 앱임과 데이터 처리 원칙을 알린다
                            Surface(
                                shape = DimaShapes.Tile,
                                color = DimaTheme.colors.card,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        com.example.dimanow.ui.UNOFFICIAL_NOTICE + " 시간표와 위치는 이 기기에만 저장돼요.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.testTag("onboarding_unofficial_notice"),
                                    )
                                    Text(
                                        com.example.dimanow.ui.DATA_ACCURACY_NOTICE,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    androidx.compose.material3.TextButton(
                                        shapes = ButtonDefaults.shapes(),
                                        onClick = { com.example.dimanow.ui.openUrl(context, com.example.dimanow.ui.AppLinks.PRIVACY_POLICY) },
                                        modifier = Modifier.testTag("onboarding_privacy_policy"),
                                    ) { Text("개인정보 처리방침 보기") }
                                }
                            }
                        }
                        OnboardingStage.HOME_BASE -> {
                            StepHeading("주로 어느 방향으로 돌아가나요?", "수업이 끝난 뒤 안내할 셔틀 방향이에요. 설정에서 언제든 바꿀 수 있어요.")
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple(HomeBase.YEIN, "엔터관 방향", null),
                                Triple(HomeBase.ONE_ROOM, "원룸촌 방향", null),
                                Triple(HomeBase.COMMUTE, "통학", "귀가 셔틀은 안내하지 않아요"),
                            ).forEach { (homeBase, label, supporting) ->
                                val selected = draft.homeBase == homeBase
                                // D-137: the chosen tile's fill changes on the effects spring and the tile gives under the
                                // finger like the app's other cards; both read one interaction source with the ripple.
                                val fill by animateColorAsState(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer else DimaTheme.colors.card,
                                    DimaMotion.effectsDefault(),
                                    label = "onboarding_home_base_fill",
                                )
                                val interaction = remember { MutableInteractionSource() }
                                Surface(
                                    shape = DimaShapes.Tile,
                                    color = fill,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).pressScale(interaction),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().selectable(
                                            selected = selected,
                                            interactionSource = interaction,
                                            indication = ripple(),
                                            enabled = !busy,
                                            role = Role.RadioButton,
                                            onClick = { onDraftChange(draft.copy(homeBase = homeBase)) },
                                        ).testTag("onboarding_home_base_${homeBase.name.lowercase()}").padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(label, style = MaterialTheme.typography.titleMedium)
                                            supporting?.let {
                                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        RadioButton(selected = selected, onClick = null, enabled = !busy)
                                    }
                                }
                            }
                            }
                        }
                        OnboardingStage.SETUP -> {
                            StepHeading(
                                "알림과 위치를 허용해 주세요",
                                "수업과 셔틀을 제때 안내하려면 두 가지가 필요해요. 허용하지 않아도 앱은 그대로 쓸 수 있어요.",
                            )
                            Column {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OnboardingRow(
                                        Icons.Default.Notifications, "알림", "수업 시작 전과 셔틀 출발 전에 알려 드려요.",
                                        granted = setup.notifications,
                                    )
                                    OnboardingRow(
                                        Icons.Default.LocationOn, "위치", "지금 있는 곳에서 출발하는 셔틀을 보여 드려요. 위치는 이 기기 안에서만 써요.",
                                        granted = setup.preciseLocation || setup.approximateLocation,
                                    )
                                }
                                // D-137: once Android has asked, the optional items grow in beneath, the gap above them
                                // inside the fold so nothing jumps when it starts or ends.
                                AnimatedVisibility(
                                    visible = essentialsHandled,
                                    enter = dimaExpandIn(),
                                    exit = dimaCollapseOut(),
                                    label = "onboarding_optional",
                                ) {
                                    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("더 정확한 안내 (선택)", style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "휴대폰 설정에서 켜는 항목이에요. 나중에 설정 → 자동 안내 권한에서도 켤 수 있어요.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        setupContent()
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                // 권한 화면의 첫 버튼은 Android의 허용 창을 띄우는 '계속' 하나다 (HIG). 묻고 난 뒤에는 '완료'로 끝낸다.
                val asksEssentials = draft.stage == OnboardingStage.SETUP && !essentialsHandled
                Column {
                    Button(shapes = ButtonDefaults.shapes(),
                        onClick = {
                            when (draft.stage) {
                                OnboardingStage.WELCOME -> onDraftChange(draft.copy(stage = OnboardingStage.HOME_BASE))
                                OnboardingStage.HOME_BASE -> onDraftChange(draft.copy(stage = OnboardingStage.SETUP))
                                OnboardingStage.SETUP -> when {
                                    // A tap while the step is still being saved (a quick double tap on 다음) never asks Android.
                                    saving -> Unit
                                    asksEssentials -> {
                                        essentialsAsked = true
                                        onRequestEssentials()
                                    }
                                    else -> draft.homeBase?.let(onComplete)
                                }
                            }
                        },
                        enabled = !busy && (draft.stage == OnboardingStage.WELCOME || draft.homeBase != null),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("onboarding_primary"),
                    ) {
                        val label = if (busy) "저장 중" else when (draft.stage) {
                            OnboardingStage.WELCOME -> "시작하기"
                            OnboardingStage.HOME_BASE -> "다음"
                            OnboardingStage.SETUP -> if (asksEssentials) "계속" else "완료"
                        }
                        // D-137: the label cross-fades as the step moves on.
                        AnimatedContent(
                            targetState = label,
                            transitionSpec = {
                                fadeIn(DimaMotion.effectsFast()) togetherWith fadeOut(DimaMotion.effectsFast()) using
                                    SizeTransform(clip = false) { _, _ -> DimaMotion.spatialFast() }
                            },
                            contentAlignment = Alignment.Center,
                            label = "onboarding_primary_label",
                        ) { Text(it) }
                    }
                    // 언제나 빠져나갈 길이 있다 (토스 UX 가이드): 묻지 않고 바로 시작할 수 있다. D-137: it folds away once
                    // Android has asked, its gap inside the fold.
                    AnimatedVisibility(
                        visible = asksEssentials,
                        enter = dimaExpandIn(),
                        exit = dimaCollapseOut(),
                        label = "onboarding_skip",
                    ) {
                        androidx.compose.material3.TextButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = { draft.homeBase?.let(onComplete) },
                            enabled = !busy && draft.homeBase != null,
                            modifier = Modifier.padding(top = 6.dp).fillMaxWidth().testTag("onboarding_skip"),
                        ) { Text("나중에 할게요") }
                    }
                }
            }
        }
        }
    }
}

/**
 * One feature or permission line: icon, name, one sentence, and a check mark once a permission is on. D-137: a
 * permission line ([granted] not null) keeps room for the mark from the start, so the check pops in when Android's
 * prompt returns and the sentence never re-wraps (a fade under reduced motion).
 */
@Composable
private fun OnboardingRow(icon: ImageVector, title: String, description: String, granted: Boolean? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = DimaShapes.Badge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted != null) {
            val reduced = LocalReducedMotion.current
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                // The plain overload, named: inside the Box the row's RowScope overload is out of reach (scope marker).
                androidx.compose.animation.AnimatedVisibility(
                    visible = granted,
                    enter = dimaPopIn(reduced),
                    exit = dimaPopOut(reduced),
                    label = "onboarding_granted",
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "허용됨",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.semantics { heading() })
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
