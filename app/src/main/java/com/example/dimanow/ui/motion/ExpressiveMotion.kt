package com.example.dimanow.ui.motion

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The app's motion tokens (D-094 item 12, D-104). Every custom animation reads one of these.
 *
 * Since D-104 they are the Material 3 Expressive *expressive* motion scheme itself
 * ([MotionScheme.expressive], the scheme the theme also provides to components), not copied values:
 * - **spatial** springs move or resize things and may overshoot slightly (fast 0.6/800,
 *   default 0.8/380, slow 0.8/200 damping/stiffness).
 * - **effects** springs change colour and opacity and never overshoot (damping 1; 3800/1600/800).
 * - **fast** is for small parts (press feedback, a changing number), **default** for most
 *   components, **slow** for large areas.
 *
 * Compose's animation clock applies the system animator duration scale to all of them, so
 * "remove animations" in the system settings stops them without extra code.
 */
object DimaMotion {
    private val scheme = MotionScheme.expressive()

    fun <T> spatialFast(): FiniteAnimationSpec<T> = scheme.fastSpatialSpec()
    fun <T> spatialDefault(): FiniteAnimationSpec<T> = scheme.defaultSpatialSpec()
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = scheme.slowSpatialSpec()

    fun <T> effectsFast(): FiniteAnimationSpec<T> = scheme.fastEffectsSpec()
    fun <T> effectsDefault(): FiniteAnimationSpec<T> = scheme.defaultEffectsSpec()
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = scheme.slowEffectsSpec()

    /** Press feedback scale of [expressiveBounceClick]. */
    const val PRESSED_SCALE = 0.97f
}

/**
 * True when the system animator duration scale is 0 ("remove animations").
 *
 * Compose already skips animations then; this local additionally drops purely decorative
 * effects such as the press scale. The app shell provides it once through [ProvideReducedMotion].
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * False once the user has moved between tabs: the tab transition is then the only motion,
 * so a list does not also animate its own entrance (D-094 item 12).
 */
val LocalEntranceMotion = staticCompositionLocalOf { true }

/** Reads the system animator duration scale once and provides [LocalReducedMotion]. */
@Composable
fun ProvideReducedMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduced = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(LocalReducedMotion provides reduced, content = content)
}

/**
 * Material fade-through between navigation-bar destinations (D-103): the outgoing page fades out
 * quickly while the incoming page fades in more slowly, so the old page is gone before the new one
 * is fully there. Sideways motion is reserved for swipeable tabs and carousels. Since D-104 both
 * fades use the motion scheme's effects springs (fast out, default in) instead of fixed durations.
 */
fun dimaFadeThrough(): ContentTransform =
    fadeIn(animationSpec = DimaMotion.effectsDefault()).togetherWith(fadeOut(animationSpec = DimaMotion.effectsFast()))

/**
 * Native click behaviour (ripple, keyboard, `Role.Button` semantics) with a small spatial press scale.
 *
 * Without [onClick] it adds nothing, so a decorative wrapper never intercepts a child control or
 * creates an empty click action. The scale lives in a [Modifier.Node] indication driven by the
 * clickable's own press interactions and is skipped under [LocalReducedMotion].
 */
fun Modifier.expressiveBounceClick(
    scaleDown: Float = DimaMotion.PRESSED_SCALE,
    onClick: (() -> Unit)? = null,
): Modifier {
    if (onClick == null) return this
    return clickable(
        interactionSource = null,
        indication = PressScaleIndication(scaleDown),
        role = Role.Button,
        onClick = onClick,
    )
}

/** Ripple plus press scale; a data class so equal parameters reuse the same node. */
private data class PressScaleIndication(val scaleDown: Float) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressScaleNode(interactionSource, scaleDown)
}

private class PressScaleNode(
    private val interactionSource: InteractionSource,
    private val scaleDown: Float,
) : DelegatingNode(), LayoutModifierNode, CompositionLocalConsumerModifierNode {
    private val scale = Animatable(1f)
    private val layerBlock: GraphicsLayerScope.() -> Unit = {
        val value = scale.value
        scaleX = value
        scaleY = value
    }

    init {
        delegate(ripple().create(interactionSource))
    }

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                val target = when (interaction) {
                    // Read at press time, so the current system setting always applies.
                    is PressInteraction.Press -> if (currentValueOf(LocalReducedMotion)) return@collect else scaleDown
                    is PressInteraction.Release, is PressInteraction.Cancel -> 1f
                    else -> return@collect
                }
                launch { scale.animateTo(target, DimaMotion.spatialFast()) }
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = layerBlock)
        }
    }
}

/**
 * A 12dp rise for a list's first appearance only. After the first tab switch
 * ([LocalEntranceMotion] false) or on a restored screen, content simply appears.
 */
@Composable
fun Modifier.entrance(): Modifier {
    if (!LocalEntranceMotion.current) return this
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val offset = with(LocalDensity.current) { 12.dp.toPx() }
    val translationY by animateFloatAsState(
        targetValue = if (entered) 0f else offset,
        animationSpec = DimaMotion.spatialDefault(),
        label = "entrance_translation",
    )
    return this.graphicsLayer { this.translationY = translationY }
}

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
                animationSpec = DimaMotion.spatialFast(),
                initialOffsetY = { it / 2 },
            ) + fadeIn(DimaMotion.effectsFast())).togetherWith(
                slideOutVertically(
                    animationSpec = DimaMotion.spatialFast(),
                    targetOffsetY = { -it / 2 },
                ) + fadeOut(DimaMotion.effectsFast()),
            ).using(SizeTransform { _, _ -> DimaMotion.spatialFast() })
        },
        label = "animated_count_text",
        modifier = modifier,
    ) { value ->
        Text(text = value, style = style, color = color, fontWeight = fontWeight)
    }
}
