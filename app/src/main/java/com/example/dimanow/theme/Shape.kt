package com.example.dimanow.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * DIMA shape tokens (D-094(8), D-104, D-105).
 *
 * The scale is the Material 3 Expressive corner scale, including the slots Material3 1.5 added
 * (largeIncreased 20, extraLargeIncreased 32, extraExtraLarge 48). Screens use the role names below
 * instead of `RoundedCornerShape(n.dp)` literals. Buttons keep their Material shapes (with the
 * expressive press morph); dialogs keep the AlertDialog default ([extraLarge]); pills use `CircleShape`.
 */
object DimaShapes {
    val ExtraSmall: CornerBasedShape = RoundedCornerShape(4.dp)
    val Small: CornerBasedShape = RoundedCornerShape(8.dp)
    val Medium: CornerBasedShape = RoundedCornerShape(12.dp)
    val Large: CornerBasedShape = RoundedCornerShape(16.dp)
    val LargeIncreased: CornerBasedShape = RoundedCornerShape(20.dp)
    val ExtraLarge: CornerBasedShape = RoundedCornerShape(28.dp)
    val ExtraLargeIncreased: CornerBasedShape = RoundedCornerShape(32.dp)
    val ExtraExtraLarge: CornerBasedShape = RoundedCornerShape(48.dp)

    /** Every card or section container (ElevatedCard, OutlinedCard, Card). */
    val Card: CornerBasedShape = LargeIncreased

    /** Departure capsules, next-departure tiles, inset panels inside cards and selector cells. */
    val Tile: CornerBasedShape = Medium

    /** Badges, tags, status chips and timetable chips. */
    val Badge: CornerBasedShape = Small

    /** Dialogs and sheets (the Material default for AlertDialog). */
    val Dialog: CornerBasedShape = ExtraLarge

    /**
     * A strip cell on its way from [Tile] (fraction 0) to a full pill (fraction 1): the next departure is always
     * round, and when a departure leaves the next cell rounds on the hand-over's one non-bouncy spring
     * (`departureMotion`, D-124). D-124 removed D-121's five-minute emphasis (the imminent departure turning into a
     * pill); it must not return.
     */
    fun tileToPill(fraction: Float): Shape = TileToPillShape(fraction)
}

/**
 * [DimaShapes.Tile]'s 12dp corners grown toward half the shorter side as [fraction] goes to 1 (D-121, D-124). The
 * radius stays within a full pill even if a spring passes 1.
 */
internal data class TileToPillShape(val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tile = with(density) { TILE_CORNER.toPx() }
        val pill = minOf(size.width, size.height) / 2f
        val radius = (tile + (pill - tile) * fraction).coerceIn(0f, pill)
        return Outline.Rounded(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius)))
    }

    private companion object {
        val TILE_CORNER = 12.dp
    }
}

val Shapes = Shapes(
    extraSmall = DimaShapes.ExtraSmall,
    small = DimaShapes.Small,
    medium = DimaShapes.Medium,
    large = DimaShapes.Large,
    extraLarge = DimaShapes.ExtraLarge,
    largeIncreased = DimaShapes.LargeIncreased,
    extraLargeIncreased = DimaShapes.ExtraLargeIncreased,
    extraExtraLarge = DimaShapes.ExtraExtraLarge,
)
