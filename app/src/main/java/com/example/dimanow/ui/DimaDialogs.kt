package com.example.dimanow.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.LocalReducedMotion
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaTheme

/**
 * What a pop-up is about (D-120): its icon circle takes the tone's container color, so a warning reads amber, a
 * destructive question red and a plain explanation in the app's secondary tone before a word is read. One meaning
 * per color: warning for "this may not work", error for "this removes something", secondary for the rest.
 */
internal enum class DialogTone { INFO, WARNING, DANGER }

@Composable
private fun DialogTone.containerColor(): Color = when (this) {
    DialogTone.INFO -> MaterialTheme.colorScheme.secondaryContainer
    DialogTone.WARNING -> DimaTheme.colors.warningContainer
    DialogTone.DANGER -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun DialogTone.contentColor(): Color = when (this) {
    DialogTone.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
    DialogTone.WARNING -> DimaTheme.colors.onWarningContainer
    DialogTone.DANGER -> MaterialTheme.colorScheme.onErrorContainer
}

/**
 * The tone's Material shape (D-137, adopting D-121's left-over "MaterialShapes for dialog icon circles"): a scalloped
 * dial for a plain explanation, a soft burst for a warning, a sharp burst for a destructive question, so the shape
 * tells a warning from a danger without colour as well.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun DialogTone.polygon(): RoundedPolygon = when (this) {
    DialogTone.INFO -> MaterialShapes.Cookie9Sided
    DialogTone.WARNING -> MaterialShapes.SoftBurst
    DialogTone.DANGER -> MaterialShapes.Burst
}

/** Degrees the dialog mark turns while it grows into its tone shape (D-137); it comes to rest upright. */
private const val DIALOG_ICON_TURN_DEGREES = 30f

/**
 * The pop-up's mark: a 56dp [DialogTone] shape in the tone's container with the icon on it. Decorative; the title
 * says it.
 *
 * D-137: when the pop-up opens, the shape grows once out of a circle while it turns [DIALOG_ICON_TURN_DEGREES] into
 * place on the default spatial spring (HomeHeroMark's technique: the morph and the turn are read in the draw phase).
 * It never loops, does not play again for a pop-up restored after rotation, and under [LocalReducedMotion] the tone
 * shape is simply drawn.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DimaDialogIcon(icon: ImageVector, tone: DialogTone) {
    val reduced = LocalReducedMotion.current
    var played by rememberSaveable { mutableStateOf(false) }
    val still = played || reduced
    val morph = remember(tone) { Morph(MaterialShapes.Circle, tone.polygon()) }
    val progress = remember { Animatable(if (still) 1f else 0f) }
    val turn = remember { Animatable(if (still) 0f else -DIALOG_ICON_TURN_DEGREES) }
    LaunchedEffect(Unit) {
        played = true
        if (progress.value >= 1f) return@LaunchedEffect
        launch { turn.animateTo(0f, DimaMotion.spatialDefault()) }
        progress.animateTo(1f, DimaMotion.spatialDefault())
    }
    val fill = tone.containerColor()
    Box(
        modifier = Modifier
            .size(56.dp)
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
        Icon(icon, contentDescription = null, tint = tone.contentColor(), modifier = Modifier.size(28.dp))
    }
}

/**
 * The app's pop-up (D-120): an M3 alert with the tone icon, a short title that says what happened or what is asked,
 * one or two plain sentences, and at most two text buttons whose labels say the result. A destructive confirm label
 * takes the error color.
 */
@Composable
internal fun DimaAlertDialog(
    onDismissRequest: () -> Unit,
    icon: ImageVector,
    tone: DialogTone,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    confirmEnabled: Boolean = true,
    confirmTag: String? = null,
    dismissTag: String? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { DimaDialogIcon(icon, tone) },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = text,
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = confirmTag?.let { Modifier.testTag(it) } ?: Modifier,
            ) {
                Text(confirmLabel, color = if (tone == DialogTone.DANGER && confirmEnabled) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = dismissLabel?.let { label ->
            {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss, modifier = dismissTag?.let { Modifier.testTag(it) } ?: Modifier) {
                    Text(label)
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * A small time line inside a pop-up (D-120): labelled clock times left to right with arrows between them; the one
 * at [highlight] is the moment that matters (now, past a deadline) and takes the tone's container. Read aloud as one
 * sentence.
 */
@Composable
internal fun DialogTimeline(points: List<Pair<String, String>>, highlight: Int, tone: DialogTone, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = points.joinToString(", ") { (label, time) -> "$label $time" } },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            points.forEachIndexed { index, (label, time) ->
                if (index > 0) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                val marked = index == highlight
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (marked) tone.containerColor() else Color.Transparent,
                    contentColor = if (marked) tone.contentColor() else MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                        Text(time, style = if (marked) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
