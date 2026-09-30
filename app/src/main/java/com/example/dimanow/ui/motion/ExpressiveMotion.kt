package com.example.dimanow.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Values from Material3 1.4.0 ExpressiveMotionTokens (v0_14_0).
 * Its MotionScheme is internal, so custom app motion uses these verified spring tokens.
 * Compose's animation clock applies the system animator duration scale to these animations.
 */
object ExpressiveMotion {
    fun <T> defaultSpatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> fastSpatial(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> defaultEffects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> fastEffects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
}

/** Native click behavior, ripple and semantics, with a small spatial press response. */
fun Modifier.expressiveBounceClick(
    scaleDown: Float = 0.97f,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    // A decorative modifier must never intercept a child control or create an empty click action.
    if (onClick == null) return@composed this
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = ExpressiveMotion.fastSpatial(),
        label = "press_scale",
    )
    this.scale(scale).clickable(
        interactionSource = interactionSource,
        indication = ripple(),
        role = Role.Button,
        onClick = onClick,
    )
}

/** Compatibility for existing status callers: persistent status stays fully readable and still. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.pulseBreath(
    minAlpha: Float = 0.65f,
    maxAlpha: Float = 1.0f,
    durationMillis: Int = 1200,
): Modifier = this

/** App decision: a subtle 12dp entry offset, with no delay before content becomes actionable. */
@Composable
fun Modifier.entrance(): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val offset = with(LocalDensity.current) { 12.dp.toPx() }
    val translationY by animateFloatAsState(
        targetValue = if (entered) 0f else offset,
        animationSpec = ExpressiveMotion.defaultSpatial(),
        label = "entrance_translation",
    )
    return this.graphicsLayer { this.translationY = translationY }
}

/** Retains existing call sites while avoiding staggered delays in operational lists. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.staggeredEntrance(index: Int): Modifier = entrance()

/** Spatial motion for the changing count, non-overshooting effects for opacity. */
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
                animationSpec = ExpressiveMotion.fastSpatial(),
                initialOffsetY = { it / 2 },
            ) + fadeIn(ExpressiveMotion.fastEffects())).togetherWith(
                slideOutVertically(
                    animationSpec = ExpressiveMotion.fastSpatial(),
                    targetOffsetY = { -it / 2 },
                ) + fadeOut(ExpressiveMotion.fastEffects()),
            ).using(SizeTransform { _, _ -> ExpressiveMotion.fastSpatial() })
        },
        label = "animated_count_text",
        modifier = modifier,
    ) { value ->
        Text(text = value, style = style, color = color, fontWeight = fontWeight)
    }
}
