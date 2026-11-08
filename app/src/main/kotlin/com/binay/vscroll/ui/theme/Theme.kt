package com.binay.vscroll.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val OceanPopColorScheme = lightColorScheme(
    primary = OceanPrimary,
    onPrimary = OceanOnPrimary,
    primaryContainer = OceanPrimaryContainer,
    onPrimaryContainer = OceanOnPrimaryContainer,
    secondary = OceanSecondary,
    onSecondary = OceanOnPrimary,
    secondaryContainer = OceanSecondaryContainer,
    onSecondaryContainer = OceanOnSecondaryContainer,
    tertiary = OceanTertiary,
    background = OceanBackground,
    onBackground = OceanOnBackground,
    surface = OceanSurface,
    onSurface = OceanOnSurface,
    surfaceVariant = OceanSurfaceVariant,
    onSurfaceVariant = OceanOnSurfaceVariant,
    outline = OceanOutline,
    outlineVariant = OceanOutlineVariant
)

val SunsetGlowColorScheme = lightColorScheme(
    primary = SunsetPrimary,
    onPrimary = SunsetOnPrimary,
    primaryContainer = SunsetPrimaryContainer,
    onPrimaryContainer = SunsetOnPrimaryContainer,
    secondary = SunsetSecondary,
    onSecondary = SunsetOnPrimary,
    secondaryContainer = SunsetSecondaryContainer,
    onSecondaryContainer = SunsetOnSecondaryContainer,
    tertiary = SunsetTertiary,
    background = SunsetBackground,
    onBackground = SunsetOnBackground,
    surface = SunsetSurface,
    onSurface = SunsetOnSurface,
    surfaceVariant = SunsetSurfaceVariant,
    onSurfaceVariant = SunsetOnSurfaceVariant,
    outline = SunsetOutline,
    outlineVariant = SunsetOutlineVariant
)

val FreshMintColorScheme = lightColorScheme(
    primary = MintPrimary,
    onPrimary = MintOnPrimary,
    primaryContainer = MintPrimaryContainer,
    onPrimaryContainer = MintOnPrimaryContainer,
    secondary = MintSecondary,
    onSecondary = MintOnPrimary,
    secondaryContainer = MintSecondaryContainer,
    onSecondaryContainer = MintOnSecondaryContainer,
    tertiary = MintTertiary,
    background = MintBackground,
    onBackground = MintOnBackground,
    surface = MintSurface,
    onSurface = MintOnSurface,
    surfaceVariant = MintSurfaceVariant,
    onSurfaceVariant = MintOnSurfaceVariant,
    outline = MintOutline,
    outlineVariant = MintOutlineVariant
)

val LavenderDreamColorScheme = lightColorScheme(
    primary = LavenderPrimary,
    onPrimary = LavenderOnPrimary,
    primaryContainer = LavenderPrimaryContainer,
    onPrimaryContainer = LavenderOnPrimaryContainer,
    secondary = LavenderSecondary,
    onSecondary = LavenderOnPrimary,
    secondaryContainer = LavenderSecondaryContainer,
    onSecondaryContainer = LavenderOnSecondaryContainer,
    tertiary = LavenderTertiary,
    background = LavenderBackground,
    onBackground = LavenderOnBackground,
    surface = LavenderSurface,
    onSurface = LavenderOnSurface,
    surfaceVariant = LavenderSurfaceVariant,
    onSurfaceVariant = LavenderOnSurfaceVariant,
    outline = LavenderOutline,
    outlineVariant = LavenderOutlineVariant
)

val MidnightColorScheme = darkColorScheme(
    primary = MidnightPrimary,
    onPrimary = MidnightOnPrimary,
    primaryContainer = MidnightPrimaryContainer,
    onPrimaryContainer = MidnightOnPrimaryContainer,
    secondary = MidnightSecondary,
    onSecondary = MidnightOnPrimary,
    secondaryContainer = MidnightSecondaryContainer,
    onSecondaryContainer = MidnightOnSecondaryContainer,
    tertiary = MidnightTertiary,
    background = MidnightBackground,
    onBackground = MidnightOnBackground,
    surface = MidnightSurface,
    onSurface = MidnightOnSurface,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = MidnightOnSurfaceVariant,
    outline = MidnightOutline,
    outlineVariant = MidnightOutlineVariant
)

