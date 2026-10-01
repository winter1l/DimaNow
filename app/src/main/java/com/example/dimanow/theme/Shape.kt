package com.example.dimanow.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * DIMA shape tokens (D-094(8)).
 *
 * The scale follows the Material 3 corner scale. Material3 1.4.0 stable `Shapes` has no
 * `largeIncreased` slot outside the experimental Expressive API, so the extra step lives here.
 * Screens use the role names below instead of `RoundedCornerShape(n.dp)` literals.
 * Buttons keep their Material default (full) shape; dialogs keep the AlertDialog default
 * ([extraLarge]); full pills use `CircleShape`.
 */
object DimaShapes {
    val ExtraSmall: CornerBasedShape = RoundedCornerShape(4.dp)
    val Small: CornerBasedShape = RoundedCornerShape(8.dp)
    val Medium: CornerBasedShape = RoundedCornerShape(12.dp)
    val Large: CornerBasedShape = RoundedCornerShape(16.dp)
    val LargeIncreased: CornerBasedShape = RoundedCornerShape(20.dp)
    val ExtraLarge: CornerBasedShape = RoundedCornerShape(28.dp)

    /** Every card or section container (ElevatedCard, OutlinedCard, Card). */
    val Card: CornerBasedShape = LargeIncreased

    /** Departure capsules, next-departure tiles, inset panels inside cards and selector cells. */
    val Tile: CornerBasedShape = Medium

    /** Badges, tags, status chips and timetable chips. */
    val Badge: CornerBasedShape = Small

    /** Dialogs and sheets (the Material default for AlertDialog). */
    val Dialog: CornerBasedShape = ExtraLarge
}

val Shapes = Shapes(
    extraSmall = DimaShapes.ExtraSmall,
    small = DimaShapes.Small,
    medium = DimaShapes.Medium,
    large = DimaShapes.Large,
    extraLarge = DimaShapes.ExtraLarge,
)
