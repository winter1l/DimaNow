package com.example.dimanow.ui.onboarding

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.data.AppPreferences
import com.example.dimanow.data.OnboardingDraft
import com.example.dimanow.data.OnboardingStage
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.live.LiveSurfaceController
import com.example.dimanow.ui.GuidanceSetup
import com.example.dimanow.ui.DimaLayout
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
    val current = draft
    if (current == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    fun save(action: suspend () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                action()
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "설정을 저장하지 못했어요. 다시 시도해 주세요."
            } finally {
                saving = false
            }
        }
    }
    OnboardingScreen(
        draft = current,
        onDraftChange = { next -> save { preferences.saveOnboardingDraft(next) } },
        onComplete = { homeBase -> save { preferences.completeOnboarding(homeBase); onComplete() } },
        modifier = modifier,
        saving = saving,
        error = error,
        setupContent = { GuidanceSetup(liveSurfaceController, onPermissionsChanged = onPermissionsChanged) },
    )
}

@Composable
internal fun OnboardingScreen(
    draft: OnboardingDraft,
    onDraftChange: (OnboardingDraft) -> Unit,
    onComplete: (HomeBase) -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
    error: String? = null,
    setupContent: @Composable () -> Unit = {},
) {
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
                if (draft.stage != OnboardingStage.WELCOME) {
                    IconButton(onClick = ::back, enabled = !saving, modifier = Modifier.testTag("onboarding_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
                Text(
                    "${draft.stage.ordinal + 1} / 3",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            key(draft.stage) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (draft.stage) {
                        OnboardingStage.WELCOME -> {
                            StepHeading("학교생활을 한눈에", "수업, 셔틀, 오늘 식단을 확인해요.")
                        }
                        OnboardingStage.HOME_BASE -> {
                            StepHeading("주로 어느 방향으로 돌아가나요?", "수업이 끝난 뒤 안내할 셔틀 방향이에요.")
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(HomeBase.YEIN to "엔터관 방향", HomeBase.ONE_ROOM to "원룸촌 방향").forEach { (homeBase, label) ->
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = if (draft.homeBase == homeBase) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().selectable(
                                            selected = draft.homeBase == homeBase,
                                            enabled = !saving,
                                            role = Role.RadioButton,
                                            onClick = { onDraftChange(draft.copy(homeBase = homeBase)) },
                                        ).testTag("onboarding_home_base_${homeBase.name.lowercase()}").padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                        RadioButton(selected = draft.homeBase == homeBase, onClick = null, enabled = !saving)
                                    }
                                }
                            }
                            }
                        }
                        OnboardingStage.SETUP -> {
                            StepHeading("자동 안내를 설정해요", "수업과 셔틀 안내에 필요한 설정을 확인해 주세요.")
                            setupContent()
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
                if (draft.stage == OnboardingStage.SETUP) {
                    Text("나중에 설정에서 변경할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = {
                        when (draft.stage) {
                            OnboardingStage.WELCOME -> onDraftChange(draft.copy(stage = OnboardingStage.HOME_BASE))
                            OnboardingStage.HOME_BASE -> onDraftChange(draft.copy(stage = OnboardingStage.SETUP))
                            OnboardingStage.SETUP -> draft.homeBase?.let(onComplete)
                        }
                    },
                    enabled = !saving && (draft.stage == OnboardingStage.WELCOME || draft.homeBase != null),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("onboarding_primary"),
                ) {
                    Text(if (saving) "저장 중" else when (draft.stage) {
                        OnboardingStage.WELCOME -> "설정 시작"
                        OnboardingStage.HOME_BASE -> "다음"
                        OnboardingStage.SETUP -> "시작하기"
                    })
                }
            }
        }
        }
    }
}

@Composable
private fun StepHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
