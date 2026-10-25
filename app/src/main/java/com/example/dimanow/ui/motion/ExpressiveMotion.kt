package com.example.dimanow.ui.motion

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
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
 * Which M3 transition pattern a page change uses (D-121, m3.material.io/styles/motion/transitions/transition-patterns).
 * - [TOP_LEVEL]: between navigation-bar destinations, the fade-through of D-103.
 * - [FORWARD]/[BACKWARD]: one level deeper (전체 → 기숙사) or back up again; "Android uses a fade as screens slide",
 *   so the pages move a short distance sideways while they fade.
 */
enum class PageMotion { TOP_LEVEL, FORWARD, BACKWARD }

/**
 * Pure choice of [PageMotion] (D-121): into a sub-page is forward, from a sub-page back to the page Back returns to
 * is backward, anything else (a tab tap, a sub-page's link to another tab) is a top-level change.
 */
fun pageMotionFor(fromSubPage: Boolean, toSubPage: Boolean, toIsBackTarget: Boolean): PageMotion = when {
    !fromSubPage && toSubPage -> PageMotion.FORWARD
    fromSubPage && !toSubPage && toIsBackTarget -> PageMotion.BACKWARD
    else -> PageMotion.TOP_LEVEL
}

/** How far a forward/backward page slides; short, so the fade carries most of the change (D-121). */
val PageSlideDistance = 32.dp

/**
 * The [ContentTransform] for [motion] (D-121). Forward: the new page comes in from the end edge while the old one
 * leaves toward the start; backward mirrors it. Position uses the *slow* spatial spring (M3: full-screen motion is
 * slow), opacity the effects springs, as the fade-through does. [slidePx] is [PageSlideDistance] in pixels, negated
 * for right-to-left layouts. With [reducedMotion] every change is the plain fade-through: no sideways movement.
 */
fun dimaPageTransform(motion: PageMotion, slidePx: Int, reducedMotion: Boolean): ContentTransform {
    if (reducedMotion || motion == PageMotion.TOP_LEVEL) return dimaFadeThrough()
    val direction = if (motion == PageMotion.FORWARD) 1 else -1
    return (
        slideInHorizontally(DimaMotion.spatialSlow()) { direction * slidePx } +
            fadeIn(DimaMotion.effectsDefault())
        ).togetherWith(
        slideOutHorizontally(DimaMotion.spatialSlow()) { -direction * slidePx } +
            fadeOut(DimaMotion.effectsFast()),
    )
}

/**
 * M3 predictive back preview of the page being left (D-121): while the back gesture is held the page shrinks toward
 * [PREDICTIVE_BACK_MIN_SCALE], shifts [PredictiveBackShift] away from the edge being swiped and rounds its corners to
 * [PredictiveBackCorner], so the student sees that letting go leaves this page. Pure, for tests.
 */
fun predictiveBackScale(progress: Float): Float = 1f - (1f - PREDICTIVE_BACK_MIN_SCALE) * progress.coerceIn(0f, 1f)

/** Smallest scale of the predictive back preview (D-121). */
const val PREDICTIVE_BACK_MIN_SCALE = 0.9f

/** Sideways shift of the predictive back preview at full progress (D-121). */
val PredictiveBackShift = 8.dp

/** Corner radius of the predictive back preview at full progress: extraLargeIncreased (D-121). */
val PredictiveBackCorner = 32.dp

/**
 * Applies the predictive back preview (D-121). [progress] and [fromLeftEdge] are read in the draw phase only, so a
 * gesture never recomposes the page; at progress 0 the layer is the identity and nothing is clipped.
 */
fun Modifier.predictiveBackPreview(progress: () -> Float, fromLeftEdge: () -> Boolean): Modifier = graphicsLayer {
    val value = progress().coerceIn(0f, 1f)
    if (value == 0f) return@graphicsLayer
    val scale = predictiveBackScale(value)
    scaleX = scale
    scaleY = scale
    translationX = (if (fromLeftEdge()) 1f else -1f) * PredictiveBackShift.toPx() * value
    shape = androidx.compose.foundation.shape.RoundedCornerShape(PredictiveBackCorner * value)
    clip = true
}

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

