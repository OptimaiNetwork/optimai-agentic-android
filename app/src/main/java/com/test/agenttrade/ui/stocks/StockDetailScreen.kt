package com.test.agenttrade.ui.stocks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.test.agenttrade.data.Candle
import com.test.agenttrade.data.Fmt
import com.test.agenttrade.data.InfoDetails
import com.test.agenttrade.data.StockApiClient
import com.test.agenttrade.data.StockQuote
import com.test.agenttrade.data.UserFacingError
import com.test.agenttrade.ui.components.ChainLogoImage
import com.test.agenttrade.ui.components.DSBadge
import com.test.agenttrade.ui.components.DSBadgeStyle
import com.test.agenttrade.ui.components.DSButton
import com.test.agenttrade.ui.components.DSButtonSize
import com.test.agenttrade.ui.components.DSButtonStyle
import com.test.agenttrade.ui.components.DSDivider
import com.test.agenttrade.ui.components.DSMenuItem
import com.test.agenttrade.ui.components.Domain
import com.test.agenttrade.ui.components.TrendTone
import com.test.agenttrade.ui.components.areaPath
import com.test.agenttrade.ui.components.color
import com.test.agenttrade.ui.components.dsCard
import com.test.agenttrade.ui.components.monotonePath
import com.test.agenttrade.ui.components.plainClickable
import com.test.agenttrade.ui.components.plotPoints
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import com.test.agenttrade.ui.theme.DSRadius
import com.test.agenttrade.ui.theme.DSSpacing
import com.test.agenttrade.ui.wallet.DSSheet
import com.test.agenttrade.ui.wallet.openUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The five timeframes, each mapped to the server's candle interval + count. */
enum class ChartRange(val label: String, val fullLabel: String, val interval: String, val limit: Int) {
    HOUR("1H", "1 Hour", "5m", 12),
    DAY("1D", "1 Day", "5m", 288),
    WEEK("1W", "1 Week", "1h", 168),
    MONTH("1M", "1 Month", "4h", 180),
    YEAR("1Y", "1 Year", "1d", 365),
}

/**
 * Stock detail (`design/stock_details_new.png`): logo/ticker header with a
 * close button, the big price + change, market status + range picker, the
 * price chart, the 1H…90D performance row, company details, and "Trade".
 */
@Composable
fun StockDetailScreen(ticker: String, logoUrl: String?, onClose: () -> Unit, onTrade: (Double?) -> Unit) {
    val c = DS.colors
    var range by remember { mutableStateOf(ChartRange.WEEK) }
    var quote by remember { mutableStateOf<StockQuote?>(null) }
    var candles by remember { mutableStateOf<List<Candle>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<InfoDetails?>(null) }
    var detailsLoading by remember { mutableStateOf(false) }
    var detailsFailed by remember { mutableStateOf(false) }
    var liveAttempt by remember { mutableIntStateOf(0) }
    var detailsAttempt by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()

    LaunchedEffect(range, liveAttempt) {
        loading = true
        error = null
        try {
            coroutineScope {
                val q = async { StockApiClient.quote(ticker) }
                val s = async { StockApiClient.candles(ticker, range.interval, range.limit) }
                quote = q.await()
                candles = s.await().candles
            }
        } catch (e: Exception) {
            error = UserFacingError.message(e, "Couldn't load $ticker. Please try again.")
        }
        loading = false
    }
    LaunchedEffect(detailsAttempt) {
        detailsLoading = true
        detailsFailed = false
        details = runCatching { StockApiClient.infoDetails(ticker) }.getOrNull()
        detailsFailed = details == null
        detailsLoading = false
    }

    val ticks = remember(candles) { correctingSpikes(candles.map { it.openTime to it.close }) }
    val price = quote?.referencePrice ?: 0.0
    val change = quote?.priceChangePct24h ?: 0.0
    val chartColor = if (ticks.size > 1 && ticks.last().second < ticks.first().second) c.negative else c.positive

    Column(Modifier.fillMaxSize().background(c.background)) {
        IdentityHeader(ticker, logoUrl ?: details?.logoUrl, details?.name, onClose)
        DSDivider(Modifier.alpha(if (scroll.value > 1) 1f else 0f))
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = DSSpacing.lg).padding(top = DSSpacing.sm, bottom = DSSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.xl),
        ) {
            when {
                loading && candles.isEmpty() -> DetailSkeleton()
                error != null && candles.isEmpty() -> ErrorState(error!!) { liveAttempt++ }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.lg)) {
                        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
                            Row(verticalAlignment = Alignment.Top) {
                                PriceHero(price, change)
                                Spacer(Modifier.weight(1f))
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                                    MarketStatusBadge(quote)
                                    RangePickerButton(range) { range = it }
                                }
                            }
                            PriceChart(ticks, chartColor, Modifier.fillMaxWidth().height(230.dp))
                        }
                        details?.marketData?.let { md ->
                            if (listOf(md.percentChange1h, md.percentChange24h, md.percentChange7d, md.percentChange30d, md.percentChange90d).any { it != null }) {
                                PerformanceRow(md, quote?.priceChangePct24h)
                            }
                        }
                    }
                    MoreDetails(ticker, details, detailsLoading, detailsFailed) { detailsAttempt++ }
                }
            }
        }
        DSDivider()
        DSButton("Trade $ticker", { onTrade(quote?.referencePrice) }, size = DSButtonSize.LG, modifier = Modifier.padding(horizontal = DSSpacing.lg, vertical = DSSpacing.md))
    }
}

