package com.example.dimanow.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.example.dimanow.data.OnboardingStage
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.LocalReducedMotion
import kotlinx.coroutines.launch

/** The Material shapes the first-run graphic is built from; kept as names so the choice per step is testable. */
internal enum class OnboardingHeroShape { SOFT_BURST, SUNNY, PILL, COOKIE_9, CIRCLE, COOKIE_12, COOKIE_4 }

/** The icon a layer of the first-run graphic carries. */
internal enum class OnboardingHeroGlyph { FORWARD, BUS, HOME, NOTIFICATIONS, LOCATION }

/**
 * One step's graphic: a large [main] shape with [mainGlyph] in the middle, a small [accent] shape overlapping its
 * top end (with [accentGlyph] when the step has a second subject) and a tiny [dot] near its bottom start.
 */
internal data class OnboardingHeroArt(
    val main: OnboardingHeroShape,
    val mainGlyph: OnboardingHeroGlyph,
    val accent: OnboardingHeroShape,
    val accentGlyph: OnboardingHeroGlyph?,
    val dot: OnboardingHeroShape,
)

/**
 * The graphic above each first-run step's title:
 * - 환영: a soft burst with a forward arrow, the app mark's arrow and spark, beside a small sun.
 * - 귀가 방향: a scalloped dial with the bus, and home in the small pill: from class to home.
 * - 알림과 위치: a twelve-sided badge with a ringing bell, and the location pin in the small soft square.
 */
internal fun onboardingHeroArt(stage: OnboardingStage): OnboardingHeroArt = when (stage) {
    OnboardingStage.WELCOME -> OnboardingHeroArt(
        main = OnboardingHeroShape.SOFT_BURST,
        mainGlyph = OnboardingHeroGlyph.FORWARD,
        accent = OnboardingHeroShape.SUNNY,
        accentGlyph = null,
        dot = OnboardingHeroShape.PILL,
    )
    OnboardingStage.HOME_BASE -> OnboardingHeroArt(
        main = OnboardingHeroShape.COOKIE_9,
        mainGlyph = OnboardingHeroGlyph.BUS,
        accent = OnboardingHeroShape.PILL,
        accentGlyph = OnboardingHeroGlyph.HOME,
        dot = OnboardingHeroShape.CIRCLE,
    )
    OnboardingStage.SETUP -> OnboardingHeroArt(
        main = OnboardingHeroShape.COOKIE_12,
        mainGlyph = OnboardingHeroGlyph.NOTIFICATIONS,
        accent = OnboardingHeroShape.COOKIE_4,
        accentGlyph = OnboardingHeroGlyph.LOCATION,
        dot = OnboardingHeroShape.SUNNY,
    )
}

/** Degrees the main shape turns per step: clockwise going on, back the same way when stepping back. */
internal const val ONBOARDING_HERO_TURN_DEGREES = 45f

/** Signed turn for a move from [from] to [to]: one [ONBOARDING_HERO_TURN_DEGREES] per step, negative going back. */
internal fun onboardingHeroTurn(from: OnboardingStage, to: OnboardingStage): Float =
    (to.ordinal - from.ordinal) * ONBOARDING_HERO_TURN_DEGREES

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun OnboardingHeroShape.polygon(): RoundedPolygon = when (this) {
    OnboardingHeroShape.SOFT_BURST -> MaterialShapes.SoftBurst
    OnboardingHeroShape.SUNNY -> MaterialShapes.Sunny
    OnboardingHeroShape.PILL -> MaterialShapes.Pill
    OnboardingHeroShape.COOKIE_9 -> MaterialShapes.Cookie9Sided
    OnboardingHeroShape.CIRCLE -> MaterialShapes.Circle
    OnboardingHeroShape.COOKIE_12 -> MaterialShapes.Cookie12Sided
    OnboardingHeroShape.COOKIE_4 -> MaterialShapes.Cookie4Sided
}

private fun OnboardingHeroGlyph.icon(): ImageVector = when (this) {
    OnboardingHeroGlyph.FORWARD -> Icons.AutoMirrored.Filled.ArrowForward
    OnboardingHeroGlyph.BUS -> Icons.Default.DirectionsBus
    OnboardingHeroGlyph.HOME -> Icons.Default.Home
    OnboardingHeroGlyph.NOTIFICATIONS -> Icons.Default.NotificationsActive
    OnboardingHeroGlyph.LOCATION -> Icons.Default.LocationOn
}

/**
 * Motion state of the first-run graphic. It lives outside the per-step `key` of [OnboardingScreen], so moving
 * between steps morphs the same shapes instead of drawing a new graphic.
 */
@Stable
internal class OnboardingHeroState(initial: OnboardingStage, playIntro: Boolean) {
    var from by mutableStateOf(initial)
    var to by mutableStateOf(initial)
    val progress = Animatable(1f)
    val turn = Animatable(0f)

    /** 0 → 1 once, when the first-run screen first appears. */
    val intro = Animatable(if (playIntro) 0f else 1f)
}

