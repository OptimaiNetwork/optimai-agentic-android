package com.test.agenttrade.ui.quote

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.test.agenttrade.data.ActivityBadgeStore
import com.test.agenttrade.data.BnbReceipt
import com.test.agenttrade.data.Fmt
import com.test.agenttrade.data.StockApiClient
import com.test.agenttrade.data.TradeGuard
import com.test.agenttrade.data.TradeQuote
import com.test.agenttrade.data.UserFacingError
import com.test.agenttrade.data.priceImpactText
import com.test.agenttrade.ui.components.ChainLogoImage
import com.test.agenttrade.ui.components.DSButton
import com.test.agenttrade.ui.components.DSButtonSize
import com.test.agenttrade.ui.components.DSButtonStyle
import com.test.agenttrade.ui.components.DSDivider
import com.test.agenttrade.ui.components.RemoteIconCircle
import com.test.agenttrade.ui.components.TrustWalletAssets
import com.test.agenttrade.ui.components.dsCard
import com.test.agenttrade.ui.components.plainClickable
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import com.test.agenttrade.ui.theme.DSRadius
import com.test.agenttrade.ui.theme.DSSpacing
import com.test.agenttrade.ui.wallet.QuoteScreenHeader
import com.test.agenttrade.ui.wallet.TransactionIdCard
import com.test.agenttrade.wallet.WalletConnectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private sealed interface Stage {
    data object Quote : Stage
    data object Review : Stage
    data class Processing(val message: String) : Stage
    /** `confirmed` is false when the chain still hadn't included the swap. */
    data class Success(val hash: String, val confirmed: Boolean) : Stage
    data class Failed(val message: String) : Stage
}

private enum class Side(val label: String) { BUY("Buy"), SELL("Sell") }

private const val SETTLEMENT = "USDT"
/** `/trade/quote` needs some taker even to price; this one only ever prices. */
private const val PLACEHOLDER_TAKER = "0x000000000000000000000000000000000000dEaD"

/**
 * Real BNB Chain bStocks quote — buy a tokenized stock with USDT or sell it
 * back, priced by `POST /trade/quote` (PancakeSwap) and signed by the user's
 * own wallet: an ERC-20 approval first when needed, then the swap, each held
 * until `/trade/receipt` says it landed. The Android port of `QuoteBuyView`.
 */
