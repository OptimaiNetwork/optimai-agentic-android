package com.test.agenttrade.keyboard

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.test.agenttrade.data.BnbBalance
import com.test.agenttrade.data.Fmt
import com.test.agenttrade.data.MarketItem
import com.test.agenttrade.data.StockApiClient
import com.test.agenttrade.data.UserFacingError
import com.test.agenttrade.wallet.WalletSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The state and logic behind the keyboard's toolbar — the Android port of
 * the iOS `StockTickerToolbar`'s `@State` and functions, on bStocks:
 *
 * - a live ticker strip (`/stocks/market`), tap a chip → banner + trading card
 * - an "@optimai …" mention watched live in the host field: a stock alone
 *   opens the banner, a stock and a buy/sell instruction opens the card too,
 *   pre-filled; a question ("…?") is only ever askable
 * - tapping the glowing brand chip asks `/agent/ask-optimai`
 *
 * Mention matching is entirely on-device (the bundled alias table); only the
 * resolved ticker is looked up, as one `?q=<ticker>&limit=1` request.
 */
class TickerToolbarModel(
    private val context: Context,
    private val scope: CoroutineScope,
    /** Text before / after the caret in the host field. */
    private val readText: () -> Pair<String, String>?,
    private val openUrl: (Uri) -> Unit,
) {
    var items by mutableStateOf<List<MarketItem>>(emptyList())
        private set

    /** The chip whose banner is showing — set by a tap or a recognized mention. */
    var cardItem by mutableStateOf<MarketItem?>(null)
        private set

    /** The trading card itself, below the banner. */
    var isTradingCardOpen by mutableStateOf(false)
        private set

    var payBalance by mutableStateOf<BnbBalance?>(null)
        private set
    var heldQuantity by mutableStateOf<Double?>(null)
        private set
    var cardSide by mutableStateOf(TradingCardSide.BUY)
        private set
    var payAmount by mutableStateOf(100.0)
        private set
    var amountDraft by mutableStateOf("")
        private set
    private var isFreshKeypadEntry = false

    /** True while the card's numpad is open — the key grid is dropped. */
    var isKeypadActive by mutableStateOf(false)
        private set
    /** True while the ask answer is showing — it *is* the whole keyboard. */
    var isNewsPreviewActive by mutableStateOf(false)
        private set

    private var lastProcessedMention: String? = null
    /** The live "@optimai …" mention — the brand chip glows while non-null. */
    var pendingNewsQuery by mutableStateOf<String?>(null)
        private set
    var activeNewsQuery by mutableStateOf<String?>(null)
        private set
    var newsAnswer by mutableStateOf<String?>(null)
        private set
    var newsError by mutableStateOf<String?>(null)
        private set
    var isLoadingNews by mutableStateOf(false)
        private set

    /** The input is a password field: nothing is read, nothing is shown. */
    var isSuppressed by mutableStateOf(false)

    private var lastStripFetch = 0L
    /**
     * When the banner/card last appeared. A card opened by a mention appears
     * mid-typing and the keyboard grows to fit it — for a moment the card
     * sits where keys were, so its controls ignore taps until it's settled.
     */
    private var cardShownAt = 0L
    private val isCardSettled: Boolean get() = SystemClock.uptimeMillis() - cardShownAt > CARD_SETTLE_MS
    private var balanceJob: Job? = null
    private var pollJob: Job? = null
    private val aliases by lazy { StockAliasCatalog.get(context) }

    val isAskable: Boolean get() = pendingNewsQuery != null

    /** The BNB wallet the app has connected, if any. A local read. */
    private val connectedAddress: String? get() = WalletSessionStore.load()?.address

    // MARK: - Lifecycle

    /** Each time the keyboard shows. */
    fun onShow() {
        if (items.isEmpty() || SystemClock.uptimeMillis() - lastStripFetch > STRIP_REFRESH_MS) loadStrip()
        // A slower fallback for text changes the editor doesn't report.
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(3000)
                checkForMention()
            }
        }
        checkForMention()
        loadBalancesIfNeeded()
    }

    fun onHide() {
        pollJob?.cancel()
        isKeypadActive = false
    }

    /** Every keystroke / caret move the editor reports. */
    fun onTextChanged() = checkForMention()

    private fun loadStrip() {
        lastStripFetch = SystemClock.uptimeMillis()
        scope.launch {
            try {
                val response = StockApiClient.market(limit = 12)
                items = response.items.filter { it.referencePrice != null }
            } catch (e: Exception) {
                Log.w(TAG, "strip: fetch failed", e)
                lastStripFetch = 0
            }
        }
    }

    // MARK: - Mentions

    /**
     * Reads both sides of the caret, so a mention the user tapped back into
     * still counts whatever follows the caret. Whole-word matching means a
     * word still being typed ("nvd…") simply matches nothing yet.
     */
    private fun checkForMention() {
        if (isSuppressed) {
            pendingNewsQuery = null
            return
        }
        val (before, after) = readText() ?: return
        val mention = TradeIntentMatch.extractMention(before + after)
        pendingNewsQuery = mention
        if (mention == null || mention == lastProcessedMention) return
        // Matching is deterministic, so dedup as soon as it's read.
        lastProcessedMention = mention

        // A question is only ever headed for the ask flow — and closes a
        // banner an earlier, still-partial keystroke opened.
        if (mention.contains("?")) {
            if (cardItem != null) dismissCard()
            return
        }
        // A stray "@optimai", or a stock bStocks doesn't carry, stays silent.
        val match = TradeIntentMatch.parse(mention, aliases) ?: return
        scope.launch {
            val stock = fetchStock(match.ticker) ?: return@launch
            val intent = match.intent
            if (intent != null) {
                openCard(stock, intent.side, intent.payAmount(stock.referencePrice))
            } else {
                // A recognized stock but no clear instruction — just the banner.
                if (cardItem?.symbol != stock.symbol) cardShownAt = SystemClock.uptimeMillis()
                cardItem = stock
                isTradingCardOpen = false
                isKeypadActive = false
                loadBalancesIfNeeded()
            }
        }
    }

    /** One small `q=<ticker>` request on the bStocks catalog. */
    private suspend fun fetchStock(ticker: String): MarketItem? = try {
        StockApiClient.market(query = ticker, limit = 1).items.firstOrNull { it.ticker.equals(ticker, ignoreCase = true) }
            ?: items.firstOrNull { it.ticker.equals(ticker, ignoreCase = true) }
    } catch (e: Exception) {
        Log.w(TAG, "fetchStock($ticker): ${e.message}")
        null
    }

    // MARK: - Chips / card

    /** Tapping the same chip again closes its banner; another opens fresh. */
    fun tapChip(item: MarketItem) {
        if (cardItem?.symbol == item.symbol) dismissCard() else openCard(item)
    }

    /**
     * The one place the card opens — a chip tap or a mention with a clear
     * instruction — so side/amount/keypad never carry over from the last card.
     */
    fun openCard(item: MarketItem, side: TradingCardSide = TradingCardSide.BUY, amount: Double = 100.0) {
        val changed = cardItem?.symbol != item.symbol
        if (changed || !isTradingCardOpen) cardShownAt = SystemClock.uptimeMillis()
        cardItem = item
        isTradingCardOpen = true
        cardSide = side
        payAmount = amount
        isKeypadActive = false
        amountDraft = ""
        if (changed) {
            payBalance = null
            heldQuantity = null
        }
        loadBalancesIfNeeded()
    }

    /** The ✕ on the banner (a tap — so only once the card has settled). */
    fun closeCard() {
        if (!isCardSettled) return
        dismissCard()
    }

    private fun dismissCard() {
        cardItem = null
        isTradingCardOpen = false
        isKeypadActive = false
        balanceJob?.cancel()
    }

    /** Carries the last computed receive amount over as the new pay amount. */
    fun swapSide() {
        if (!isCardSettled) return
        payAmount = receiveAmount()
        cardSide = if (cardSide == TradingCardSide.BUY) TradingCardSide.SELL else TradingCardSide.BUY
        isKeypadActive = false
    }

    fun receiveAmount(): Double {
        val price = cardItem?.referencePrice ?: 0.0
        if (price <= 0) return 0.0
        return if (cardSide == TradingCardSide.BUY) payAmount / price else payAmount * price
    }

    /**
     * Never a placeholder: balances load only once the app actually has a
     * wallet connected, and only for the stock the banner shows.
     */
    private fun loadBalancesIfNeeded() {
        balanceJob?.cancel()
        val item = cardItem
        val address = connectedAddress
        if (item == null || address == null) {
            payBalance = null
            heldQuantity = null
            return
        }
        balanceJob = scope.launch {
            val usdt = async { runCatching { StockApiClient.bnbBalance(address, "USDT") }.getOrNull() }
            val stock = async { runCatching { StockApiClient.bnbBalance(address, item.ticker) }.getOrNull() }
            payBalance = usdt.await()
            heldQuantity = stock.await()?.amount
        }
    }

    val payBalanceText: String?
        get() = payBalance?.takeIf { connectedAddress != null }?.let { "${Fmt.number(it.amount, 2)} ${it.token}" }

    fun heldText(symbol: String): String? =
        heldQuantity?.takeIf { connectedAddress != null }?.let { "${Fmt.number(it, 4)} $symbol" }

    // MARK: - Keypad

    /** Tapping "From": the numpad opens seeded with the current amount, selected. */
    fun beginEditingAmount() {
        if (!isCardSettled) return
        amountDraft = Fmt.trimmedAmount(payAmount)
        isFreshKeypadEntry = true
        isKeypadActive = true
    }

    fun keypadKey(key: String) {
        // Only the first press replaces the pre-filled amount, like typing
        // over a fully selected text field.
        val first = isFreshKeypadEntry
        isFreshKeypadEntry = false
        when (key) {
            "⌫" -> amountDraft = if (first) "" else amountDraft.dropLast(1)
            "." -> amountDraft = when {
                first -> "0."
                amountDraft.contains('.') -> amountDraft
                amountDraft.isEmpty() -> "0."
                else -> "$amountDraft."
            }
            else -> {
                if (first) {
                    amountDraft = key
                    return
                }
                if (amountDraft.length < 9) amountDraft += key
            }
        }
    }

    fun keypadDone() {
        amountDraft.toDoubleOrNull()?.takeIf { it > 0 }?.let { payAmount = it }
        isKeypadActive = false
    }

    /**
     * Hands the exact side and amount typed here to the app's real quote
     * screen — `agenttrade://buy?ticker=…&side=…&amount=…`.
     */
    fun buy() {
        if (!isCardSettled) return
        val item = cardItem ?: return
        val uri = Uri.Builder().scheme("agenttrade").authority("buy")
            .appendQueryParameter("ticker", item.ticker)
            .appendQueryParameter("provider", "bstock")
            .appendQueryParameter("side", cardSide.raw)
            .appendQueryParameter("amount", Fmt.trimmedAmount(payAmount))
            .build()
        openUrl(uri)
    }

    // MARK: - Ask

    fun startAskingNews() {
        val query = pendingNewsQuery ?: return
        activeNewsQuery = query
        newsAnswer = null
        newsError = null
        isNewsPreviewActive = true
        isLoadingNews = true
        scope.launch {
            try {
                val result = StockApiClient.askOptimAI(query)
                if (result.understood && result.summary != null) {
                    newsAnswer = result.summary
                } else {
                    newsError = UserFacingError.clean(result.note, "Couldn't find a clear answer for that.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "askOptimAI failed: ${e.message}")
                newsError = "Something went wrong — try again."
            }
            isLoadingNews = false
        }
    }

    fun closeNewsPreview() {
        isNewsPreviewActive = false
    }

    companion object {
        private const val TAG = "OptimAIKeyboard"
        private const val STRIP_REFRESH_MS = 60_000L
        private const val CARD_SETTLE_MS = 500L

        /** Issuer boilerplate stripped for the narrow keyboard rows. */
        fun displayName(item: MarketItem): String {
            val raw = item.name ?: item.underlyingName ?: item.ticker
            val cleaned = raw.replace(" (Ondo)", "").replace(" Tokenized Stock", "")
                .replace(" Tokenized ETF", " ETF").replace(" Tokenized", "").trim()
            return cleaned.ifEmpty { item.ticker }
        }

        fun priceText(price: Double?): String = price?.let { "$" + Fmt.number(it, if (it < 10) 4 else 2) } ?: "—"

        fun changeText(pct: Double?): String = pct?.let { (if (it >= 0) "+" else "") + Fmt.number(it, 2) + "%" } ?: ""
    }
}
