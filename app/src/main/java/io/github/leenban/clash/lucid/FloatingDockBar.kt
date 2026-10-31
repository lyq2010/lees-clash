package io.github.leenban.clash.lucid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.leenban.clash.design.lucid.lucidClickable

private enum class DockSymbol {
    Home,
    Groups,
    Tools,
    More,
}

@Composable
internal fun FloatingDockBar(selectedRoute: String, onSelect: (String) -> Unit) {
    val palette = rememberHomeBentoPalette()
    val shape = RoundedCornerShape(28.dp)
    val pillShape = RoundedCornerShape(20.dp)
    val borderBrush =
        remember(palette) {
            Brush.verticalGradient(listOf(palette.borderTop, palette.borderBottom))
        }
    val items =
        remember {
            listOf(
                Triple("home", "首页", DockSymbol.Home),
                Triple("groups", "策略", DockSymbol.Groups),
                Triple("tools", "工具", DockSymbol.Tools),
                Triple("more", "更多", DockSymbol.More),
            )
        }

    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(top = 4.dp, bottom = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(palette.cardBg.copy(alpha = 0.96f))
                .border(1.dp, borderBrush, shape)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { (route, title, symbol) ->
                val active = selectedRoute == route
                val tint by
                    animateColorAsState(
                        targetValue =
                            if (active) {
                                if (palette.dark) palette.primaryText else palette.blueAccent
                            } else {
                                palette.mutedText.copy(alpha = 0.62f)
                            },
                        animationSpec = tween(160),
                        label = "dock-item-$route",
                    )
                val pillBg by
                    animateColorAsState(
                        targetValue =
                            when {
                                active && palette.dark -> Color.White.copy(alpha = 0.11f)
                                active -> palette.blueAccent.copy(alpha = 0.12f)
                                else -> Color.Transparent
                            },
                        animationSpec = tween(160),
                        label = "dock-pill-$route",
                    )
                val pillBorder by
                    animateColorAsState(
                        targetValue =
                            when {
                                active && palette.dark -> Color.White.copy(alpha = 0.18f)
                                active -> palette.blueAccent.copy(alpha = 0.24f)
                                else -> Color.Transparent
                            },
                        animationSpec = tween(160),
                        label = "dock-border-$route",
                    )
                Box(
                    Modifier.weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(pillShape)
                        .background(pillBg)
                        .border(0.8.dp, pillBorder, pillShape)
                        .semantics {
                            contentDescription = title
                            selected = active
                        }
                        .testTag("dock-$route")
                        .lucidClickable(role = Role.Tab, scale = true) { onSelect(route) },
                    contentAlignment = Alignment.Center,
                ) {
                    DockSymbolCanvas(symbol, tint, Modifier.size(21.dp))
                }
            }
        }
    }
}

@Composable
private fun DockSymbolCanvas(symbol: DockSymbol, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.85f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (symbol) {
                DockSymbol.Home -> {
                    val cr = CornerRadius(2.0f)
                    drawRoundRect(color, Offset(4f, 4f), Size(6.8f, 6.8f), cr, style = stroke)
                    drawRoundRect(color, Offset(13.2f, 4f), Size(6.8f, 6.8f), cr, style = stroke)
                    drawRoundRect(color, Offset(4f, 13.2f), Size(6.8f, 6.8f), cr, style = stroke)
                    drawRoundRect(color, Offset(13.2f, 13.2f), Size(6.8f, 6.8f), cr, style = stroke)
                }
                DockSymbol.Groups -> {
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
                DockSymbol.Tools -> {
                    drawCircle(color, 8.5f, Offset(12f, 12f), style = stroke)
                    val pulse =
                        Path().apply {
                            moveTo(6.2f, 12.3f)
                            lineTo(9.1f, 12.3f)
                            lineTo(10.8f, 8.2f)
                            lineTo(13.3f, 15.8f)
                            lineTo(15f, 12.3f)
                            lineTo(17.8f, 12.3f)
                        }
                    drawPath(pulse, color, style = Stroke(1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                DockSymbol.More -> {
                    drawLine(color, Offset(4.2f, 7.2f), Offset(8.2f, 7.2f), 1.85f, StrokeCap.Round)
                    drawLine(color, Offset(12.6f, 7.2f), Offset(19.8f, 7.2f), 1.85f, StrokeCap.Round)
                    drawCircle(color, 2.2f, Offset(10.4f, 7.2f), style = stroke)

                    drawLine(color, Offset(4.2f, 12.2f), Offset(13.2f, 12.2f), 1.85f, StrokeCap.Round)
                    drawLine(color, Offset(17.6f, 12.2f), Offset(19.8f, 12.2f), 1.85f, StrokeCap.Round)
                    drawCircle(color, 2.2f, Offset(15.4f, 12.2f), style = stroke)

                    drawLine(color, Offset(4.2f, 17.2f), Offset(7.2f, 17.2f), 1.85f, StrokeCap.Round)
                    drawLine(color, Offset(11.6f, 17.2f), Offset(19.8f, 17.2f), 1.85f, StrokeCap.Round)
                    drawCircle(color, 2.2f, Offset(9.4f, 17.2f), style = stroke)
                }
            }
        }
    }
}
