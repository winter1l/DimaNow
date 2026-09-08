package com.example.dimanow.ui.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.ui.motion.expressiveBounceClick
import com.example.dimanow.ui.motion.staggeredEntrance

/**
 * 최초 실행 권한 온보딩 (D-056).
 *
 * 이전에는 권한을 설정 탭 깊숙한 카드에서만 요청할 수 있어, 새로 설치하면 지오펜스와
 * Live Update가 조용히 동작하지 않았다. 이 화면은 앱이 실제로 쓰는 권한을 순서대로
 * 설명하고 요청한 뒤 귀가 기준지까지 받는다. 사용자는 각 단계를 건너뛸 수 있다.
 */
internal enum class OnboardingStep { WELCOME, NOTIFICATION, LOCATION, BACKGROUND_LOCATION, EXACT_ALARM, HOME_BASE }

/**
 * 온보딩을 보여줄지 결정한다.
 *
 * 최초 설치에서만 보여주고, 귀가 기준지를 이미 확정한 기존 설치는 업데이트해도 다시 보지 않는다
 * (`onboardingCompleted`는 D-056에서 추가된 키라 기존 설치에서는 항상 false로 읽힌다).
 */
internal fun shouldShowOnboarding(onboardingCompleted: Boolean, homeBaseConfirmed: Boolean): Boolean =
    !onboardingCompleted && !homeBaseConfirmed

/**
 * 시스템 설정에서 권한을 켜고 돌아왔을 때 그 단계를 자동으로 넘길지 (D-059).
 *
 * 정확한 알람은 런타임 권한이 아니라 시스템 설정 화면으로만 켤 수 있어 결과 콜백이 없다.
 * 그래서 복귀 시점에 직접 다시 읽어야 하고, 이 화면에 머무는 동안 꺼짐→켜짐으로 바뀐
 * 경우에만 넘긴다. 허용하지 않고 돌아왔거나 이미 켜져 있었다면 화면을 그대로 둔다.
 */
internal fun shouldAdvanceOnResume(step: OnboardingStep, wasGranted: Boolean, isGranted: Boolean): Boolean =
    step == OnboardingStep.EXACT_ALARM && !wasGranted && isGranted

/** 현재 기기 상태에서 실제로 보여줘야 하는 단계만 남긴다. */
internal fun onboardingSteps(
    needsNotification: Boolean,
    needsExactAlarm: Boolean,
): List<OnboardingStep> = buildList {
    add(OnboardingStep.WELCOME)
    if (needsNotification) add(OnboardingStep.NOTIFICATION)
    add(OnboardingStep.LOCATION)
    add(OnboardingStep.BACKGROUND_LOCATION)
    if (needsExactAlarm) add(OnboardingStep.EXACT_ALARM)
    add(OnboardingStep.HOME_BASE)
}

