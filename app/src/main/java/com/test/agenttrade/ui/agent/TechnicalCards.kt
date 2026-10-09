package com.test.agenttrade.ui.agent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.agenttrade.data.AskProTechnical
import com.test.agenttrade.data.Fmt
import com.test.agenttrade.data.MarketItem
import com.test.agenttrade.data.TechnicalLiquidity
import com.test.agenttrade.data.TechnicalScan
import com.test.agenttrade.data.TechnicalSnapshot
import com.test.agenttrade.ui.AppRouter
import com.test.agenttrade.ui.QuoteTarget
import com.test.agenttrade.ui.components.DSBadge
import com.test.agenttrade.ui.components.DSButton
import com.test.agenttrade.ui.components.DSDivider
import com.test.agenttrade.ui.components.DSIconTile
import com.test.agenttrade.ui.components.Domain
import com.test.agenttrade.ui.components.DrawIn
import com.test.agenttrade.ui.components.RemoteIconCircle
import com.test.agenttrade.ui.components.TrendTone
import com.test.agenttrade.ui.components.areaPath
import com.test.agenttrade.ui.components.color
import com.test.agenttrade.ui.components.dsCard
import com.test.agenttrade.ui.components.monotonePath
import com.test.agenttrade.ui.components.plotPoints
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import com.test.agenttrade.ui.theme.DSRadius
import com.test.agenttrade.ui.theme.DSSpacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Agent chat's `technical` card: a signal gauge, an indicator chart,
 * price levels, performance, the off-hours move, liquidity, or an RSI scan
 * of the user's holdings. Every number is the server's; the card only draws.
 */
@Composable
fun TechnicalCard(card: AskProTechnical, stock: MarketItem?) {
    val c = DS.colors
    val snapshot = card.snapshot
    val ticker = stock?.ticker ?: snapshot?.ticker ?: card.liquidity?.ticker
    val symbol = snapshot?.symbol ?: card.liquidity?.symbol ?: stock?.symbol
    Column(Modifier.fillMaxWidth().dsCard(radius = DSRadius.xl), verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        // Header
        if (card.kind == "holdings_scan") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
                DSIconTile(Icons.Filled.BarChart, c.accentBrand)
                Column(Modifier.weight(1f)) {
                    Text("Your holdings", style = DSFont.base(FontWeight.Bold), color = c.foreground)
                    Text("RSI (14) · lowest first", style = DSFont.xs(), color = c.mutedForeground)
                }
                card.scan?.interval?.let { DSBadge(it.uppercase()) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
                RemoteIconCircle(stock?.logoUrl, 36.dp, ticker ?: "?")
                Column(Modifier.weight(1f)) {
                    Text(ticker ?: "—", style = DSFont.base(FontWeight.Bold), color = c.foreground)
                    Text(kindTitle(card), style = DSFont.xs(), color = c.mutedForeground, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    (snapshot?.price ?: stock?.referencePrice)?.let { Text(Fmt.usd(it), style = DSFont.display(17), color = c.foreground) }
                    (if (card.kind == "off_hours") "1h" else snapshot?.interval)?.let { DSBadge(it.uppercase()) }
                }
            }
        }

        // Content
        when (card.kind) {
            "liquidity" -> card.liquidity?.let { LiquidityView(it) }
            "holdings_scan" -> card.scan?.let { ScanList(it) }
            "off_hours" -> {
                val move = snapshot?.offHours
                if (move?.changePercent != null) OffHoursView(move) else NotEnoughHistory("No recent off-hours trading.")
            }
            else -> if (snapshot != null && snapshot.isReady) SnapshotContent(card, snapshot) else NotEnoughHistory("${snapshot?.candleCount ?: 0} of 20 candles so far.")
        }

        // Footer
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            Text(
                when (card.kind) {
                    "holdings_scan" -> "From each token's own price · Not financial advice"
                    "liquidity" -> "Price-only quotes, live · Not financial advice"
                    else -> "Based on ${ticker ?: "the"} token price · Not financial advice"
                },
                fontSize = 10.5.sp, color = c.mutedForeground,
            )
            if (card.kind != "holdings_scan" && ticker != null) {
                DSButton("Trade $ticker", { AppRouter.presentQuote(QuoteTarget(ticker, currentPrice = snapshot?.price ?: stock?.referencePrice)) })
            }
        }
    }
}