val ZunoColorScheme = lightColorScheme(
    primary = ZunoPrimary,
    onPrimary = ZunoOnPrimary,
    primaryContainer = ZunoPrimaryContainer,
    onPrimaryContainer = ZunoOnPrimaryContainer,
    secondary = ZunoSecondary,
    onSecondary = ZunoOnSecondary,
    secondaryContainer = ZunoSecondaryContainer,
    onSecondaryContainer = ZunoOnSecondaryContainer,
    tertiary = ZunoTertiary,
    background = ZunoBackground,
    onBackground = ZunoOnBackground,
    surface = ZunoSurface,
    onSurface = ZunoOnSurface,
    surfaceVariant = ZunoSurfaceVariant,
    onSurfaceVariant = ZunoOnSurfaceVariant,
    outline = ZunoOutline,
    outlineVariant = ZunoOutlineVariant
)

@Composable
fun VScrollTheme(
    themeMode: VScrollThemeMode = VScrollThemeMode.ZUNO,
    content: @Composable () -> Unit
) {
    val targetScheme = when (themeMode) {
        VScrollThemeMode.ZUNO -> ZunoColorScheme
        VScrollThemeMode.OCEAN_POP -> OceanPopColorScheme
        VScrollThemeMode.SUNSET_GLOW -> SunsetGlowColorScheme
        VScrollThemeMode.FRESH_MINT -> FreshMintColorScheme
        VScrollThemeMode.LAVENDER_DREAM -> LavenderDreamColorScheme
        VScrollThemeMode.MIDNIGHT -> MidnightColorScheme
    }

    val animatedScheme = animateColorScheme(targetScheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                // If it's a light theme, we want dark icons (isAppearanceLightStatusBars = true)
                // If Midnight (dark), we want light icons (isAppearanceLightStatusBars = false)
                val isLight = !themeMode.isDark
                insetsController.isAppearanceLightStatusBars = isLight
                insetsController.isAppearanceLightNavigationBars = isLight
            }
        }
    }

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = Typography,
        content = content
    )
}

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val animDuration = 250
    val primary = animateColorAsState(target.primary, tween(animDuration), label = "primary").value
    val onPrimary = animateColorAsState(target.onPrimary, tween(animDuration), label = "onPrimary").value
    val primaryContainer = animateColorAsState(target.primaryContainer, tween(animDuration), label = "primaryContainer").value
    val onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, tween(animDuration), label = "onPrimaryContainer").value
    val secondary = animateColorAsState(target.secondary, tween(animDuration), label = "secondary").value
    val onSecondary = animateColorAsState(target.onSecondary, tween(animDuration), label = "onSecondary").value
    val secondaryContainer = animateColorAsState(target.secondaryContainer, tween(animDuration), label = "secondaryContainer").value
    val onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, tween(animDuration), label = "onSecondaryContainer").value
    val background = animateColorAsState(target.background, tween(animDuration), label = "background").value
    val onBackground = animateColorAsState(target.onBackground, tween(animDuration), label = "onBackground").value
    val surface = animateColorAsState(target.surface, tween(animDuration), label = "surface").value
    val onSurface = animateColorAsState(target.onSurface, tween(animDuration), label = "onSurface").value
    val surfaceVariant = animateColorAsState(target.surfaceVariant, tween(animDuration), label = "surfaceVariant").value
    val onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, tween(animDuration), label = "onSurfaceVariant").value
    val outline = animateColorAsState(target.outline, tween(animDuration), label = "outline").value
    val outlineVariant = animateColorAsState(target.outlineVariant, tween(animDuration), label = "outlineVariant").value

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant
    )
}
