package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = YTRed,
    onPrimary = Color.White,
    primaryContainer = YTRedContainer,
    onPrimaryContainer = YTOnRedContainer,
    secondary = YTSecondary,
    onSecondary = Color.White,
    secondaryContainer = YTSurfaceElevated,
    onSecondaryContainer = YTOnBackground,
    tertiary = YTTertiary,
    onTertiary = Color.Black,
    background = YTBackground,
    onBackground = YTOnBackground,
    surface = YTSurface,
    onSurface = YTOnBackground,
    surfaceVariant = YTSurfaceVariant,
    onSurfaceVariant = YTOnSurfaceVariant,
    outline = YTOutline
)

private val LightColorScheme = lightColorScheme(
    primary = YTRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5E9),
    onPrimaryContainer = YTRedDark,
    secondary = YTRedLight,
    onSecondary = Color.White,
    background = YTLighBackground,
    onBackground = Color(0xFF121212),
    surface = YTLightSurface,
    onSurface = Color(0xFF121212),
    surfaceVariant = YTLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF555555),
    outline = Color(0xFFCCCCCC)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek dark YouTube Music aesthetic
    dynamicColor: Boolean = false, // Keep brand YouTube Red consistency
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
