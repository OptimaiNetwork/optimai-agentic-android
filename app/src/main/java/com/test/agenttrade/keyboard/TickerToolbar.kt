package com.test.agenttrade.keyboard

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.agenttrade.data.Fmt
import com.test.agenttrade.data.MarketItem
import com.test.agenttrade.ui.components.OptimAILogo
import com.test.agenttrade.ui.components.RemoteIconCircle
import com.test.agenttrade.ui.components.TrendTone
import com.test.agenttrade.ui.components.TrustWalletAssets
import com.test.agenttrade.ui.components.color
import com.test.agenttrade.ui.components.markdown
import com.test.agenttrade.ui.components.plainClickable
import com.test.agenttrade.ui.theme.DS
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Process-wide marquee epoch: the keyboard's views are rebuilt freely (new
 * field, focus changes), and deriving the strip's offset from time since
 * this one fixed moment means a rebuild resumes the same continuous
 * position instead of jumping back to the start.
 */
object MarqueeClock {
    val epochNanos: Long = System.nanoTime()
    fun seconds(now: Long = System.nanoTime()): Double = (now - epochNanos) / 1e9
}

private const val ROW_HEIGHT = 56
private const val CHIP_WIDTH = 122
private const val CHIP_PADDING_H = 8
private const val CHIP_SPACING = 8
private const val STRIP_PADDING = 8
private const val PIXELS_PER_SECOND = 24.0

/**
 * The toolbar above the keys (the design/keyboard mocks): the ticker strip,
 * the stock banner + trading card, or the ask answer — the Android port of
 * the iOS `StockTickerToolbar` view, in the app's dark DS colors.
 */
@Composable
fun TickerToolbar(model: TickerToolbarModel, feedback: KeyFeedback) {
    Column(Modifier.fillMaxWidth()) {
        if (model.isNewsPreviewActive) {
            NewsPreview(model)
        } else {
            TopRow(model, feedback)
            AnimatedVisibility(
                visible = model.cardItem != null && model.isTradingCardOpen,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
            ) {
                model.cardItem?.let { TradingCard(model, it, feedback) }
            }
        }
    }
}

// MARK: - Top row

