package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = JluGold,
    onPrimary = JluNavyDark,
    primaryContainer = JluNavyLight,
    onPrimaryContainer = JluOnPrimaryContainer,
    secondary = JluInfo,
    onSecondary = Color.White,
    secondaryContainer = JluCardDark,
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = JluSuccess,
    background = JluSurface,
    onBackground = Color(0xFFF1F5F9),
    surface = JluCardDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = JluSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = JluCardBorderDark,
    error = JluError
)

private val LightColorScheme = lightColorScheme(
    primary = JluLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAF7),
    onPrimaryContainer = JluNavy,
    secondary = JluLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2F1),
    onSecondaryContainer = Color(0xFF004D40),
    tertiary = JluGoldDark,
    background = JluLightBackground,
    onBackground = Color(0xFF0F172A),
    surface = JluLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = JluSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF475569),
    outline = JluLightOutline,
    error = JluError
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our signature JLU Gießen palette
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
