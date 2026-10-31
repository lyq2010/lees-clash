package io.github.leenban.clash.design.lucid

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class LucidColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondary: Color,
    val separator: Color,
    val field: Color,
    val blue: Color = Color(0xFF2B65F6),
    val green: Color = Color(0xFF25B84B),
    val red: Color = Color(0xFFE83E35),
    val borderTop: Color = Color(0xFFD5DBE8),
    val borderBottom: Color = Color(0xFFE2E7F2),
    val ambientGlow: Color = Color(0xFFDABBD4).copy(alpha = 0.24f),
    val dark: Boolean = false,
)

val LocalLucidColors = staticCompositionLocalOf {
    LucidColors(
        background = Color(0xFFECEFF5),
        surface = Color.White,
        text = Color(0xFF14161C),
        secondary = Color(0xFF6B7180),
        separator = Color(0xFFE3E7F0),
        field = Color(0xFFE2E6F0),
    )
}

@Composable
fun LucidTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors =
        if (dark)
            LucidColors(
                background = Color(0xFF0A0A0E),
                surface = Color(0xFF141419),
                text = Color(0xFFF6F6F9),
                secondary = Color(0xFF9898A6),
                separator = Color.White.copy(alpha = 0.08f),
                field = Color(0xFF1D1D25),
                blue = Color(0xFF4F8BFF),
                green = Color(0xFF3ADE68),
                red = Color(0xFFFF5A52),
                borderTop = Color.White.copy(alpha = 0.14f),
                borderBottom = Color.White.copy(alpha = 0.05f),
                ambientGlow = Color(0xFF3B1735).copy(alpha = 0.34f),
                dark = true,
            )
        else LocalLucidColors.current
    val scheme =
        if (dark)
            darkColorScheme(
                primary = colors.blue,
                background = colors.background,
                surface = colors.surface,
                onSurface = colors.text,
            )
        else
            lightColorScheme(
                primary = colors.blue,
                background = colors.background,
                surface = colors.surface,
                onSurface = colors.text,
            )
    CompositionLocalProvider(LocalLucidColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
