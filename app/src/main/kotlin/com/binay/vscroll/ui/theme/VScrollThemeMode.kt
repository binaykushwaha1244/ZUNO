package com.binay.vscroll.ui.theme

import androidx.compose.ui.graphics.Color

enum class VScrollThemeMode(
    val displayName: String,
    val subtitle: String,
    val primaryPreviewColor: Color,
    val secondaryPreviewColor: Color,
    val surfacePreviewColor: Color,
    val isDark: Boolean = false
) {
    ZUNO(
        displayName = "ZUNO",
        subtitle = "Rich violet, midnight navy & warm ivory",
        primaryPreviewColor = Color(0xFF5935A8),
        secondaryPreviewColor = Color(0xFF202443),
        surfacePreviewColor = Color(0xFFFAF9F6),
        isDark = false
    ),
    SUNSET_GLOW(
        displayName = "Sunset Glow",
        subtitle = "Vibrant coral, orange & warm peach",
        primaryPreviewColor = Color(0xFFE11D48),
        secondaryPreviewColor = Color(0xFFEA580C),
        surfacePreviewColor = Color(0xFFFFFFFF),
        isDark = false
    ),
    FRESH_MINT(
        displayName = "Fresh Mint",
        subtitle = "Mint green, emerald & lively white",
        primaryPreviewColor = Color(0xFF059669),
        secondaryPreviewColor = Color(0xFF10B981),
        surfacePreviewColor = Color(0xFFFFFFFF),
        isDark = false
    ),
    LAVENDER_DREAM(
        displayName = "Lavender Dream",
        subtitle = "Royal lavender, violet & soft pink",
        primaryPreviewColor = Color(0xFF7C3AED),
        secondaryPreviewColor = Color(0xFFEC4899),
        surfacePreviewColor = Color(0xFFFFFFFF),
        isDark = false
    ),
    MIDNIGHT(
        displayName = "Midnight",
        subtitle = "Obsidian black, electric cyan & violet",
        primaryPreviewColor = Color(0xFF38BDF8),
        secondaryPreviewColor = Color(0xFFA855F7),
        surfacePreviewColor = Color(0xFF111622),
        isDark = true
    )
}