@Composable
fun QuoteBuyScreen(ticker: String, currentPrice: Double?, initialSide: String?, initialPayAmount: Double?, onClose: () -> Unit) {
    val c = DS.colors
    val session by WalletConnectManager.session.collectAsState()
    val startSide = if (initialSide.equals("sell", ignoreCase = true)) Side.SELL else Side.BUY
    var side by remember { mutableStateOf(startSide) }
    // One share's worth of USDT by default (then snapped to the live price);
    // a passed amount wins; a sell opens on one share.
    var payText by remember {
        mutableStateOf(
            when {
                initialPayAmount != null -> Fmt.trimmedAmount(initialPayAmount)
                startSide == Side.SELL -> "1"
                else -> currentPrice?.let { Fmt.fixed(it, 2) } ?: "100"
            },
        )
    }
    var receiveText by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf<String?>(null) }
    var quote by remember { mutableStateOf<TradeQuote?>(null) }
    var loadingQuote by remember { mutableStateOf(false) }
    var quoteError by remember { mutableStateOf<String?>(null) }
    var stage by remember { mutableStateOf<Stage>(Stage.Quote) }
    var logoUrl by remember { mutableStateOf<String?>(null) }
    var companyName by remember { mutableStateOf<String?>(null) }
    var snapped by remember { mutableStateOf(initialPayAmount != null || startSide == Side.SELL) }
    var firstQuote by remember { mutableStateOf(false) }
    var usdtBalance by remember { mutableStateOf<Double?>(null) }
    var stockBalance by remember { mutableStateOf<Double?>(null) }
    var priceMoved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val payAmount = Fmt.parseAmount(payText)
    val stockSymbol = quote?.symbol ?: "${ticker}B"
    val inputSymbol = if (side == Side.BUY) SETTLEMENT else stockSymbol
    val outputSymbol = if (side == Side.BUY) stockSymbol else SETTLEMENT
    val inputLogo = if (side == Side.BUY) TrustWalletAssets.usdtOnBsc else logoUrl
    val outputLogo = if (side == Side.BUY) logoUrl else TrustWalletAssets.usdtOnBsc
    val inputDigits = if (side == Side.BUY) 2 else 4
    val outputDigits = if (side == Side.BUY) 4 else 2
    val inputBalance = if (side == Side.BUY) usdtBalance else stockBalance
    val outputBalance = if (side == Side.BUY) stockBalance else usdtBalance
    val exceedsBalance = session != null && inputBalance != null && payAmount > inputBalance!!
    val walletAddress = session?.address ?: PLACEHOLDER_TAKER

    suspend fun loadBalances() {
        val address = session?.address
        if (address == null) {
            usdtBalance = null
            stockBalance = null
            return
        }
        coroutineScope {
            val u = async { runCatching { StockApiClient.bnbBalance(address, SETTLEMENT) }.getOrNull() }
            val s = async { runCatching { StockApiClient.bnbBalance(address, ticker) }.getOrNull() }
            usdtBalance = u.await()?.amount
            stockBalance = s.await()?.amount
        }
    }

    LaunchedEffect(Unit) {
        val details = runCatching { StockApiClient.infoDetails(ticker) }.getOrNull()
        logoUrl = details?.logoUrl
        companyName = details?.name
    }
    LaunchedEffect(session?.address) { loadBalances() }

    // Re-quotes on any change, and every 5s otherwise — quotes expire in ~30s.
    LaunchedEffect(side, payText, walletAddress, stage is Stage.Quote) {
        if (stage !is Stage.Quote) return@LaunchedEffect
        while (isActive) {
            val amount = Fmt.parseAmount(payText)
            if (amount <= 0) {
                quote = null
                quoteError = null
                loadingQuote = false
            } else {
                loadingQuote = true
                delay(400) // debounce a burst of keystrokes
                try {
                    val fresh = StockApiClient.quoteTrade(ticker, side.name.lowercase(), amount, walletAddress)
                    quote = fresh
                    quoteError = null
                    if (side == Side.BUY && !snapped) {
                        snapped = true
                        payText = Fmt.fixed(fresh.pricePerToken, 2)
                    }
                    firstQuote = true
                    if (focused != "receive") receiveText = Fmt.trimmedAmount(fresh.receiveAmount)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A standing error isn't blanked every cycle — only replaced.
                    quote = null
                    quoteError = UserFacingError.message(e, "Couldn't get a price right now. Please try again.")
                }
                loadingQuote = false
            }
            delay(5000)
        }
    }

    fun setSide(newSide: Side) {
        if (newSide == side) return
        focused = null
        quote?.let { payText = Fmt.trimmedAmount(it.receiveAmount) }
        quote = null
        quoteError = null
        side = newSide
    }

    /**
     * Re-prices exactly what was reviewed; returns null — back to review with
     * the new numbers — when it came back worse than the slippage allows.
     */
    suspend fun freshQuote(reviewed: TradeQuote, address: String): TradeQuote? {
        val fresh = StockApiClient.quoteTrade(ticker, reviewed.side, reviewed.payAmount, address)
        quote = fresh
        val floor = reviewed.receiveAmount * (1 - reviewed.slippagePercent / 100)
        if (fresh.receiveAmount < floor) {
            priceMoved = true
            stage = Stage.Review
            return null
        }
        priceMoved = false
        return fresh
    }

    fun track(q: TradeQuote, hash: String, address: String) {
        // A simulated wallet's hash never reached a chain; never record it.
        if (!TradeGuard.txHash.matches(hash)) return
        val selling = side == Side.SELL
        ActivityBadgeStore.noteSubmitted(address, hash)
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.Main) {
            runCatching {
                StockApiClient.trackBnbTrade(
                    address = address, ticker = q.ticker, symbol = q.symbol, side = q.side,
                    inputSymbol = if (selling) q.symbol else q.payToken, inputAmount = q.payAmount,
                    outputSymbol = if (selling) q.payToken else q.symbol, outputAmount = q.receiveAmount,
                    pricePerToken = q.pricePerToken, txHash = hash,
                )
            }
            ActivityBadgeStore.refresh(force = true)
        }
    }

    /** Re-quote → (approve → wait) → swap → wait. */
    fun confirmOrder() = scope.launch {
        val reviewed = quote ?: return@launch
        val address = session?.address ?: return@launch
        try {
            stage = Stage.Processing("Getting a fresh price…")
            var fresh = freshQuote(reviewed, address) ?: return@launch
            // Only PancakeSwap on BNB Smart Chain: refuse before any wallet prompt.
            blockedReason(fresh)?.let { stage = Stage.Failed(it); return@launch }
            val simulated = session?.isSimulated == true && WalletConnectManager.simulatedMode
            fresh.approval?.let { approval ->
                stage = Stage.Processing("Approve $inputSymbol in your wallet…")
                val approvalHash = WalletConnectManager.sendTransaction(approval.to, approval.data, "0x0", null)
                if (!isAcceptableHash(approvalHash, simulated)) {
                    stage = Stage.Failed(BAD_HASH_MESSAGE)
                    return@launch
                }
                stage = Stage.Processing("Waiting for the approval to confirm…")
                val receipt = waitForReceipt(approvalHash, simulated)
                when (receipt.status) {
                    BnbReceipt.Status.SUCCESS -> {}
                    BnbReceipt.Status.PENDING -> {
                        stage = Stage.Failed("The approval hasn't confirmed on BNB Chain yet. Wait a moment, then try again.")
                        return@launch
                    }
                    BnbReceipt.Status.REVERTED -> {
                        stage = Stage.Failed(revertMessage("The approval", receipt.reason))
                        return@launch
                    }
                }
                // The approval took a wallet round-trip and a block; re-price.
                stage = Stage.Processing("Getting a fresh price…")
                fresh = freshQuote(reviewed, address) ?: return@launch
                blockedReason(fresh)?.let { stage = Stage.Failed(it); return@launch }
            }
            val tx = fresh.transaction ?: run {
                stage = Stage.Failed("Couldn't build a transaction for this order. Try again.")
                return@launch
            }
            stage = Stage.Processing("Confirm the swap in your wallet…")
            // No gas limit: the wallet estimates against the chain itself.
            val hash = WalletConnectManager.sendTransaction(tx.to, tx.data, tx.value, null)
            if (!isAcceptableHash(hash, simulated)) {
                stage = Stage.Failed(BAD_HASH_MESSAGE)
                return@launch
            }
            stage = Stage.Processing("Waiting for BNB Chain to confirm…")
            val receipt = waitForReceipt(hash, simulated)
            when (receipt.status) {
                BnbReceipt.Status.SUCCESS -> {
                    stage = Stage.Success(hash, true)
                    track(fresh, hash, address)
                }
                BnbReceipt.Status.PENDING -> stage = Stage.Success(hash, false)
                BnbReceipt.Status.REVERTED -> stage = Stage.Failed(revertMessage("The swap", receipt.reason))
            }
            loadBalances()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            stage = Stage.Failed(UserFacingError.message(e, "The order couldn't be completed. Please try again."))
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        AnimatedContent(targetState = stage, contentKey = { it::class }, label = "stage") { current ->
            when (current) {
                Stage.Quote -> QuoteStage(
                    ticker, logoUrl, companyName, onClose,
                    side = side, onSide = ::setSide,
                    payText = payText, onPayText = { payText = it },
                    receiveText = receiveText,
                    onReceiveText = { text ->
                        receiveText = text
                        val qty = Fmt.parseAmount(text)
                        if (qty <= 0) payText = "" else {
                            val price = quote?.pricePerToken ?: currentPrice
                            val estimate = if (price != null && price > 0) (if (side == Side.BUY) qty * price else qty / price) else 0.0
                            payText = if (estimate > 0) Fmt.fixed(estimate, 4) else ""
                        }
                    },
                    focused = focused, onFocus = { focused = it },
                    firstQuote = firstQuote, loadingQuote = loadingQuote, quote = quote, quoteError = quoteError,
                    inputSymbol = inputSymbol, outputSymbol = outputSymbol, inputLogo = inputLogo, outputLogo = outputLogo,
                    inputDigits = inputDigits, outputDigits = outputDigits,
                    inputBalance = inputBalance, outputBalance = outputBalance, stockBalance = stockBalance,
                    connected = session != null, exceedsBalance = exceedsBalance,
                    onReview = {
                        priceMoved = false
                        stage = Stage.Review
                    },
                )
                Stage.Review -> ReviewStage(quote, priceMoved, inputSymbol, outputSymbol, inputDigits, outputDigits, onSubmit = { confirmOrder() }, onBack = {
                    priceMoved = false
                    stage = Stage.Quote
                })
                is Stage.Processing -> ProcessingStage(current.message)
                is Stage.Success -> SuccessStage(current.hash, current.confirmed, side.label, successMessage(current.confirmed, quote, side, inputSymbol, outputSymbol, inputDigits, outputDigits), onClose)
                is Stage.Failed -> FailedStage(side.label, current.message, onRetry = { stage = Stage.Review }, onClose = onClose)
            }
        }
    }
}