private fun kindTitle(card: AskProTechnical) = when (card.kind) {
    "indicator" -> when (card.indicator) {
        "macd" -> "MACD (12, 26, 9)"
        "bollinger" -> "Bollinger Bands (20, 2)"
        else -> "RSI (14)"
    }
    "levels" -> "Key levels"
    "performance" -> "Performance"
    "off_hours" -> "Off-hours move"
    "liquidity" -> "Liquidity"
    else -> "Technical summary"
}

@Composable
private fun SnapshotContent(card: AskProTechnical, s: TechnicalSnapshot) {
    when (card.kind) {
        "indicator" -> when (card.indicator) {
            "macd" -> s.macd?.let { MacdChart(s.series, it) }
            "bollinger" -> s.bollinger?.let { BollingerChart(s.series, it) }
            else -> s.rsi?.let { RsiChart(s.series, it) }
        }
        "levels" -> s.levels?.let { LevelsChart(s.series, it) }
        "performance" -> s.performance?.let { PerformanceView(it) }
        else -> s.summary?.let { SignalGauge(it) }
    }
}

@Composable
private fun biasColor(bias: String): Color = when (bias) {
    "bullish" -> DS.colors.positive
    "bearish" -> DS.colors.negative
    else -> DS.colors.mutedForeground
}

@Composable
private fun stateColor(state: String): Color = when (state) {
    "oversold" -> DS.colors.positive
    "overbought" -> DS.colors.negative
    else -> DS.colors.accentBrand
}

// MARK: - Gauge

