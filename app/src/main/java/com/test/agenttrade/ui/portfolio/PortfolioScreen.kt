package com.test.agenttrade.ui.portfolio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.agenttrade.data.Fmt
import com.test.agenttrade.data.MarketItem
import com.test.agenttrade.data.StockApiClient
import com.test.agenttrade.data.TrackedTrade
import com.test.agenttrade.data.UserFacingError
import com.test.agenttrade.ui.components.CountBadge
import com.test.agenttrade.ui.components.DSBadge
import com.test.agenttrade.ui.components.DSBadgeStyle
import com.test.agenttrade.ui.components.DSButton
import com.test.agenttrade.ui.components.DSButtonSize
import com.test.agenttrade.ui.components.DSButtonStyle
import com.test.agenttrade.ui.components.DSDivider
import com.test.agenttrade.ui.components.DSIconTile
import com.test.agenttrade.ui.components.DSMenuItem
import com.test.agenttrade.ui.components.PortfolioValueChart
import com.test.agenttrade.ui.components.RemoteIconCircle
import com.test.agenttrade.ui.components.TrendTone
import com.test.agenttrade.ui.components.WithChainBadge
import com.test.agenttrade.ui.components.color
import com.test.agenttrade.ui.components.dsCard
import com.test.agenttrade.ui.components.plainClickable
import com.test.agenttrade.ui.stocks.ErrorState
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import com.test.agenttrade.ui.theme.DSRadius
import com.test.agenttrade.ui.theme.DSRow
import com.test.agenttrade.ui.theme.DSSpacing
import com.test.agenttrade.ui.wallet.ConnectWalletSheet
import com.test.agenttrade.ui.wallet.DSSheet
import com.test.agenttrade.ui.wallet.TabHeader
import com.test.agenttrade.ui.wallet.openUrl
import com.test.agenttrade.wallet.WalletConnectManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One held (or fully exited) symbol, grouped from the wallet's fills. */
data class Position(
    val symbol: String,
    val ticker: String,
    /** Net of every buy's output and every sell's input. */
    val quantity: Double,
    /** Weighted-average cost of every buy (average-cost method). */
    val avgCost: Double,
    val currentPrice: Double,
    val logoUrl: String?,
    val chain: String,
    val sparkline: List<Double>,
) {
    val marketValue get() = quantity * currentPrice
    val costBasis get() = quantity * avgCost
    val unrealizedPnL get() = marketValue - costBasis
    val unrealizedPnLPercent get() = if (costBasis > 0) unrealizedPnL / costBasis else 0.0
    /** Below this the quantity reads "0.0000" — a fully exited position. */
    val isHeld get() = quantity > 0.00005
}

/** The value chart's timeframe: 24H from sparklines, the rest from candles. */
private enum class PortfolioRange(val label: String, val interval: String?, val limit: Int) {
    DAY("24H", null, 0), WEEK("7D", "1h", 168), MONTH("1M", "4h", 180), QUARTER("3M", "1d", 90), YEAR("1Y", "1d", 365)
}

private enum class PortfolioTab(val label: String) { ASSETS("Assets"), TXS("Transactions") }