@Composable
private fun TopRow(model: TickerToolbarModel, feedback: KeyFeedback) {
    Row(
        Modifier.fillMaxWidth().height(ROW_HEIGHT.dp).padding(horizontal = STRIP_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING.dp),
    ) {
        val item = model.cardItem
        // The brand chip only shows beside the marquee; an open banner takes
        // the whole row. Always a tap target, so a tap here never falls
        // through to a chip scrolled underneath.
        if (item == null) {
            Row(
                Modifier.fillMaxHeight().plainClickable {
                    if (model.isAskable) {
                        feedback.tap()
                        model.startAskingNews()
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING.dp),
            ) {
                BrandChip(model.isAskable)
                Box(Modifier.width(1.dp).height(24.dp).background(DS.colors.border))
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            AnimatedContent(
                targetState = item,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                contentKey = { it?.symbol },
                label = "topRow",
            ) { current ->
                if (current != null) DetailRow(current, model, feedback) else Marquee(model, feedback)
            }
        }
    }
}

/**
 * Plain logo + wordmark; once there's an "@optimai …" mention to ask about
 * it gains a capsule fill and a bright arc sweeping around its border.
 */
@Composable
private fun BrandChip(isAskable: Boolean) {
    val c = DS.colors
    val transition = rememberInfiniteTransition(label = "ring")
    val degrees by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "deg")
    val accent = c.accentBrand
    Row(
        Modifier
            .then(
                if (isAskable) {
                    Modifier
                        .drawBehind {
                            val r = CornerRadius(size.height / 2)
                            drawRoundRect(c.secondary, cornerRadius = r)
                            val shader = SweepGradient(
                                size.width / 2, size.height / 2,
                                intArrayOf(Color.Transparent.toArgb(), Color.Transparent.toArgb(), accent.toArgb(), Color.Transparent.toArgb(), Color.Transparent.toArgb()),
                                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
                            ).apply { setLocalMatrix(Matrix().apply { setRotate(degrees, size.width / 2, size.height / 2) }) }
                            val stroke = 2.dp.toPx()
                            drawRoundRect(
                                ShaderBrush(shader),
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(size.width - stroke, size.height - stroke),
                                cornerRadius = CornerRadius((size.height - stroke) / 2),
                                style = Stroke(stroke),
                            )
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OptimAILogo(Modifier.size(16.dp), color = c.accentBrand)
        Text("OptimAI", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.foreground, maxLines = 1, softWrap = false)
    }
}

/**
 * The scrolling ticker strip: a plain offset recomputed every frame from
 * elapsed time (so taps always land on what's on screen), with the list
 * duplicated back-to-back and the edges faded.
 */
@Composable
private fun Marquee(model: TickerToolbarModel, feedback: KeyFeedback) {
    val items = model.items
    if (items.isEmpty()) {
        Text("Agentic Trading", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = DS.colors.mutedForeground)
        return
    }
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(Unit) {
        while (true) androidx.compose.runtime.withFrameNanos { now = System.nanoTime() }
    }
    val oneSetWidth = items.size * (CHIP_WIDTH + 2 * CHIP_PADDING_H + CHIP_SPACING)
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent, 0.06f to Color.Black, 0.9f to Color.Black, 1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .offset {
                    val elapsed = MarqueeClock.seconds(now)
                    val x = -((elapsed * PIXELS_PER_SECOND) % oneSetWidth)
                    IntOffset((x * density).toInt(), 0)
                },
            horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (items + items).forEach { item ->
                TickerChip(item) {
                    feedback.tap()
                    model.tapChip(item)
                }
            }
        }
    }
}

@Composable
private fun TickerChip(item: MarketItem, onTap: () -> Unit) {
    val c = DS.colors
    val shape = RoundedCornerShape(12.dp)
    val tone = TrendTone.from(item.priceChangePct24h ?: 0.0)
    Row(
        Modifier
            .clip(shape)
            .background(c.secondary, shape)
            .border(1.dp, c.border, shape)
            .plainClickable(onClick = onTap)
            .padding(horizontal = CHIP_PADDING_H.dp, vertical = 6.dp)
            .width(CHIP_WIDTH.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RemoteIconCircle(item.logoUrl, 22.dp, item.ticker)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(item.ticker, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.foreground, maxLines = 1, lineHeight = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(TickerToolbarModel.priceText(item.referencePrice), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = c.mutedForeground, maxLines = 1, lineHeight = 12.sp)
                Text(TickerToolbarModel.changeText(item.priceChangePct24h), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = tone.color(), maxLines = 1, lineHeight = 12.sp, softWrap = false, overflow = TextOverflow.Clip)
            }
        }
    }
}

// MARK: - Detail row (banner)

/** Ticker, name, price, change and a sparkline, with ✕ to close. */
@Composable
private fun DetailRow(item: MarketItem, model: TickerToolbarModel, feedback: KeyFeedback) {
    val c = DS.colors
    val shape = RoundedCornerShape(12.dp)
    val tone = TrendTone.from(item.priceChangePct24h ?: 0.0)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.card, shape).border(1.dp, c.border, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RemoteIconCircle(item.logoUrl, 28.dp, item.ticker)
        Column(Modifier.weight(1f)) {
            Text(item.ticker, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.foreground, lineHeight = 15.sp)
            Text(TickerToolbarModel.displayName(item), fontSize = 10.sp, color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(TickerToolbarModel.priceText(item.referencePrice), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.foreground, lineHeight = 15.sp)
            Text(TickerToolbarModel.changeText(item.priceChangePct24h), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = tone.color(), lineHeight = 12.sp)
        }
        val points = item.sparkline
        if (points != null && points.size > 1) {
            val color = tone.color()
            Canvas(Modifier.width(44.dp).height(20.dp)) {
                drawPath(midpointSparkline(points, size), color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(c.secondary).plainClickable {
                feedback.tap()
                model.closeCard()
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, "Close", Modifier.size(11.dp), tint = c.mutedForeground) }
    }
}

/**
 * Min/max-normalized, drawn as a smooth curve through the midpoints between
 * samples — a noisy 24h series reads as a trend, not a row of spikes.
 */
private fun midpointSparkline(values: List<Double>, size: Size): Path {
    val path = Path()
    val lo = values.min()
    val hi = values.max()
    val range = hi - lo
    val step = size.width / (values.size - 1)
    fun point(i: Int): Offset {
        val norm = if (range > 0) (values[i] - lo) / range else 0.5
        return Offset(i * step, size.height - (norm * size.height).toFloat())
    }
    val first = point(0)
    path.moveTo(first.x, first.y)
    for (i in 1 until values.size) {
        val prev = point(i - 1)
        val cur = point(i)
        path.quadraticTo(prev.x, prev.y, (prev.x + cur.x) / 2, (prev.y + cur.y) / 2)
    }
    val last = point(values.size - 1)
    path.lineTo(last.x, last.y)
    return path
}

// MARK: - Trading card

/**
 * From/To boxes (left spends, right receives — only which asset sits where
 * flips with the side), a rate/slippage/fee line and the Buy/Sell CTA, or
 * the numpad while the amount is being typed.
 */
@Composable
private fun TradingCard(model: TickerToolbarModel, item: MarketItem, feedback: KeyFeedback) {
    val c = DS.colors
    val buy = model.cardSide == TradingCardSide.BUY
    val receive = model.receiveAmount()
    val usdValue = if (buy) model.payAmount else receive
    val stockLogo = item.logoUrl
    val payText = if (model.isKeypadActive) model.amountDraft.ifEmpty { "0" } else Fmt.number(model.payAmount, if (buy) 0 else 4)

    Column(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TradingCardBox(
                label = "From",
                balance = if (buy) model.payBalanceText else model.heldText(item.symbol),
                amount = payText,
                symbol = if (buy) "USDT" else item.symbol,
                logo = if (buy) TrustWalletAssets.usdtOnBsc else stockLogo,
                editing = model.isKeypadActive,
                modifier = Modifier.weight(1f).plainClickable {
                    feedback.tap()
                    model.beginEditingAmount()
                },
            )
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(c.secondary).border(1.dp, c.border, CircleShape).plainClickable {
                    feedback.tap()
                    model.swapSide()
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.SwapHoriz, "Switch buy / sell", Modifier.size(15.dp), tint = c.accentBrand) }
            TradingCardBox(
                label = "To",
                balance = if (buy) model.heldText(item.symbol) else model.payBalanceText,
                amount = Fmt.number(receive, if (buy) 4 else 2),
                symbol = if (buy) item.symbol else "USDT",
                logo = if (buy) stockLogo else TrustWalletAssets.usdtOnBsc,
                editing = false,
                modifier = Modifier.weight(1f),
            )
        }

        if (model.isKeypadActive) {
            NumericKeypad(model, feedback)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoCell("1 ${item.symbol} = ${TickerToolbarModel.priceText(item.referencePrice)}", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(12.dp).background(c.border))
                InfoCell("Slippage 0.5%", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(12.dp).background(c.border))
                // Illustrative only — a real fee comes back with a real quote.
                InfoCell("Est. fee ~${Fmt.usd(usdValue * 0.001)}", Modifier.weight(1f))
            }
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier.fillMaxWidth().clip(shape).background(if (buy) c.primary else c.destructive, shape)
                    .plainClickable {
                        feedback.tap()
                        model.buy()
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (buy) "Buy ${item.ticker}" else "Sell ${item.ticker}",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (buy) c.primaryForeground else c.destructiveForeground,
                )
            }
        }
    }
}

