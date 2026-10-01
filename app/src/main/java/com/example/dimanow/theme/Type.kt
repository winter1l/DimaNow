package com.example.dimanow.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val baseline = Typography()

/**
 * DIMA type scale (D-094(8)): Material 3 baseline sizes and line heights.
 *
 * Body and label roles keep the baseline weights (body 400, label 500). Headline and title roles
 * use Medium (500) so section and card titles read as titles without per-call overrides.
 * Stronger weight is reserved for [emphasized] styles: page titles, next-departure countdowns,
 * the primary Home briefing line and selected state.
 */
val Typography = Typography(
    headlineLarge = baseline.headlineLarge.copy(fontWeight = FontWeight.Medium),
    headlineMedium = baseline.headlineMedium.copy(fontWeight = FontWeight.Medium),
    headlineSmall = baseline.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = baseline.titleLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    titleMedium = baseline.titleMedium.copy(fontWeight = FontWeight.Medium),
    titleSmall = baseline.titleSmall.copy(fontWeight = FontWeight.Medium),
)

/** Weight used by every emphasized role (Material3 1.4 `*Emphasized` roles step up the weight). */
val EmphasizedWeight: FontWeight = FontWeight.SemiBold

/**
 * The emphasized counterpart of a type role. Use only where emphasis carries meaning:
 * page titles, the next departure countdown, the primary briefing line and selected state.
 */
fun TextStyle.emphasized(): TextStyle = copy(fontWeight = EmphasizedWeight)

/**
 * Material3 1.4 TitleLargeEmphasized: 22sp / 28sp / SemiBold 600.
 */
@Composable
internal fun dimaPageTitleStyle(): TextStyle = MaterialTheme.typography.titleLarge.emphasized()

/** Page title for the in-content screen headers (headlineMedium, emphasized). */
@Composable
internal fun dimaScreenTitleStyle(): TextStyle = MaterialTheme.typography.headlineMedium.emphasized()