/**
 * The connected BNB wallet's portfolio (`design/porfolio_trade.png`),
 * grouped client-side from every bStocks fill recorded for it
 * (`/wallet/portfolio`), priced from the bStocks market list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen() {
    val c = DS.colors
    val session by WalletConnectManager.session.collectAsState()
    val address = session?.address
    var tab by remember { mutableStateOf(PortfolioTab.ASSETS) }
    var positions by remember { mutableStateOf<List<Position>>(emptyList()) }
    var allTrades by remember { mutableStateOf<List<TrackedTrade>>(emptyList()) }
    var logos by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var txs by remember { mutableStateOf<List<TrackedTrade>>(emptyList()) }
    var txHasMore by remember { mutableStateOf(false) }
    var txLoading by remember { mutableStateOf(false) }
    var txError by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf(PortfolioRange.YEAR) }
    val history = remember { mutableStateMapOf<PortfolioRange, Map<String, List<Pair<Instant, Double>>>>() }
    var showConnect by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val held = positions.filter { it.isHeld }

    suspend fun loadRange(force: Boolean) {
        val r = range
        val interval = r.interval
        if (interval == null || held.isEmpty()) {
            if (force) history.clear()
            return
        }
        if (!force && history.containsKey(r)) return
        val result = coroutineScope {
            held.map { p ->
                async {
                    val points = runCatching { StockApiClient.candles(p.ticker, interval, r.limit) }.getOrNull()?.candles
                        ?.map { it.openTime to it.close }?.sortedBy { it.first } ?: emptyList()
                    p.symbol to points
                }
            }.awaitAll().toMap()
        }
        if (force) history.clear()
        history[r] = result
    }

    suspend fun loadTransactions(reset: Boolean) {
        val a = address ?: run {
            txs = emptyList()
            return
        }
        txLoading = true
        txError = null
        try {
            val page = StockApiClient.walletTransactions(a, offset = if (reset) 0 else txs.size, limit = 20)
            txs = if (reset) page.transactions else txs + page.transactions
            txHasMore = page.hasMore
        } catch (e: Exception) {
            txError = UserFacingError.message(e, "Couldn't load your transactions. Please try again.")
        }
        txLoading = false
    }

    suspend fun loadAll() {
        val a = address ?: run {
            positions = emptyList()
            allTrades = emptyList()
            txs = emptyList()
            return
        }
        loading = true
        error = null
        coroutineScope {
            val tx = async { loadTransactions(true) }
            try {
                val portfolio = async { StockApiClient.walletPortfolio(a) }
                val market = async { StockApiClient.market(limit = 100) }
                val trades = portfolio.await().trades
                val items = market.await().items
                positions = groupIntoPositions(trades, items)
                allTrades = trades
                logos = items.associate { it.symbol to it.logoUrl }
            } catch (e: Exception) {
                error = UserFacingError.message(e, "Couldn't load your portfolio. Please try again.")
            }
            tx.await()
        }
        loading = false
        loadRange(force = true)
    }

    LaunchedEffect(address, reload) { loadAll() }
    LaunchedEffect(range) { loadRange(force = false) }

    Column(Modifier.fillMaxSize().background(c.background)) {
        Box(Modifier.padding(start = DSSpacing.lg, end = DSSpacing.lg, top = DSSpacing.sm, bottom = DSSpacing.xs)) { TabHeader() }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    loadAll()
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = DSSpacing.lg).padding(top = DSSpacing.sm, bottom = DSSpacing.xl2),
                verticalArrangement = Arrangement.spacedBy(DSSpacing.lg),
            ) {
                when {
                    address == null -> ConnectPrompt { showConnect = true }
                    loading && positions.isEmpty() && error == null -> Box(Modifier.fillMaxWidth().padding(top = DSSpacing.xl3), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = c.mutedForeground, strokeWidth = 2.dp)
                    }
                    error != null && positions.isEmpty() -> Box(Modifier.height(320.dp)) { ErrorState(error!!) { reload++ } }
                    else -> {
                        if (positions.isNotEmpty()) {
                            SummaryCard(positions, held, range, { range = it }, valueSeries(range, held, history))
                            if (held.isNotEmpty()) AllocationCard(held)
                        }
                        TabSwitcher(tab, mapOf(PortfolioTab.ASSETS to positions.size, PortfolioTab.TXS to allTrades.size)) { tab = it }
                        when (tab) {
                            PortfolioTab.ASSETS -> if (positions.isEmpty()) EmptyAssets() else Column {
                                positions.forEachIndexed { i, p ->
                                    if (i > 0) DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                                    PositionRow(p, allTrades.filter { it.symbol == p.symbol })
                                }
                            }
                            PortfolioTab.TXS -> Column(Modifier.heightIn(min = 560.dp), verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
                                TransactionsList(txs, logos, txLoading, txError, txHasMore,
                                    onRetry = { scope.launch { loadTransactions(true) } },
                                    onMore = { scope.launch { loadTransactions(false) } })
                            }
                        }
                    }
                }
            }
        }
    }
    if (showConnect) ConnectWalletSheet { showConnect = false }
}

/**
 * Average-cost: a buy adds its output to quantity and its USDT to cost; a
 * sell only subtracts quantity — what remains still cost what it cost.
 */
