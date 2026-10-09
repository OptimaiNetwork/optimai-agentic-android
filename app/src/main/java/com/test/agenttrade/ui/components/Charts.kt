package com.test.agenttrade.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A value range with `lo < hi` guaranteed. */
data class Domain(val lo: Double, val hi: Double) {
    val span: Double get() = hi - lo

    companion object {
        /** min/max padded by `pad` × range, or ±1 around a flat series. */
        fun padded(values: List<Double>, pad: Double): Domain {
            val lo = values.minOrNull()
            val hi = values.maxOrNull()
            if (lo == null || hi == null || lo >= hi) {
                val v = values.firstOrNull() ?: 0.0
                return Domain(v - 1, v + 1)
            }
            val p = (hi - lo) * pad
            return Domain(lo - p, hi + p)
        }
    }
}

/** Evenly spaced x, value → y inside `rect` against `domain`. */
fun plotPoints(values: List<Double>, rect: Rect, domain: Domain): List<Offset> {
    if (values.isEmpty()) return emptyList()
    val step = if (values.size > 1) rect.width / (values.size - 1) else 0f
    return values.mapIndexed { i, v ->
        val norm = ((v - domain.lo) / domain.span).toFloat()
        Offset(rect.left + i * step, rect.bottom - norm * rect.height)
    }
}

/**
 * A monotone cubic through `points` (Fritsch–Carlson) — Swift Charts'
 * `.monotone` interpolation: smooth, and never overshoots past a local
 * min/max the way a Catmull-Rom spline does.
 */
fun monotonePath(points: List<Offset>, path: Path = Path()): Path {
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    val n = points.size
    val dx = FloatArray(n - 1) { points[it + 1].x - points[it].x }
    val slope = FloatArray(n - 1) { if (dx[it] == 0f) 0f else (points[it + 1].y - points[it].y) / dx[it] }
    val tangent = FloatArray(n)
    tangent[0] = slope[0]
    tangent[n - 1] = slope[n - 2]
    for (i in 1 until n - 1) {
        tangent[i] = if (slope[i - 1] * slope[i] <= 0f) 0f else (slope[i - 1] + slope[i]) / 2f
    }
    for (i in 0 until n - 1) {
        if (slope[i] == 0f) {
            tangent[i] = 0f
            tangent[i + 1] = 0f
            continue
        }
        val a = tangent[i] / slope[i]
        val b = tangent[i + 1] / slope[i]
        val h = a * a + b * b
        if (h > 9f) {
            val t = 3f / kotlin.math.sqrt(h)
            tangent[i] = t * a * slope[i]
            tangent[i + 1] = t * b * slope[i]
        }
    }
    for (i in 0 until n - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val h = dx[i] / 3f
        path.cubicTo(p0.x + h, p0.y + tangent[i] * h, p1.x - h, p1.y - tangent[i + 1] * h, p1.x, p1.y)
    }
    return path
}

/** Straight segments through `points`. */
fun linearPath(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
}

/** Closes a line path down to `floorY`, for an area fill under it. */
fun areaPath(line: Path, points: List<Offset>, floorY: Float): Path = Path().apply {
    addPath(line)
    lineTo(points.last().x, floorY)
    lineTo(points.first().x, floorY)
    close()
}

/** The list rows' sparkline: a thin linear line, domain padded 10%. */
@Composable
fun SparklineView(values: List<Double>, tone: TrendTone, modifier: Modifier = Modifier) {
    val color = tone.color()
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val rect = Rect(0f, 2.dp.toPx(), size.width, size.height - 2.dp.toPx())
        val pts = plotPoints(values, rect, Domain.padded(values, 0.1))
        drawPath(linearPath(pts), color, style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Area + monotone line, domain padded 15% — the chat and share cards' sparkline. */
@Composable
fun AreaSparkline(values: List<Double>, tone: TrendTone, modifier: Modifier = Modifier, lineWidth: Float = 1.6f, pad: Double = 0.15) {
    val color = tone.color()
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val rect = Rect(0f, 0f, size.width, size.height)
        val pts = plotPoints(values, rect, Domain.padded(values, pad))
        val line = monotonePath(pts)
        drawPath(areaPath(line, pts, size.height), Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = lineWidth.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * The portfolio's value line: a dashed start reference, area fill, monotone
 * line, and a dot on the latest point. At least ±1% of the value tall, so a
 * quiet day draws as a gentle line instead of dramatic peaks.
 */
@Composable
fun PortfolioValueChart(values: List<Double>, positive: Color, negative: Color, muted: Color, modifier: Modifier = Modifier) {
    val color = if ((values.lastOrNull() ?: 0.0) < (values.firstOrNull() ?: 0.0)) negative else positive
    DrawIn(modifier) { m ->
        Canvas(m) {
            if (values.size < 2) return@Canvas
            val lo = values.min()
            val hi = values.max()
            val mid = (lo + hi) / 2
            val half = max(max((hi - lo) * 0.6, abs(mid) * 0.01), 1.0)
            val domain = Domain(mid - half, mid + half)
            val rect = Rect(0f, 4.dp.toPx(), size.width, size.height - 4.dp.toPx())
            val pts = plotPoints(values, rect, domain)
            val startY = pts.first().y
            drawLine(muted.copy(alpha = 0.4f), Offset(0f, startY), Offset(size.width, startY), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
            val line = monotonePath(pts)
            drawPath(areaPath(line, pts, size.height), Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, 3.6.dp.toPx(), pts.last())
        }
    }
}

/** Reveals its content left to right once, like a line being drawn. */
@Composable
fun DrawIn(modifier: Modifier = Modifier, durationMillis: Int = 800, content: @Composable (Modifier) -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing)) }
    content(
        modifier.drawWithContent {
            clipRect(right = size.width * progress.value) { this@drawWithContent.drawContent() }
        },
    )
}

/** Splits `values` into a simple min/max pair, for callers that need both. */
fun minMax(values: List<Double>): Pair<Double, Double>? =
    if (values.isEmpty()) null else values.fold(Double.MAX_VALUE to -Double.MAX_VALUE) { (lo, hi), v -> min(lo, v) to max(hi, v) }

val Transparent = Color.Transparent
