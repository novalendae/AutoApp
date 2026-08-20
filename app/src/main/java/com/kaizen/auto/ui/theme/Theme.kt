package com.kaizen.auto.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Paleta: azul-petróleo com acento âmbar. Escura por padrão porque a maior
// parte do uso é sobre jogos/apps em tela cheia, à noite.
private val Teal = Color(0xFF00BFA5)
private val TealDark = Color(0xFF00897B)
private val Amber = Color(0xFFFFB300)
private val Ink = Color(0xFF0E1416)
private val Surface1 = Color(0xFF16211F)
private val Surface2 = Color(0xFF1E2B29)
private val Danger = Color(0xFFE5534B)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF00201B),
    primaryContainer = TealDark,
    onPrimaryContainer = Color(0xFFB2FFF2),
    secondary = Amber,
    onSecondary = Color(0xFF3A2A00),
    background = Ink,
    onBackground = Color(0xFFE2E6E5),
    surface = Surface1,
    onSurface = Color(0xFFE2E6E5),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFB6C2C0),
    error = Danger,
    outline = Color(0xFF3D4B49),
)

private val LightColors = lightColorScheme(
    primary = TealDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2FFF2),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF9A6B00),
    background = Color(0xFFF7FAF9),
    surface = Color.White,
    surfaceVariant = Color(0xFFE3EBE9),
    error = Color(0xFFB3261E),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
)

/** Estilo do editor e dos logs: monoespaçado é inegociável para código. */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

@Composable
fun KaizenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
