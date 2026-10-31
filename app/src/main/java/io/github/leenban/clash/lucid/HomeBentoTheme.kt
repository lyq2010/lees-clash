package io.github.leenban.clash.lucid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.leenban.clash.design.lucid.LocalLucidColors
import io.github.leenban.clash.design.lucid.lucidClickable

internal enum class BentoGlow {
    None,
    BottomMagenta,
    TopStartBlue,
    BottomEndAmber,
    EndEmerald,
}

internal data class HomeBentoPalette(
    val dark: Boolean,
    val pageBg: Color,
    val topAmbientGlow: Color,
    val cardBg: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val primaryText: Color,
    val mutedText: Color,
    val subSurface: Color,
    val subSurfaceActive: Color,
    val subBorder: Color,
    val subBorderActive: Color,
    val pillBg: Color,
    val trackFill: Color,
    val blueAccent: Color,
    val amberAccent: Color,
    val emeraldAccent: Color,
    val heroGlowCenter: Color,
    val heroGlowMid: Color,
    val blueGlowCenter: Color,
    val blueGlowMid: Color,
    val amberGlowCenter: Color,
    val amberGlowMid: Color,
    val emeraldGlowCenter: Color,
    val accentOrbBg: Color,
    val accentOrbFg: Color,
    val policyBadgeBg: Color,
    val policyBadgeFg: Color,
    val readyDot: Color,
)

@Composable
internal fun rememberHomeBentoPalette(): HomeBentoPalette {
    val c = LocalLucidColors.current
    return remember(c) {
        if (c.dark) {
            HomeBentoPalette(
                dark = true,
                pageBg = c.background,
                topAmbientGlow = c.ambientGlow,
                cardBg = c.surface,
                borderTop = c.borderTop,
                borderBottom = c.borderBottom,
                primaryText = c.text,
                mutedText = c.secondary,
                subSurface = Color.White.copy(alpha = 0.05f),
                subSurfaceActive = Color.White.copy(alpha = 0.14f),
                subBorder = Color.White.copy(alpha = 0.08f),
                subBorderActive = Color.White.copy(alpha = 0.38f),
                pillBg = Color.White.copy(alpha = 0.08f),
                trackFill = Color.White.copy(alpha = 0.15f),
                blueAccent = Color(0xFF5C95FF),
                amberAccent = Color(0xFFFF9A3E),
                emeraldAccent = Color(0xFF3ADE68),
                heroGlowCenter = Color(0xFFA22970).copy(alpha = 0.66f),
                heroGlowMid = Color(0xFF52133A).copy(alpha = 0.30f),
                blueGlowCenter = Color(0xFF3362F8).copy(alpha = 0.64f),
                blueGlowMid = Color(0xFF1A3182).copy(alpha = 0.27f),
                amberGlowCenter = Color(0xFFE85C1A).copy(alpha = 0.64f),
                amberGlowMid = Color(0xFF7D2D0A).copy(alpha = 0.27f),
                emeraldGlowCenter = Color(0xFF1E9B60).copy(alpha = 0.26f),
                accentOrbBg = Color(0xFFF6F6F9),
                accentOrbFg = Color(0xFF111116),
                policyBadgeBg = Color(0xFFF6F6F9),
                policyBadgeFg = Color(0xFF111116),
                readyDot = c.green,
            )
        } else {
            HomeBentoPalette(
                dark = false,
                pageBg = c.background,
                topAmbientGlow = c.ambientGlow,
                cardBg = c.surface,
                borderTop = c.borderTop,
                borderBottom = c.borderBottom,
                primaryText = c.text,
                mutedText = c.secondary,
                subSurface = Color(0xFFF4F6FB),
                subSurfaceActive = Color(0xFFEAF1FF),
                subBorder = Color(0xFFE2E7F2),
                subBorderActive = c.blue.copy(alpha = 0.45f),
                pillBg = Color(0xFFF1F4FA),
                trackFill = Color(0xFFE3E8F2),
                blueAccent = c.blue,
                amberAccent = Color(0xFFE66A1F),
                emeraldAccent = c.green,
                heroGlowCenter = Color(0xFF6B5BFF).copy(alpha = 0.11f),
                heroGlowMid = Color(0xFFD93B90).copy(alpha = 0.04f),
                blueGlowCenter = Color(0xFF2B62FF).copy(alpha = 0.14f),
                blueGlowMid = Color(0xFF2B62FF).copy(alpha = 0.05f),
                amberGlowCenter = Color(0xFFF06A24).copy(alpha = 0.15f),
                amberGlowMid = Color(0xFFF06A24).copy(alpha = 0.05f),
                emeraldGlowCenter = Color(0xFF25B84B).copy(alpha = 0.10f),
                accentOrbBg = c.blue,
                accentOrbFg = Color.White,
                policyBadgeBg = c.blue.copy(alpha = 0.13f),
                policyBadgeFg = c.blue,
                readyDot = c.green,
            )
        }
    }
}