fun groupIntoPositions(trades: List<TrackedTrade>, market: List<MarketItem>): List<Position> {
    class Acc(var ticker: String = "", var chain: String = "bnb", var boughtQty: Double = 0.0, var boughtCost: Double = 0.0, var soldQty: Double = 0.0)
    val bySymbol = linkedMapOf<String, Acc>()
    trades.forEach { t ->
        val acc = bySymbol.getOrPut(t.symbol) { Acc() }
        acc.ticker = t.ticker
        acc.chain = t.chain
        if (t.side == "buy") {
            acc.boughtQty += t.outputAmount
            acc.boughtCost += t.inputAmount
        } else {
            acc.soldQty += t.inputAmount
        }
    }
    val marketBySymbol = market.associateBy { it.symbol }
    return bySymbol.mapNotNull { (symbol, acc) ->
        if (acc.boughtQty <= 0) return@mapNotNull null
        val avg = acc.boughtCost / acc.boughtQty
        val item = marketBySymbol[symbol]
        Position(symbol, acc.ticker, maxOf(0.0, acc.boughtQty - acc.soldQty), avg, item?.referencePrice ?: avg, item?.logoUrl, acc.chain, item?.sparkline ?: emptyList())
    }.sortedByDescending { it.marketValue }
}

/**
 * Today's holdings priced at each point of the range, summed — with a
 * 3-point rolling median so one bad print doesn't spike the line.
 */
private fun valueSeries(range: PortfolioRange, held: List<Position>, history: Map<PortfolioRange, Map<String, List<Pair<Instant, Double>>>>): List<Double>? {
    val raw: List<Double> = if (range.interval == null) {
        val charted = held.filter { it.sparkline.size > 1 }
        val length = charted.minOfOrNull { it.sparkline.size } ?: return emptyList()
        val flat = held.filter { it.sparkline.size <= 1 }.sumOf { it.marketValue }
        (0 until length).map { i ->
            charted.fold(flat) { sum, p ->
                val idx = ((i.toDouble() / (length - 1)) * (p.sparkline.size - 1)).roundToInt()
                sum + p.sparkline[idx] * p.quantity
            }
        }
    } else {
        val h = history[range] ?: return null
        val charted = mutableListOf<Pair<Double, List<Pair<Instant, Double>>>>()
        var flat = 0.0
        held.forEach { p ->
            val pts = h[p.symbol]
            if (pts != null && pts.size > 1) charted += p.quantity to pts else flat += p.marketValue
        }
        val grid = charted.maxByOrNull { it.second.size }?.second?.map { it.first } ?: return emptyList()
        val cursors = IntArray(charted.size)
        grid.map { time ->
            var sum = flat
            charted.forEachIndexed { k, (qty, pts) ->
                while (cursors[k] + 1 < pts.size && !pts[cursors[k] + 1].first.isAfter(time)) cursors[k]++
                sum += pts[cursors[k]].second * qty
            }
            sum
        }
    }
    if (raw.size <= 2) return raw
    return raw.indices.map { i -> raw.subList(maxOf(0, i - 1), minOf(raw.size, i + 2)).sorted().let { it[it.size / 2] } }
}

