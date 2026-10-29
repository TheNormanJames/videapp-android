package com.videapp.mobile.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkOledColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = SheetBackground,
    primaryContainer = CardContainer,
    onPrimaryContainer = TextPrimary,
    secondary = ButtonCta,
    onSecondary = TextPrimary,
    background = SheetBackground,
    surface = SheetBackground,
    surfaceVariant = CardContainer,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun VideAPPTheme(
    darkTheme: Boolean = true, // Default to OLED dark theme for media downloader
    dynamicColor: Boolean = false, // Preserve dedicated high-contrast OLED styling
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkOledColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