@Composable
fun OnboardingRoute(
    onSelectHomeBase: (HomeBase) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val alarmManager = remember(context) { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    var notificationGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var fineGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var backgroundGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) }
    var exactAlarmGranted by remember { mutableStateOf(alarmManager.canScheduleExactAlarms()) }

    val steps = remember(notificationGranted) {
        onboardingSteps(
            needsNotification = Build.VERSION.SDK_INT >= 33,
            needsExactAlarm = true,
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val step = steps.getOrElse(index) { OnboardingStep.HOME_BASE }

    fun advance() {
        if (index < steps.lastIndex) index++ else onComplete()
    }

    // 설정 앱에서 권한을 켜고 돌아오면 화면에 곧바로 반영한다 (D-059).
    // 이 재확인이 없으면 정확한 알람 단계가 '설정 열기'인 채로 멈춰, 이미 허용했는데도
    // 버튼이 설정 화면만 다시 열어 온보딩을 진행할 수 없었다.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationGranted = hasNotificationPermission(context)
        fineGranted = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        backgroundGranted = hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val exactAlarmNow = alarmManager.canScheduleExactAlarms()
        val advanceAfterGrant = shouldAdvanceOnResume(step, exactAlarmGranted, exactAlarmNow)
        exactAlarmGranted = exactAlarmNow
        if (advanceAfterGrant) advance()
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationGranted = granted
        advance()
    }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        advance()
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        backgroundGranted = granted
        advance()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("onboarding_root"),
    ) {
        OnboardingProgress(
            current = index,
            total = steps.size,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp),
        )

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (
                    slideInHorizontally(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        initialOffsetX = { it / 4 },
                    ) + fadeIn(tween(180))
                ).togetherWith(
                    slideOutHorizontally(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        targetOffsetX = { -it / 4 },
                    ) + fadeOut(tween(180)),
                )
            },
            label = "onboarding_step",
            modifier = Modifier.weight(1f),
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                when (current) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.NOTIFICATION -> PermissionStep(
                        icon = Icons.Default.Notifications,
                        title = "수업과 셔틀을 제때 알려드릴게요",
                        body = "수업 시작 전 알림과 잠금화면 실시간 안내(나우바)를 쓰려면 알림 권한이 필요합니다.",
                        detail = "알림을 끄면 시간표와 셔틀을 앱에서 직접 확인해야 합니다.",
                        granted = notificationGranted,
                    )
                    OnboardingStep.LOCATION -> PermissionStep(
                        icon = Icons.Default.LocationOn,
                        title = "지금 있는 캠퍼스를 자동으로 인식해요",
                        body = "엔터관·본관·원룸촌 중 어디에 있는지에 따라 출발 셔틀과 식단을 자동으로 골라 보여줍니다.",
                        detail = "위치는 기기 안에서만 판정하며 어디에도 보내지 않습니다.",
                        granted = fineGranted,
                    )
                    OnboardingStep.BACKGROUND_LOCATION -> PermissionStep(
                        icon = Icons.Default.MyLocation,
                        title = "앱을 열지 않아도 위젯이 맞춰져요",
                        body = "캠퍼스에 들어오고 나갈 때 위젯과 실시간 안내가 자동으로 바뀌려면 '항상 허용'이 필요합니다.",
                        detail = if (fineGranted) {
                            "다음 화면에서 '항상 허용'을 선택해 주세요."
                        } else {
                            "정확한 위치를 먼저 허용해야 설정할 수 있습니다. 지금은 건너뛰고 나중에 설정에서 켤 수 있어요."
                        },
                        granted = backgroundGranted,
                    )
                    OnboardingStep.EXACT_ALARM -> PermissionStep(
                        icon = Icons.Default.Alarm,
                        title = "수업 시작 시각을 정확히 맞춰요",
                        body = "정확한 알람을 허용하면 수업 시작·종료와 셔틀 카운트다운이 분 단위로 정확해집니다.",
                        detail = "시스템 설정 화면이 열립니다. 돌아오면 다음으로 진행하세요.",
                        granted = exactAlarmGranted,
                    )
                    OnboardingStep.HOME_BASE -> HomeBaseStep(onSelectHomeBase = onSelectHomeBase, onComplete = onComplete)
                }
            }
        }

        if (step != OnboardingStep.HOME_BASE) {
            OnboardingActions(
                step = step,
                notificationGranted = notificationGranted,
                fineGranted = fineGranted,
                backgroundGranted = backgroundGranted,
                exactAlarmGranted = exactAlarmGranted,
                onSkip = { advance() },
                onPrimary = {
                    when (step) {
                        OnboardingStep.WELCOME -> advance()
                        OnboardingStep.NOTIFICATION ->
                            if (notificationGranted || Build.VERSION.SDK_INT < 33) {
                                advance()
                            } else {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        OnboardingStep.LOCATION ->
                            if (fineGranted) {
                                advance()
                            } else {
                                fineLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        // 백그라운드 위치는 정확한 위치가 승인된 뒤에만 시스템이 받아준다
                        OnboardingStep.BACKGROUND_LOCATION ->
                            if (backgroundGranted || !fineGranted) {
                                advance()
                            } else {
                                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        // 열 수 있는 설정 화면이 없으면 눌러도 아무 일이 없는 대신 단계를 넘긴다 (D-059)
                        OnboardingStep.EXACT_ALARM ->
                            if (exactAlarmGranted || !openExactAlarmSettings(context)) {
                                advance()
                            }
                        OnboardingStep.HOME_BASE -> Unit
                    }
                },
            )
        }
    }
}

@Composable
private fun OnboardingProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { position ->
            val active = position <= current
            val barWidth by animateFloatAsState(
                targetValue = if (position == current) 1f else 0.45f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                label = "onboarding_progress_$position",
            )
            Box(
                modifier = Modifier
                    .weight(barWidth)
                    .height(4.dp)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp).staggeredEntrance(0),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.staggeredEntrance(1),
        ) {
            Text(
                text = "DIMA Now에 오신 걸 환영해요",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "수업·셔틀·식단을 한 화면에서 실시간으로 안내합니다. 먼저 필요한 권한을 함께 확인할게요.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WelcomeHighlight(Icons.Default.Schedule, "수업 시작 전 미리 알림", 2)
            WelcomeHighlight(Icons.Default.DirectionsBus, "현재 위치 기준 셔틀 카운트다운", 3)
            WelcomeHighlight(Icons.Default.Restaurant, "오늘의 학생식당·기숙사 식단", 4)
        }
    }
}