@Composable
private fun InfoCell(text: String, modifier: Modifier) {
    Text(
        text, modifier, fontSize = 10.sp, color = DS.colors.mutedForeground, maxLines = 1,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TradingCardBox(label: String, balance: String?, amount: String, symbol: String, logo: String?, editing: Boolean, modifier: Modifier) {
    val c = DS.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).background(c.secondary, shape)
            .border(1.5.dp, if (editing) c.accentBrand else Color.Transparent, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 11.sp, color = c.mutedForeground)
            Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
            if (balance != null) Text(balance, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            AutoShrinkText(amount, TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.foreground), minScale = 0.7f, modifier = Modifier.weight(1f, fill = false))
            if (editing) BlinkingCaret()
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RemoteIconCircle(logo, 14.dp, symbol)
            Text(symbol, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.mutedForeground, maxLines = 1)
        }
    }
}

/** One line that scales down (to `minScale`) instead of truncating. */
@Composable
fun AutoShrinkText(text: String, style: TextStyle, minScale: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val full = measurer.measure(text, style, maxLines = 1, softWrap = false)
        val available = with(density) { maxWidth.toPx() }
        val scale = if (full.size.width > available && available > 0) max(minScale, available / full.size.width) else 1f
        Text(text, style = style.copy(fontSize = style.fontSize * scale), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

/** The caret after the "From" amount while the numpad is open. */
@Composable
private fun BlinkingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "blink")
    Box(
        Modifier.width(2.dp).height(20.dp)
            .graphicsLayer { alpha = if (phase < 0.5f) 1f else 0f }
            .background(DS.colors.accentBrand, RoundedCornerShape(1.dp)),
    )
}

