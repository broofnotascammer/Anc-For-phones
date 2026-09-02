package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF00363F),
    primaryContainer = CyanPrimaryContainer,
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondary = MintSecondary,
    onSecondary = Color(0xFF00382E),
    secondaryContainer = MintSecondaryContainer,
    onSecondaryContainer = Color(0xFFB5F4E4),
    tertiary = AmberTertiary,
    onTertiary = Color(0xFF432C00),
    tertiaryContainer = AmberTertiaryContainer,
    onTertiaryContainer = Color(0xFFFFDF9E),
    error = AncEmergencyRed,
    onError = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceBorder
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF006877),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBCE9FF),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = Color(0xFF006B5B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF74F8DC),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFF7F5B00),
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to high-contrast acoustic dark theme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

