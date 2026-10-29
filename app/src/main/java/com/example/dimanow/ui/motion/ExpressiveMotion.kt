package com.example.dimanow.ui.motion

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.IntSize
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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

    /**
     * Position or size changes that must not overshoot (D-137): the departure hand-over of D-124, the mode-switch
     * shift of D-129, the map camera, list placement, pinned-header folds and the navigation bar. Where several things
     * move together on one change, the expressive spatial spring's overshoot made them jump (D-124); the spatial
     * springs stay for single parts that may bounce.
     */
    fun <T> settle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

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

/**
 * Provides [LocalReducedMotion] from the system animator duration scale and keeps it current: turning "remove
 * animations" on or off does not recreate the activity, so the setting is observed while the app runs (D-137).
 */
@Composable
fun ProvideReducedMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var reduced by remember(resolver) { mutableStateOf(animationsRemoved(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = animationsRemoved(resolver)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        // A change made while the observer was not registered (the app was in the background) is read again here.
        reduced = animationsRemoved(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(LocalReducedMotion provides reduced, content = content)
}

private fun animationsRemoved(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Material fade-through between navigation-bar destinations (D-103): the outgoing page fades out
 * quickly while the incoming page fades in more slowly, so the old page is gone before the new one
 * is fully there. Since D-104 both fades use the motion scheme's effects springs (fast out, default in)
 * instead of fixed durations. D-138: only fades, as M3's top-level pattern says ("a quick fade"); D-137's growth from
 * 92% on the bouncy spring was too much for the most frequent change in the app.
 */
fun dimaFadeThrough(): ContentTransform =
    fadeIn(animationSpec = DimaMotion.effectsDefault()).togetherWith(fadeOut(animationSpec = DimaMotion.effectsFast()))

/**
 * M3's lateral pattern for a screen's own tabs (D-138; 셔틀's 교내 셔틀/4402, 식단's 본관/기숙사, 수업's 할 일/과목별): the
 * two pages slide together by the full width, the new one in from the side of its tab, with no fade ("elements are
 * grouped and slide in unison"), on the non-bouncy spring. [forward] is a move to a tab further toward the end edge;
 * pass it already mirrored for right-to-left layouts. Under [reducedMotion] the pages only fade.
 */
fun dimaLateral(forward: Boolean, reducedMotion: Boolean): ContentTransform {
    if (reducedMotion) {
        return ContentTransform(fadeIn(DimaMotion.effectsDefault()), fadeOut(DimaMotion.effectsFast()), sizeTransform = null)
    }
    val direction = if (forward) 1 else -1
    return ContentTransform(
        targetContentEnter = slideInHorizontally(LateralSpring) { direction * it },
        initialContentExit = slideOutHorizontally(LateralSpring) { -direction * it },
        sizeTransform = null,
    )
}

/** The lateral slide's spring: [DimaMotion.settle]'s values, ending within a pixel. */
private val LateralSpring = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/**
 * Which M3 transition pattern a page change uses (D-121, m3.material.io/styles/motion/transitions/transition-patterns).
 * - [TOP_LEVEL]: between navigation-bar destinations, the fade-through of D-103.
 * - [FORWARD]/[BACKWARD]: one level deeper (전체 → 기숙사) or back up again; "Android uses a fade as screens slide",
 *   so the pages move a short distance sideways while they fade. M3 calls a full-width slide here excessive for so
 *   frequent a change; that slide is the lateral pattern of a screen's own tabs ([dimaLateral]).
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
 * leaves toward the start; backward mirrors it. Position uses the non-bouncy [DimaMotion.settle] spring (D-138: M3
 * says common transitions should not use bouncy springs; before, the slow spatial spring overshot), opacity the
 * effects springs, as the fade-through does. [slidePx] is [PageSlideDistance] in pixels, negated for right-to-left
 * layouts. With [reducedMotion] every change is the plain fade-through: no sideways movement.
 */
fun dimaPageTransform(motion: PageMotion, slidePx: Int, reducedMotion: Boolean): ContentTransform {
    if (reducedMotion || motion == PageMotion.TOP_LEVEL) return dimaFadeThrough()
    val direction = if (motion == PageMotion.FORWARD) 1 else -1
    return (
        slideInHorizontally(DimaMotion.settle()) { direction * slidePx } +
            fadeIn(DimaMotion.effectsDefault())
        ).togetherWith(
        slideOutHorizontally(DimaMotion.settle()) { -direction * slidePx } +
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
 * The state of one predictive back gesture (D-121, D-137), shared by the page that is being left and the backdrop
 * behind it. Only the page whose key was showing when the gesture started takes the preview, so after the gesture
 * commits the leaving page keeps its shrunk, rounded look while the page transition moves it out, and the incoming
 * page is never scaled by it (D-137: before, the whole transition sprang back to full size while it ran).
 */
@Stable
class PredictiveBackPreview internal constructor(internal val scope: CoroutineScope) {
    internal val progressValue = Animatable(0f)

    /** Gesture progress, 0 when no preview is shown. Read it in the draw phase only. */
    val progress: Float get() = progressValue.value

    /** True when the gesture started at the left edge. */
    var fromLeftEdge by mutableStateOf(true)
        internal set

    /** True while the backdrop is needed: during the gesture and while a cancelled preview springs back. */
    var active by mutableStateOf(false)
        internal set

    /** The key of the page taking the preview, or null. */
    internal var previewKey: Any? by mutableStateOf(null)
}

/**
 * Registers a [PredictiveBackHandler] (D-121) whose preview is drawn by [PredictiveBackPage]. On commit it calls
 * [onBack] and leaves the preview on the leaving page until that page leaves composition; on cancel the preview
 * springs back on the fast spatial spring. Under [LocalReducedMotion] there is no preview: committing only calls
 * [onBack]. [currentKey] names the page showing now, as passed to [PredictiveBackPage].
 */
@Composable
fun rememberPredictiveBackPreview(enabled: Boolean, currentKey: Any?, onBack: () -> Unit): PredictiveBackPreview {
    val scope = rememberCoroutineScope()
    val preview = remember(scope) { PredictiveBackPreview(scope) }
    val reduced by rememberUpdatedState(LocalReducedMotion.current)
    val key by rememberUpdatedState(currentKey)
    val back by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                if (reduced) return@collect
                if (preview.previewKey == null || !preview.active) {
                    preview.previewKey = key
                    preview.active = true
                }
                preview.fromLeftEdge = event.swipeEdge == BackEventCompat.EDGE_LEFT
                preview.progressValue.snapTo(event.progress)
            }
            preview.active = false
            back()
            // A Back that did not leave the page (nothing to go back to) springs the preview back instead of leaving
            // the page shrunk; two frames let the page change, if any, reach composition first.
            preview.scope.launch {
                withFrameNanos { }
                withFrameNanos { }
                if (preview.previewKey != null && preview.previewKey == key) {
                    preview.progressValue.animateTo(0f, DimaMotion.spatialFast())
                    preview.previewKey = null
                }
            }
        } catch (cancelled: CancellationException) {
            preview.scope.launch {
                preview.progressValue.animateTo(0f, DimaMotion.spatialFast())
                preview.active = false
                preview.previewKey = null
            }
            throw cancelled
        }
    }
    // The page that was left can be opened again while it is still leaving: the transition then keeps the same page,
    // which is never disposed, so its committed preview springs back here instead of staying shrunk. A new gesture's
    // snapTo cancels this spring before the key is cleared.
    LaunchedEffect(preview, currentKey) {
        if (!preview.active && preview.previewKey != null && preview.previewKey == currentKey) {
            preview.progressValue.animateTo(0f, DimaMotion.spatialFast())
            preview.previewKey = null
        }
    }
    return preview
}

/**
 * One page that may be left with a predictive back gesture (D-137): applies [predictiveBackPreview] while [key] is
 * the page the gesture started on, and clears the preview once that page has left composition.
 */
@Composable
fun PredictiveBackPage(
    preview: PredictiveBackPreview,
    key: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    DisposableEffect(preview, key) {
        onDispose {
            if (preview.previewKey == key && !preview.active) {
                preview.previewKey = null
                preview.scope.launch { preview.progressValue.snapTo(0f) }
            }
        }
    }
    Box(
        modifier = modifier.predictiveBackPreview(
            progress = { if (preview.previewKey == key) preview.progress else 0f },
            fromLeftEdge = { preview.fromLeftEdge },
        ),
        propagateMinConstraints = true,
    ) { content() }
}

/**
 * Draws [color] behind the pages while a predictive back preview is shown (D-121): the shrinking page needs a
 * backdrop one tone apart, or its edges vanish into the same page colour. It stays while a committed preview is still
 * drawn on the leaving page. Read in the draw phase.
 */
fun Modifier.predictiveBackBackdrop(preview: PredictiveBackPreview, color: Color): Modifier = drawBehind {
    if (preview.previewKey != null && preview.progress > 0f) drawRect(color)
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
 * Pair with [dimaCollapseOut] and a chevron turning on the same spring. A control folding in a pinned header while
 * the list beneath moves passes [DimaMotion.settle] as [size] (D-137), so the header never bobs.
 */
fun dimaExpandIn(size: FiniteAnimationSpec<IntSize> = DimaMotion.spatialDefault()): EnterTransition =
    expandVertically(size) + fadeIn(DimaMotion.effectsDefault())

/** A fold or a card section closing (D-118): it shrinks on the default spatial spring and fades on the fast one. */
fun dimaCollapseOut(size: FiniteAnimationSpec<IntSize> = DimaMotion.spatialDefault()): ExitTransition =
    shrinkVertically(size) + fadeOut(DimaMotion.effectsFast())

/**
 * A small element appearing (D-137): a FAB, a badge, a check mark, a back-to-next arrow or a map control grows from
 * its own centre on the fast spatial spring while it fades in. With [reduced] ([LocalReducedMotion]) it only fades.
 */
fun dimaPopIn(reduced: Boolean = false, initialScale: Float = POP_SCALE): EnterTransition {
    val fade = fadeIn(DimaMotion.effectsFast())
    return if (reduced) fade else scaleIn(DimaMotion.spatialFast(), initialScale) + fade
}

/** The small element of [dimaPopIn] leaving: it shrinks and fades on the fast effects spring, never overshooting. */
fun dimaPopOut(reduced: Boolean = false, targetScale: Float = POP_SCALE): ExitTransition {
    val fade = fadeOut(DimaMotion.effectsFast())
    return if (reduced) fade else scaleOut(DimaMotion.effectsFast(), targetScale) + fade
}

/** How small a popping element starts and ends (D-137). */
const val POP_SCALE = 0.6f

/**
 * One icon or small control replaced by another in the same place (D-137), such as download → progress → done or a
 * button → its check mark: the new one pops in ([dimaPopIn]) while the old one fades out, and the slot's size
 * follows on the fast spatial spring. Use as an `AnimatedContent` transitionSpec.
 */
fun dimaIconSwap(reduced: Boolean = false): ContentTransform = ContentTransform(
    targetContentEnter = dimaPopIn(reduced),
    initialContentExit = fadeOut(DimaMotion.effectsFast()),
    sizeTransform = SizeTransform(clip = false) { _, _ -> DimaMotion.spatialFast() },
)

/**
 * False where a whole list is being replaced at once (D-137): a mode switch or a content change that
 * [com.example.dimanow.ui.ScreenScaffold] fades as one, so its items do not also fade on their own (D-094(12)).
 */
val LocalListChangeMotion = compositionLocalOf { true }

/**
 * Motion for a lazy-list item added, removed or moved by a data change (D-137, adopting D-121's left-over
 * "animateItem for list changes"): it fades in on the default effects spring, fades out on the fast one and, with
 * [placement], glides to its new place on [DimaMotion.settle]. `animateItem` never runs for items composed by
 * scrolling or by a list's first layout, so this is not a per-item entrance (D-058).
 *
 * Use `placement = false` for items that animate their own height (the list would chase it), and `fadeOut = false`
 * for items a whole-list switch replaces: a removed item keeps the specs of its last composition, so it would still
 * fade while the new list fades in. Under [LocalListChangeMotion] false fade-in and placement are off.
 */
// animateItem exists only on a lazy item's scope, so this cannot be an extension on Modifier.
@Suppress("ModifierFactoryExtensionFunction")
@Composable
fun LazyItemScope.dimaItemChange(fadeIn: Boolean = true, fadeOut: Boolean = true, placement: Boolean = false): Modifier {
    val changes = LocalListChangeMotion.current
    return Modifier.animateItem(
        fadeInSpec = if (fadeIn && changes) DimaMotion.effectsDefault() else null,
        placementSpec = if (placement && changes) DimaMotion.settle() else null,
        fadeOutSpec = if (fadeOut) DimaMotion.effectsFast() else null,
    )
}

/**
 * Waits under this long show no loading indicator (M3 loading indicator guidance and the UX guide: nothing below
 * 200 ms, the loading indicator from 200 ms to 5 s), D-137.
 */
const val LOADING_GRACE_MILLIS = 200L

/**
 * True once [active] has stayed true for [delayMillis], false as soon as it turns false (D-137): a busy label or a
 * loading placeholder for a wait that ends within the grace period is never shown.
 */
@Composable
fun rememberShownAfter(active: Boolean, delayMillis: Long = LOADING_GRACE_MILLIS): Boolean {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active, delayMillis) {
        if (active) {
            delay(delayMillis)
            shown = true
        } else {
            shown = false
        }
    }
    return shown && active
}

/**
 * A loading placeholder that keeps its place and semantics from the first frame but stays transparent for
 * [delayMillis], then fades in on the default effects spring (D-137), so a read that finishes quickly shows nothing.
 * The opacity is read in the draw phase.
 */
@Composable
fun Modifier.loadingGrace(delayMillis: Long = LOADING_GRACE_MILLIS): Modifier {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(delayMillis) {
        delay(delayMillis)
        alpha.animateTo(1f, DimaMotion.effectsDefault())
    }
    return this.graphicsLayer { this.alpha = alpha.value }
}

/**
 * The rolling change of [AnimatedCountText] (D-027) as a transform of its own: the new text rises in from half its
 * height while the old one leaves upward, on the fast spatial spring, and the slot's size follows on [sizeSpec].
 */
fun dimaTextRoll(sizeSpec: FiniteAnimationSpec<IntSize> = DimaMotion.spatialFast()): ContentTransform = ContentTransform(
    targetContentEnter = slideInVertically(
        animationSpec = DimaMotion.spatialFast(),
        initialOffsetY = { it / 2 },
    ) + fadeIn(DimaMotion.effectsFast()),
    initialContentExit = slideOutVertically(
        animationSpec = DimaMotion.spatialFast(),
        targetOffsetY = { -it / 2 },
    ) + fadeOut(DimaMotion.effectsFast()),
    sizeTransform = SizeTransform { _, _ -> sizeSpec },
)

/**
 * Spatial motion for the changing count, non-overshooting effects for opacity. A slot whose width already follows
 * another spring (the strip cells of D-124) passes that spring as [sizeSpec], so one width has one spring (D-137).
 */
@Composable
fun AnimatedCountText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    singleLine: Boolean = false,
    autoSize: androidx.compose.foundation.text.TextAutoSize? = null,
    sizeSpec: FiniteAnimationSpec<IntSize> = DimaMotion.spatialFast(),
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = { dimaTextRoll(sizeSpec) },
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