@Composable
private fun ConnectPrompt(onConnect: () -> Unit) {
    val c = DS.colors
    Column(Modifier.fillMaxWidth().padding(top = DSSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.lg)) {
        Canvas(Modifier.size(96.dp)) {
            val stroke = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
            val inset = 6.dp.toPx()
            val s = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(c.accentBrand, -90f, 360f * 0.62f, false, Offset(inset, inset), s, style = stroke)
            drawArc(c.accentBrand.copy(alpha = 0.5f), -90f + 360f * 0.66f, 360f * 0.2f, false, Offset(inset, inset), s, style = stroke)
            drawArc(c.accentBrand.copy(alpha = 0.28f), -90f + 360f * 0.9f, 360f * 0.06f, false, Offset(inset, inset), s, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Text("No Wallet Connected", style = DSFont.lg(FontWeight.SemiBold), color = c.foreground)
            Text(
                "Connect MetaMask or Trust Wallet to track your bStocks on BNB Chain — value, P&L, and allocation in one place.",
                style = DSFont.sm(), color = c.mutedForeground, textAlign = TextAlign.Center,
            )
        }
        DSButton("Connect Wallet", onConnect, size = DSButtonSize.LG, icon = Icons.Filled.AccountBalanceWallet)
    }
}

@Composable
private fun SummaryCard(positions: List<Position>, held: List<Position>, range: PortfolioRange, onRange: (PortfolioRange) -> Unit, series: List<Double>?) {
    val c = DS.colors
    val total = positions.sumOf { it.marketValue }
    val cost = held.sumOf { it.costBasis }
    val pnl = held.sumOf { it.unrealizedPnL }
    val pnlPct = if (cost > 0) pnl / cost else 0.0
    Column(Modifier.fillMaxWidth().dsCard(radius = DSRadius.xl), verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xxs)) {
            Text("Total balance", style = DSFont.sm(), color = c.mutedForeground)
            Text(Fmt.usd(total), style = DSFont.display(36), color = c.foreground)
            if (held.isNotEmpty()) {
                Text(
                    "${if (pnl >= 0) "+" else "-"}${Fmt.usd(abs(pnl))}  ·  ${if (pnlPct >= 0) "+" else ""}${Fmt.percentOfRatio(pnlPct)} all time",
                    style = DSFont.sm(FontWeight.SemiBold), color = TrendTone.from(pnl).color(),
                )
            }
        }
        if (held.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                when {
                    series != null && series.size > 1 -> PortfolioValueChart(series, c.positive, c.negative, c.mutedForeground, Modifier.fillMaxSize())
                    series == null -> CircularProgressIndicator(Modifier.size(22.dp), color = c.mutedForeground, strokeWidth = 2.dp)
                    else -> Text("No price history for this range", style = DSFont.xs(), color = c.mutedForeground)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PortfolioRange.entries.forEach { r ->
                    val selected = r == range
                    Box(
                        Modifier.weight(1f).clip(CircleShape).background(if (selected) c.secondary else Color.Transparent).plainClickable { onRange(r) }.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(r.label, style = DSFont.xs(FontWeight.SemiBold), color = if (selected) c.foreground else c.mutedForeground) }
                }
            }
        }
    }
}

private data class Slice(val label: String, val value: Double, val share: Double, val rank: Int, val logo: String?, val isOther: Boolean)

