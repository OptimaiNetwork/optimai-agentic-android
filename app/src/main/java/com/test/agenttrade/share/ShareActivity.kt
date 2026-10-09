package com.test.agenttrade.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.agenttrade.MainActivity
import com.test.agenttrade.data.AppPrefs
import com.test.agenttrade.data.Fmt
import com.test.agenttrade.data.MarketItem
import com.test.agenttrade.data.SharedContentAnalysis
import com.test.agenttrade.data.StockApiClient
import com.test.agenttrade.data.TradeQuote
import com.test.agenttrade.data.UserFacingError
import com.test.agenttrade.data.priceImpactText
import com.test.agenttrade.ui.components.AreaSparkline
import com.test.agenttrade.ui.components.ChainLogoImage
import com.test.agenttrade.ui.components.CircleCloseButton
import com.test.agenttrade.ui.components.DSButton
import com.test.agenttrade.ui.components.DSButtonSize
import com.test.agenttrade.ui.components.DSButtonStyle
import com.test.agenttrade.ui.components.DSDivider
import com.test.agenttrade.ui.components.DSIconTile
import com.test.agenttrade.ui.components.DSMenuItem
import com.test.agenttrade.ui.components.OptimAILogo
import com.test.agenttrade.ui.components.RemoteIconCircle
import com.test.agenttrade.ui.components.TrendLabel
import com.test.agenttrade.ui.components.TrendTone
import com.test.agenttrade.ui.components.TrustWalletAssets
import com.test.agenttrade.ui.components.dsCard
import com.test.agenttrade.ui.components.plainClickable
import com.test.agenttrade.ui.stocks.StockSearchField
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import com.test.agenttrade.ui.theme.DSRadius
import com.test.agenttrade.ui.theme.DSSpacing
import com.test.agenttrade.ui.theme.OptimAITheme
import com.test.agenttrade.wallet.WalletSessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * "Trade with OptimAI" — the Android port of the iOS Share / Action
 * extension. Any shared text or link (or selected text) goes to
 * `/catalyst/analyze-user-text`; the stock it's about gets a live bStocks
 * quote; Buy opens the app's quote screen pre-filled. Android lets a share
 * target open its app directly, so there's no notification hop.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val text = sharedText(intent)
        setContent {
            val theme by AppPrefs.theme.collectAsState()
            OptimAITheme(theme) {
                Box(Modifier.fillMaxSize().background(DS.colors.background).safeDrawingPadding()) {
                    if (text.isNullOrBlank()) NothingToAnalyze { finish() } else ShareFlow(text, onClose = { finish() }, onBuy = ::handoff)
                }
            }
        }
    }

    private fun sharedText(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT)).joinToString("\n").trim()
        Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        else -> null
    }

    /** Opens the quote screen in the app with this ticker and amount. */
    private fun handoff(ticker: String, amount: Double) {
        val uri = Uri.Builder().scheme("agenttrade").authority("buy")
            .appendQueryParameter("ticker", ticker)
            .appendQueryParameter("provider", "bstock")
            .appendQueryParameter("side", "buy")
            .appendQueryParameter("amount", Fmt.trimmedAmount(amount))
            .build()
        startActivity(Intent(Intent.ACTION_VIEW, uri, this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}

private sealed interface ShareStage {
    data object Analyzing : ShareStage
    data class Result(val analysis: SharedContentAnalysis?, val matches: List<SharedContentAnalysis.Match>) : ShareStage
    data class PickStock(val analysis: SharedContentAnalysis?) : ShareStage
    data class Failed(val message: String) : ShareStage
}

@Composable
private fun ShareFlow(text: String, onClose: () -> Unit, onBuy: (String, Double) -> Unit) {
    var stage by remember { mutableStateOf<ShareStage>(ShareStage.Analyzing) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        stage = ShareStage.Analyzing
        stage = try {
            val analysis = StockApiClient.analyzeUserText(text)
            if (analysis.stocks.isEmpty()) ShareStage.PickStock(analysis) else ShareStage.Result(analysis, analysis.stocks)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ShareStage.Failed(UserFacingError.message(e, "Couldn't analyze this right now. Please try again."))
        }
    }
    AnimatedContent(stage, contentKey = { it::class }, label = "share") { s ->
        when (s) {
            ShareStage.Analyzing -> Analyzing(text, onClose)
            is ShareStage.Result -> ResultView(s.analysis, s.matches, onClose, onBuy)
            is ShareStage.PickStock -> PickStock(s.analysis, onClose) { stage = ShareStage.Result(s.analysis, listOf(it)) }
            is ShareStage.Failed -> Failed(s.message, onRetry = { attempt++ }, onPick = { stage = ShareStage.PickStock(null) }, onClose = onClose)
        }
    }
}

@Composable
private fun AgentMark(size: Int = 36) {
    val c = DS.colors
    Box(Modifier.size(size.dp).background(c.accentBrand.copy(alpha = 0.12f), CircleShape).border(1.dp, c.accentBrand.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
        OptimAILogo(Modifier.size((size / 2).dp))
    }
}

@Composable
private fun Analyzing(text: String, onClose: () -> Unit) {
    val c = DS.colors
    val t = rememberInfiniteTransition(label = "rings")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "p")
    val link = Regex("https?://\\S+").find(text)?.value?.let { runCatching { Uri.parse(it) }.getOrNull() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(DSSpacing.lg)) {
            Spacer(Modifier.weight(1f))
            CircleCloseButton(onClose)
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.lg)) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                for (ring in 0 until 2) {
                    val p = (phase + ring * 0.5f) % 1f
                    Box(Modifier.size((72 + 56 * p).dp).border(1.5.dp, c.accentBrand.copy(alpha = 0.35f * (1 - p)), CircleShape))
                }
                AgentMark(72)
            }
            Text("Reading what you shared", style = DSFont.xl(), color = c.foreground)
            Text("OptimAI Agent is finding the stock", style = DSFont.sm(), color = c.mutedForeground)
        }
        Row(Modifier.padding(horizontal = DSSpacing.lg).padding(top = DSSpacing.xl2).fillMaxWidth().dsCard(padding = DSSpacing.md), horizontalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
            Box(Modifier.size(28.dp).background(c.secondary, RoundedCornerShape(DSRadius.sm)), contentAlignment = Alignment.Center) {
                Icon(if (link == null) Icons.Filled.FormatQuote else Icons.Filled.Link, null, Modifier.size(14.dp), tint = c.mutedForeground)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                link?.host?.let { Text(it.removePrefix("www."), style = DSFont.xs(FontWeight.SemiBold), color = c.foreground) }
                Text(text, style = DSFont.xs(), color = c.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.weight(2f))
    }
}