private fun successMessage(confirmed: Boolean, quote: TradeQuote?, side: Side, inSym: String, outSym: String, inD: Int, outD: Int): String {
    if (!confirmed) return "BNB Chain hasn't confirmed it yet. Check BscScan for the final result."
    quote ?: return "Your order settled on BNB Smart Chain."
    val paid = "${Fmt.fixed(quote.payAmount, inD)} $inSym"
    val got = "${Fmt.fixed(quote.receiveAmount, outD)} $outSym"
    return if (side == Side.BUY) "Bought $got with $paid on BNB Smart Chain." else "Sold $paid for $got on BNB Smart Chain."
}

private const val BAD_HASH_MESSAGE =
    "Your wallet did not return a valid transaction hash, so the order was not confirmed. Check your wallet and BscScan before trying again."

/** A real wallet must return a 0x + 64 hex hash; only the debug simulated wallet may return anything else. */
private fun isAcceptableHash(hash: String, simulated: Boolean): Boolean =
    simulated || TradeGuard.txHash.matches(hash)

private fun blockedReason(quote: TradeQuote): String? = TradeGuard.violation(quote)

/**
 * Polls `/trade/receipt` until the chain includes `hash` (~2 minutes at
 * most). The debug simulated wallet's hash has no chain to ask, so with
 * `simulated` set it passes straight away. A real wallet's malformed hash is
 * an error, never a success.
 */