@Composable
private fun AllocationCard(held: List<Position>) {
    val c = DS.colors
    val sorted = held.sortedByDescending { it.marketValue }
    val total = sorted.sumOf { it.marketValue }
    if (total <= 0) return
    val shown = if (sorted.size <= 5) 5 else 4
    val slices = sorted.take(shown).mapIndexed { i, p -> Slice(p.ticker, p.marketValue, p.marketValue / total, i, p.logoUrl, false) }.toMutableList()
    val rest = sorted.drop(shown).sumOf { it.marketValue }
    if (rest > 0) slices += Slice("Other", rest, rest / total, slices.size, null, true)
    val ramp = listOf(1f, 0.74f, 0.54f, 0.38f, 0.24f)
    fun color(rank: Int) = if (rank < ramp.size) c.accentBrand.copy(alpha = ramp[rank]) else c.mutedForeground.copy(alpha = 0.45f)

    Column(Modifier.fillMaxWidth().dsCard(radius = DSRadius.xl), verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        Text("Allocation", style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.xl)) {
            Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val thickness = size.minDimension * 0.18f
                    val inset = thickness / 2
                    var start = -90f
                    val gap = if (slices.size > 1) 1.5f else 0f
                    slices.forEach { s ->
                        val sweep = (s.share * 360).toFloat()
                        drawArc(color(s.rank), start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, Offset(inset, inset), Size(size.width - thickness, size.height - thickness), style = Stroke(thickness))
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${held.size}", style = DSFont.display(22), color = c.foreground)
                    Text(if (held.size == 1) "Asset" else "Assets", fontSize = 11.sp, color = c.mutedForeground)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
                slices.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                        Box(Modifier.size(3.dp, 18.dp).background(color(s.rank), CircleShape))
                        if (s.isOther) {
                            Box(Modifier.size(18.dp).background(c.secondary, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.MoreHoriz, null, Modifier.size(11.dp), tint = c.mutedForeground)
                            }
                        } else {
                            RemoteIconCircle(s.logo, 18.dp, s.label)
                        }
                        Text(s.label, style = DSFont.xs(FontWeight.SemiBold), color = c.foreground, modifier = Modifier.weight(1f))
                        Text(Fmt.percentOfRatio(s.share, 1), style = DSFont.xs(), color = c.mutedForeground)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabSwitcher(selected: PortfolioTab, counts: Map<PortfolioTab, Int>, onSelect: (PortfolioTab) -> Unit) {
    val c = DS.colors
    Row(
        Modifier.fillMaxWidth().padding(top = DSSpacing.xs).clip(RoundedCornerShape(DSRadius.lg)).background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(DSRadius.lg)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PortfolioTab.entries.forEach { tab ->
            val isSel = tab == selected
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(DSRadius.md))
                    .then(if (isSel) Modifier.background(c.primary.copy(alpha = 0.14f)).border(1.dp, c.primary.copy(alpha = 0.45f), RoundedCornerShape(DSRadius.md)) else Modifier)
                    .plainClickable { onSelect(tab) }.padding(vertical = DSSpacing.smd),
                horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (tab == PortfolioTab.ASSETS) Icons.Filled.PieChart else Icons.Filled.SwapHoriz, null, Modifier.size(13.dp), tint = if (isSel) c.accentBrand else c.mutedForeground)
                Text(tab.label, style = DSFont.sm(FontWeight.SemiBold), color = if (isSel) c.foreground else c.mutedForeground, maxLines = 1)
                CountBadge(counts[tab] ?: 0, isSel)
            }
        }
    }
}

