package ir.courseplanner.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ir.courseplanner.app.data.preferences.AppColorTheme

/**
 * Colour scheme of one of the seven app palettes, in the requested mode.
 * Every palette is a complete light + dark Material 3 role set (see
 * AppThemePalettes.kt), so surfaces, containers, outlines and the tertiary
 * accents follow the selected theme - not only the buttons.
 */
fun createCustomColorScheme(theme: AppColorTheme, isDark: Boolean): ColorScheme =
    paletteOf(theme).scheme(isDark)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    colorTheme: AppColorTheme = AppColorTheme.INDIGO,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> createCustomColorScheme(colorTheme, darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}