private val AMOUNTS = listOf(50.0, 100.0, 250.0, 500.0)

@Composable
private fun ResultView(analysis: SharedContentAnalysis?, matches: List<SharedContentAnalysis.Match>, onClose: () -> Unit, onBuy: (String, Double) -> Unit) {
    val c = DS.colors
    var selected by remember { mutableStateOf(matches.first()) }
    var amount by remember { mutableStateOf(100.0) }
    var item by remember { mutableStateOf<MarketItem?>(null) }
    var itemFailed by remember { mutableStateOf(false) }
    var quote by remember { mutableStateOf<TradeQuote?>(null) }
    var quoteError by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableIntStateOf(0) }

    LaunchedEffect(selected.ticker) {
        item = null
        itemFailed = false
        item = runCatching { StockApiClient.market(query = selected.ticker, limit = 10).items.firstOrNull { it.ticker.equals(selected.ticker, true) } }.getOrNull()
        itemFailed = item == null
    }
    LaunchedEffect(selected.ticker, amount) {
        quoteError = null
        val ticker = selected.ticker
        while (isActive) {
            try {
                quote = StockApiClient.quoteTrade(ticker, "buy", amount, WalletSessionStore.load()?.address ?: "0x000000000000000000000000000000000000dEaD")
                quoteError = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (quote?.ticker != ticker) {
                    quoteError = UserFacingError.message(e, "Couldn't get a price right now. Please try again.")
                    return@LaunchedEffect
                }
            }
            for (r in 15 downTo 1) {
                secondsLeft = r
                delay(1000)
            }
        }
    }
    val liveQuote = quote?.takeIf { it.ticker.equals(selected.ticker, true) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(DSSpacing.lg), verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
                AgentMark()
                Column(Modifier.weight(1f)) {
                    Text("OptimAI Agent", style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
                    Text(if (matches.size == 1) "Found 1 stock in what you shared" else "Found ${matches.size} stocks in what you shared", style = DSFont.xs(), color = c.mutedForeground)
                }
                CircleCloseButton(onClose)
            }
            analysis?.let { SourceInsight(it) }
            if (matches.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                    matches.forEach { m ->
                        val isSel = m == selected
                        Text(
                            m.ticker, style = DSFont.sm(FontWeight.SemiBold), color = if (isSel) c.primaryForeground else c.foreground,
                            modifier = Modifier.height(32.dp).clip(CircleShape).background(if (isSel) c.primary else c.secondary).plainClickable { selected = m }
                                .padding(horizontal = DSSpacing.md, vertical = 7.dp),
                        )
                    }
                }
            }
            StockRowCard(selected, item, itemFailed)
            // Live quote
            Column(Modifier.fillMaxWidth().dsCard(radius = DSRadius.xl), verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Live Quote", style = DSFont.base(FontWeight.SemiBold), color = c.foreground)
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.height(24.dp).background(c.secondary, CircleShape).padding(horizontal = DSSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ChainLogoImage("BSC", 14.dp)
                        Text("BNB Chain", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.foreground)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                    AMOUNTS.forEach { preset ->
                        val isSel = preset == amount
                        Box(
                            Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(DSRadius.sm))
                                .background(if (isSel) c.accentBrand.copy(alpha = 0.12f) else c.secondary)
                                .border(1.dp, if (isSel) c.accentBrand.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(DSRadius.sm))
                                .plainClickable { amount = preset },
                            contentAlignment = Alignment.Center,
                        ) { Text("$${preset.toInt()}", style = DSFont.sm(FontWeight.SemiBold), color = if (isSel) c.accentBrand else c.mutedForeground) }
                    }
                }
                Box {
                    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xxs)) {
                        AmountBox("You pay", Fmt.number(amount, 0, 2), liveQuote?.payToken ?: "USDT", TrustWalletAssets.usdtOnBsc, "$")
                        AmountBox("You receive", liveQuote?.let { "≈ " + Fmt.number(it.receiveAmount, 4) }, liveQuote?.symbol ?: selected.ticker, item?.logoUrl, selected.ticker)
                    }
                    Box(
                        Modifier.align(Alignment.Center).offset(y = 0.dp).size(28.dp).background(c.card, CircleShape).border(1.dp, c.border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.ArrowDownward, null, Modifier.size(13.dp), tint = c.foreground) }
                }
                if (quoteError != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                        Icon(Icons.Filled.ErrorOutline, null, Modifier.size(13.dp), tint = c.amber)
                        Text(quoteError!!, style = DSFont.xs(), color = c.amber)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                        DetailRow("Price per share", liveQuote?.let { Fmt.usd(it.pricePerToken) })
                        DetailRow("Price impact", liveQuote?.let { priceImpactText(it.priceImpactPercent) })
                        DetailRow("Minimum received", liveQuote?.let { q -> q.minimumReceived?.let { "${Fmt.number(it, 4)} ${q.symbol}" } ?: "—" })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        if (liveQuote == null) {
                            CircularProgressIndicator(Modifier.size(11.dp), color = c.mutedForeground, strokeWidth = 1.5.dp)
                            Text("Fetching a live quote", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c.mutedForeground)
                        } else {
                            Icon(Icons.Filled.Schedule, null, Modifier.size(11.dp), tint = c.mutedForeground)
                            Text("Refreshes in ${secondsLeft}s", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c.mutedForeground)
                        }
                    }
                }
            }
        }
        DSDivider()
        Column(Modifier.padding(horizontal = DSSpacing.lg).padding(top = DSSpacing.md, bottom = DSSpacing.sm), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            DSButton("Buy ${selected.ticker}", { onBuy(selected.ticker, amount) }, size = DSButtonSize.LG, icon = Icons.Filled.Bolt, enabled = liveQuote != null)
            Text("You'll review and sign in OptimAI Agentic. Nothing is sent from here.", fontSize = 11.sp, color = c.mutedForeground)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    val c = DS.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = DSFont.xs(), color = c.mutedForeground)
        Spacer(Modifier.weight(1f))
        if (value != null) Text(value, style = DSFont.xs(), color = c.foreground)
        else Box(Modifier.size(70.dp, 10.dp).background(c.secondary, CircleShape))
    }
}