@Composable
private fun EmptyAssets() = EmptyState(Icons.Outlined.PieChart, "No Assets Yet", "Buy a stock and it'll show up here.")

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    val c = DS.colors
    Column(Modifier.fillMaxWidth().padding(top = DSSpacing.xl3, start = DSSpacing.xl2, end = DSSpacing.xl2), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        DSIconTile(icon)
        Text(title, style = DSFont.lg(FontWeight.SemiBold), color = c.foreground)
        Text(message, style = DSFont.sm(), color = c.mutedForeground, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PositionRow(p: Position, trades: List<TrackedTrade>) {
    val c = DS.colors
    var show by remember { mutableStateOf(false) }
    DSMenuItem(onClick = if (trades.isEmpty()) null else ({ show = true })) {
        WithChainBadge("bsc", ring = c.background) { RemoteIconCircle(p.logoUrl, DSRow.logo, p.symbol) }
        Spacer(Modifier.width(DSSpacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${Fmt.number(p.quantity, 4)} ${p.symbol}", style = DSRow.title, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val avg = "Avg ${Fmt.usd(p.avgCost)}"
            Text(if (trades.isEmpty()) avg else "$avg · ${trades.size} tx${if (trades.size == 1) "" else "s"}", style = DSRow.subtitle, color = c.mutedForeground, maxLines = 1)
        }
        Spacer(Modifier.width(DSSpacing.sm))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Fmt.usd(p.marketValue), style = DSRow.value, color = c.foreground)
            if (p.isHeld) {
                Text(
                    "${if (p.unrealizedPnL >= 0) "+" else "-"}${Fmt.usd(abs(p.unrealizedPnL))} (${if (p.unrealizedPnL >= 0) "+" else ""}${Fmt.percentOfRatio(p.unrealizedPnLPercent)})",
                    style = DSRow.change, color = TrendTone.from(p.unrealizedPnL).color(), maxLines = 1,
                )
            }
        }
    }
    if (show) {
        DSSheet(onDismiss = { show = false }, title = "${p.ticker} Transactions") {
            Column {
                trades.sortedByDescending { it.createdAt }.forEachIndexed { i, t ->
                    if (i > 0) DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                    TransactionRow(t, p.logoUrl)
                }
            }
        }
    }
}

@Composable
fun TransactionsList(
    txs: List<TrackedTrade>,
    logos: Map<String, String?>,
    loading: Boolean,
    error: String?,
    hasMore: Boolean,
    onRetry: () -> Unit,
    onMore: () -> Unit,
    caption: (@Composable () -> Unit)? = null,
) {
    val c = DS.colors
    when {
        txs.isEmpty() && loading -> Box(Modifier.fillMaxWidth().padding(top = DSSpacing.xl2), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), color = c.mutedForeground, strokeWidth = 2.dp)
        }
        txs.isEmpty() && error != null -> Box(Modifier.height(320.dp)) { ErrorState(error, onRetry) }
        txs.isEmpty() -> EmptyState(Icons.Outlined.History, "No Transactions Yet", "Every buy and sell for this wallet will show up here.")
        else -> Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            caption?.invoke()
            Column {
                txs.forEachIndexed { i, t ->
                    if (i > 0) DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                    TransactionRow(t, logos[t.symbol])
                }
            }
            if (hasMore) {
                DSButton("Load More", onMore, style = DSButtonStyle.OUTLINE, size = DSButtonSize.SM, loading = loading, enabled = !loading)
            }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.US)
private val pastYearFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.US)

/** One fill, linking out to BscScan. Shared with Activity. */
@Composable
fun TransactionRow(trade: TrackedTrade, logoUrl: String?) {
    val c = DS.colors
    val context = LocalContext.current
    val isBuy = trade.side == "buy"
    val zoned = trade.createdAt.atZone(ZoneId.systemDefault())
    val time = (if (zoned.year == java.time.LocalDate.now().year) timeFormat else pastYearFormat).format(zoned)
    val stockAmount = if (isBuy) trade.outputAmount else trade.inputAmount
    val quoteAmount = if (isBuy) trade.inputAmount else trade.outputAmount
    val quoteSymbol = if (isBuy) trade.inputSymbol else trade.outputSymbol
    val url = "https://bscscan.com/tx/${trade.txId}"
    DSMenuItem(onClick = { openUrl(context, url) }) {
        WithChainBadge(if (trade.chain == "bnb") "bsc" else trade.chain, ring = c.background) { RemoteIconCircle(logoUrl, DSRow.logo, trade.symbol) }
        Spacer(Modifier.width(DSSpacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                DSBadge(if (isBuy) "Buy" else "Sell", if (isBuy) DSBadgeStyle.POSITIVE else DSBadgeStyle.NEGATIVE, uppercase = true, minTextWidth = 30.dp)
                Text(trade.ticker, style = DSRow.title, color = c.foreground, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("$time · ${Fmt.shortAddress(trade.txId)}", style = DSRow.subtitle, color = c.mutedForeground, maxLines = 1)
                Icon(Icons.Filled.NorthEast, null, Modifier.size(9.dp), tint = c.mutedForeground)
            }
        }
        Spacer(Modifier.width(DSSpacing.sm))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${if (isBuy) "+" else "-"}${Fmt.number(stockAmount, 4)}", style = DSRow.value, color = if (isBuy) c.positive else c.negative, maxLines = 1)
            Text("${Fmt.number(quoteAmount, 2)} $quoteSymbol", style = DSRow.subtitle, color = c.mutedForeground)
        }
    }
}
