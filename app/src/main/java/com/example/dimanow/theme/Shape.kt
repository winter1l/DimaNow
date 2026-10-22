package com.example.dimanow.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * DIMA shape tokens (D-094(8), D-104).
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

    /** Every card or section container: the M3 card default (12dp), D-104 replacing D-081's 20dp. */
    val Card: CornerBasedShape = Medium

    /**
     * Departure capsules, inset panels inside cards and selector cells. Nested in a 12dp card with
     * 12–16dp padding, optical roundness (outer − padding) asks for a smaller inner radius (8dp).
     */
    val Tile: CornerBasedShape = Small

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
    largeIncreased = DimaShapes.LargeIncreased,
    extraLargeIncreased = DimaShapes.ExtraLargeIncreased,
    extraExtraLarge = DimaShapes.ExtraExtraLarge,
)