@Composable
private fun AmountBox(label: String, value: String?, token: String, logo: String?, placeholder: String) {
    val c = DS.colors
    Column(Modifier.fillMaxWidth().background(c.secondary.copy(alpha = 0.6f), RoundedCornerShape(DSRadius.lg)).padding(horizontal = DSSpacing.md, vertical = DSSpacing.smd), verticalArrangement = Arrangement.spacedBy(DSSpacing.xxs)) {
        Text(label, style = DSFont.xs(), color = c.mutedForeground)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) Text(value, style = DSFont.display(24), color = c.foreground, maxLines = 1, modifier = Modifier.weight(1f))
            else Box(Modifier.weight(1f)) { Box(Modifier.size(120.dp, 22.dp).background(c.secondary, CircleShape)) }
            Row(
                Modifier.height(32.dp).background(c.card, CircleShape).border(1.dp, c.border, CircleShape).padding(start = 6.dp, end = DSSpacing.smd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RemoteIconCircle(logo, 20.dp, placeholder)
                Text(token, style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
            }
        }
    }
}

@Composable
private fun StockRowCard(match: SharedContentAnalysis.Match, item: MarketItem?, failed: Boolean) {
    val c = DS.colors
    val change = item?.priceChangePct24h ?: 0.0
    val tone = TrendTone.from(change)
    Row(Modifier.fillMaxWidth().dsCard(radius = DSRadius.xl, padding = DSSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
        Box(Modifier.size(40.dp).border(1.dp, c.border, CircleShape)) { RemoteIconCircle(item?.logoUrl, 40.dp, match.ticker) }
        Column(Modifier.weight(1f)) {
            Text(match.ticker, style = DSFont.display(18), color = c.foreground)
            Text(match.displayName, style = DSFont.xs(), color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val spark = item?.sparkline
        Box(Modifier.size(64.dp, 28.dp)) {
            if (spark != null && spark.size > 1) AreaSparkline(spark, tone, Modifier.fillMaxSize())
            else if (!failed) Box(Modifier.fillMaxSize().background(c.secondary.copy(alpha = 0.6f), RoundedCornerShape(4.dp)))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when {
                item?.referencePrice != null -> {
                    Text(Fmt.usd(item.referencePrice), style = DSFont.display(17), color = c.foreground)
                    TrendLabel(tone, (if (change > 0) "+" else "") + Fmt.number(change, 2) + "%", DSFont.xs(FontWeight.SemiBold))
                }
                failed -> Text("—", style = DSFont.display(17), color = c.mutedForeground)
                else -> {
                    Box(Modifier.size(72.dp, 16.dp).background(c.secondary, CircleShape))
                    Box(Modifier.size(44.dp, 10.dp).background(c.secondary, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun SourceInsight(analysis: SharedContentAnalysis) {
    val c = DS.colors
    val isX = analysis.preview.siteName == "X"
    val host = analysis.requestedUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }?.removePrefix("www.")
    val label = if (isX) analysis.preview.author?.let { "Post by $it on X" } ?: "Post on X" else analysis.preview.siteName ?: host ?: "Shared text"
    val title = if (isX) null else analysis.preview.title
    Column(Modifier.fillMaxWidth().dsCard(radius = DSRadius.xl, padding = DSSpacing.md), verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
            Box(Modifier.size(26.dp).background(c.secondary, RoundedCornerShape(DSRadius.sm)), contentAlignment = Alignment.Center) {
                Icon(if (analysis.requestedUrl == null) Icons.Filled.FormatQuote else if (isX) Icons.Outlined.ChatBubbleOutline else Icons.Filled.Link, null, Modifier.size(13.dp), tint = c.mutedForeground)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = DSFont.xs(FontWeight.SemiBold), color = c.mutedForeground, maxLines = 1)
                title?.let { Text(it, style = DSFont.sm(FontWeight.SemiBold), color = c.foreground, maxLines = 2) }
                val desc = analysis.preview.description
                if (!desc.isNullOrBlank()) Text(desc, style = DSFont.xs(), color = if (title == null) c.foreground else c.mutedForeground, maxLines = 2)
                else if (title == null && analysis.requestedUrl != null) Text(analysis.requestedUrl, style = DSFont.xs(), color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
        }
        DSDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
            Box(Modifier.size(26.dp).background(c.accentBrand.copy(alpha = 0.12f), RoundedCornerShape(DSRadius.sm)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(13.dp), tint = c.accentBrand)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Agent insight", style = DSFont.xs(FontWeight.SemiBold), color = c.accentBrand)
                Text(analysis.answer, style = DSFont.sm(), color = c.foreground)
            }
        }
    }
}

/** No stock matched — pick one from the bStocks catalog. */
@Composable
private fun PickStock(analysis: SharedContentAnalysis?, onClose: () -> Unit, onPick: (SharedContentAnalysis.Match) -> Unit) {
    val c = DS.colors
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<MarketItem>>(emptyList()) }
    LaunchedEffect(Unit) { items = runCatching { StockApiClient.market(limit = 100).items }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = DSSpacing.lg, vertical = DSSpacing.sm)) {
            Text("Cancel", style = DSFont.base(FontWeight.SemiBold), color = c.accentBrand, modifier = Modifier.align(Alignment.CenterStart).plainClickable(onClick = onClose))
            Text("Select Stock", style = DSFont.base(FontWeight.SemiBold), color = c.foreground, modifier = Modifier.align(Alignment.Center))
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = DSSpacing.lg), verticalArrangement = Arrangement.spacedBy(DSSpacing.lg)) {
            item { StockSearchField(query) { query = it } }
            if (analysis != null) item {
                Row(Modifier.fillMaxWidth().dsCard(padding = DSSpacing.md), horizontalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
                    AgentMark(30)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("No supported stock found", style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
                        Text(analysis.answer, style = DSFont.xs(), color = c.mutedForeground)
                    }
                }
            }
            item { Text("Pick a company to continue", style = DSFont.xs(FontWeight.SemiBold), color = c.mutedForeground) }
            item {
                Column(Modifier.fillMaxWidth().dsCard(padding = DSSpacing.xxs)) {
                    items.filter { it.matches(query) }.forEach { s ->
                        DSMenuItem(onClick = { onPick(SharedContentAnalysis.Match(s.ticker, s.symbol, s.name ?: s.ticker)) }) {
                            RemoteIconCircle(s.logoUrl, 28.dp, s.ticker)
                            Spacer(Modifier.width(DSSpacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(s.ticker, style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
                                Text(s.name ?: s.symbol, style = DSFont.xs(), color = c.mutedForeground)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp), tint = c.mutedForeground)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(DSSpacing.xl)) }
        }
    }
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit, onPick: () -> Unit, onClose: () -> Unit) {
    val c = DS.colors
    Column(Modifier.fillMaxSize().padding(DSSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.xl)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            CircleCloseButton(onClose)
        }
        Spacer(Modifier.weight(1f))
        DSIconTile(Icons.Filled.Warning, c.amber)
        Text("Couldn't analyze this", style = DSFont.xl(), color = c.foreground)
        Text(message, style = DSFont.sm(), color = c.mutedForeground, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        DSButton("Try Again", onRetry, size = DSButtonSize.LG)
        DSButton("Pick a Stock Instead", onPick, style = DSButtonStyle.OUTLINE, size = DSButtonSize.LG)
    }
}

@Composable
private fun NothingToAnalyze(onClose: () -> Unit) {
    val c = DS.colors
    Column(Modifier.fillMaxSize().padding(DSSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.xl)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            CircleCloseButton(onClose)
        }
        Spacer(Modifier.weight(1f))
        AgentMark(64)
        Text("Nothing to Analyze", style = DSFont.xl(), color = c.foreground)
        Text("Share a post, an article link, or some text about a company, and OptimAI Agent will find the stock and quote it for you.", style = DSFont.sm(), color = c.mutedForeground, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        DSButton("Close", onClose, style = DSButtonStyle.OUTLINE, size = DSButtonSize.LG)
    }
}

@Suppress("unused")
private val unusedCheck = Icons.Filled.Check
