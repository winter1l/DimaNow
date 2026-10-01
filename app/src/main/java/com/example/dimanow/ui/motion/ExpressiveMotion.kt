package com.example.dimanow.ui.motion

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
 * The app's only motion tokens (D-094 item 12). Every custom animation reads one of these.
 *
 * They mirror the Material 3 Expressive *standard* motion scheme, whose `MotionScheme` is internal
 * in the pinned material3 1.4.0:
 * - **spatial** springs move or resize things; a 0.9 damping ratio settles with barely any overshoot,
 *   which suits an information app better than the bouncier expressive scheme.
 * - **effects** springs change colour and opacity; they never overshoot (damping ratio 1).
 * - **fast** is for small parts (press feedback, a changing number), **default** for most
 *   components, **slow** for large areas.
 *
 * Compose's animation clock applies the system animator duration scale to all of them, so
 * "remove animations" in the system settings stops them without extra code.
 */
object DimaMotion {
    private const val SPATIAL_DAMPING = 0.9f
    private const val EFFECTS_DAMPING = 1f

    fun <T> spatialFast(): SpringSpec<T> = spring(dampingRatio = SPATIAL_DAMPING, stiffness = 1400f)
    fun <T> spatialDefault(): SpringSpec<T> = spring(dampingRatio = SPATIAL_DAMPING, stiffness = 700f)
    fun <T> spatialSlow(): SpringSpec<T> = spring(dampingRatio = SPATIAL_DAMPING, stiffness = 300f)

    fun <T> effectsFast(): SpringSpec<T> = spring(dampingRatio = EFFECTS_DAMPING, stiffness = 3800f)
    fun <T> effectsDefault(): SpringSpec<T> = spring(dampingRatio = EFFECTS_DAMPING, stiffness = 1600f)
    fun <T> effectsSlow(): SpringSpec<T> = spring(dampingRatio = EFFECTS_DAMPING, stiffness = 800f)

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
 * Material shared-axis X transition between bottom tabs: a short directional slide with both pages
 * fading (D-042), on non-overshooting effects springs so it never competes with content motion.
 * [slideDistancePx] is the M3 shared-axis travel (30dp).
 */
fun dimaSharedAxisX(forward: Boolean, slideDistancePx: Int): ContentTransform {
    val distance = if (forward) slideDistancePx else -slideDistancePx
    return (
        slideInHorizontally(animationSpec = DimaMotion.effectsDefault<IntOffset>(), initialOffsetX = { distance }) +
            fadeIn(animationSpec = DimaMotion.effectsDefault())
        ).togetherWith(
        slideOutHorizontally(animationSpec = DimaMotion.effectsDefault<IntOffset>(), targetOffsetX = { -distance }) +
            fadeOut(animationSpec = DimaMotion.effectsFast()),
    ).apply { targetContentZIndex = 1f }
}

/** M3 shared-axis travel distance. */
val SharedAxisDistance = 30.dp

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
