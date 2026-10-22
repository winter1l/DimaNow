package com.example.dimanow.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val baseline = Typography()

/**
 * DIMA type scale (D-104): the Material 3 baseline sizes and weights with the official emphasized
 * styles (`*Emphasized`: 500 for display, headline, title-large and body; 700 for title-medium/small
 * and labels), replacing the D-094(8) Medium titles and the single SemiBold emphasis.
 *
 * Korean is in M3's "medium" language-height group, so every role takes the taller line heights the
 * type-scale tokens give for that group (display 73/56/47, headline 42/38/35, title 31/27/23,
 * body 27/23/18, label 23/18/18 sp). Emphasized styles keep the same metrics with a heavier weight.
 * Use emphasis only where it carries meaning: screen titles, next-departure countdowns, the primary
 * briefing line and selected state.
 */
val Typography = Typography(
    displayLarge = baseline.displayLarge.korean(73),
    displayMedium = baseline.displayMedium.korean(56),
    displaySmall = baseline.displaySmall.korean(47),
    headlineLarge = baseline.headlineLarge.korean(42),
    headlineMedium = baseline.headlineMedium.korean(38),
    headlineSmall = baseline.headlineSmall.korean(35),
    titleLarge = baseline.titleLarge.korean(31),
    titleMedium = baseline.titleMedium.korean(27),
    titleSmall = baseline.titleSmall.korean(23),
    bodyLarge = baseline.bodyLarge.korean(27),
    bodyMedium = baseline.bodyMedium.korean(23),
    bodySmall = baseline.bodySmall.korean(18),
    labelLarge = baseline.labelLarge.korean(23),
    labelMedium = baseline.labelMedium.korean(18),
    labelSmall = baseline.labelSmall.korean(18),
    displayLargeEmphasized = baseline.displayLargeEmphasized.korean(73),
    displayMediumEmphasized = baseline.displayMediumEmphasized.korean(56),
    displaySmallEmphasized = baseline.displaySmallEmphasized.korean(47),
    headlineLargeEmphasized = baseline.headlineLargeEmphasized.korean(42),
    headlineMediumEmphasized = baseline.headlineMediumEmphasized.korean(38),
    headlineSmallEmphasized = baseline.headlineSmallEmphasized.korean(35),
    titleLargeEmphasized = baseline.titleLargeEmphasized.korean(31),
    titleMediumEmphasized = baseline.titleMediumEmphasized.korean(27),
    titleSmallEmphasized = baseline.titleSmallEmphasized.korean(23),
    bodyLargeEmphasized = baseline.bodyLargeEmphasized.korean(27),
    bodyMediumEmphasized = baseline.bodyMediumEmphasized.korean(23),
    bodySmallEmphasized = baseline.bodySmallEmphasized.korean(18),
    labelLargeEmphasized = baseline.labelLargeEmphasized.korean(23),
    labelMediumEmphasized = baseline.labelMediumEmphasized.korean(18),
    labelSmallEmphasized = baseline.labelSmallEmphasized.korean(18),
)

/** M3 medium language-height line height for Korean text. */
private fun TextStyle.korean(lineHeightSp: Int): TextStyle = copy(lineHeight = lineHeightSp.sp)

/** Title of full-screen panes (title-large emphasized). */
@Composable
internal fun dimaPageTitleStyle(): TextStyle = MaterialTheme.typography.titleLargeEmphasized

/** Screen title in the pinned top app bar: headline-medium at its baseline weight, as D-104 showed it (D-106). */
@Composable
internal fun dimaScreenTitleStyle(): TextStyle = MaterialTheme.typography.headlineMedium
