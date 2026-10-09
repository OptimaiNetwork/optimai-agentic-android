package com.test.agenttrade.ui

import android.net.Uri
import com.test.agenttrade.wallet.WalletSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** The app's top-level tabs. */
enum class AppTab { STOCKS, AGENT, PORTFOLIO, ACTIVITY, SETTINGS }

/**
 * A ticker handed to the quote screen — from the keyboard's trading card,
 * the share flow, or a "Trade" button. Unique per handoff, so a second link
 * for the same ticker (different side or amount) opens fresh.
 */
data class QuoteTarget(
    val ticker: String,
    val side: String? = null,
    /** USDT on a buy, the stock itself on a sell. */
    val payAmount: Double? = null,
    val currentPrice: Double? = null,
    val id: String = UUID.randomUUID().toString(),
)

/**
 * Which tab is showing and which quote (if any) is presented over everything
 * — held here so any screen, the keyboard's deep link or the share flow can
 * route the app. The Android port of the iOS `AppRouter`.
 */
object AppRouter {
    /** Opens on Settings until a wallet is connected, Stocks after that. */
    private val _selectedTab = MutableStateFlow(if (WalletSessionStore.load() == null) AppTab.SETTINGS else AppTab.STOCKS)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _pendingQuote = MutableStateFlow<QuoteTarget?>(null)
    val pendingQuote: StateFlow<QuoteTarget?> = _pendingQuote.asStateFlow()

    fun select(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun presentQuote(target: QuoteTarget) {
        _pendingQuote.value = target
    }

    fun dismissQuote() {
        _pendingQuote.value = null
    }

    /**
     * `agenttrade://buy?ticker=NVDA&side=sell&amount=12.5` — the keyboard's
     * trading-card handoff. `provider` is accepted for parity with iOS links
     * but this build only trades bStocks.
     */
    fun handle(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "agenttrade") return false
        if (uri.host != "buy") return false
        val ticker = uri.getQueryParameter("ticker")?.takeIf { it.isNotBlank() } ?: return false
        presentQuote(
            QuoteTarget(
                ticker = ticker,
                side = uri.getQueryParameter("side"),
                payAmount = uri.getQueryParameter("amount")?.toDoubleOrNull(),
            ),
        )
        return true
    }
}