private suspend fun waitForReceipt(hash: String, simulated: Boolean): BnbReceipt {
    if (simulated) return BnbReceipt(hash, BnbReceipt.Status.SUCCESS, null)
    if (!TradeGuard.txHash.matches(hash)) throw IllegalStateException(BAD_HASH_MESSAGE)
    repeat(60) {
        val receipt = runCatching { StockApiClient.bnbReceipt(hash) }.getOrNull()
        if (receipt != null && receipt.status != BnbReceipt.Status.PENDING) return receipt
        delay(2000)
    }
    return BnbReceipt(hash, BnbReceipt.Status.PENDING, null)
}

private fun revertMessage(subject: String, reason: String?): String {
    val readable = UserFacingError.clean(reason, "")
    val why = if (readable.isEmpty()) "" else " ($readable)"
    return "$subject was reverted on BNB Chain$why. Nothing was traded; only the network fee was spent."
}

// MARK: - Quote stage

@Composable
private fun QuoteStage(
    ticker: String, logoUrl: String?, companyName: String?, onClose: () -> Unit,
    side: Side, onSide: (Side) -> Unit,
    payText: String, onPayText: (String) -> Unit,
    receiveText: String, onReceiveText: (String) -> Unit,
    focused: String?, onFocus: (String?) -> Unit,
    firstQuote: Boolean, loadingQuote: Boolean, quote: TradeQuote?, quoteError: String?,
    inputSymbol: String, outputSymbol: String, inputLogo: String?, outputLogo: String?,
    inputDigits: Int, outputDigits: Int,
    inputBalance: Double?, outputBalance: Double?, stockBalance: Double?,
    connected: Boolean, exceedsBalance: Boolean,
    onReview: () -> Unit,
) {
    val c = DS.colors
    val focus = LocalFocusManager.current
    var payCardHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Column(Modifier.fillMaxSize().imePadding()) {
        QuoteScreenHeader(ticker, logoUrl, companyName, onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(DSSpacing.lg), verticalArrangement = Arrangement.spacedBy(DSSpacing.lg)) {
            // Side switch + chain.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.background(c.secondary, CircleShape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Side.entries.forEach { option ->
                        Text(
                            option.label, style = DSFont.sm(FontWeight.SemiBold),
                            color = if (side == option) c.accentBrand else c.mutedForeground,
                            modifier = Modifier.clip(CircleShape).background(if (side == option) c.accentBrand.copy(alpha = 0.12f) else c.secondary)
                                .plainClickable { onSide(option) }.padding(horizontal = DSSpacing.lg, vertical = DSSpacing.sm),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.background(c.secondary, CircleShape).padding(horizontal = DSSpacing.smd, vertical = DSSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChainLogoImage("BSC", 18.dp)
                    Text("BNB Chain", style = DSFont.sm(FontWeight.Medium), color = c.foreground)
                }
            }

            // Pay / receive cards, the swap button on their seam.
            Box {
                Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                    Column(Modifier.fillMaxWidth().onSizeChanged { payCardHeight = it.height }.dsCard(), verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
                        Text("You pay", style = DSFont.sm(), color = c.mutedForeground)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                if (!firstQuote && focused != "pay") {
                                    CircularProgressIndicator(Modifier.size(22.dp), color = c.mutedForeground, strokeWidth = 2.dp)
                                } else {
                                    AmountField(payText, onPayText, "pay", onFocus)
                                }
                            }
                            TokenPill(inputLogo, inputSymbol)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                            val picks = if (side == Side.BUY) listOf("10" to 10.0, "50" to 50.0, "100" to 100.0)
                            else stockBalance?.takeIf { it > 0 }?.let { listOf("25%" to it * 0.25, "50%" to it * 0.5) } ?: emptyList()
                            picks.forEach { (label, amount) ->
                                val text = Fmt.flooredAmount(amount)
                                QuickPick(label, payText == text) {
                                    focus.clearFocus()
                                    onFocus(null)
                                    onPayText(text)
                                }
                            }
                            if (connected) {
                                val max = inputBalance ?: 0.0
                                QuickPick("Max", false, enabled = max > 0) {
                                    focus.clearFocus()
                                    onFocus(null)
                                    onPayText(Fmt.flooredAmount(max))
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            if (connected) BalanceLabel(inputBalance, inputSymbol, inputDigits)
                        }
                    }
                    Column(Modifier.fillMaxWidth().dsCard(), verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
                        Text("You receive", style = DSFont.sm(), color = c.mutedForeground)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                if (loadingQuote && quote == null && focused != "receive") {
                                    CircularProgressIndicator(Modifier.size(22.dp), color = c.mutedForeground, strokeWidth = 2.dp)
                                } else {
                                    AmountField(receiveText, onReceiveText, "receive", onFocus)
                                }
                            }
                            TokenPill(outputLogo, outputSymbol)
                        }
                        if (connected) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { BalanceLabel(outputBalance, outputSymbol, outputDigits) }
                    }
                }
                val offsetPx = payCardHeight + with(density) { (DSSpacing.xs / 2 - 18.dp).toPx() }
                Box(
                    Modifier.align(Alignment.TopCenter).offset { IntOffset(0, offsetPx.toInt()) }
                        .size(36.dp).clip(CircleShape).background(c.card).border(1.dp, c.border, CircleShape)
                        .plainClickable { onSide(if (side == Side.BUY) Side.SELL else Side.BUY) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.SwapVert, "Switch buy / sell", Modifier.size(17.dp), tint = c.accentBrand) }
            }

            QuoteDetailsCard(quote, quoteError, inputSymbol, outputSymbol, outputDigits, connected)
        }
        DSDivider()
        Column(Modifier.padding(horizontal = DSSpacing.lg, vertical = DSSpacing.md), verticalArrangement = Arrangement.spacedBy(DSSpacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
            DSButton("Review Order", onReview, size = DSButtonSize.LG, enabled = connected && quote?.transaction != null && !exceedsBalance)
            val (icon, text) = when {
                !connected -> Icons.Filled.AccountBalanceWallet to "Connect a BNB Chain wallet to review this order."
                exceedsBalance -> Icons.Outlined.ErrorOutline to "Not enough $inputSymbol in your wallet for this amount."
                else -> Icons.Filled.Lock to "You will review the transaction before signing."
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, null, Modifier.size(12.dp), tint = if (exceedsBalance) c.amber else c.mutedForeground)
                Text(text, style = DSFont.xs(), color = if (exceedsBalance) c.amber else c.mutedForeground)
            }
        }
    }
}