/**
 * The same press scale for a component that already draws its own ripple and owns its clickable, such as an
 * `ElevatedCard(onClick)` (D-121): pass the [interactionSource] given to that component. Skipped under
 * [LocalReducedMotion]; the scale lives in a layer, so a press never recomposes the content.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    scaleDown: Float = DimaMotion.PRESSED_SCALE,
): Modifier = this then PressScaleElement(interactionSource, scaleDown)

private data class PressScaleElement(
    val interactionSource: InteractionSource,
    val scaleDown: Float,
) : ModifierNodeElement<PressScaleNode>() {
    override fun create(): PressScaleNode = PressScaleNode(interactionSource, scaleDown, withRipple = false)

    override fun update(node: PressScaleNode) {
        node.update(interactionSource, scaleDown)
    }
}

/** Ripple plus press scale; a data class so equal parameters reuse the same node. */
private data class PressScaleIndication(val scaleDown: Float) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressScaleNode(interactionSource, scaleDown, withRipple = true)
}

private class PressScaleNode(
    private var interactionSource: InteractionSource,
    private var scaleDown: Float,
    withRipple: Boolean,
) : DelegatingNode(), LayoutModifierNode, CompositionLocalConsumerModifierNode {
    private val scale = Animatable(1f)
    private var collectJob: Job? = null
    private val layerBlock: GraphicsLayerScope.() -> Unit = {
        val value = scale.value
        scaleX = value
        scaleY = value
    }

    init {
        if (withRipple) delegate(ripple().create(interactionSource))
    }

    fun update(source: InteractionSource, scaleDown: Float) {
        this.scaleDown = scaleDown
        if (source != interactionSource) {
            interactionSource = source
            if (isAttached) collect()
        }
    }

    override fun onAttach() {
        collect()
    }

    private fun collect() {
        collectJob?.cancel()
        collectJob = coroutineScope.launch {
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

/**
 * A block of content replaced by the next one of the same kind (D-121), such as the Home briefing moving on to the
 * next class: the new block rises in on the default spatial spring as the old one leaves upward and fades on the
 * fast effects spring, and the container's height follows on the spatial spring. Runs only when the content changes.
 */
fun dimaContentSwap(): ContentTransform = ContentTransform(
    targetContentEnter = slideInVertically(DimaMotion.spatialDefault()) { it / 3 } + fadeIn(DimaMotion.effectsDefault()),
    initialContentExit = slideOutVertically(DimaMotion.spatialDefault()) { -it / 3 } + fadeOut(DimaMotion.effectsFast()),
    sizeTransform = SizeTransform(clip = false) { _, _ -> DimaMotion.spatialDefault() },
)

/**
 * Content arriving in place of a placeholder (a loading line, an empty or error message), such as a list that has
 * just been read: the placeholder fades out on the fast effects spring, the content fades in on the default one and
 * the container's height follows on the default spatial spring, so the page grows into the new content instead of
 * popping. Nothing moves sideways or vertically; use [dimaContentSwap] for one block replaced by the next of its kind.
 * Under the system's "remove animations" Compose finishes it at once.
 */
fun dimaContentReveal(): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(DimaMotion.effectsDefault()),
    initialContentExit = fadeOut(DimaMotion.effectsFast()),
    sizeTransform = SizeTransform(clip = false) { _, _ -> DimaMotion.spatialDefault() },
)

/**
 * A fold or a card section opening (D-118): it grows downward on the default spatial spring while it fades in.
 * Pair with [dimaCollapseOut] and a chevron turning on the same spring.
 */
fun dimaExpandIn(): EnterTransition =
    expandVertically(DimaMotion.spatialDefault()) + fadeIn(DimaMotion.effectsDefault())

/** A fold or a card section closing (D-118): it shrinks on the default spatial spring and fades on the fast one. */
fun dimaCollapseOut(): ExitTransition =
    shrinkVertically(DimaMotion.spatialDefault()) + fadeOut(DimaMotion.effectsFast())

/** Spatial motion for the changing count, non-overshooting effects for opacity. */
@Composable
fun AnimatedCountText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    singleLine: Boolean = false,
    autoSize: androidx.compose.foundation.text.TextAutoSize? = null,
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
        if (singleLine) {
            Text(text = value, style = style, color = color, fontWeight = fontWeight, maxLines = 1, softWrap = false, autoSize = autoSize)
        } else {
            Text(text = value, style = style, color = color, fontWeight = fontWeight)
        }
    }
}
