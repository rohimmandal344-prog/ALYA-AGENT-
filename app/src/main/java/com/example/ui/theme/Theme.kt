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
    primary = AlyaRuby,
    onPrimary = Color.White,
    primaryContainer = AlyaRubyDark,
    onPrimaryContainer = AlyaRubyLight,
    secondary = AlyaSapphire,
    onSecondary = Color.Black,
    secondaryContainer = AlyaNavy700,
    onSecondaryContainer = Color.White,
    background = AlyaSurfaceDark,
    onBackground = AlyaSilver,
    surface = AlyaCardDark,
    onSurface = AlyaSilver,
    surfaceVariant = AlyaNavy800,
    onSurfaceVariant = AlyaSilverMuted,
    error = StateErrorColor
)

private val LightColorScheme = lightColorScheme(
    primary = AlyaRuby,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4E6),
    onPrimaryContainer = AlyaRubyDark,
    secondary = AlyaSapphireDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    error = StateErrorColor
)

@Composable
fun AlyaAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep signature anime styling consistent
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
