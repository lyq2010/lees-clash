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
    val background: Color, val surface: Color, val text: Color, val secondary: Color,
    val separator: Color, val field: Color, val blue: Color = Color(0xFF007AFF),
    val green: Color = Color(0xFF34C759), val red: Color = Color(0xFFFF453A),
)

val LocalLucidColors = staticCompositionLocalOf {
    LucidColors(Color(0xFFF2F2F7), Color.White, Color(0xFF17171A), Color(0xFF86868D), Color(0xFFE6E6EB), Color(0xFFE9E9EE))
}

@Composable
fun LucidTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) LucidColors(Color(0xFF000000), Color(0xFF1C1C1E), Color(0xFFF3F3F6), Color(0xFF9D9DA4), Color(0xFF343438), Color(0xFF2C2C2E), Color(0xFF0A84FF)) else LocalLucidColors.current
    val scheme = if (dark) darkColorScheme(primary = colors.blue, background = colors.background, surface = colors.surface, onSurface = colors.text) else lightColorScheme(primary = colors.blue, background = colors.background, surface = colors.surface, onSurface = colors.text)
    CompositionLocalProvider(LocalLucidColors provides colors) { MaterialTheme(colorScheme = scheme, content = content) }
}