/** Bearish → neutral → bullish, the needle sweeping to the summary's score. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalGauge(summary: TechnicalSnapshot.Summary) {
    val c = DS.colors
    val angle = remember { Animatable(270f) }
    LaunchedEffect(summary.score) { angle.animateTo(270f + summary.score * 0.9f, spring(dampingRatio = 0.62f, stiffness = 60f)) }
    val tint = biasColor(if (summary.score > 10) "bullish" else if (summary.score < -10) "bearish" else "neutral")
    val label = when (summary.label) {
        "bullish" -> "Bullish"
        "lean_bullish" -> "Leaning bullish"
        "lean_bearish" -> "Leaning bearish"
        "bearish" -> "Bearish"
        else -> "Neutral"
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Canvas(Modifier.fillMaxWidth().padding(horizontal = DSSpacing.xl2).height(104.dp)) {
            val radius = minOf(size.width / 2, size.height) - 6.dp.toPx()
            val center = Offset(size.width / 2, size.height)
            val tl = Offset(center.x - radius, center.y - radius)
            val sz = Size(radius * 2, radius * 2)
            val stroke = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(c.negative, 180f, 52f, false, tl, sz, style = stroke)
            drawArc(c.mutedForeground.copy(alpha = 0.45f), 238f, 64f, false, tl, sz, style = stroke)
            drawArc(c.positive, 308f, 52f, false, tl, sz, style = stroke)
            val rad = Math.toRadians(angle.value.toDouble())
            val needle = radius * 0.74f
            drawLine(c.foreground, center, Offset(center.x + needle * cos(rad).toFloat(), center.y + needle * sin(rad).toFloat()), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(c.foreground, 6.dp.toPx(), center)
        }
        Text(label, style = DSFont.lg(FontWeight.SemiBold), color = tint)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            summary.signals.forEach { s ->
                val bc = biasColor(s.bias)
                Row(Modifier.background(bc.copy(alpha = 0.12f), CircleShape).padding(horizontal = DSSpacing.sm, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.xxs)) {
                    Box(Modifier.size(6.dp).background(bc, CircleShape))
                    Text(s.detail, style = DSFont.xs(FontWeight.Medium), color = c.foreground)
                }
            }
        }
    }
}

// MARK: - Indicator charts

@Composable
private fun StatePill(text: String, color: Color) {
    Text(
        text.split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) },
        fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = color,
        modifier = Modifier.background(color.copy(alpha = 0.12f), CircleShape).padding(horizontal = DSSpacing.sm, vertical = 3.dp),
    )
}

@Composable
private fun RsiChart(series: List<TechnicalSnapshot.Point>, rsi: TechnicalSnapshot.Rsi) {
    val c = DS.colors
    val points = series.mapNotNull { it.rsi }
    val endColor = stateColor(rsi.state)
    val measurer = rememberTextMeasurer()
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            Text(Fmt.fixed(rsi.value, 0), style = DSFont.display(30), color = c.foreground)
            StatePill(rsi.state, stateColor(rsi.state))
            Spacer(Modifier.weight(1f))
            if (abs(rsi.streak) >= 2) Text("${if (rsi.streak > 0) "▲" else "▼"} ${abs(rsi.streak)} in a row", style = DSFont.xs(FontWeight.Medium), color = if (rsi.streak > 0) c.positive else c.negative)
        }
        if (points.size > 1) DrawIn { m ->
            Canvas(m.fillMaxWidth().height(140.dp)) {
                val labelW = 18.dp.toPx()
                val rect = Rect(0f, 0f, size.width - labelW, size.height)
                fun y(v: Double) = rect.bottom - (v / 100.0 * rect.height).toFloat()
                drawRect(c.negative.copy(alpha = 0.1f), Offset(0f, y(100.0)), Size(rect.width, y(70.0) - y(100.0)))
                drawRect(c.positive.copy(alpha = 0.1f), Offset(0f, y(30.0)), Size(rect.width, y(0.0) - y(30.0)))
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                drawLine(c.negative.copy(alpha = 0.6f), Offset(0f, y(70.0)), Offset(rect.width, y(70.0)), 1.dp.toPx(), pathEffect = dash)
                drawLine(c.positive.copy(alpha = 0.6f), Offset(0f, y(30.0)), Offset(rect.width, y(30.0)), 1.dp.toPx(), pathEffect = dash)
                listOf(30.0, 70.0).forEach { v ->
                    val l = measurer.measure("${v.toInt()}", DSFont.sized(9f).copy(color = c.mutedForeground))
                    drawText(l, topLeft = Offset(rect.width + 4.dp.toPx(), y(v) - l.size.height / 2))
                }
                val pts = plotPoints(points, rect, Domain(0.0, 100.0))
                drawPath(monotonePath(pts), c.accentBrand, style = Stroke(2.dp.toPx()))
                drawCircle(endColor, 4.4.dp.toPx(), pts.last())
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp, 3.dp).background(color, CircleShape))
        Text(text, fontSize = 10.sp, color = DS.colors.mutedForeground)
    }
}

@Composable
private fun MacdChart(series: List<TechnicalSnapshot.Point>, macd: TechnicalSnapshot.Macd) {
    val c = DS.colors
    val points = series.filter { it.macdHistogram != null && it.macdSignal != null }
    val crossIndex = macd.crossBarsAgo?.takeIf { points.size > it }?.let { points.size - 1 - it }
    val crossColor = biasColor(macd.cross ?: "neutral")
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            if (macd.cross != null) StatePill(if (macd.cross == "bullish") "bullish cross" else "bearish cross", biasColor(macd.cross))
            else StatePill(if (macd.histogram >= 0) "above signal" else "below signal", if (macd.histogram >= 0) c.positive else c.negative)
            Spacer(Modifier.weight(1f))
            Legend(c.accentBrand, "MACD")
            Legend(c.mutedForeground, "Signal")
        }
        if (points.size > 1) DrawIn { m ->
            Canvas(m.fillMaxWidth().height(140.dp)) {
                val all = points.flatMap { listOf(it.macdHistogram!!, it.macd ?: 0.0, it.macdSignal!!) } + 0.0
                val domain = Domain.padded(all, 0.08)
                val rect = Rect(0f, 0f, size.width, size.height)
                fun y(v: Double) = rect.bottom - ((v - domain.lo) / domain.span * rect.height).toFloat()
                val step = rect.width / points.size
                points.forEachIndexed { i, p ->
                    val h = p.macdHistogram!!
                    val top = minOf(y(h), y(0.0))
                    drawRect((if (h >= 0) c.positive else c.negative).copy(alpha = 0.5f), Offset(i * step + step * 0.15f, top), Size(step * 0.7f, abs(y(h) - y(0.0))))
                }
                val xs = points.indices.map { it * step + step / 2 }
                val macdPts = points.mapIndexed { i, p -> Offset(xs[i], y(p.macd ?: 0.0)) }
                val signalPts = points.mapIndexed { i, p -> Offset(xs[i], y(p.macdSignal!!)) }
                drawPath(monotonePath(signalPts), c.mutedForeground, style = Stroke(1.5.dp.toPx()))
                drawPath(monotonePath(macdPts), c.accentBrand, style = Stroke(2.dp.toPx()))
                crossIndex?.let { drawCircle(crossColor, 5.4.dp.toPx(), macdPts[it]) }
            }
        }
    }
}

@Composable
private fun BollingerChart(series: List<TechnicalSnapshot.Point>, bands: TechnicalSnapshot.Bollinger) {
    val c = DS.colors
    val points = series.filter { it.bbUpper != null && it.bbLower != null }
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            if (bands.squeeze) StatePill("squeeze", c.amber)
            Text("Width ${Fmt.fixed(bands.bandwidthPercent, 1)}% · %B ${Fmt.fixed(bands.percentB, 2)}", style = DSFont.xs(FontWeight.Medium), color = c.mutedForeground)
        }
        if (points.size > 1) DrawIn { m ->
            Canvas(m.fillMaxWidth().height(150.dp)) {
                val domain = Domain.padded(points.flatMap { listOf(it.bbUpper!!, it.bbLower!!, it.close) }, 0.12)
                val rect = Rect(0f, 0f, size.width, size.height)
                val upper = plotPoints(points.map { it.bbUpper!! }, rect, domain)
                val lower = plotPoints(points.map { it.bbLower!! }, rect, domain)
                val band = monotonePath(upper).apply {
                    lower.reversed().forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(band, c.accentBrand.copy(alpha = 0.12f))
                drawPath(monotonePath(plotPoints(points.map { it.bbMiddle ?: it.close }, rect, domain)), c.mutedForeground, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
                drawPath(monotonePath(plotPoints(points.map { it.close }, rect, domain)), c.foreground, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun LevelsChart(series: List<TechnicalSnapshot.Point>, levels: TechnicalSnapshot.Levels) {
    val c = DS.colors
    val measurer = rememberTextMeasurer()
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        if (series.size > 1) DrawIn { m ->
            Canvas(m.fillMaxWidth().height(170.dp)) {
                val domain = Domain.padded(series.map { it.close } + levels.supports + levels.resistances, 0.12)
                val rect = Rect(0f, 0f, size.width, size.height)
                fun y(v: Double) = rect.bottom - ((v - domain.lo) / domain.span * rect.height).toFloat()
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                // Only the nearest level on each side is labelled.
                levels.resistances.forEachIndexed { i, l ->
                    drawLine(c.negative.copy(alpha = if (i == 0) 1f else 0.45f), Offset(0f, y(l)), Offset(size.width, y(l)), 1.dp.toPx(), pathEffect = dash)
                    if (i == 0) measurer.measure("R ${Fmt.usd(l)}", DSFont.sized(9.5f, FontWeight.SemiBold).copy(color = c.negative)).let { drawText(it, topLeft = Offset(0f, y(l) - it.size.height - 2.dp.toPx())) }
                }
                levels.supports.forEachIndexed { i, l ->
                    drawLine(c.positive.copy(alpha = if (i == 0) 1f else 0.45f), Offset(0f, y(l)), Offset(size.width, y(l)), 1.dp.toPx(), pathEffect = dash)
                    if (i == 0) measurer.measure("S ${Fmt.usd(l)}", DSFont.sized(9.5f, FontWeight.SemiBold).copy(color = c.positive)).let { drawText(it, topLeft = Offset(0f, y(l) + 2.dp.toPx())) }
                }
                val emaVals = series.mapNotNull { it.ema20 }
                if (emaVals.size == series.size) drawPath(monotonePath(plotPoints(emaVals, rect, domain)), c.accentBrand.copy(alpha = 0.8f), style = Stroke(1.2.dp.toPx()))
                drawPath(monotonePath(plotPoints(series.map { it.close }, rect, domain)), c.foreground, style = Stroke(2.dp.toPx()))
            }
        }
        // Where the price sits between the window's low and high.
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).align(Alignment.CenterStart).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(c.negative.copy(alpha = 0.35f), c.mutedForeground.copy(alpha = 0.25f), c.positive.copy(alpha = 0.35f)))))
                Box(Modifier.offset(x = (maxWidth - 12.dp) * levels.rangePosition.toFloat().coerceIn(0f, 1f)).size(12.dp).background(c.foreground, CircleShape))
            }
            Row {
                Text("Low ${Fmt.usd(levels.low)}", fontSize = 10.sp, color = c.mutedForeground)
                Spacer(Modifier.weight(1f))
                Text("since ${DateTimeFormatter.ofPattern("MMM d", Locale.US).format(levels.since.atZone(ZoneId.systemDefault()))}", fontSize = 10.sp, color = c.mutedForeground)
                Spacer(Modifier.weight(1f))
                Text("High ${Fmt.usd(levels.high)}", fontSize = 10.sp, color = c.mutedForeground)
            }
        }
    }
}

@Composable
private fun PerformanceView(p: TechnicalSnapshot.Performance) {
    val c = DS.colors
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            listOf("1D" to p.change1d, "7D" to p.change7d, "30D" to p.change30d).forEach { (label, value) ->
                Column(Modifier.weight(1f).background(c.secondary, RoundedCornerShape(DSRadius.md)).padding(DSSpacing.smd), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = DSFont.xs(), color = c.mutedForeground)
                    Text(value?.let { Fmt.signedPercent(it) } ?: "—", style = DSFont.sm(FontWeight.Bold), color = value?.let { TrendTone.from(it).color() } ?: c.mutedForeground)
                }
            }
        }
        if (p.sparkline.size > 1) {
            val tone = TrendTone.from(p.change30d ?: p.change7d ?: 0.0)
            val color = tone.color()
            DrawIn { m ->
                Canvas(m.fillMaxWidth().height(80.dp)) {
                    val pts = plotPoints(p.sparkline, Rect(0f, 0f, size.width, size.height), Domain.padded(p.sparkline, 0.12))
                    val line = monotonePath(pts)
                    drawPath(areaPath(line, pts, size.height), Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
                    drawPath(line, color, style = Stroke(1.8.dp.toPx()))
                }
            }
        }
        p.dailyMovePercent?.let { Text("Typical daily move ±${Fmt.fixed(it, 1)}%", style = DSFont.xs(FontWeight.Medium), color = c.mutedForeground) }
    }
}

@Composable
private fun OffHoursView(move: TechnicalSnapshot.OffHours) {
    val c = DS.colors
    val change = move.changePercent ?: 0.0
    val color = TrendTone.from(change).color()
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            Text(Fmt.signedPercent(change), style = DSFont.display(28), color = color)
            Text(if (move.inProgress) "while the market is closed" else "last closed session", style = DSFont.xs(), color = c.mutedForeground)
            Spacer(Modifier.weight(1f))
            if (move.inProgress) StatePill("live", c.accentBrand)
        }
        if (move.points.size > 1) DrawIn { m ->
            Canvas(m.fillMaxWidth().height(130.dp)) {
                val rect = Rect(0f, 0f, size.width, size.height)
                val step = rect.width / (move.points.size - 1)
                // Closed-market stretches shaded.
                var runStart: Int? = null
                move.points.forEachIndexed { i, p ->
                    if (p.closed && runStart == null) runStart = i
                    val last = i == move.points.size - 1
                    if (runStart != null && (!p.closed || last)) {
                        val end = if (p.closed) i else i - 1
                        drawRect(c.mutedForeground.copy(alpha = 0.12f), Offset(runStart!! * step, 0f), Size((end - runStart!!) * step, size.height))
                        runStart = null
                    }
                }
                drawPath(monotonePath(plotPoints(move.points.map { it.close }, rect, Domain.padded(move.points.map { it.close }, 0.12))), color, style = Stroke(2.dp.toPx()))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Box(Modifier.size(10.dp).background(c.mutedForeground.copy(alpha = 0.25f), RoundedCornerShape(2.dp)))
            Text("US market closed", fontSize = 10.5.sp, color = c.mutedForeground)
            if (move.fromPrice != null && move.toPrice != null) {
                Spacer(Modifier.weight(1f))
                Text("${Fmt.usd(move.fromPrice)} → ${Fmt.usd(move.toPrice)}", fontSize = 10.5.sp, color = c.mutedForeground)
            }
        }
    }
}

@Composable
private fun LiquidityView(report: TechnicalLiquidity) {
    val c = DS.colors
    DrawIn { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            report.tiers.forEach { tier ->
                val size = abs(tier.priceImpactPercent ?: 0.0)
                val color = when {
                    !tier.available -> c.mutedForeground.copy(alpha = 0.3f)
                    size < 0.5 -> c.positive
                    size < 2 -> c.amber
                    else -> c.negative
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
                    Text(if (tier.usd >= 1000) "$${(tier.usd / 1000).toInt()}K" else "$${tier.usd.toInt()}", style = DSFont.sm(FontWeight.SemiBold), color = c.foreground, modifier = Modifier.width(48.dp))
                    BoxWithConstraints(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(c.secondary)) {
                        Box(Modifier.width(maxOf(6.dp, maxWidth * minOf(1.0, size).toFloat())).height(6.dp).background(color, CircleShape))
                    }
                    Text(
                        if (!tier.available || tier.priceImpactPercent == null) "No quote" else if (size < 0.01) "<0.01%" else Fmt.fixed(size, 2) + "%",
                        style = DSFont.xs(FontWeight.SemiBold), color = if (tier.available) c.foreground else c.mutedForeground, modifier = Modifier.width(70.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
            Text("Estimated price impact of a buy", fontSize = 10.5.sp, color = c.mutedForeground)
            report.activityRatio?.let { Text("On-chain activity today: ${Fmt.fixed(it, 2)}× its 7-day average", style = DSFont.xs(FontWeight.Medium), color = c.mutedForeground) }
        }
    }
}

@Composable
private fun ScanList(scan: TechnicalScan) {
    val c = DS.colors
    Column {
        scan.rows.forEachIndexed { i, row ->
            if (i > 0) DSDivider()
            Row(Modifier.padding(vertical = DSSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
                RemoteIconCircle(row.logoUrl, 28.dp, row.ticker)
                Text(row.ticker, style = DSFont.sm(FontWeight.SemiBold), color = c.foreground, modifier = Modifier.width(78.dp), maxLines = 1)
                val rsi = row.rsi
                if (rsi != null) {
                    val sc = stateColor(if (rsi <= 30) "oversold" else if (rsi >= 70) "overbought" else "neutral")
                    BoxWithConstraints(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(c.secondary)) {
                        Box(Modifier.width(maxWidth * 0.3f).height(10.dp).background(c.positive.copy(alpha = 0.25f)))
                        Box(Modifier.offset(x = maxWidth * 0.7f).width(maxWidth * 0.3f).height(10.dp).background(c.negative.copy(alpha = 0.25f)))
                        Box(Modifier.offset(x = (maxWidth - 10.dp) * (rsi / 100).toFloat().coerceIn(0f, 1f)).size(10.dp).background(sc, CircleShape))
                    }
                    Text(Fmt.fixed(rsi, 0), style = DSFont.sm(FontWeight.Bold), color = stateColor(row.state ?: "neutral"), modifier = Modifier.width(26.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                } else {
                    Text("Not enough history", style = DSFont.xs(), color = c.mutedForeground, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NotEnoughHistory(detail: String) {
    val c = DS.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
        DSIconTile(Icons.Outlined.HourglassEmpty)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Not enough history yet", style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
            Text(detail, style = DSFont.xs(), color = c.mutedForeground)
        }
    }
}

@Suppress("unused")
private fun DrawScope.unused() = Unit
