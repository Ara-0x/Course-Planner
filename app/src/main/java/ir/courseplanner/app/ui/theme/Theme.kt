package ir.courseplanner.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ir.courseplanner.app.data.preferences.AppColorTheme

fun createCustomColorScheme(theme: AppColorTheme, isDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = theme.darkPrimaryColor,
            onPrimary = OnPrimaryDark,
            primaryContainer = theme.darkPrimaryContainerColor,
            onPrimaryContainer = OnPrimaryContainerDark,
            secondary = theme.secondaryColor,
            onSecondary = OnSecondaryDark,
            secondaryContainer = SecondaryContainerDark,
            onSecondaryContainer = OnSecondaryContainerDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark,
            surfaceVariant = SurfaceVariantDark,
            onSurfaceVariant = OnSurfaceVariantDark,
            background = BackgroundDark,
            onBackground = OnSurfaceDark,
            outline = OutlineDark,
            outlineVariant = OutlineVariantDark
        )
    } else {
        lightColorScheme(
            primary = theme.primaryColor,
            onPrimary = theme.onPrimaryColor,
            primaryContainer = theme.primaryContainerColor,
            onPrimaryContainer = OnPrimaryContainerLight,
            secondary = theme.secondaryColor,
            onSecondary = theme.onSecondaryColor,
            secondaryContainer = SecondaryContainerLight,
            onSecondaryContainer = OnSecondaryContainerLight,
            surface = SurfaceLight,
            onSurface = OnSurfaceLight,
            surfaceVariant = SurfaceVariantLight,
            onSurfaceVariant = OnSurfaceVariantLight,
            background = BackgroundLight,
            onBackground = OnSurfaceLight,
            outline = OutlineLight,
            outlineVariant = OutlineVariantLight
        )
    }
}

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
