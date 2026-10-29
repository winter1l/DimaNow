package com.example.dimanow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dimanow.ui.motion.dimaCollapseOut
import com.example.dimanow.ui.motion.dimaExpandIn

/**
 * A short column of keyed rows that fold in and out (D-137), for rows inside one lazy item such as 시간표's 지정한
 * 휴강일: a removed key stays composed with its last item until its fold ([dimaCollapseOut]) has settled, and a key
 * that comes back (되돌리기) grows in again where it was ([dimaExpandIn]). The rows present at the first composition
 * simply show, so the column never adds an entrance of its own (D-058).
 *
 * Each row carries the [gap] after it inside its fold, so a leaving row takes its gap with it and the rows below move
 * up continuously; the last row's gap is not part of the column's height ([withoutTrailingGap]).
 */
@Composable
internal fun <T> AnimatedKeyedColumn(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    content: @Composable (T) -> Unit,
) {
    val rows = remember { KeyedRows<T>() }
    val shown = rows.update(items, key)
    Column(modifier.withoutTrailingGap(gap)) {
        shown.forEach { row ->
            key(row.key) {
                AnimatedVisibility(visibleState = row.visible, enter = dimaExpandIn(), exit = dimaCollapseOut()) {
                    Column(Modifier.padding(bottom = gap)) { content(row.item) }
                }
            }
        }
    }
}

/** The rows of one [AnimatedKeyedColumn], kept across compositions; plain fields, rebuilt by [update] in composition. */
private class KeyedRows<T> {
    class Row<T>(val key: Any, var item: T, val visible: MutableTransitionState<Boolean>)

    private var rows: List<Row<T>> = emptyList()
    private var started = false

    fun update(items: List<T>, key: (T) -> Any): List<Row<T>> {
        val previous = rows.associateBy { it.key }
        val current = items.map { item ->
            val rowKey = key(item)
            // A key new after the first composition starts folded and grows in; the first rows simply show.
            val row = previous[rowKey] ?: Row(rowKey, item, MutableTransitionState(!started))
            row.item = item
            row.visible.targetState = true
            row
        }
        val currentKeys = current.mapTo(HashSet()) { it.key }
        // A row that left folds away with its last item; once its fold has settled it is dropped. Reading the
        // transition state here recomposes the column when a fold ends.
        val leaving = rows.filter { it.key !in currentKeys }
            .onEach { it.visible.targetState = false }
            .filterNot { it.visible.isIdle && !it.visible.currentState }
        val byKey = (current + leaving).associateBy { it.key }
        rows = keyedRowOrder(rows.map { it.key }, current.map { it.key }).mapNotNull { byKey[it] }
        started = true
        return rows
    }
}

/**
 * The order an [AnimatedKeyedColumn] shows its rows in (D-137): the [current] keys in their order, and every key of
 * [shown] that is no longer current (a row still folding away) right after the current key it followed, or first when
 * none came before it, so a leaving row folds away where it was. Pure, for tests.
 */
internal fun <K : Any> keyedRowOrder(shown: List<K>, current: List<K>): List<K> {
    val present = current.toHashSet()
    val leading = mutableListOf<K>()
    val leavingAfter = HashMap<K, MutableList<K>>()
    var anchor: K? = null
    for (key in shown) {
        val after = anchor
        when {
            key in present -> anchor = key
            after == null -> leading += key
            else -> leavingAfter.getOrPut(after) { mutableListOf() } += key
        }
    }
    return buildList {
        addAll(leading)
        current.forEach { key ->
            add(key)
            leavingAfter[key]?.let(::addAll)
        }
    }
}

/**
 * Lays the content out [gap] shorter at its bottom (D-137): for a column or flow row whose items each carry the gap
 * after them inside their own fold, so the last gap does not add to the height. The trimmed strip is empty space.
 */
internal fun Modifier.withoutTrailingGap(gap: Dp): Modifier = if (gap == 0.dp) {
    this
} else {
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height - gap.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(placeable.width, height) { placeable.place(0, 0) }
    }
}
