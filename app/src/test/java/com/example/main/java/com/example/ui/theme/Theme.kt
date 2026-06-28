package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cosmic Color Palette Definitions
val SpaceBackgroundDark = Color(0xFF0B0813) // Deep Purple Black
val SpaceSurfaceDark = Color(0xFF161226)    // Semi-transparent Slate Purple
val SpacePrimaryPurple = Color(0xFF9B51E0)  // Cosmic Violet
val SpacePrimaryBlue = Color(0xFF2F80ED)    // Cosmic Blue
val SpacePrimaryGreen = Color(0xFF27AE60)   // Solar Aurora Green
val SpacePrimaryPink = Color(0xFFEB5757)    // Nebula Red

// Accent Palette Mapping
fun getAccentColor(name: String): Color {
    return when (name) {
        "Blue" -> Color(0xFF00D4FF)
        "Purple" -> Color(0xFFD0BCFF)
        "Pink" -> Color(0xFFFF4081)
        "Red" -> Color(0xFFFF5252)
        "Green" -> Color(0xFF69F0AE)
        "Gold" -> Color(0xFFFFD740)
        else -> Color(0xFFD0BCFF)
    }
}

@Composable
fun SpaceTheme(
    themeName: String,
    accentColorName: String,
    content: @Composable () -> Unit
) {
    val accent = getAccentColor(accentColorName)

    val colorScheme = when (themeName) {
        "OLED" -> darkColorScheme(
            primary = accent,
            secondary = accent.copy(alpha = 0.7f),
            tertiary = Color(0xFF121212),
            background = Color.Black,
            surface = Color(0xFF0A0A0A),
            onBackground = Color.White,
            onSurface = Color.White,
            onPrimary = Color.Black
        )
        "Deep Cosmic" -> darkColorScheme(
            primary = accent,
            secondary = SpacePrimaryBlue,
            tertiary = Color(0xFF001122),
            background = Color(0xFF000814),
            surface = Color(0xFF00152B),
            onBackground = Color(0xFFE2E8F0),
            onSurface = Color(0xFFF1F5F9),
            onPrimary = Color.Black
        )
        "Solar Aurora" -> darkColorScheme(
            primary = accent,
            secondary = Color(0xFF00E676),
            tertiary = Color(0xFF001F18),
            background = Color(0xFF000D0A),
            surface = Color(0xFF001C15),
            onBackground = Color(0xFFE0F2F1),
            onSurface = Color(0xFFE0F2F1),
            onPrimary = Color.Black
        )
        else -> { // "Galaxy Dark" (Default)
            darkColorScheme(
                primary = accent,
                secondary = SpacePrimaryPurple,
                tertiary = SpaceSurfaceDark,
                background = SpaceBackgroundDark,
                surface = SpaceSurfaceDark,
                onBackground = Color(0xFFE8E6EF),
                onSurface = Color(0xFFF0EDF5),
                onPrimary = Color.Black
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
