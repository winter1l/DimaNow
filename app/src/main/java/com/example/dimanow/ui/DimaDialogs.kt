package com.example.dimanow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/** The pop-up's mark: a 56dp circle in the tone's container with the icon on it. Decorative; the title says it. */
@Composable
internal fun DimaDialogIcon(icon: ImageVector, tone: DialogTone) {
    Surface(shape = CircleShape, color = tone.containerColor(), contentColor = tone.contentColor(), modifier = Modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp)) }
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