@Composable
private fun AmountField(text: String, onChange: (String) -> Unit, name: String, onFocus: (String?) -> Unit) {
    val c = DS.colors
    val requester = remember { FocusRequester() }
    BasicTextField(
        value = text,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }) },
        textStyle = DSFont.display(40).copy(color = c.foreground),
        singleLine = true,
        cursorBrush = SolidColor(c.accentBrand),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) Text("0", style = DSFont.display(40), color = c.mutedForeground)
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged { if (it.isFocused) onFocus(name) else onFocus(null) },
    )
}

@Composable
private fun TokenPill(logo: String?, symbol: String) {
    val c = DS.colors
    Row(Modifier.background(c.secondary, CircleShape).padding(horizontal = DSSpacing.smd, vertical = DSSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RemoteIconCircle(logo, 20.dp, symbol)
        Text(symbol, style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
    }
}

@Composable
private fun QuickPick(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = DS.colors
    Text(
        label, style = DSFont.sm(FontWeight.SemiBold),
        color = if (selected) c.accentBrand else c.mutedForeground,
        modifier = Modifier.clip(CircleShape).background(if (selected) c.accentBrand.copy(alpha = 0.12f) else c.secondary)
            .plainClickable(enabled = enabled, onClick = onClick).padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm)
            .then(if (enabled) Modifier else Modifier.background(c.background.copy(alpha = 0.5f))),
    )
}

@Composable
private fun BalanceLabel(amount: Double?, symbol: String, digits: Int) {
    val c = DS.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.AccountBalanceWallet, null, Modifier.size(12.dp), tint = c.mutedForeground)
        Text(amount?.let { "${Fmt.number(it, digits)} $symbol" } ?: "… $symbol", style = DSFont.xs(), color = c.mutedForeground, maxLines = 1)
    }
}

