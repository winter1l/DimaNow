package com.example.dimanow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.LocalEntranceMotion
import com.example.dimanow.ui.motion.LocalReducedMotion
import kotlinx.coroutines.launch

/** What the Home briefing is about right now (D-121); the briefing's mark takes a different shape for each. */
internal enum class HeroMood { UPCOMING, ONGOING, FREE }

/** [HeroMood] for the briefing: no class left today is [HeroMood.FREE], otherwise the next class is ahead or on. */
internal fun heroMood(hasNextCourse: Boolean, ongoing: Boolean): HeroMood = when {
    !hasNextCourse -> HeroMood.FREE
    ongoing -> HeroMood.ONGOING
    else -> HeroMood.UPCOMING
}

/** Degrees the mark turns at each change of mood, so the change reads as one movement (D-121). */
internal const val HERO_MARK_TURN_DEGREES = 45f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun HeroMood.polygon(): RoundedPolygon = when (this) {
    // A scalloped dial while the class is still ahead, a sun while it runs, a calm soft square once the day is free.
    HeroMood.UPCOMING -> MaterialShapes.Cookie9Sided
    HeroMood.ONGOING -> MaterialShapes.Sunny
    HeroMood.FREE -> MaterialShapes.Cookie4Sided
}

private fun HeroMood.icon(): ImageVector = when (this) {
    HeroMood.UPCOMING -> Icons.Default.Schedule
    HeroMood.ONGOING -> Icons.Default.AutoStories
    HeroMood.FREE -> Icons.Default.EventAvailable
}

/**
 * The Home briefing's mark (D-121), the first of the app's two hero moments. M3 Expressive asks shapes to morph to
 * show "changes in the environment, like … time of day" and keeps abstract Material shapes for decorative UI, so the
 * one graphic on the briefing card is a [MaterialShapes] shape that morphs between moods: a scalloped dial before the
 * class, a sun while it runs, a soft square once the day is free. Each change turns it a little on the expressive
 * spatial spring; its fill follows the badge beside the course (primary while the class runs, secondary before).
 *
 * - At app launch (the first page, [LocalEntranceMotion]) it grows out of a circle once; after a tab change, on a
 *   restored screen or once the intro has played for this card it simply shows its shape.
 * - It never loops: it moves only when the mood changes (a few times a day) and is still otherwise.
 * - Under [LocalReducedMotion] it snaps to the new shape without turning.
 * - It is decorative: the text beside it says the same thing, so it has no semantics.
 * - Progress and rotation are read in the draw phase, so the morph never recomposes the card.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HeroMoodMark(mood: HeroMood, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val reduced = LocalReducedMotion.current
    var introPlayed by rememberSaveable { mutableStateOf(false) }
    val playIntro = LocalEntranceMotion.current && !reduced && !introPlayed
    // The mood morphing from (null: the launch circle) and to; a Morph needs both ends.
    var fromMood by remember { mutableStateOf(if (playIntro) null else mood) }
    var toMood by remember { mutableStateOf(mood) }
    val morph = remember(fromMood, toMood) { Morph(fromMood?.polygon() ?: MaterialShapes.Circle, toMood.polygon()) }
    val progress = remember { Animatable(if (playIntro) 0f else 1f) }
    val turn = remember { Animatable(0f) }
    LaunchedEffect(mood) {
        introPlayed = true
        if (toMood != mood) {
            fromMood = toMood
            toMood = mood
            progress.snapTo(0f)
        }
        if (progress.value >= 1f) return@LaunchedEffect
        if (reduced) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        launch { turn.animateTo(turn.targetValue + HERO_MARK_TURN_DEGREES, DimaMotion.spatialDefault()) }
        progress.animateTo(1f, DimaMotion.spatialDefault())
    }
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        if (mood == HeroMood.UPCOMING) colors.secondary else colors.primary,
        DimaMotion.effectsDefault(),
        label = "hero_mark_fill",
    )
    val ink by animateColorAsState(
        if (mood == HeroMood.UPCOMING) colors.onSecondary else colors.onPrimary,
        DimaMotion.effectsDefault(),
        label = "hero_mark_ink",
    )
    Box(
        modifier = modifier
            .size(size)
            .testTag("home_hero_mark")
            .clearAndSetSemantics {}
            .drawWithCache {
                val path = Path()
                val unitToSize = Matrix().apply { scale(this@drawWithCache.size.width, this@drawWithCache.size.height) }
                onDrawBehind {
                    path.rewind()
                    morph.toPath(progress.value, path)
                    path.transform(unitToSize)
                    rotate(turn.value) { drawPath(path, fill) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(mood.icon(), contentDescription = null, tint = ink, modifier = Modifier.size(size * 0.45f))
    }
}
