package com.ravi.freedium.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PeacockLight = lightColorScheme(
    primary = PeacockTeal40,
    onPrimary = PeacockBone,
    primaryContainer = PeacockTeal90,
    onPrimaryContainer = PeacockTeal10,

    secondary = PlumeBlue40,
    onSecondary = PeacockBone,
    secondaryContainer = PlumeBlue90,
    onSecondaryContainer = PlumeBlue10,

    tertiary = TailGold40,
    onTertiary = PeacockBone,
    tertiaryContainer = TailGold90,
    onTertiaryContainer = TailGold10,

    background = PeacockBone,
    onBackground = PeacockOnSurfaceLight,
    surface = PeacockBone,
    onSurface = PeacockOnSurfaceLight,
    surfaceVariant = PeacockSurfaceLight,
    onSurfaceVariant = PeacockOnSurfaceVariantLight,
    // The full surface ramp has to be spelled out. Any token left undefined falls back
    // to M3's baseline purple, which is what turned every Card lavender.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F8F8),
    surfaceContainer = Color(0xFFEAF3F3),
    surfaceContainerHigh = Color(0xFFE1EDEE),
    surfaceContainerHighest = Color(0xFFD7E7E8),
    surfaceBright = PeacockBone,
    surfaceDim = Color(0xFFD5E0E0),
    inverseSurface = Color(0xFF2B3232),
    inverseOnSurface = Color(0xFFEDF2F2),
    inversePrimary = PeacockTeal80,
    scrim = Color(0xFF000000),

    outline = PeacockOutline,
    outlineVariant = Color(0xFFC8D3D3),

    error = ErrorRed40,
    onError = PeacockBone,
    errorContainer = ErrorRed90,
    onErrorContainer = ErrorRed10
)

private val PeacockDark = darkColorScheme(
    primary = PeacockTeal80,
    onPrimary = PeacockTeal20,
    primaryContainer = PeacockTeal30,
    onPrimaryContainer = PeacockTeal90,

    secondary = PlumeBlue80,
    onSecondary = PlumeBlue20,
    secondaryContainer = PlumeBlue30,
    onSecondaryContainer = PlumeBlue90,

    tertiary = TailGold80,
    onTertiary = TailGold20,
    tertiaryContainer = TailGold30,
    onTertiaryContainer = TailGold90,

    background = PeacockInk,
    onBackground = PeacockOnSurfaceDark,
    surface = PeacockInk,
    onSurface = PeacockOnSurfaceDark,
    surfaceVariant = PeacockSurfaceDark,
    onSurfaceVariant = PeacockOnSurfaceVariantDark,
    surfaceContainerLowest = Color(0xFF060E0F),
    surfaceContainerLow = Color(0xFF0F1B1D),
    surfaceContainer = PeacockSurfaceDark,
    surfaceContainerHigh = PeacockSurfaceDarkHigh,
    surfaceContainerHighest = Color(0xFF243133),
    surfaceBright = Color(0xFF33403F),
    surfaceDim = PeacockInk,
    inverseSurface = PeacockOnSurfaceDark,
    inverseOnSurface = Color(0xFF1B2223),
    inversePrimary = PeacockTeal40,
    scrim = Color(0xFF000000),

    outline = PeacockOutlineDark,
    outlineVariant = Color(0xFF2A3739),

    error = ErrorRed80,
    onError = ErrorRed10,
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = ErrorRed90
)

/**
 * Material You dynamic colour is deliberately **not** used.
 *
 * It used to be on, which meant the whole app took its colours from the device wallpaper
 * and the palette defined here was never seen on Android 12+. Since the point of choosing
 * a peacock palette is that the app looks like itself on every device, wallpaper-derived
 * colour is exactly the thing to opt out of.
 */
@Composable
fun FreediumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PeacockDark else PeacockLight,
        typography = Typography,
        content = content
    )
}