/**
 * Runs of bogus prints are snapped to a rolling median (median + MAD over a
 * local window), so one bad tick can't spike the line — the iOS
 * `correctingSpikes`.
 */
private fun correctingSpikes(points: List<Pair<Instant, Double>>, radius: Int = 6, threshold: Double = 3.0): List<Pair<Instant, Double>> {
    if (points.size <= radius * 2) return points
    val prices = points.map { it.second }
    val corrected = prices.toMutableList()
    for (i in prices.indices) {
        val window = prices.subList(max(0, i - radius), min(prices.size, i + radius + 1)).sorted()
        val median = window[window.size / 2]
        val mad = window.map { abs(it - median) }.sorted()[window.size / 2] * 1.4826
        if (mad > 0 && abs(prices[i] - median) > threshold * mad) corrected[i] = median
    }
    return points.mapIndexed { i, p -> p.first to corrected[i] }
}

@Composable
private fun IdentityHeader(ticker: String, logoUrl: String?, subtitle: String?, onClose: () -> Unit) {
    val c = DS.colors
    Row(Modifier.fillMaxWidth().padding(start = DSSpacing.lg, end = DSSpacing.lg, top = DSSpacing.lg, bottom = DSSpacing.md), horizontalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(DSRadius.lg)).background(c.secondary), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ShowChart, null, Modifier.size(22.dp), tint = c.accentBrand)
            if (logoUrl != null) AsyncImage(logoUrl, null, Modifier.fillMaxSize().padding(10.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ticker, style = DSFont.xl2(FontWeight.Bold), color = c.foreground)
            Text(subtitle ?: "Tokenized Stock", style = DSFont.sm(), color = c.mutedForeground)
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.secondary).plainClickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "Close", Modifier.size(17.dp), tint = c.foreground)
        }
    }
}

@Composable
private fun DetailSkeleton() {
    val c = DS.colors
    val t = rememberInfiniteTransition(label = "pulse")
    val alpha by t.animateFloat(1f, 0.45f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Column(Modifier.alpha(alpha), verticalArrangement = Arrangement.spacedBy(DSSpacing.lg)) {
        Row {
            Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
                Box(Modifier.size(190.dp, 44.dp).background(c.secondary, RoundedCornerShape(DSRadius.md)))
                Box(Modifier.size(170.dp, 28.dp).background(c.secondary, RoundedCornerShape(14.dp)))
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                Box(Modifier.size(104.dp, 28.dp).background(c.secondary, RoundedCornerShape(14.dp)))
                Box(Modifier.size(50.dp, 28.dp).background(c.secondary, RoundedCornerShape(14.dp)))
            }
        }
        Canvas(Modifier.fillMaxWidth().height(230.dp)) {
            val ys = listOf(0.62f, 0.55f, 0.6f, 0.42f, 0.48f, 0.35f, 0.4f, 0.25f, 0.32f, 0.2f)
            val pts = ys.mapIndexed { i, y -> Offset(size.width * i / (ys.size - 1), size.height * y) }
            drawPath(monotonePath(pts), c.secondary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            repeat(5) { Box(Modifier.weight(1f).height(58.dp).background(c.secondary, RoundedCornerShape(DSRadius.md))) }
        }
    }
}

/** Big price + signed change, a tinted percentage pill, and "24H". */
@Composable
private fun PriceHero(price: Double, change: Double) {
    val c = DS.colors
    val color = if (change < 0) c.negative else c.positive
    val dollar = price * change / 100
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Text(Fmt.usd(price), style = DSFont.display(40), color = c.foreground)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            Text((if (dollar >= 0) "+" else "-") + Fmt.usd(abs(dollar)), style = DSFont.base(FontWeight.SemiBold), color = color)
            Text(
                (if (change >= 0) "+" else "-") + Fmt.fixed(abs(change), 2) + "%",
                style = DSFont.sm(FontWeight.SemiBold), color = color,
                modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(DSRadius.sm)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Text("24H", style = DSFont.sm(), color = c.mutedForeground)
        }
    }
}