@Composable
private fun QuoteDetailsCard(quote: TradeQuote?, error: String?, inputSymbol: String, outputSymbol: String, outputDigits: Int, connected: Boolean) {
    val c = DS.colors
    Column(Modifier.fillMaxWidth().dsCard(), verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Text("Quote details", style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
        when {
            quote != null -> {
                Text("1 ${quote.symbol} = ${Fmt.usd(quote.pricePerToken)}", style = DSFont.display(20), color = c.foreground)
                DSDivider()
                DetailRow("Network fee", networkFee(quote))
                DSDivider()
                DetailRow("Minimum received", quote.minimumReceived?.let { "${Fmt.fixed(it, outputDigits)} $outputSymbol" } ?: "—")
                DSDivider()
                DetailRow("Max slippage", Fmt.fixed(quote.slippagePercent, 2) + "%")
                DSDivider()
                DetailRow("Price impact", priceImpactText(quote.priceImpactPercent))
                // Without a wallet the quote is priced for a placeholder taker.
                if (quote.approval != null && connected) {
                    Row(Modifier.padding(top = DSSpacing.xxs), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Verified, null, Modifier.size(14.dp), tint = c.amber)
                        Text("Approval needed — your wallet will ask you to approve $inputSymbol first, then to sign the swap.", style = DSFont.xs(), color = c.amber)
                    }
                }
            }
            error != null -> Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, Modifier.size(14.dp), tint = c.amber)
                Text(error, style = DSFont.xs(), color = c.mutedForeground)
            }
            else -> Box(Modifier.fillMaxWidth().padding(vertical = DSSpacing.lg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = c.mutedForeground, strokeWidth = 2.dp)
            }
        }
    }
}

