package com.test.agenttrade.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The OptimAI icon mark, traced from design/logo/logo_optimai.svg (viewBox
 * 0 0 504.395 440.635) — the same two path strings the iOS
 * `OptimAILogoMark` parses.
 */
object OptimAIMark {
    const val VIEW_W = 504.395f
    const val VIEW_H = 440.635f

    enum class Part { ALL, LEG, ARCH }

    private val pathData = listOf(
        "M492.875 440.594H427.704C424.211 440.594 420.986 438.711 419.105 435.616L209.752 72.8607C207.737 69.4968 207.737 65.4602 209.752 61.9619L242.405 5.44953C246.57 -1.81635 257.051 -1.81635 261.217 5.44953L485.619 394.308L502.55 423.641C506.85 431.041 501.475 440.46 492.875 440.46V440.594Z",
        "M316.31 440.594C315.235 440.998 314.026 441.133 312.816 441.133H247.511C246.302 441.133 245.092 440.998 244.018 440.594C241.599 439.787 239.449 438.172 238.105 435.75L170.65 318.689C166.485 311.423 156.138 311.423 151.972 318.689L85.3237 434.27C83.0394 438.172 78.8738 440.594 74.3052 440.594H11.1502C2.41595 440.594 -3.09332 431.176 1.34097 423.506L18.5406 393.635L127.114 205.799C133.698 193.555 146.463 185.078 161.244 185.078C176.025 185.078 187.715 192.613 194.568 204.05H194.837L322.222 424.986C325.716 431.176 322.491 438.442 316.31 440.594Z",
    )

    /** Parsed once — the splash and header glint redraw the mark every frame. */
    private val parsed: List<Path> = pathData.map { PathParser().parsePathString(it).toPath() }

    /** The mark (or one stroke of it) fitted and centered in `size`. */
    fun path(size: Size, part: Part = Part.ALL): Path {
        val scale = min(size.width / VIEW_W, size.height / VIEW_H)
        val dx = (size.width - VIEW_W * scale) / 2
        val dy = (size.height - VIEW_H * scale) / 2
        val matrix = Matrix().apply {
            translate(dx, dy)
            scale(scale, scale)
        }
        val sources = when (part) {
            Part.ALL -> parsed
            Part.LEG -> listOf(parsed[0])
            Part.ARCH -> listOf(parsed[1])
        }
        val combined = Path()
        sources.forEach { src ->
            val copy = Path().apply { addPath(src) }
            copy.transform(matrix)
            combined.addPath(copy)
        }
        return combined
    }
}

/** Sizable, tintable OptimAI icon. Defaults to brand-ink green. */
@Composable
fun OptimAILogo(modifier: Modifier = Modifier, color: Color = DS.colors.accentBrand) {
    Canvas(modifier) { drawPath(OptimAIMark.path(size), color) }
}

/**
 * The brand icon with a periodic glint: every `period` seconds a diagonal
 * highlight sweeps across the mark (clipped to its shape), the mark glows
 * softly, and a small sparkle flares off the apex.
 */
@Composable
fun OptimAIGlintLogo(
    modifier: Modifier = Modifier,
    color: Color = DS.colors.accentBrand,
    glintColor: Color = Color.White,
    periodSeconds: Float = 5f,
) {
    val transition = rememberInfiniteTransition(label = "glint")
    val t by transition.animateFloat(
        0f, periodSeconds,
        infiniteRepeatable(tween((periodSeconds * 1000).toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(modifier) {
        val mark = OptimAIMark.path(size)
        val sweep = smoothstep(t / 0.9f)
        val sparkle = ((t - 0.45f) / 0.7f).coerceIn(0f, 1f)
        val glow = sin(sweep * PI).toFloat()
        val flare = sin(sparkle * PI).toFloat()
        if (glow > 0.01f) drawPath(mark, glintColor.copy(alpha = 0.25f * glow))
        drawPath(mark, color)
        clipPath(mark) {
            val bandWidth = size.width * 0.45f
            val travel = size.width / 2 + bandWidth
            val cx = size.width / 2 - travel + 2 * travel * sweep
            rotate(25f, Offset(cx, size.height / 2)) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(glintColor.copy(alpha = 0f), glintColor.copy(alpha = 0.85f), glintColor.copy(alpha = 0f)),
                        startX = cx - bandWidth / 2, endX = cx + bandWidth / 2,
                    ),
                    topLeft = Offset(cx - bandWidth / 2, -size.height / 2),
                    size = Size(bandWidth, size.height * 2),
                )
            }
        }
        if (flare > 0.01f) drawSparkle(Offset(size.width * 0.66f, size.height * 0.04f), size.width * 0.2f * flare, 90f * sparkle, glintColor.copy(alpha = flare))
    }
}

/** A four-point sparkle (SF Symbols' "sparkle"). */
fun DrawScope.drawSparkle(center: Offset, radius: Float, rotation: Float, color: Color) {
    val path = Path().apply {
        val r = radius
        val w = radius * 0.28f
        moveTo(center.x, center.y - r)
        quadraticTo(center.x + w * 0.4f, center.y - w * 0.4f, center.x + r, center.y)
        quadraticTo(center.x + w * 0.4f, center.y + w * 0.4f, center.x, center.y + r)
        quadraticTo(center.x - w * 0.4f, center.y + w * 0.4f, center.x - r, center.y)
        quadraticTo(center.x - w * 0.4f, center.y - w * 0.4f, center.x, center.y - r)
        close()
    }
    rotate(rotation, center) { drawPath(path, color) }
}

internal fun smoothstep(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3 - 2 * c)
}

/**
 * Icon mark + "OptimAI" + "AGENTIC TRADING" — the top-left brand header of
 * every tab. The icon is in the foreground color; green is kept for its glint.
 */
@Composable
fun OptimAIWordmark(compact: Boolean = false, glints: Boolean = false) {
    val c = DS.colors
    val iconSize = if (compact) 22.dp else 26.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        if (glints) {
            OptimAIGlintLogo(Modifier.size(iconSize), color = c.foreground, glintColor = c.accentBrand)
        } else {
            OptimAILogo(Modifier.size(iconSize), color = c.foreground)
        }
        Column {
            Text("OptimAI", style = if (compact) DSFont.sm(FontWeight.Bold) else DSFont.base(FontWeight.Bold), color = c.foreground, lineHeight = 18.sp)
            Text(
                "AGENTIC TRADING", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp,
                color = c.foreground.copy(alpha = 0.8f), lineHeight = 11.sp,
            )
        }
    }
}
