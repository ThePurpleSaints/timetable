package com.azu.timetable.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
)

// Blue Palette
private val BlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF2E3B4B),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF433250),
    onTertiaryContainer = Color(0xFFF2DAFF),
    background = Color(0xFF0C1015),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF10141A),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF181F28),
    onSurfaceVariant = Color(0xFFC3C7D0),
    outline = Color(0xFF3B4450)
)

// Green Palette
private val GreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8BD89B),
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF005226),
    onPrimaryContainer = Color(0xFFA6F5B5),
    secondary = Color(0xFFB9CCB5),
    onSecondary = Color(0xFF243424),
    secondaryContainer = Color(0xFF2E3E2E),
    onSecondaryContainer = Color(0xFFD5E8D0),
    tertiary = Color(0xFFA1CED5),
    onTertiary = Color(0xFF00363C),
    tertiaryContainer = Color(0xFF224348),
    onTertiaryContainer = Color(0xFFBCEBF2),
    background = Color(0xFF0A120D),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF0E1711),
    onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF16241B),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF36443A)
)

// Orange Palette
private val OrangeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB596),
    onPrimary = Color(0xFF561F00),
    primaryContainer = Color(0xFF7A3300),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFE6BEAC),
    onSecondary = Color(0xFF432B20),
    secondaryContainer = Color(0xFF4D362A),
    onSecondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFFD3C890),
    onTertiary = Color(0xFF373107),
    tertiaryContainer = Color(0xFF423D14),
    onTertiaryContainer = Color(0xFFEEE3A9),
    background = Color(0xFF140D0A),
    onBackground = Color(0xFFECE0DA),
    surface = Color(0xFF18100C),
    onSurface = Color(0xFFECE0DA),
    surfaceVariant = Color(0xFF251914),
    onSurfaceVariant = Color(0xFFD7C2B9),
    outline = Color(0xFF4E3D35)
)

// Midnight (Pitch Dark OLED) Palette
private val MidnightDarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF2D1B4E),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFF03DAC6),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1A3330),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFFCF6679),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF3B1E24),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF08080A),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF121216),
    onSurfaceVariant = Color(0xFFB0B0C0),
    outline = Color(0xFF2A2A35)
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun TimetableTheme(
    appThemeColor: String = "Dynamic",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when (appThemeColor) {
        "Blue" -> BlueDarkColorScheme
        "Green" -> GreenDarkColorScheme
        "Orange" -> OrangeDarkColorScheme
        "Midnight" -> MidnightDarkColorScheme
        "Purple" -> DarkColorScheme
        else -> { // "Dynamic" (Wallpaper colors on Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dynamic = dynamicDarkColorScheme(context)
                // Ensure the dark background, surface, and surfaceVariant remain deep, rich, and dark
                dynamic.copy(
                    background = Color(0xFF0F0E13),
                    surface = Color(0xFF131218),
                    surfaceVariant = Color(0xFF1E1C24),
                    onSurface = Color(0xFFF1EEF7),
                    onSurfaceVariant = Color(0xFFCDC8D6),
                    outline = Color(0xFF383442)
                )
            } else {
                DarkColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
