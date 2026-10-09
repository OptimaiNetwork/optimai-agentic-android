package com.test.agenttrade.ui.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.test.agenttrade.data.AskProResult
import com.test.agenttrade.data.AskProTechnical
import com.test.agenttrade.data.AskSource
import com.test.agenttrade.data.Fmt
import com.test.agenttrade.data.MarketItem
import com.test.agenttrade.data.StockApiClient
import com.test.agenttrade.data.TradeQuote
import com.test.agenttrade.data.UserFacingError
import com.test.agenttrade.ui.AppRouter
import com.test.agenttrade.ui.QuoteTarget
import com.test.agenttrade.ui.components.AreaSparkline
import com.test.agenttrade.ui.components.DSBadge
import com.test.agenttrade.ui.components.DSButton
import com.test.agenttrade.ui.components.DSDivider
import com.test.agenttrade.ui.components.OptimAILogo
import com.test.agenttrade.ui.components.RemoteIconCircle
import com.test.agenttrade.ui.components.TrendLabel
import com.test.agenttrade.ui.components.TrendTone
import com.test.agenttrade.ui.components.TrustWalletAssets
import com.test.agenttrade.ui.components.dsCard
import com.test.agenttrade.ui.components.markdown
import com.test.agenttrade.ui.components.plainClickable
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import com.test.agenttrade.ui.theme.DSRadius
import com.test.agenttrade.ui.theme.DSSpacing
import com.test.agenttrade.ui.wallet.TabHeader
import com.test.agenttrade.ui.wallet.openUrl
import com.test.agenttrade.wallet.WalletConnectManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ChatKind {
    data class Agent(val text: String) : ChatKind
    data class User(val text: String) : ChatKind
    data object Suggestions : ChatKind
    data class Answer(val summary: String, val sources: List<AskSource>) : ChatKind
    data class Snapshot(val stock: MarketItem) : ChatKind
    /** `inputAmount` is USD to spend on a buy, tokens on a sell. */
    data class OrderPreview(val stock: MarketItem, val side: String, val inputAmount: Double?) : ChatKind
    data class Technical(val card: AskProTechnical, val stock: MarketItem?) : ChatKind
}

data class ChatMessage(val kind: ChatKind, val id: String = UUID.randomUUID().toString())

private object ChatMemory {
    /** Kept across tab switches, like the iOS tab's state. */
    val messages = mutableStateListOf(
        ChatMessage(ChatKind.Agent("Hi, I'm OptimAI. Ask me about stocks, crypto, or technical indicators — or tell me what to trade.")),
        ChatMessage(ChatKind.Suggestions),
    )
}

private data class Suggestion(val text: String, val icon: ImageVector)

private val suggestionPool = listOf(
    Suggestion("Tesla technicals", Icons.Outlined.Speed),
    Suggestion("Buy $100 of NVIDIA", Icons.Outlined.ShoppingCart),
    Suggestion("Is MSTR overbought?", Icons.Outlined.MonitorHeart),
    Suggestion("What's Tesla's price?", Icons.Outlined.AttachMoney),
    Suggestion("Did MACD cross on META?", Icons.Filled.BarChart),
    Suggestion("Sell 1 share of AAPL", Icons.Filled.NorthEast),
    Suggestion("How has MSTR done this month?", Icons.Outlined.TrendingUp),
    Suggestion("Latest news on Apple", Icons.Outlined.Newspaper),
    Suggestion("Are my holdings oversold?", Icons.Outlined.ViewList),
    Suggestion("Buy $50 of Circle", Icons.Outlined.ShoppingCart),
    Suggestion("Where's support for Oracle?", Icons.Outlined.Straighten),
    Suggestion("How is Microsoft doing today?", Icons.Outlined.AttachMoney),
)

private data class ComposerAction(val title: String, val icon: ImageVector, val starter: String)