/** "Market Open" / "Closed · reopens in Xh" — US regular hours, server-reported. */
@Composable
private fun MarketStatusBadge(quote: StockQuote?) {
    val c = DS.colors
    val open = quote?.isMarketOpen ?: USMarketHours.isOpen()
    val tint = if (open) c.positive else c.mutedForeground
    val boundary = quote?.nextOpenAt ?: USMarketHours.nextBoundary()
    val hours = Duration.between(Instant.now(), boundary).toHours()
    val text = when {
        hours < 1 -> "<1h"
        hours < 24 -> "${hours}h"
        else -> "${hours / 24}d"
    }
    Row(
        Modifier.clip(CircleShape).background(c.card).border(1.dp, tint.copy(alpha = 0.3f), CircleShape).padding(horizontal = DSSpacing.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(5.dp).background(tint, CircleShape))
        Text(if (open) "Market Open" else "Closed · reopens in $text", style = DSFont.xs(FontWeight.Medium), color = tint, maxLines = 1)
    }
}

@Composable
private fun RangePickerButton(selected: ChartRange, onSelect: (ChartRange) -> Unit) {
    val c = DS.colors
    var show by remember { mutableStateOf(false) }
    Row(
        Modifier.clip(CircleShape).background(c.card).border(1.dp, c.border, CircleShape).plainClickable { show = true }
            .padding(horizontal = DSSpacing.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(selected.label, style = DSFont.xs(FontWeight.SemiBold), color = c.foreground)
        Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(12.dp), tint = c.foreground)
    }
    if (show) {
        DSSheet(onDismiss = { show = false }, title = "Time Range") {
            Column {
                ChartRange.entries.forEachIndexed { i, option ->
                    if (i > 0) DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                    DSMenuItem(onClick = {
                        onSelect(option)
                        show = false
                    }) {
                        Text(option.fullLabel, style = DSFont.base(FontWeight.Medium), color = c.foreground, modifier = Modifier.weight(1f))
                        if (option == selected) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = c.accentBrand)
                    }
                }
            }
        }
    }
}

/**
 * Smoothed price line (moving average, then monotone — never overshoots),
 * an area fill fading to the floor, a dashed line at the range's opening
 * price, and a bubble with the real last price on the last point.
 */
@Composable
private fun PriceChart(ticks: List<Pair<Instant, Double>>, color: Color, modifier: Modifier) {
    val c = DS.colors
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        if (ticks.size < 2) return@Canvas
        val window = max(5, min(21, ticks.size / 12))
        val raw = ticks.map { it.second }
        val smoothed = if (ticks.size > 4) raw.indices.map { i ->
            val slice = raw.subList(max(0, i - window / 2), min(raw.size, i + window / 2 + 1))
            slice.average()
        } else raw
        val lo = smoothed.min()
        val hi = smoothed.max()
        val span = hi - lo
        val domain = if (span > 0) Domain(lo - span * 0.2, hi + span * 0.32) else Domain(lo - 1, hi + 1)
        val rect = Rect(0f, 0f, size.width - 6.dp.toPx(), size.height)
        val pts = plotPoints(smoothed, rect, domain)
        val openY = (rect.bottom - ((raw.first() - domain.lo) / domain.span * rect.height)).toFloat()
        drawLine(c.mutedForeground.copy(alpha = 0.5f), Offset(0f, openY), Offset(size.width, openY), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        val line = monotonePath(pts)
        drawPath(areaPath(line, pts, size.height), Brush.verticalGradient(listOf(color.copy(alpha = 0.24f), color.copy(alpha = 0f)), endY = size.height))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = pts.last()
        drawCircle(color, 4.4.dp.toPx(), last)
        // The bubble shows the real, unsmoothed last price.
        val label = measurer.measure(Fmt.usd(raw.last()), DSFont.xs(FontWeight.Bold).copy(color = color))
        val bw = label.size.width + 16.dp.toPx()
        val bh = label.size.height + 8.dp.toPx()
        val bx = (last.x - bw / 2).coerceIn(0f, size.width - bw)
        val by = (last.y - 8.dp.toPx() - bh).coerceAtLeast(0f)
        drawRoundRect(c.card, Offset(bx, by), androidx.compose.ui.geometry.Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(DSRadius.sm.toPx()))
        drawRoundRect(color, Offset(bx, by), androidx.compose.ui.geometry.Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(DSRadius.sm.toPx()), style = Stroke(1.dp.toPx()))
        drawText(label, topLeft = Offset(bx + 8.dp.toPx(), by + 4.dp.toPx()))
    }
}