@Composable
internal fun HomeBentoCard(
    palette: HomeBentoPalette,
    cornerRadius: Dp = 24.dp,
    glow: BentoGlow = BentoGlow.None,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush =
        remember(palette) {
            Brush.verticalGradient(listOf(palette.borderTop, palette.borderBottom))
        }
    Box(
        modifier
            .clip(shape)
            .background(palette.cardBg)
            .drawBehind {
                when (glow) {
                    BentoGlow.None -> Unit
                    BentoGlow.BottomMagenta -> {
                        val radius = size.width.coerceAtLeast(1f) * 0.80f
                        drawRect(
                            Brush.radialGradient(
                                0.0f to palette.heroGlowCenter,
                                0.52f to palette.heroGlowMid,
                                1.0f to Color.Transparent,
                                center = Offset(size.width * 0.52f, size.height * 1.14f),
                                radius = radius,
                            )
                        )
                    }
                    BentoGlow.TopStartBlue -> {
                        val radius = size.width.coerceAtLeast(1f) * 1.08f
                        drawRect(
                            Brush.radialGradient(
                                0.0f to palette.blueGlowCenter,
                                0.52f to palette.blueGlowMid,
                                1.0f to Color.Transparent,
                                center = Offset(size.width * 0.08f, size.height * 0.08f),
                                radius = radius,
                            )
                        )
                    }
                    BentoGlow.BottomEndAmber -> {
                        val radius = size.width.coerceAtLeast(1f) * 1.08f
                        drawRect(
                            Brush.radialGradient(
                                0.0f to palette.amberGlowCenter,
                                0.52f to palette.amberGlowMid,
                                1.0f to Color.Transparent,
                                center = Offset(size.width * 0.92f, size.height * 0.94f),
                                radius = radius,
                            )
                        )
                    }
                    BentoGlow.EndEmerald -> {
                        val radius = size.width.coerceAtLeast(1f) * 0.65f
                        drawRect(
                            Brush.radialGradient(
                                0.0f to palette.emeraldGlowCenter,
                                1.0f to Color.Transparent,
                                center = Offset(size.width * 0.92f, size.height * 0.50f),
                                radius = radius,
                            )
                        )
                    }
                }
            }
            .border(1.dp, borderBrush, shape)
            .then(
                if (onClick != null)
                    Modifier.lucidClickable(enabled = enabled, role = role, scale = true, onClick = onClick)
                else Modifier
            ),
        content = content,
    )
}

internal enum class HomeBentoSymbol {
    SplitRoute,
    ModeRule,
    ModeGlobal,
    ModeDirect,
    Layers,
    Throughput,
    Connections,
    Subscription,
    Play,
    Refresh,
}