private val composerActions = listOf(
    ComposerAction("Alerts", Icons.Outlined.NotificationsActive, "Alert me when "),
    ComposerAction("Schedules", Icons.Outlined.CalendarMonth, "Every week, buy $50 of "),
    ComposerAction("Technicals", Icons.Filled.BarChart, "Technicals for "),
    ComposerAction("Compare", Icons.AutoMirrored.Filled.CompareArrows, "Compare "),
    ComposerAction("News", Icons.Outlined.Newspaper, "Latest news on "),
    ComposerAction("My holdings", Icons.Outlined.BusinessCenter, "How are my holdings doing?"),
)

/**
 * The Agent tab (`design/agent_trade.png`): every message goes to
 * `/agent/ask-optimai-pro` with the conversation so far. The server picks
 * the card — an order preview, a price card, a technical card, or a
 * grounded answer with sources. Cards trade on bStocks: the ticker the
 * server resolved is looked up on the bStocks catalog first.
 */
@Composable
fun AgentChatScreen() {
    val c = DS.colors
    val messages = ChatMemory.messages
    var draft by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    val dictation = rememberDictation { draft = it }

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || thinking) return
        val history = messages.mapNotNull {
            when (val k = it.kind) {
                is ChatKind.User -> "user" to k.text
                is ChatKind.Agent -> "assistant" to k.text
                is ChatKind.Answer -> "assistant" to k.summary
                else -> null
            }
        }
        draft = ""
        messages += ChatMessage(ChatKind.User(question))
        thinking = true
        scope.launch {
            try {
                val result = StockApiClient.askOptimAIPro(question, history, WalletConnectManager.session.value?.address)
                val bstock = result.stock?.let { resolveOnBStocks(it) }
                when {
                    result.previewType == AskProResult.PreviewType.TECHNICAL && result.technical != null -> {
                        messages += ChatMessage(ChatKind.Agent(result.answer))
                        messages += ChatMessage(ChatKind.Technical(result.technical, bstock ?: result.stock))
                    }
                    result.previewType == AskProResult.PreviewType.QUOTE_CARD && result.stock != null -> {
                        messages += ChatMessage(ChatKind.Agent(result.answer))
                        if (bstock != null) {
                            messages += ChatMessage(ChatKind.OrderPreview(bstock, result.trade?.side ?: "buy", result.trade?.inputAmount))
                        } else {
                            messages += ChatMessage(ChatKind.Agent("${result.stock.ticker} isn't available on BNB Chain yet."))
                        }
                    }
                    result.previewType == AskProResult.PreviewType.QUOTE && result.stock != null -> {
                        messages += ChatMessage(ChatKind.Agent(result.answer))
                        messages += ChatMessage(ChatKind.Snapshot(bstock ?: result.stock))
                    }
                    else -> messages += ChatMessage(
                        if (result.sources.isEmpty()) ChatKind.Agent(result.answer) else ChatKind.Answer(result.answer, result.sources),
                    )
                }
            } catch (e: Exception) {
                messages += ChatMessage(ChatKind.Agent(UserFacingError.message(e, "Something went wrong reaching OptimAI. Please try again.")))
            }
            thinking = false
        }
    }

    LaunchedEffect(messages.size, thinking) {
        val target = messages.size - 1 + if (thinking) 1 else 0
        if (target >= 0) listState.animateScrollToItem(target)
    }

    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        Box(Modifier.padding(start = DSSpacing.lg, end = DSSpacing.lg, top = DSSpacing.sm, bottom = DSSpacing.xs)) { TabHeader() }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().plainClickable { focus.clearFocus() },
            state = listState,
            contentPadding = PaddingValues(DSSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.lg),
        ) {
            items(messages, key = { it.id }) { message -> MessageRow(message) { send(it) } }
            if (thinking) item(key = "thinking") { ThinkingRow() }
        }
        // Composer: quick-action chips over the field, mic and send.
        Column(Modifier.padding(top = DSSpacing.xs, bottom = DSSpacing.sm), verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            dictation.error?.let { Text(it, style = DSFont.xs(), color = c.mutedForeground, modifier = Modifier.padding(horizontal = DSSpacing.lg)) }
            LazyRow(contentPadding = PaddingValues(horizontal = DSSpacing.lg), horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                items(composerActions) { action ->
                    Row(
                        Modifier.height(30.dp).clip(CircleShape).background(c.card).border(1.dp, c.border, CircleShape)
                            .plainClickable {
                                draft = action.starter
                                requester.requestFocus()
                            }.padding(horizontal = DSSpacing.smd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(action.icon, null, Modifier.size(13.dp), tint = c.accentBrand)
                        Text(action.title, style = DSFont.xs(FontWeight.Medium), color = c.foreground)
                    }
                }
            }
            val canSend = draft.isNotBlank() && !thinking
            Row(
                Modifier.padding(horizontal = DSSpacing.lg).fillMaxWidth().clip(RoundedCornerShape(DSRadius.xl)).background(c.card)
                    .border(1.dp, if (dictation.isRecording) c.primary.copy(alpha = 0.6f) else c.border, RoundedCornerShape(DSRadius.xl)).padding(6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
            ) {
                Box(Modifier.weight(1f).heightIn(min = 32.dp).padding(start = DSSpacing.smd), contentAlignment = Alignment.CenterStart) {
                    if (draft.isEmpty()) {
                        Text(if (dictation.isRecording) "Listening…" else "Ask OptimAI or tell it what to trade…", style = DSFont.sm(), color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BasicTextField(
                        draft, { draft = it },
                        textStyle = DSFont.sm().copy(color = c.foreground),
                        maxLines = 4,
                        cursorBrush = SolidColor(c.accentBrand),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send(draft) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(requester),
                    )
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(if (dictation.isRecording) c.destructive else c.card)
                        .plainClickable(enabled = !thinking) {
                            focus.clearFocus()
                            dictation.toggle()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (dictation.isRecording) Icons.Filled.Stop else Icons.Filled.Mic, if (dictation.isRecording) "Stop dictation" else "Dictate",
                        Modifier.size(if (dictation.isRecording) 14.dp else 18.dp), tint = if (dictation.isRecording) c.destructiveForeground else c.mutedForeground,
                    )
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(if (canSend) c.primary else c.secondary)
                        .plainClickable(enabled = canSend) {
                            dictation.stop()
                            send(draft)
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.ArrowUpward, "Send", Modifier.size(16.dp), tint = if (canSend) c.primaryForeground else c.mutedForeground) }
            }
        }
    }
}

/** The server's stock, re-resolved on the bStocks catalog (null when bStocks doesn't list it). */
private suspend fun resolveOnBStocks(stock: MarketItem): MarketItem? =
    runCatching { StockApiClient.market(query = stock.ticker, limit = 5).items.firstOrNull { it.ticker.equals(stock.ticker, true) } }.getOrNull()

@Composable
private fun MessageRow(message: ChatMessage, onSuggestion: (String) -> Unit) {
    when (val k = message.kind) {
        is ChatKind.Agent -> AgentBubble(k.text)
        is ChatKind.User -> UserBubble(k.text)
        ChatKind.Suggestions -> Box(Modifier.padding(start = AVATAR_INDENT)) { SuggestionGrid(onSuggestion) }
        is ChatKind.Answer -> AnswerRow(k.summary, k.sources)
        is ChatKind.Snapshot -> Box(Modifier.padding(start = AVATAR_INDENT)) { SnapshotCard(k.stock) }
        is ChatKind.OrderPreview -> Box(Modifier.padding(start = AVATAR_INDENT)) { OrderPreviewCard(k.stock, k.side, k.inputAmount) }
        is ChatKind.Technical -> Box(Modifier.padding(start = AVATAR_INDENT)) { TechnicalCard(k.card, k.stock) }
    }
}

private val AVATAR_SIZE = 30.dp
private val AVATAR_INDENT = AVATAR_SIZE + DSSpacing.sm

@Composable
fun AgentAvatar() {
    Box(Modifier.size(AVATAR_SIZE).background(DS.colors.accentBrand.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
        OptimAILogo(Modifier.size(14.dp))
    }
}

@Composable
private fun AgentBubble(text: String) {
    val c = DS.colors
    Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        AgentAvatar()
        Text(
            markdown(text, c.accentBrand), style = DSFont.sm(), color = c.foreground,
            modifier = Modifier.weight(1f, fill = false).background(c.secondary, RoundedCornerShape(DSRadius.lg)).padding(horizontal = DSSpacing.md, vertical = DSSpacing.smd),
        )
        Spacer(Modifier.width(DSSpacing.xl3))
    }
}

@Composable
private fun UserBubble(text: String) {
    val c = DS.colors
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f).widthIn(min = DSSpacing.xl3 * 1.5f))
        Text(
            text, style = DSFont.sm(FontWeight.Medium), color = c.background,
            modifier = Modifier.background(c.foreground, RoundedCornerShape(DSRadius.lg)).padding(horizontal = DSSpacing.md, vertical = DSSpacing.smd),
        )
    }
}