@Composable
private fun WelcomeHighlight(icon: ImageVector, text: String, entranceIndex: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().staggeredEntrance(entranceIndex),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PermissionStep(
    icon: ImageVector,
    title: String,
    body: String,
    detail: String,
    granted: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp).staggeredEntrance(0),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (granted) Icons.Default.Check else icon,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.staggeredEntrance(1),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().staggeredEntrance(2),
        ) {
            Text(
                text = if (granted) "이미 허용되어 있어요." else detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun HomeBaseStep(onSelectHomeBase: (HomeBase) -> Unit, onComplete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.staggeredEntrance(0),
        ) {
            Text("어디로 돌아가시나요?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "수업이 끝난 뒤 본관에서 안내할 셔틀 방향입니다. 설정에서 언제든 바꿀 수 있어요.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HomeBaseChoice(
            label = "예인관",
            description = "엔터관 방향으로 귀가해요",
            entranceIndex = 1,
            testTag = "onboarding_home_base_yein",
            onClick = { onSelectHomeBase(HomeBase.YEIN); onComplete() },
        )
        HomeBaseChoice(
            label = "원룸촌",
            description = "원룸촌 방향으로 귀가해요",
            entranceIndex = 2,
            testTag = "onboarding_home_base_one_room",
            onClick = { onSelectHomeBase(HomeBase.ONE_ROOM); onComplete() },
        )
    }
}

@Composable
private fun HomeBaseChoice(
    label: String,
    description: String,
    entranceIndex: Int,
    testTag: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(entranceIndex)
            .expressiveBounceClick(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun OnboardingActions(
    step: OnboardingStep,
    notificationGranted: Boolean,
    fineGranted: Boolean,
    backgroundGranted: Boolean,
    exactAlarmGranted: Boolean,
    onSkip: () -> Unit,
    onPrimary: () -> Unit,
) {
    val alreadyGranted = when (step) {
        OnboardingStep.NOTIFICATION -> notificationGranted
        OnboardingStep.LOCATION -> fineGranted
        OnboardingStep.BACKGROUND_LOCATION -> backgroundGranted || !fineGranted
        OnboardingStep.EXACT_ALARM -> exactAlarmGranted
        else -> false
    }
    val primaryLabel = when {
        step == OnboardingStep.WELCOME -> "시작하기"
        alreadyGranted -> "다음"
        step == OnboardingStep.EXACT_ALARM -> "설정 열기"
        else -> "허용하기"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth().testTag("onboarding_primary"),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(primaryLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (step != OnboardingStep.WELCOME && !alreadyGranted) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().testTag("onboarding_skip"),
            ) {
                Text("나중에 하기", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)

/** 정확한 알람 설정 화면을 연다. 열 수 있는 화면이 하나도 없으면 false. */
private fun openExactAlarmSettings(context: Context): Boolean {
    val intents = listOf(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
    val intent = intents.firstOrNull { it.resolveActivity(context.packageManager) != null } ?: return false
    return runCatching { context.startActivity(intent) }.isSuccess
}