/** Hex wei gas × gas price, shown in BNB (no BNB/USD feed to convert with). */
private fun networkFee(quote: TradeQuote): String {
    val gas = quote.transaction?.gas?.removePrefix("0x")?.toLongOrNull(16)
    val price = quote.transaction?.gasPrice?.removePrefix("0x")?.toLongOrNull(16)
    if (gas == null || price == null) return "Paid in BNB"
    return "~" + Fmt.fixed(gas.toDouble() * price.toDouble() / 1e18, 6) + " BNB"
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(label, style = DSFont.sm(), color = DS.colors.mutedForeground)
        Spacer(Modifier.weight(1f))
        Text(value, style = DSFont.sm(FontWeight.Medium), color = DS.colors.foreground)
    }
}

// MARK: - Review / processing / result

@Composable
private fun ReviewStage(quote: TradeQuote?, priceMoved: Boolean, inSym: String, outSym: String, inD: Int, outD: Int, onSubmit: () -> Unit, onBack: () -> Unit) {
    val c = DS.colors
    Column(Modifier.fillMaxSize().padding(bottom = DSSpacing.lg), verticalArrangement = Arrangement.spacedBy(DSSpacing.xl)) {
        Column(Modifier.fillMaxWidth().padding(top = DSSpacing.xl2), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            Icon(Icons.Outlined.FindInPage, null, Modifier.size(34.dp), tint = c.accentBrand)
            Text("Review Order", style = DSFont.xl(), color = c.foreground)
        }
        if (priceMoved) {
            Row(
                Modifier.padding(horizontal = DSSpacing.lg).fillMaxWidth().background(c.amber.copy(alpha = 0.12f), RoundedCornerShape(DSRadius.md)).padding(DSSpacing.smd),
                horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
            ) {
                Icon(Icons.Filled.Sync, null, Modifier.size(15.dp), tint = c.amber)
                Text("The price moved since you reviewed. Check the updated amounts below before submitting again.", style = DSFont.xs(), color = c.foreground)
            }
        }
        if (quote != null) {
            Column(Modifier.padding(horizontal = DSSpacing.lg).fillMaxWidth().dsCard(padding = DSSpacing.xxs)) {
                ReviewRow("You pay", "${Fmt.fixed(quote.payAmount, inD)} $inSym")
                DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                ReviewRow("You receive", "${Fmt.fixed(quote.receiveAmount, outD)} $outSym")
                DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                ReviewRow("Price", "1 ${quote.symbol} = ${Fmt.usd(quote.pricePerToken)}")
                DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                ReviewRow("Route", "PancakeSwap · BNB Smart Chain")
                DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                ReviewRow("Max slippage", Fmt.fixed(quote.slippagePercent, 2) + "%")
                if (quote.approval != null) {
                    DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                    ReviewRow("Wallet prompts", "2 · approve $inSym, then swap")
                }
            }
        }
        Row(
            Modifier.padding(horizontal = DSSpacing.lg).fillMaxWidth().background(c.amber.copy(alpha = 0.1f), RoundedCornerShape(DSRadius.md)).padding(DSSpacing.smd),
            horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
        ) {
            Icon(Icons.Filled.Warning, null, Modifier.size(15.dp), tint = c.amber)
            Text("This is a real on-chain swap. Confirming will move real $inSym from your connected wallet — this can't be undone.", style = DSFont.xs(), color = c.mutedForeground)
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = DSSpacing.lg), verticalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
            DSButton("Submit Order", onSubmit, size = DSButtonSize.LG, enabled = quote?.transaction != null)
            DSButton("Back", onBack, style = DSButtonStyle.GHOST, size = DSButtonSize.SM, fullWidth = true)
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(Modifier.padding(horizontal = DSSpacing.smd, vertical = DSSpacing.sm)) {
        Text(label, style = DSFont.sm(), color = DS.colors.mutedForeground)
        Spacer(Modifier.weight(1f))
        Text(value, style = DSFont.sm(FontWeight.Medium), color = DS.colors.foreground, textAlign = TextAlign.End)
    }
}

@Composable
private fun ProcessingStage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.xl)) {
            CircularProgressIndicator(Modifier.size(36.dp), color = DS.colors.mutedForeground, strokeWidth = 3.dp)
            Text(message, style = DSFont.lg(FontWeight.SemiBold), color = DS.colors.foreground, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = DSSpacing.xl2))
        }
    }
}