/**
 * Remembers [OnboardingHeroState] and drives it:
 * - Once per first run (not again after rotation or process restore) the graphic grows in from 60% and turns into
 *   place on the slow spatial spring.
 * - On a step change the three shapes morph to the next step's on the default spatial spring while the main shape
 *   turns [ONBOARDING_HERO_TURN_DEGREES] and the accent the other way.
 * - Nothing loops. Under [LocalReducedMotion] there is no growth, no turn and no morph: each step shows its shapes.
 */
@Composable
internal fun rememberOnboardingHeroState(stage: OnboardingStage): OnboardingHeroState {
    val reduced = LocalReducedMotion.current
    var introPlayed by rememberSaveable { mutableStateOf(false) }
    val state = remember { OnboardingHeroState(stage, playIntro = !reduced && !introPlayed) }
    LaunchedEffect(Unit) {
        introPlayed = true
        if (state.intro.value < 1f) state.intro.animateTo(1f, DimaMotion.spatialSlow())
    }
    LaunchedEffect(stage) {
        if (state.to == stage) return@LaunchedEffect
        val turnBy = onboardingHeroTurn(state.to, stage)
        state.from = state.to
        state.to = stage
        if (reduced) {
            state.progress.snapTo(1f)
            return@LaunchedEffect
        }
        state.progress.snapTo(0f)
        launch { state.turn.animateTo(state.turn.targetValue + turnBy, DimaMotion.spatialDefault()) }
        state.progress.animateTo(1f, DimaMotion.spatialDefault())
    }
    return state
}

/** Width of the first-run graphic; it stays this size at any font scale, and the step scrolls below it. */
private val HeroWidth = 152.dp
private val HeroHeight = 136.dp
private const val INTRO_START_SCALE = 0.6f
private const val INTRO_START_TURN = -60f

/**
 * The first-run graphic above each step's title: two or three Material shapes in the primary, tertiary and secondary
 * container tones with the step's icon in the middle (see [onboardingHeroArt]). It is decorative, since the title
 * below says the same, so it has no semantics. Morph, turn and growth are read in the draw and layer phases, so the
 * motion never recomposes the step.
 */
@Composable
internal fun OnboardingHero(state: OnboardingHeroState, modifier: Modifier = Modifier) {
    val fromArt = onboardingHeroArt(state.from)
    val toArt = onboardingHeroArt(state.to)
    val colors = MaterialTheme.colorScheme
    val progress = { state.progress.value }
    val introTurn = { (1f - state.intro.value) * INTRO_START_TURN }
    Box(
        modifier = modifier
            .size(HeroWidth, HeroHeight)
            .testTag("onboarding_hero")
            .clearAndSetSemantics {}
            .graphicsLayer {
                val intro = state.intro.value
                val scale = INTRO_START_SCALE + (1f - INTRO_START_SCALE) * intro
                scaleX = scale
                scaleY = scale
                alpha = intro.coerceIn(0f, 1f)
            },
    ) {
        HeroLayer(
            from = fromArt.main,
            to = toArt.main,
            progress = progress,
            rotation = { state.turn.value + introTurn() },
            color = colors.primaryContainer,
            size = 112.dp,
            glyph = toArt.mainGlyph,
            glyphTint = colors.onPrimaryContainer,
            glyphSize = 48.dp,
            modifier = Modifier.align(Alignment.Center),
        )
        HeroLayer(
            from = fromArt.dot,
            to = toArt.dot,
            progress = progress,
            rotation = { 0f },
            color = colors.secondaryContainer,
            size = 22.dp,
            glyph = null,
            glyphTint = colors.onSecondaryContainer,
            glyphSize = 0.dp,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = 10.dp, y = (-6).dp),
        )
        HeroLayer(
            from = fromArt.accent,
            to = toArt.accent,
            progress = progress,
            rotation = { -state.turn.value - introTurn() },
            color = colors.tertiaryContainer,
            size = 52.dp,
            glyph = toArt.accentGlyph,
            glyphTint = colors.onTertiaryContainer,
            glyphSize = 24.dp,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroLayer(
    from: OnboardingHeroShape,
    to: OnboardingHeroShape,
    progress: () -> Float,
    rotation: () -> Float,
    color: Color,
    size: Dp,
    glyph: OnboardingHeroGlyph?,
    glyphTint: Color,
    glyphSize: Dp,
    modifier: Modifier = Modifier,
) {
    val morph = remember(from, to) { Morph(from.polygon(), to.polygon()) }
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val path = Path()
                val unitToSize = Matrix().apply { scale(this@drawWithCache.size.width, this@drawWithCache.size.height) }
                onDrawBehind {
                    path.rewind()
                    morph.toPath(progress(), path)
                    path.transform(unitToSize)
                    rotate(rotation()) { drawPath(path, color) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The icon stays upright while its shape turns; a new step's icon fades in over the old one.
        Crossfade(targetState = glyph, animationSpec = DimaMotion.effectsDefault(), label = "onboarding_hero_glyph") { shown ->
            if (shown != null) {
                Icon(shown.icon(), contentDescription = null, tint = glyphTint, modifier = Modifier.size(glyphSize))
            }
        }
    }
}