/**
 * A self-drawn numpad — the keyboard can't summon another keyboard for its
 * own field, so this is how an arbitrary amount gets typed.
 */
@Composable
private fun NumericKeypad(model: TickerToolbarModel, feedback: KeyFeedback) {
    val c = DS.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("123", "456", "789", ".0⌫").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { ch ->
                    val key = ch.toString()
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier.weight(1f).clip(shape).background(c.secondary, shape).plainClickable {
                            feedback.press(if (key == "⌫") KeyAction.Backspace else KeyAction.Character(key))
                            model.keypadKey(key)
                        }.padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == "⌫") {
                            Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete", Modifier.size(17.dp).padding(vertical = 0.5.dp), tint = c.foreground)
                        } else {
                            Text(key, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.foreground, lineHeight = 19.sp)
                        }
                    }
                }
            }
        }
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier.fillMaxWidth().clip(shape).background(c.primary, shape).plainClickable {
                feedback.tap()
                model.keypadDone()
            }.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Done", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.primaryForeground) }
    }
}

// MARK: - Ask answer

/** Replaces the whole toolbar (and the keys) while open. */
@Composable
private fun NewsPreview(model: TickerToolbarModel) {
    val c = DS.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(8.dp).clip(shape).background(c.card, shape).border(1.dp, c.border, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OptimAILogo(Modifier.size(14.dp), color = c.accentBrand)
            Text("OptimAI", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.foreground)
            model.activeNewsQuery?.let {
                Text(
                    it.replace("@optimai", "", ignoreCase = true).trim(),
                    fontSize = 11.sp, color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(c.secondary).plainClickable { model.closeNewsPreview() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "Close", Modifier.size(11.dp), tint = c.mutedForeground) }
        }
        // A fixed height, so a short answer doesn't collapse the keyboard.
        Box(Modifier.fillMaxWidth().height(220.dp).verticalScroll(rememberScrollState())) {
            when {
                model.isLoadingNews -> NewsThinking(model.activeNewsQuery.orEmpty())
                model.newsAnswer != null -> Text(markdown(model.newsAnswer!!, c.accentBrand), fontSize = 13.sp, color = c.foreground, lineHeight = 17.sp)
                model.newsError != null -> Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    Text(model.newsError!!, fontSize = 13.sp, color = c.mutedForeground)
                }
            }
        }
    }
}