@Composable
private fun SuccessStage(hash: String, confirmed: Boolean, sideLabel: String, message: String, onDone: () -> Unit) {
    val c = DS.colors
    Column(Modifier.fillMaxSize().padding(DSSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.xl)) {
        Spacer(Modifier.weight(1f))
        Icon(if (confirmed) Icons.Filled.CheckCircle else Icons.Filled.Schedule, null, Modifier.size(64.dp), tint = if (confirmed) c.positive else c.amber)
        Text(if (confirmed) "$sideLabel Complete" else "Order Submitted", style = DSFont.xl2(), color = c.foreground)
        Text(message, style = DSFont.sm(), color = c.mutedForeground, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = DSSpacing.lg))
        TransactionIdCard(hash)
        Spacer(Modifier.weight(1f))
        DSButton("Done", onDone, size = DSButtonSize.LG)
    }
}

@Composable
private fun FailedStage(sideLabel: String, message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    val c = DS.colors
    Column(Modifier.fillMaxSize().padding(DSSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.xl)) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.Error, null, Modifier.size(64.dp), tint = c.destructive)
        Text("$sideLabel Failed", style = DSFont.xl2(), color = c.foreground)
        Text(message, style = DSFont.sm(), color = c.mutedForeground, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = DSSpacing.lg))
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.smd)) {
            DSButton("Try Again", onRetry, size = DSButtonSize.LG)
            DSButton("Close", onClose, style = DSButtonStyle.GHOST, size = DSButtonSize.SM, fullWidth = true)
        }
    }
}

@Suppress("unused")
private fun CoroutineScope.unused() = Unit
