package com.example.dimanow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaShapes

/**
 * Shared "nothing here" message for lists and cards (D-094(14)): Shuttle, Meal, Home notices and
 * the Courses list all explain an empty result the same way.
 *
 * - With [icon] it is a centered illustration-style card (used when the whole view is empty).
 * - [contained] = false draws only the text and action, for use inside another card.
 * - [actionLabel] + [onAction] add one follow-up action.
 */
@Composable
internal fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionEnabled: Boolean = true,
    actionTag: String? = null,
    contained: Boolean = true,
) {
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(if (contained) 24.dp else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                }
                Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                supporting?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                StateAction(actionLabel, onAction, actionIcon, actionEnabled, actionTag, tonal = true)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(if (contained) 20.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                supporting?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StateAction(actionLabel, onAction, actionIcon, actionEnabled, actionTag, tonal = false)
            }
        }
    }
    if (contained) {
        ElevatedCard(
            modifier = modifier.fillMaxWidth(),
            shape = DimaShapes.Card,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) { content() }
    } else {
        Box(modifier.fillMaxWidth()) { content() }
    }
}

/**
 * Shared failure message (D-094(14)): what went wrong in plain words, what to do next, and a retry
 * action wherever the screen can refresh. Technical details stay in Settings diagnostics.
 */
@Composable
internal fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "다시 시도",
    contained: Boolean = true,
) {
    val contentColor = if (contained) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.error
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (contained) 16.dp else 0.dp)
                .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = contentColor)
            supporting?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (contained) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                ) { Text(retryLabel) }
            }
        }
    }
    if (contained) {
        ElevatedCard(
            modifier = modifier.fillMaxWidth(),
            shape = DimaShapes.Card,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) { content() }
    } else {
        Box(modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun StateAction(
    label: String?,
    onAction: (() -> Unit)?,
    icon: ImageVector?,
    enabled: Boolean,
    tag: String?,
    tonal: Boolean,
) {
    if (label == null || onAction == null) return
    val tagged = if (tag != null) Modifier.testTag(tag) else Modifier
    if (tonal) {
        FilledTonalButton(onClick = onAction, enabled = enabled, modifier = tagged) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            }
            Text(label)
        }
    } else {
        TextButton(onClick = onAction, enabled = enabled, modifier = tagged) { Text(label) }
    }
}
