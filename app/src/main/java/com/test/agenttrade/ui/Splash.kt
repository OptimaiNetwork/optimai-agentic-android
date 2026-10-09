package com.test.agenttrade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.agenttrade.ui.components.OptimAIMark
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import kotlin.math.min
import kotlin.math.pow

/**
 * Launch splash, ported from the iOS `SplashView`: the OptimAI mark draws its
 * outline, the two strokes slide in and fill with a settle "pop", the mark
 * glides up as "OptimAI" rises in letter by letter and the tagline closes up,
 * then a green light band sweeps across and a soft glow pulses.
 *
 * The clock advances by at most 1/30 s per frame, so a busy first second
 * slows the animation rather than skipping it.
 */
@Composable
fun SplashScreen(onContentReady: () -> Unit, onFinished: () -> Unit) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = -1L
        var contentFired = false
        while (t < 3.1f) {
            withFrameNanos { now ->
                val delta = if (last < 0) 0f else (now - last) / 1e9f
                last = now
                t += delta.coerceIn(0f, 1f / 30f)
            }
            if (!contentFired && t >= 2.6f) {
                contentFired = true
                onContentReady()
            }
        }
        onFinished()
    }
    val c = DS.colors
    val lift = smooth(progress(t, 1.25f, 1.85f))
    Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SplashMark(t, Modifier.offset(y = ((WORDMARK_HEIGHT + 24) / 2f * (1 - lift)).dp))
            Wordmark(t)
        }
    }
}

/** Tall enough for the 34sp wordmark, the 6dp gap and the 12sp tagline without clipping. */
private const val WORDMARK_HEIGHT = 72

private fun progress(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun smooth(x: Float) = x * x * (3 - 2 * x)
private fun easeOut(x: Float) = 1 - (1 - x).pow(4)

@Composable
private fun Wordmark(t: Float) {
    val c = DS.colors
    val tagline = easeOut(progress(t, 1.95f, 2.6f))
    Column(Modifier.height(WORDMARK_HEIGHT.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            "OptimAI".forEachIndexed { i, ch ->
                val p = easeOut(progress(t, 1.45f + 0.05f * i, 1.95f + 0.05f * i))
                Text(
                    ch.toString(), style = DSFont.heading(34), color = c.foreground,
                    modifier = Modifier.alpha(p).offset(y = (14 * (1 - p)).dp).blur((5 * (1 - p)).dp),
                )
            }
        }
        Text(
            "AGENTIC TRADING", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = (2.4f + 6 * (1 - tagline)).sp, color = c.mutedForeground, modifier = Modifier.alpha(tagline),
        )
    }
}

@Composable
private fun SplashMark(t: Float, modifier: Modifier) {
    val c = DS.colors
    val width = 120.dp
    val height = width * (OptimAIMark.VIEW_H / OptimAIMark.VIEW_W)
    Canvas(modifier.size(width, height)) {
        val slide = size.width * 0.08f
        val legIn = easeOut(progress(t, 0f, 1f))
        val archIn = easeOut(progress(t, 0.15f, 1.2f))
        val glow = progress(t, 1.8f, 2.15f) * (1 - progress(t, 2.15f, 2.6f))
        val scale = when {
            t < 1.2f -> 0.94f + 0.04f * smooth(progress(t, 0f, 1.2f))
            t < 1.4f -> 0.98f + 0.04f * smooth(progress(t, 1.2f, 1.4f))
            else -> 1.02f - 0.02f * smooth(progress(t, 1.4f, 1.6f))
        }
        val alpha = progress(t, 0f, 0.2f)
        scale(scale) {
            if (glow > 0f) drawPath(OptimAIMark.path(size), c.accentBrand.copy(alpha = 0.35f * glow * alpha))
            translate(slide * (1 - legIn), -slide * (1 - legIn)) {
                stroke(OptimAIMark.path(size, OptimAIMark.Part.LEG), smooth(progress(t, 0f, 0.85f)), progress(t, 0.7f, 1.2f), 1 - progress(t, 0.85f, 1.3f), alpha, c.foreground, c.accentBrand)
            }
            translate(-slide * (1 - archIn), slide * (1 - archIn)) {
                stroke(OptimAIMark.path(size, OptimAIMark.Part.ARCH), smooth(progress(t, 0.25f, 1.1f)), progress(t, 0.9f, 1.4f), 1 - progress(t, 1.1f, 1.5f), alpha, c.foreground, c.accentBrand)
            }
            val sweep = smooth(progress(t, 1.4f, 2.1f))
            if (sweep > 0f && sweep < 1f) {
                clipPath(OptimAIMark.path(size)) {
                    val band = size.width * 0.6f
                    val travel = size.width / 2 + band
                    val cx = size.width / 2 - travel + 2 * travel * sweep
                    rotate(18f, Offset(cx, size.height / 2)) {
                        drawRect(
                            Brush.horizontalGradient(
                                0f to c.accentBrand.copy(alpha = 0f), 0.42f to c.accentBrand.copy(alpha = 0.55f), 0.5f to Color.White.copy(alpha = 0.9f),
                                0.58f to c.accentBrand.copy(alpha = 0.55f), 1f to c.accentBrand.copy(alpha = 0f),
                                startX = cx - band / 2, endX = cx + band / 2,
                            ),
                            topLeft = Offset(cx - band / 2, -size.height / 2),
                            size = androidx.compose.ui.geometry.Size(band, size.height * 2),
                        )
                    }
                }
            }
        }
    }
}

/** One stroke of the mark: its outline traced to `drawn`, under a fill fading in by `fill`. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.stroke(
    path: Path, drawn: Float, fill: Float, outline: Float, alpha: Float, fillColor: Color, outlineColor: Color,
) {
    drawPath(path, fillColor.copy(alpha = fill * alpha))
    if (outline > 0f && drawn > 0f) {
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        // The mark's paths are single closed contours, so one segment is enough.
        measure.getSegment(0f, measure.length * min(drawn, 1f), partial, true)
        drawPath(partial, outlineColor.copy(alpha = outline * alpha), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
