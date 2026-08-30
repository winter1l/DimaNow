package com.example.dimanow.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive Motion Specifications
 */
object ExpressiveMotion {
    val SmoothSpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    val BouncySpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    val SnappySpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** 카드 스태거드 입장 간격 (index당 지연) */
    const val STAGGER_DELAY_MILLIS = 60L
}

/**
 * M3 Expressive 터치 바운스 인터랙션 모디파이어
 */
fun Modifier.expressiveBounceClick(
    scaleDown: Float = 0.97f,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    this
        .scale(scale.value)
        .pointerInput(onClick) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    scope.launch {
                        scale.animateTo(
                            scaleDown,
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                        )
                    }
                    val up = waitForUpOrCancellation()
                    scope.launch {
                        scale.animateTo(
                            1f,
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    }
                    if (up != null && onClick != null) {
                        onClick()
                    }
                }
            }
        }
}

/**
 * 실시간 상태("수업 중", "곧 출발", "현재 위치") 뱃지용 은은한 알파 호흡 애니메이션
 */
@Composable
fun Modifier.pulseBreath(
    minAlpha: Float = 0.65f,
    maxAlpha: Float = 1.0f,
    durationMillis: Int = 1200,
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_breath")
    val alpha by infiniteTransition.animateFloat(
        initialValue = maxAlpha,
        targetValue = minAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath_alpha",
    )
    return this.alpha(alpha)
}

/** M3 emphasized decelerate — 화면 진입 전환용 표준 이징 */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * 화면 진입 시 카드가 순서대로(index 기반 지연) 아래에서 떠오르며 나타나는
 * M3 Expressive 스태거드 입장 애니메이션. 지연을 포함한 전 구간이 컴포즈
 * 프레임 클록으로 구동되어 UI 테스트의 idle 대기와도 호환된다.
 */
@Composable
fun Modifier.staggeredEntrance(index: Int): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    // 리스트가 길어도 마지막 항목이 과도하게 늦게 나타나지 않도록 지연을 상한 처리한다
    val delayMillis = (index.coerceAtMost(8) * ExpressiveMotion.STAGGER_DELAY_MILLIS).toInt()
    val translationY by animateFloatAsState(
        targetValue = if (entered) 0f else 36f,
        animationSpec = tween(durationMillis = 420, delayMillis = delayMillis, easing = EmphasizedDecelerate),
        label = "entrance_translation_$index",
    )
    return this.graphicsLayer {
        this.translationY = translationY
    }
}

/**
 * 분 카운트다운처럼 매분 바뀌는 짧은 텍스트를 위·아래 슬라이드 spring 전환으로
 * 갈아끼우는 M3 Expressive 숫자 전환 컴포저블. 시맨틱 텍스트 값은 그대로 유지된다.
 */
@Composable
fun AnimatedCountText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                initialOffsetY = { it / 2 },
            ) + fadeIn(spring(stiffness = Spring.StiffnessMediumLow))).togetherWith(
                slideOutVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    targetOffsetY = { -it / 2 },
                ) + fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
            )
        },
        label = "animated_count_text",
        modifier = modifier,
    ) { value ->
        Text(text = value, style = style, color = color, fontWeight = fontWeight)
    }
}
