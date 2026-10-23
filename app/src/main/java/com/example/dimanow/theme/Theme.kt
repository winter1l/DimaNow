package com.example.dimanow.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    surfaceBright = SurfaceLight,
    surfaceDim = SurfaceDimLight,
    surfaceTint = PrimaryLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = SurfaceContainerHighDark,
    inverseOnSurface = SurfaceContainerLowLight,
    inversePrimary = PrimaryDark,
    error = ErrorRedLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    primaryFixed = PrimaryContainerLight,
    primaryFixedDim = PrimaryDark,
    onPrimaryFixed = OnPrimaryContainerLight,
    onPrimaryFixedVariant = PrimaryContainerDark,
    secondaryFixed = SecondaryContainerLight,
    secondaryFixedDim = SecondaryDark,
    onSecondaryFixed = OnSecondaryContainerLight,
    onSecondaryFixedVariant = SecondaryContainerDark,
    tertiaryFixed = TertiaryContainerLight,
    tertiaryFixedDim = TertiaryDark,
    onTertiaryFixed = OnTertiaryContainerLight,
    onTertiaryFixedVariant = TertiaryContainerDark,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    surfaceBright = SurfaceBrightDark,
    surfaceDim = SurfaceDark,
    surfaceTint = PrimaryDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = SurfaceContainerHighestLight,
    inverseOnSurface = SurfaceContainerHighDark,
    inversePrimary = PrimaryLight,
    error = ErrorRedDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    primaryFixed = PrimaryContainerLight,
    primaryFixedDim = PrimaryDark,
    onPrimaryFixed = OnPrimaryContainerLight,
    onPrimaryFixedVariant = PrimaryContainerDark,
    secondaryFixed = SecondaryContainerLight,
    secondaryFixedDim = SecondaryDark,
    onSecondaryFixed = OnSecondaryContainerLight,
    onSecondaryFixedVariant = SecondaryContainerDark,
    tertiaryFixed = TertiaryContainerLight,
    tertiaryFixedDim = TertiaryDark,
    onTertiaryFixed = OnTertiaryContainerLight,
    onTertiaryFixedVariant = TertiaryContainerDark,
)

/**
 * Static app colors outside the M3 scheme: the warning roles (D-104) and the surfaces cards sit on (D-114).
 * [page] is the screen background, [card] every card and segmented row on it, [cardInner] badges and tiles inside a
 * card, and [scrolledHeader] the pinned header once content scrolls under it. Each is an M3 surface role chosen so a
 * card stands clearly apart from the page in both themes: in light a white card on a tinted page, in dark a card
 * two tones above the page, as the system Settings app does.
 */
@Immutable
data class DimaExtendedColors(
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val page: Color,
    val card: Color,
    val cardInner: Color,
    val scrolledHeader: Color,
    /** D-119: rows that are done or past (기한 지남, 완료한 학습), a quieter tone than [card] and close to the page. */
    val cardMuted: Color,
)

private val LightExtendedColors = DimaExtendedColors(
    WarningLight, OnWarningLight, WarningContainerLight, OnWarningContainerLight,
    page = SurfaceContainerLowLight,
    card = SurfaceContainerLowestLight,
    cardInner = SurfaceContainerHighLight,
    scrolledHeader = SurfaceContainerLight,
    cardMuted = SurfaceContainerLight,
)
private val DarkExtendedColors = DimaExtendedColors(
    WarningDark, OnWarningDark, WarningContainerDark, OnWarningContainerDark,
    page = SurfaceDark,
    card = SurfaceContainerHighDark,
    cardInner = SurfaceContainerHighestDark,
    scrolledHeader = SurfaceContainerDark,
    cardMuted = SurfaceContainerLowDark,
)

private val LocalDimaExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** The app's extra color roles, e.g. `DimaTheme.colors.warningContainer`. */
object DimaTheme {
    val colors: DimaExtendedColors
        @Composable @ReadOnlyComposable get() = LocalDimaExtendedColors.current
}

/**
 * DIMA 브랜드 M3 Expressive 테마.
 *
 * 브랜드 정체성(#EC268F 시드 팔레트, D-024·D-027)을 유지하기 위해 dynamic color 대신
 * 고정 브랜드 스킴을 사용한다. D-104부터 `MaterialExpressiveTheme`에 expressive 모션 스킴을 쓰므로
 * 컴포넌트와 앱의 애니메이션이 같은 스프링(`MaterialTheme.motionScheme`)을 공유한다.
 * 모양은 [Shapes], 글꼴은 [Typography], 경고색은 [DimaTheme.colors]가 맡는다.
 */
@Composable
fun DIMANowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDimaExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors) {
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = Shapes,
            typography = Typography,
            content = content,
        )
    }
}