@Composable
internal fun HomeBentoIcon(
    symbol: HomeBentoSymbol,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (symbol) {
                HomeBentoSymbol.SplitRoute -> {
                    drawCircle(color, 2.2f, Offset(5f, 12f), style = stroke)
                    val branch =
                        Path().apply {
                            moveTo(7.2f, 12f)
                            lineTo(11f, 12f)
                            lineTo(14.5f, 7f)
                            lineTo(19.5f, 7f)
                            moveTo(11f, 12f)
                            lineTo(14.5f, 17f)
                            lineTo(19.5f, 17f)
                        }
                    drawPath(branch, color, style = stroke)
                    val heads =
                        Path().apply {
                            moveTo(17.2f, 4.8f)
                            lineTo(19.8f, 7f)
                            lineTo(17.2f, 9.2f)
                            moveTo(17.2f, 14.8f)
                            lineTo(19.8f, 17f)
                            lineTo(17.2f, 19.2f)
                        }
                    drawPath(heads, color, style = stroke)
                }
                HomeBentoSymbol.ModeRule -> {
                    drawLine(color, Offset(4f, 7f), Offset(20f, 7f), 1.85f, StrokeCap.Round)
                    drawCircle(color, 2.1f, Offset(9f, 7f))
                    drawLine(color, Offset(4f, 12f), Offset(20f, 12f), 1.85f, StrokeCap.Round)
                    drawCircle(color, 2.1f, Offset(15.5f, 12f))
                    drawLine(color, Offset(4f, 17f), Offset(20f, 17f), 1.85f, StrokeCap.Round)
                    drawCircle(color, 2.1f, Offset(11f, 17f))
                }
                HomeBentoSymbol.ModeGlobal -> {
                    drawCircle(color, 8.2f, Offset(12f, 12f), style = stroke)
                    drawOval(color, Offset(8.2f, 3.8f), Size(7.6f, 16.4f), style = Stroke(1.55f))
                    drawLine(color, Offset(4.2f, 12f), Offset(19.8f, 12f), 1.55f, StrokeCap.Round)
                }
                HomeBentoSymbol.ModeDirect -> {
                    drawLine(color, Offset(4.5f, 12f), Offset(19.5f, 12f), 2.0f, StrokeCap.Round)
                    val arrow =
                        Path().apply {
                            moveTo(14f, 6.8f)
                            lineTo(19.8f, 12f)
                            lineTo(14f, 17.2f)
                        }
                    drawPath(arrow, color, style = stroke)
                }
                HomeBentoSymbol.Layers -> {
                    val topLayer =
                        Path().apply {
                            moveTo(12f, 3.8f)
                            lineTo(20.2f, 8.2f)
                            lineTo(12f, 12.6f)
                            lineTo(3.8f, 8.2f)
                            close()
                        }
                    drawPath(topLayer, color, style = stroke)
                    val midLayer =
                        Path().apply {
                            moveTo(3.8f, 12.6f)
                            lineTo(12f, 17f)
                            lineTo(20.2f, 12.6f)
                        }
                    drawPath(midLayer, color, style = stroke)
                    val botLayer =
                        Path().apply {
                            moveTo(3.8f, 16.4f)
                            lineTo(12f, 20.8f)
                            lineTo(20.2f, 16.4f)
                        }
                    drawPath(botLayer, color, style = stroke)
                }
                HomeBentoSymbol.Throughput -> {
                    drawLine(color, Offset(8f, 19f), Offset(8f, 6f), 1.95f, StrokeCap.Round)
                    val down =
                        Path().apply {
                            moveTo(4.8f, 15.6f)
                            lineTo(8f, 19.2f)
                            lineTo(11.2f, 15.6f)
                        }
                    drawPath(down, color, style = stroke)
                    drawLine(color, Offset(16f, 18f), Offset(16f, 5f), 1.95f, StrokeCap.Round)
                    val up =
                        Path().apply {
                            moveTo(12.8f, 8.4f)
                            lineTo(16f, 4.8f)
                            lineTo(19.2f, 8.4f)
                        }
                    drawPath(up, color, style = stroke)
                }
                HomeBentoSymbol.Connections -> {
                    drawCircle(color, 2.6f, Offset(12f, 12f), style = stroke)
                    drawCircle(color, 1.9f, Offset(5.2f, 6.5f))
                    drawCircle(color, 1.9f, Offset(18.8f, 6.5f))
                    drawCircle(color, 1.9f, Offset(5.2f, 17.5f))
                    drawCircle(color, 1.9f, Offset(18.8f, 17.5f))
                    drawLine(color, Offset(9.8f, 10.3f), Offset(6.7f, 7.8f), 1.65f, StrokeCap.Round)
                    drawLine(color, Offset(14.2f, 10.3f), Offset(17.3f, 7.8f), 1.65f, StrokeCap.Round)
                    drawLine(color, Offset(9.8f, 13.7f), Offset(6.7f, 16.2f), 1.65f, StrokeCap.Round)
                    drawLine(color, Offset(14.2f, 13.7f), Offset(17.3f, 16.2f), 1.65f, StrokeCap.Round)
                }
                HomeBentoSymbol.Subscription -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(3.5f, 5.5f),
                        size = Size(17f, 13f),
                        cornerRadius = CornerRadius(3.2f),
                        style = stroke,
                    )
                    drawLine(color, Offset(3.8f, 10f), Offset(20.2f, 10f), 1.8f, StrokeCap.Round)
                    drawLine(color, Offset(7.5f, 14.5f), Offset(12.5f, 14.5f), 1.8f, StrokeCap.Round)
                }
                HomeBentoSymbol.Play -> {
                    val p =
                        Path().apply {
                            moveTo(8f, 5.5f)
                            lineTo(19f, 12f)
                            lineTo(8f, 18.5f)
                            close()
                        }
                    drawPath(p, color)
                }
                HomeBentoSymbol.Refresh -> {
                    val arcRect = Rect(4.4f, 4.4f, 19.6f, 19.6f)
                    val upper =
                        Path().apply {
                            arcTo(arcRect, 192f, 123f, true)
                            lineTo(19.5f, 8.75f)
                        }
                    val upperHead =
                        Path().apply {
                            moveTo(19.5f, 4.55f)
                            lineTo(19.5f, 8.75f)
                            lineTo(15.3f, 8.75f)
                        }
                    val lower =
                        Path().apply {
                            arcTo(arcRect, 12f, 123f, true)
                            lineTo(4.5f, 15.25f)
                        }
                    val lowerHead =
                        Path().apply {
                            moveTo(4.5f, 19.45f)
                            lineTo(4.5f, 15.25f)
                            lineTo(8.7f, 15.25f)
                        }
                    drawPath(upper, color, style = stroke)
                    drawPath(upperHead, color, style = stroke)
                    drawPath(lower, color, style = stroke)
                    drawPath(lowerHead, color, style = stroke)
                }
            }
        }
    }
}