@Composable
private fun PerformanceRow(md: InfoDetails.MarketData, change24h: Double?) {
    val c = DS.colors
    val items = listOfNotNull(
        md.percentChange1h?.let { "1H" to it },
        (change24h ?: md.percentChange24h)?.let { "24H" to it },
        md.percentChange7d?.let { "7D" to it },
        md.percentChange30d?.let { "30D" to it },
        md.percentChange90d?.let { "90D" to it },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
        items.forEach { (label, value) ->
            // Rounded first, so -0.03% reads as a flat "0.0%".
            val shown = Math.round(value * 10) / 10.0
            val tone = TrendTone.from(shown)
            Column(
                Modifier.weight(1f).background(if (tone == TrendTone.FLAT) c.secondary else tone.color().copy(alpha = 0.1f), RoundedCornerShape(DSRadius.md))
                    .padding(vertical = DSSpacing.smd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label, style = DSFont.xs(), color = c.mutedForeground)
                Text((if (shown > 0) "+" else "") + Fmt.fixed(if (shown == 0.0) 0.0 else shown, 1) + "%", style = DSFont.sm(FontWeight.Bold), color = tone.color())
            }
        }
    }
}

@Composable
private fun MoreDetails(ticker: String, details: InfoDetails?, loading: Boolean, failed: Boolean, onRetry: () -> Unit) {
    val c = DS.colors
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.lg)) {
        when {
            details != null -> {
                AboutCard(details)
                val md = details.marketData
                if (md?.marketCap != null || md?.volume24h != null || md?.circulatingSupply != null || details.dividendYield != null) MarketDataCard(details)
                if (details.contracts.isNotEmpty()) ChainsCard(details.contracts)
                if (details.websiteUrl != null || details.twitterUrl != null) LinksRow(details)
            }
            loading -> Box(Modifier.fillMaxWidth().padding(vertical = DSSpacing.xl2), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = c.mutedForeground, strokeWidth = 2.dp)
            }
            failed -> Row(
                Modifier.fillMaxWidth().background(c.secondary, RoundedCornerShape(DSRadius.md)).padding(DSSpacing.smd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm),
            ) {
                Icon(Icons.Filled.Info, null, Modifier.size(16.dp), tint = c.mutedForeground)
                Text("No company details available for $ticker.", style = DSFont.xs(), color = c.mutedForeground, modifier = Modifier.weight(1f))
                DSButton("Retry", onRetry, style = DSButtonStyle.GHOST, size = DSButtonSize.SM, foreground = c.accentBrand)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutCard(details: InfoDetails) {
    val c = DS.colors
    var expanded by remember { mutableStateOf(false) }
    val body = details.description ?: details.companyProfileSummary ?: details.coinmarketcapAbout
    Column(Modifier.fillMaxWidth().dsCard().animateContentSize(), verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            Text("Company Overview", style = DSFont.sm(FontWeight.Medium), color = c.foreground, modifier = Modifier.weight(1f))
            DSBadge("Tokenized Stock", DSBadgeStyle.BRAND)
        }
        if (!body.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                Text(body, style = DSFont.xs(), color = c.mutedForeground, maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
                Text(if (expanded) "Show Less" else "Read More", style = DSFont.sm(FontWeight.SemiBold), color = c.accentBrand, modifier = Modifier.plainClickable { expanded = !expanded }.padding(vertical = 6.dp))
            }
        }
        if (details.tags.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                details.tags.take(8).forEach { DSBadge(it) }
            }
        }
    }
}