@Composable
private fun ThinkingRow() {
    val c = DS.colors
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "p")
    Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        AgentAvatar()
        Row(
            Modifier.background(c.secondary, RoundedCornerShape(DSRadius.lg)).padding(horizontal = DSSpacing.md, vertical = DSSpacing.smd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { i -> Box(Modifier.size(5.dp).alpha(if (phase.toInt() == i) 1f else 0.35f).background(c.mutedForeground, CircleShape)) }
            }
            Text("Thinking", style = DSFont.xs(FontWeight.Medium), color = c.mutedForeground)
        }
    }
}

/** Four suggestions in a 2×2 grid; every 3s one cell fades to the next question. */
@Composable
private fun SuggestionGrid(onTap: (String) -> Unit) {
    val c = DS.colors
    val slots = remember { mutableStateListOf(*suggestionPool.take(4).toTypedArray()) }
    LaunchedEffect(Unit) {
        var nextItem = 4
        var nextSlot = 0
        while (isActive) {
            delay(3000)
            var candidate = suggestionPool[nextItem % suggestionPool.size]
            var tries = 0
            while (candidate in slots && tries < suggestionPool.size) {
                nextItem++
                candidate = suggestionPool[nextItem % suggestionPool.size]
                tries++
            }
            slots[nextSlot % slots.size] = candidate
            nextItem++
            nextSlot++
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                for (col in 0 until 2) {
                    val index = row * 2 + col
                    AnimatedContent(
                        targetState = slots[index],
                        transitionSpec = { fadeIn(tween(300, delayMillis = 300)) togetherWith fadeOut(tween(300)) },
                        modifier = Modifier.weight(1f),
                        label = "chip",
                    ) { s ->
                        Row(
                            Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(DSRadius.md)).background(c.card)
                                .border(1.dp, c.border, RoundedCornerShape(DSRadius.md)).plainClickable { onTap(s.text) }
                                .padding(horizontal = DSSpacing.smd, vertical = DSSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
                        ) {
                            Icon(s.icon, null, Modifier.size(14.dp), tint = c.accentBrand)
                            Text(s.text, style = DSFont.xs(FontWeight.Medium), color = c.foreground, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerRow(summary: String, sources: List<AskSource>) {
    val c = DS.colors
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        AgentAvatar()
        Column(Modifier.weight(1f).dsCard(padding = DSSpacing.md), verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
            Text(markdown(summary, c.accentBrand), style = DSFont.sm(), color = c.foreground)
            if (sources.isNotEmpty()) {
                DSDivider()
                Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                    Text("Sources", style = DSFont.xs(FontWeight.SemiBold), color = c.mutedForeground)
                    sources.take(4).forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(DSRadius.sm)).background(c.secondary).plainClickable { openUrl(context, s.url) }
                                .padding(horizontal = DSSpacing.smd, vertical = DSSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
                        ) {
                            Icon(Icons.Filled.Link, null, Modifier.size(11.dp), tint = c.accentBrand)
                            Text(s.publisher ?: s.title, style = DSFont.xs(FontWeight.Medium), color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.NorthEast, null, Modifier.size(9.dp), tint = c.mutedForeground)
                        }
                    }
                }
            }
        }
    }
}

/** A stock's live price as the server resolved it; "Trade" opens the quote screen. */
@Composable
private fun SnapshotCard(item: MarketItem) {
    val c = DS.colors
    val tone = TrendTone.from(item.priceChangePct24h ?: 0.0)
    Column(Modifier.fillMaxWidth().dsCard(radius = DSRadius.xl), verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
            RemoteIconCircle(item.logoUrl, 36.dp, item.ticker)
            Column(Modifier.weight(1f)) {
                Text(item.ticker, style = DSFont.base(FontWeight.Bold), color = c.foreground)
                Text(item.name ?: item.symbol, style = DSFont.xs(), color = c.mutedForeground, maxLines = 1)
            }
            DSBadge("bStocks · BNB Chain")
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            Text(item.referencePrice?.let { Fmt.usd(it) } ?: "—", style = DSFont.display(26), color = c.foreground)
            item.priceChangePct24h?.let { TrendLabel(tone, Fmt.signedPercent(it)) }
            Text("24h", style = DSFont.xs(), color = c.mutedForeground)
        }
        item.sparkline?.takeIf { it.size > 1 }?.let { AreaSparkline(it, tone, Modifier.fillMaxWidth().height(64.dp)) }
        Row(Modifier.fillMaxWidth().background(c.secondary, RoundedCornerShape(DSRadius.md)).padding(DSSpacing.smd)) {
            StatCell("Market cap", Fmt.compactCurrency(item.marketCap), Modifier.weight(1f))
            StatCell("24h volume", Fmt.compactCurrency(item.volume24h), Modifier.weight(1f))
        }
        DSButton("Trade ${item.ticker}", { AppRouter.presentQuote(QuoteTarget(item.ticker, currentPrice = item.referencePrice)) })
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = DSFont.xs(), color = DS.colors.mutedForeground)
        Text(value, style = DSFont.sm(FontWeight.SemiBold), color = DS.colors.foreground)
    }
}

/**
 * A live bStocks quote for the order the message asked for, re-priced every
 * 15s. Without a wallet it's priced for a placeholder taker. "Review Order"
 * opens the quote screen pre-filled — where signing happens.
 */
@Composable
private fun OrderPreviewCard(stock: MarketItem, side: String, inputAmount: Double?) {
    val c = DS.colors
    val session by WalletConnectManager.session.collectAsState()
    val isSell = side.equals("sell", true)
    val payAmount = inputAmount ?: if (isSell) 1.0 else 100.0
    var quote by remember { mutableStateOf<TradeQuote?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(session?.address) {
        while (isActive) {
            try {
                quote = StockApiClient.quoteTrade(stock.ticker, if (isSell) "sell" else "buy", payAmount, session?.address ?: "0x000000000000000000000000000000000000dEaD")
                failed = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = quote == null
            }
            delay(15_000)
        }
    }
    val q = quote
    if (q == null) {
        Box(Modifier.fillMaxWidth().heightIn(min = if (failed) 60.dp else 300.dp).dsCard(radius = DSRadius.xl), contentAlignment = Alignment.Center) {
            if (failed) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, Modifier.size(13.dp), tint = c.mutedForeground)
                Text("Couldn't load live data", style = DSFont.xs(FontWeight.Medium), color = c.mutedForeground)
            } else CircularProgressIndicator(Modifier.size(22.dp), color = c.mutedForeground, strokeWidth = 2.dp)
        }
        return
    }
    Column(Modifier.fillMaxWidth().dsCard(radius = DSRadius.xl), verticalArrangement = Arrangement.spacedBy(DSSpacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
            RemoteIconCircle(stock.logoUrl, 40.dp, stock.ticker)
            Column(Modifier.weight(1f)) {
                Text("Order preview", style = DSFont.xs(), color = c.mutedForeground)
                Text("${if (isSell) "Sell" else "Buy"} ${stock.name ?: stock.ticker}", style = DSFont.lg(FontWeight.Bold), color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DSBadge("bStocks · BNB Chain")
        }
        Row(Modifier.fillMaxWidth().background(c.secondary, RoundedCornerShape(DSRadius.md)).padding(DSSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            AssetColumn("You pay", q.payAmount, if (isSell) q.symbol else q.payToken, if (isSell) stock.logoUrl else TrustWalletAssets.usdtOnBsc, Modifier.weight(1f))
            Box(Modifier.padding(horizontal = DSSpacing.md).width(1.dp).height(44.dp).background(c.border))
            AssetColumn("Est. receive", q.receiveAmount, if (isSell) q.payToken else q.symbol, if (isSell) TrustWalletAssets.usdtOnBsc else stock.logoUrl, Modifier.weight(1f))
        }
        Column {
            InfoRow("Price per share", Fmt.usd(q.pricePerToken))
            DSDivider()
            InfoRow("Network", "BNB Smart Chain")
            q.priceImpactPercent?.let {
                DSDivider()
                InfoRow("Price impact", Fmt.number(kotlin.math.abs(it), 2) + "%")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
            Icon(Icons.Filled.Sync, null, Modifier.size(13.dp), tint = c.positive)
            Text("Live quote · refreshes every 15s", style = DSFont.xs(FontWeight.Medium), color = c.mutedForeground)
        }
        DSButton("Review Order", {
            AppRouter.presentQuote(QuoteTarget(stock.ticker, side = if (isSell) "sell" else "buy", payAmount = payAmount, currentPrice = stock.referencePrice))
        })
    }
}

@Composable
private fun AssetColumn(label: String, amount: Double, symbol: String, logo: String?, modifier: Modifier) {
    val c = DS.colors
    val stable = symbol.uppercase() == "USDT"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = DSFont.xs(), color = c.mutedForeground)
        Text(if (stable) Fmt.usd(amount) else Fmt.number(amount, 0, 4), style = DSFont.base(FontWeight.Bold), color = c.foreground, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RemoteIconCircle(logo, 16.dp, symbol)
            Text(symbol, style = DSFont.xs(FontWeight.Medium), color = c.foreground)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = DSSpacing.sm)) {
        Text(label, style = DSFont.sm(), color = DS.colors.mutedForeground)
        Spacer(Modifier.weight(1f))
        Text(value, style = DSFont.sm(FontWeight.Medium), color = DS.colors.foreground)
    }
}

// MARK: - Dictation

class Dictation(private val context: Context, private val onText: (String) -> Unit, private val requestPermission: () -> Unit) {
    var isRecording by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var recognizer: SpeechRecognizer? = null

    fun toggle() = if (isRecording) stop() else start()

    fun start() {
        error = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermission()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            error = "Dictation isn't available on this device."
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(code: Int) {
                isRecording = false
                if (code != SpeechRecognizer.ERROR_NO_MATCH && code != SpeechRecognizer.ERROR_CLIENT) error = "Couldn't hear that. Try again."
            }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)
                isRecording = false
            }
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onText)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        r.startListening(intent)
        isRecording = true
    }

    fun stop() {
        if (!isRecording) return
        recognizer?.stopListening()
        isRecording = false
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }
}

@Composable
private fun rememberDictation(onText: (String) -> Unit): Dictation {
    val context = LocalContext.current
    var pending: Dictation? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) pending?.start() }
    val dictation = remember { Dictation(context, onText) { launcher.launch(Manifest.permission.RECORD_AUDIO) } }
    pending = dictation
    DisposableEffect(Unit) { onDispose { dictation.release() } }
    return dictation
}

@Suppress("unused")
private val unusedIcon = Icons.Outlined.ShowChart
