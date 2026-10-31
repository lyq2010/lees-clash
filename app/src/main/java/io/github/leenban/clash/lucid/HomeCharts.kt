package io.github.leenban.clash.lucid

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeRateSparkline(
    history: List<Long>,
    running: Boolean,
    lineColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val samples = if (history.size >= 2) history.takeLast(14) else emptyList()
    val peak = samples.maxOrNull() ?: 0L
    val liveRatioTarget =
        if (!running || peak <= 0L) 0.18f
        else (samples.last().toFloat() / peak.toFloat()).coerceIn(0.16f, 0.92f)
    val animatedEndRatio by
        animateFloatAsState(liveRatioTarget, tween(260, easing = FastOutSlowInEasing), label = "sparkline-end")

    Canvas(modifier) {
        val leftPad = 2.dp.toPx()
        val rightPad = 5.dp.toPx()
        val w = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val topPad = 4.dp.toPx()
        val bottomPad = 4.dp.toPx()
        val h = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val normalized: List<Float> =
            if (running && peak > 0L && samples.size >= 2) {
                samples.mapIndexed { idx, value ->
                    if (idx == samples.lastIndex) animatedEndRatio
                    else (value.toFloat() / peak.toFloat()).coerceIn(0.12f, 0.90f)
                }
            } else if (running) {
                listOf(0.16f, 0.22f, 0.19f, 0.26f, 0.20f, animatedEndRatio)
            } else {
                listOf(0.14f, 0.16f, 0.15f, 0.18f, 0.16f, 0.15f)
            }

        val points =
            normalized.mapIndexed { index, ratio ->
                val x = leftPad + w * (index.toFloat() / (normalized.size - 1).coerceAtLeast(1))
                val y = topPad + h * (1f - ratio)
                Offset(x, y)
            }

        val curve = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
        }

        val fill = Path().apply {
            addPath(curve)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(
            fill,
            Brush.verticalGradient(
                listOf(accentColor.copy(alpha = if (running) 0.28f else 0.10f), Color.Transparent),
                startY = topPad,
                endY = size.height,
            ),
        )
        drawPath(
            curve,
            color = if (running) lineColor else lineColor.copy(alpha = 0.42f),
            style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round),
        )
        if (running) {
            val end = points.last()
            drawCircle(accentColor.copy(alpha = 0.28f), 5.5.dp.toPx(), end)
            drawCircle(lineColor, 2.8.dp.toPx(), end)
        }
    }
}

private val CONNECTION_SEGMENT_RATIOS =
    floatArrayOf(0.32f, 0.38f, 0.46f, 0.54f, 0.62f, 0.70f, 0.78f, 0.85f, 0.91f, 0.96f, 1.0f, 0.88f)

@Composable
internal fun HomeConnectionLoadBar(
    activeConnections: Int,
    running: Boolean,
    activeColor: Color,
    accentColor: Color,
    dimColor: Color,
    modifier: Modifier = Modifier,
) {
    val targetLoad =
        when {
            !running || activeConnections <= 0 -> 0f
            activeConnections >= 60 -> 1f
            else -> ( kotlin.math.ln((activeConnections + 1).toDouble()) / kotlin.math.ln(61.0) ).toFloat().coerceIn(0.12f, 1f)
        }
    val load by
        animateFloatAsState(targetLoad, tween(260, easing = FastOutSlowInEasing), label = "conn-load")

    Canvas(modifier) {
        val count = CONNECTION_SEGMENT_RATIOS.size
        val barWidth = 4.2.dp.toPx().coerceAtMost(size.width / (count * 1.9f))
        val spacing = (size.width - barWidth * count) / (count - 1).coerceAtLeast(1)
        val activeBars = load * count
        for (i in 0 until count) {
            val barH = size.height * CONNECTION_SEGMENT_RATIOS[i]
            val lit = (activeBars - i).coerceIn(0f, 1f)
            val color =
                if (lit > 0f) {
                    val base = if (i >= count - 3) activeColor else accentColor
                    base.copy(alpha = 0.30f + 0.68f * lit)
                } else {
                    dimColor
                }
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + spacing), size.height - barH),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
internal fun HomeQuotaProgressBar(
    progress: Float,
    activeBrush: Brush,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val clamped by
        animateFloatAsState(progress.coerceIn(0f, 1f), tween(260, easing = FastOutSlowInEasing), label = "quota-bar")

    Canvas(modifier) {
        val barHeight = size.height.coerceAtMost(7.dp.toPx())
        val top = (size.height - barHeight) / 2f
        val radius = CornerRadius(barHeight / 2f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, barHeight),
            cornerRadius = radius,
        )
        if (clamped > 0.005f) {
            val fillWidth = (size.width * clamped).coerceAtLeast(barHeight)
            drawRoundRect(
                brush = activeBrush,
                topLeft = Offset(0f, top),
                size = Size(fillWidth.coerceAtMost(size.width), barHeight),
                cornerRadius = radius,
            )
        }
    }
}