@Composable
private fun MarketDataCard(details: InfoDetails) {
    val c = DS.colors
    val md = details.marketData
    Column(Modifier.fillMaxWidth().dsCard(), verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        Text("Onchain Market Data", style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DSSpacing.lg)) {
                md?.marketCap?.let { StatCell("Token Market Cap", "$" + Fmt.compactNumber(it)) }
                md?.circulatingSupply?.let { StatCell("Circulating Supply", "${Fmt.compactNumber(it)} ${details.ticker ?: details.symbol}") }
            }
            Box(Modifier.padding(horizontal = DSSpacing.md).width(1.dp).height(80.dp).background(c.border))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DSSpacing.lg)) {
                md?.volume24h?.let { StatCell("Token Volume 24h", "$" + Fmt.compactNumber(it)) }
                details.dividendYield?.let { StatCell("Dividend Yield", Fmt.fixed(it * 100, 2) + "%") }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = DSFont.sm(), color = DS.colors.mutedForeground)
        Text(value, style = DSFont.lg(FontWeight.Bold), color = DS.colors.foreground)
    }
}

@Composable
private fun ChainsCard(contracts: List<InfoDetails.Contract>) {
    val c = DS.colors
    val context = LocalContext.current
    var copied by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Text("Supported Chains", style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
        Column(Modifier.fillMaxWidth().dsCard(padding = DSSpacing.xxs)) {
            contracts.forEachIndexed { i, contract ->
                if (i > 0) DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                DSMenuItem(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Address", contract.address))
                    copied = contract.address
                }) {
                    ChainLogoImage(contract.network, 22.dp)
                    Spacer(Modifier.width(DSSpacing.sm))
                    Text(chainName(contract.network), style = DSFont.sm(FontWeight.Medium), color = c.foreground, modifier = Modifier.weight(1f))
                    Text(
                        if (copied == contract.address) "Copied" else Fmt.shortAddress(contract.address, 6, 4),
                        style = DSFont.mono(12), color = if (copied == contract.address) c.positive else c.mutedForeground,
                    )
                    Spacer(Modifier.width(DSSpacing.sm))
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(12.dp), tint = c.mutedForeground)
                }
            }
        }
    }
}

private fun chainName(network: String) = when (network.uppercase()) {
    "BSC", "BNB", "SMARTCHAIN" -> "BNB Chain"
    "HYPEREVM" -> "HyperEVM"
    else -> network.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun LinksRow(details: InfoDetails) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        details.websiteUrl?.let { url ->
            DSButton("Website", { openUrl(context, url) }, style = DSButtonStyle.OUTLINE, size = DSButtonSize.SM, icon = Icons.Filled.Public, modifier = Modifier.weight(1f))
        }
        details.twitterUrl?.let { url ->
            DSButton("View on X", { openUrl(context, url) }, style = DSButtonStyle.OUTLINE, size = DSButtonSize.SM, icon = Icons.Filled.NorthEast, modifier = Modifier.weight(1f))
        }
    }
}

/** US equities regular hours (Mon–Fri 09:30–16:00 New York), clock-only. */
object USMarketHours {
    private val zone = ZoneId.of("America/New_York")

    fun isOpen(at: Instant = Instant.now()): Boolean {
        val t = ZonedDateTime.ofInstant(at, zone)
        if (t.dayOfWeek.value > 5) return false
        val minutes = t.hour * 60 + t.minute
        return minutes in (9 * 60 + 30) until (16 * 60)
    }

    fun nextBoundary(after: Instant = Instant.now()): Instant {
        val t = ZonedDateTime.ofInstant(after, zone)
        if (isOpen(after)) return t.withHour(16).withMinute(0).withSecond(0).toInstant()
        var candidate = t
        repeat(8) {
            val minutes = candidate.hour * 60 + candidate.minute
            if (candidate.dayOfWeek.value <= 5 && minutes < 9 * 60 + 30) return candidate.withHour(9).withMinute(30).withSecond(0).toInstant()
            candidate = candidate.plusDays(1).withHour(0).withMinute(0)
        }
        return candidate.toInstant()
    }
}

@Suppress("unused")
private val unusedSp = 0.sp
