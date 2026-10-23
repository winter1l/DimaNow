package com.example.dimanow.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val baseline = Typography()

/**
 * DIMA type scale (D-104, weights raised by D-118): the Material 3 baseline sizes with heavier weights, because the
 * user found the baseline text too thin on the phone ("텍스트를 전체적으로 굵게"). Body text is Medium (500) and
 * its emphasis Bold (700); titles and headlines are Bold (700) and their emphasis ExtraBold (800); labels are
 * SemiBold (600) and their emphasis ExtraBold. Display roles keep their baseline weights. A font without a weight
 * shows the nearest one it has.
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
    headlineLarge = baseline.headlineLarge.korean(42).weight(700),
    headlineMedium = baseline.headlineMedium.korean(38).weight(700),
    headlineSmall = baseline.headlineSmall.korean(35).weight(700),
    titleLarge = baseline.titleLarge.korean(31).weight(700),
    titleMedium = baseline.titleMedium.korean(27).weight(700),
    titleSmall = baseline.titleSmall.korean(23).weight(700),
    bodyLarge = baseline.bodyLarge.korean(27).weight(500),
    bodyMedium = baseline.bodyMedium.korean(23).weight(500),
    bodySmall = baseline.bodySmall.korean(18).weight(500),
    labelLarge = baseline.labelLarge.korean(23).weight(600),
    labelMedium = baseline.labelMedium.korean(18).weight(600),
    labelSmall = baseline.labelSmall.korean(18).weight(600),
    displayLargeEmphasized = baseline.displayLargeEmphasized.korean(73),
    displayMediumEmphasized = baseline.displayMediumEmphasized.korean(56),
    displaySmallEmphasized = baseline.displaySmallEmphasized.korean(47),
    headlineLargeEmphasized = baseline.headlineLargeEmphasized.korean(42).weight(800),
    headlineMediumEmphasized = baseline.headlineMediumEmphasized.korean(38).weight(800),
    headlineSmallEmphasized = baseline.headlineSmallEmphasized.korean(35).weight(800),
    titleLargeEmphasized = baseline.titleLargeEmphasized.korean(31).weight(800),
    titleMediumEmphasized = baseline.titleMediumEmphasized.korean(27).weight(800),
    titleSmallEmphasized = baseline.titleSmallEmphasized.korean(23).weight(800),
    bodyLargeEmphasized = baseline.bodyLargeEmphasized.korean(27).weight(700),
    bodyMediumEmphasized = baseline.bodyMediumEmphasized.korean(23).weight(700),
    bodySmallEmphasized = baseline.bodySmallEmphasized.korean(18).weight(700),
    labelLargeEmphasized = baseline.labelLargeEmphasized.korean(23).weight(800),
    labelMediumEmphasized = baseline.labelMediumEmphasized.korean(18).weight(800),
    labelSmallEmphasized = baseline.labelSmallEmphasized.korean(18).weight(800),
)

/** M3 medium language-height line height for Korean text. */
private fun TextStyle.korean(lineHeightSp: Int): TextStyle = copy(lineHeight = lineHeightSp.sp)

private fun TextStyle.weight(weight: Int): TextStyle = copy(fontWeight = FontWeight(weight))

/** Title of full-screen panes (title-large emphasized). */
@Composable
internal fun dimaPageTitleStyle(): TextStyle = MaterialTheme.typography.titleLargeEmphasized

/**
 * Screen title in the pinned top app bar: headline-medium emphasized (ExtraBold), so the header reads first
 * (D-118, superseding D-106's baseline weight at the user's request).
 */
@Composable
internal fun dimaScreenTitleStyle(): TextStyle = MaterialTheme.typography.headlineMediumEmphasized
