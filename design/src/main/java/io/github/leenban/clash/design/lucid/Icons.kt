package io.github.leenban.clash.design.lucid

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

enum class LucidSymbol {
    Home,
    Tools,
    More,
    Mode,
    Dns,
    List,
    Chart,
    Network,
    Document,
    Settings,
    Search,
    Check,
    Chevron,
    Down,
    Refresh,
    Edit,
    Back,
    Add,
    Folder,
    Shield,
    Bell,
}

@Composable
fun LucidIcon(symbol: LucidSymbol, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun line(x: Float, y: Float, xx: Float, yy: Float) =
                drawLine(color, Offset(x, y), Offset(xx, yy), 1.8f, StrokeCap.Round)
            fun path(vararg p: Float) {
                val shape = Path()
                shape.moveTo(p[0], p[1])
                for (i in 2 until p.size step 2) shape.lineTo(p[i], p[i + 1])
                drawPath(shape, color, style = Stroke(1.8f, cap = StrokeCap.Round))
            }
            when (symbol) {
                LucidSymbol.Bell -> {
                    path(5f, 17f, 7f, 14f, 7f, 9f, 9f, 5f, 15f, 5f, 17f, 9f, 17f, 14f, 19f, 17f, 5f, 17f)
                    path(10f, 20f, 14f, 20f)
                }
                LucidSymbol.Check -> path(5f, 12f, 10f, 17f, 20f, 6f)
                LucidSymbol.Chevron -> path(9f, 5f, 16f, 12f, 9f, 19f)
                LucidSymbol.Down -> path(5f, 9f, 12f, 16f, 19f, 9f)
                LucidSymbol.Refresh -> {
                    val refreshStroke = Stroke(1.85f, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
                    drawPath(upper, color, style = refreshStroke)
                    drawPath(upperHead, color, style = refreshStroke)
                    drawPath(lower, color, style = refreshStroke)
                    drawPath(lowerHead, color, style = refreshStroke)
                }
                LucidSymbol.Edit -> {
                    path(4f, 16f, 15f, 5f, 19f, 9f, 8f, 20f, 4f, 20f, 4f, 16f)
                    line(13f, 7f, 17f, 11f)
                }
                LucidSymbol.Back -> path(15f, 4f, 7f, 12f, 15f, 20f)
                LucidSymbol.Add -> {
                    line(12f, 4f, 12f, 20f)
                    line(4f, 12f, 20f, 12f)
                }
                LucidSymbol.More ->
                    for (x in listOf(5f, 12f, 19f)) drawCircle(color, 2f, Offset(x, 12f))
                LucidSymbol.Mode -> {
                    path(7f, 6f, 2f, 12f, 7f, 18f)
                    path(17f, 6f, 22f, 12f, 17f, 18f)
                    drawCircle(color, 1.3f, Offset(12f, 12f))
                }
                LucidSymbol.Home -> {
                    drawRoundRect(color, Offset(3f, 7f), Size(18f, 14f), CornerRadius(3f))
                    line(7f, 3f, 17f, 3f)
                    line(5f, 5f, 19f, 5f)
                }
                LucidSymbol.Tools -> {
                    drawRoundRect(
                        color,
                        Offset(2f, 8f),
                        Size(20f, 13f),
                        CornerRadius(2f),
                        style = Stroke(1.8f),
                    )
                    path(8f, 8f, 8f, 4f, 16f, 4f, 16f, 8f)
                    line(2f, 13f, 22f, 13f)
                    line(11f, 12f, 13f, 12f)
                }
                LucidSymbol.Dns -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = Stroke(1.8f))
                    line(3f, 12f, 21f, 12f)
                    drawOval(color, Offset(8f, 3f), Size(8f, 18f), style = Stroke(1.4f))
                }
                LucidSymbol.List ->
                    for (y in listOf(6f, 12f, 18f)) {
                        drawCircle(color, 1f, Offset(4f, y))
                        line(8f, y, 21f, y)
                    }
                LucidSymbol.Chart -> {
                    line(3f, 21f, 21f, 21f)
                    for ((i, h) in listOf(7f, 13f, 18f, 10f).withIndex()) drawRoundRect(
                        color,
                        Offset(4f + i * 4.5f, 20f - h),
                        Size(3f, h),
                        CornerRadius(1f),
                    )
                }
                LucidSymbol.Network -> {
                    drawCircle(color, 8f, Offset(12f, 12f), style = Stroke(1.6f))
                    path(2f, 12f, 7f, 12f, 10f, 6f, 14f, 18f, 17f, 12f, 22f, 12f)
                }
                LucidSymbol.Search -> {
                    drawCircle(color, 6.5f, Offset(10f, 10f), style = Stroke(1.8f))
                    line(15f, 15f, 21f, 21f)
                }
                LucidSymbol.Document -> {
                    drawRoundRect(
                        color,
                        Offset(5f, 2f),
                        Size(14f, 20f),
                        CornerRadius(2f),
                        style = Stroke(1.8f),
                    )
                    line(8f, 8f, 16f, 8f)
                    line(8f, 12f, 16f, 12f)
                    line(8f, 16f, 13f, 16f)
                }
                LucidSymbol.Folder -> {
                    path(2f, 7f, 2f, 20f, 22f, 20f, 22f, 7f, 12f, 7f, 10f, 4f, 2f, 4f, 2f, 7f)
                }
                LucidSymbol.Shield ->
                    path(12f, 2f, 21f, 6f, 20f, 15f, 12f, 22f, 4f, 15f, 3f, 6f, 12f, 2f)
                LucidSymbol.Settings -> {
                    drawCircle(color, 4f, Offset(12f, 12f), style = Stroke(1.8f))
                    drawCircle(color, 9f, Offset(12f, 12f), style = Stroke(1.8f))
                    for (x in listOf(3f, 21f)) line(x, 10f, x, 14f)
                }
            }
        }
    }
}