/**
 * While the answer loads: a ring orbits the OptimAI mark, three dots ripple
 * beside a shimmering status line that steps through what it's doing, and
 * skeleton lines stand in for the answer to come.
 */
@Composable
private fun NewsThinking(query: String) {
    val c = DS.colors
    val start = remember { System.nanoTime() }
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(Unit) { while (true) androidx.compose.runtime.withFrameNanos { now = System.nanoTime() } }
    val elapsed = (now - start) / 1e9
    val steps = remember(query) { thinkingSteps(query) }
    val step = minOf((elapsed / 2.2).toInt(), steps.size - 1)

    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.height(28.dp).clipToBounds(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(26.dp).background(c.accentBrand.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    OptimAILogo(Modifier.size(12.dp), color = c.accentBrand)
                }
                val accent = c.accentBrand
                Canvas(Modifier.size(30.dp)) {
                    val shader = SweepGradient(size.width / 2, size.height / 2, intArrayOf(accent.copy(alpha = 0f).toArgb(), accent.toArgb()), null)
                        .apply { setLocalMatrix(Matrix().apply { setRotate(((elapsed * 300) % 360).toFloat(), size.width / 2, size.height / 2) }) }
                    drawCircle(ShaderBrush(shader), radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            ThinkingDots(elapsed)
            AnimatedContent(
                targetState = step,
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "step",
            ) { s ->
                val phase = phase(elapsed, 1.6)
                Text(
                    steps[s],
                    style = TextStyle(
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        brush = Brush.horizontalGradient(
                            0f to c.mutedForeground, 0.35f to c.mutedForeground, 0.5f to c.foreground, 0.65f to c.mutedForeground, 1f to c.mutedForeground,
                            startX = (phase - 0.5f) * 400f, endX = (phase + 0.5f) * 400f,
                        ),
                    ),
                    maxLines = 1,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf(1f, 0.93f, 0.97f, 0.58f).forEachIndexed { i, w ->
                val p = phase(elapsed - i * 0.12, 1.6)
                Box(
                    Modifier.fillMaxWidth(w).height(9.dp).clip(CircleShape).background(c.secondary)
                        .drawBehind {
                            drawRect(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, c.foreground.copy(alpha = 0.10f), Color.Transparent),
                                    startX = (p - 0.35f) * size.width, endX = (p + 0.35f) * size.width,
                                ),
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun ThinkingDots(time: Double) {
    val accent = DS.colors.accentBrand
    Canvas(Modifier.width(24.dp).height(12.dp)) {
        for (i in 0 until 3) {
            val wave = max(0.0, sin(time * 2 * PI / 1.2 - i * 0.7)).toFloat()
            val r = 2.75.dp.toPx() * (0.85f + 0.25f * wave)
            val x = 2.75.dp.toPx() + i * (5.5.dp.toPx() + 3.5.dp.toPx())
            drawCircle(accent.copy(alpha = 0.35f + 0.65f * wave), r, Offset(x, size.height / 2 - 3.dp.toPx() * wave))
        }
    }
}

/** -0.5 → 1.5 once per `period`, so a band starts and ends off-screen. */
private fun phase(time: Double, period: Double): Float {
    val cycle = (time % period) / period
    return ((if (cycle < 0) cycle + 1 else cycle) * 2 - 0.5).toFloat()
}

private fun thinkingSteps(query: String): List<String> {
    val text = query.lowercase()
    fun mentions(words: List<String>) = words.any { text.contains(it) }
    return when {
        mentions(listOf("news", "latest", "earnings", "why")) -> listOf("Searching the latest news", "Reading the sources", "Writing a short answer")
        mentions(listOf("rsi", "macd", "technical", "overbought", "oversold", "support", "resistance")) -> listOf("Reading the indicators", "Checking the chart", "Writing a short answer")
        mentions(listOf("price", "doing", "today")) -> listOf("Checking the latest prices", "Looking at today's move", "Writing a short answer")
        else -> listOf("Thinking", "Searching the web", "Writing a short answer")
    }
}
