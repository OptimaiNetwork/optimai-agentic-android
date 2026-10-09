package com.test.agenttrade.data

import java.time.Instant

/**
 * DTOs for the OptimAI Agentic FastAPI server, the Android counterparts of the
 * iOS client's `StockAPIClient.swift` types. Decimal fields arrive as JSON
 * strings (Python `Decimal`), so every numeric field is parsed from a string
 * (see `Json.kt`'s `dbl()`, which accepts either form).
 *
 * This build is bStocks-only: BNB Smart Chain, PancakeSwap routes, MetaMask /
 * Trust Wallet. Nothing here talks to the Solana catalogs.
 */

/** One row from `GET /stocks/market` — live price/change/sparkline. */
data class MarketItem(
    val symbol: String,
    val ticker: String,
    /** Full display name, when the provider sends one. */
    val name: String?,
    val underlyingName: String?,
    /** A hosted logo (BNB's CDN for bStocks rows). */
    val logoUrl: String?,
    val referencePrice: Double?,
    val priceChangePct24h: Double?,
    val volume24h: Double?,
    val marketCap: Double?,
    val sparkline: List<Double>?,
    val error: String?,
) {
    val id: String get() = symbol

    /**
     * Case-insensitive substring match on the ticker, token symbol and
     * names. An empty query matches everything.
     */
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return listOfNotNull(ticker, symbol, name, underlyingName).any { it.contains(q, ignoreCase = true) }
    }
}

data class MarketResponse(val total: Int, val items: List<MarketItem>)

data class StockQuote(
    val symbol: String,
    val ticker: String,
    val referencePrice: Double,
    val priceChangePct24h: Double?,
    val marketCap: Double?,
    val volume24h: Double?,
    val week52High: Double?,
    val week52Low: Double?,
    val isMarketOpen: Boolean,
    val nextOpenAt: Instant?,
    val nextCloseAt: Instant?,
)

data class Candle(
    val openTime: Instant,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    /** "regular" | "closed" */
    val session: String,
)

data class CandleSeries(val symbol: String, val ticker: String, val interval: String, val candles: List<Candle>)

/** `GET /stocks/{symbol_or_ticker}/info-details` — company profile + market data. */
data class InfoDetails(
    val symbol: String,
    val ticker: String?,
    val name: String,
    val underlyingName: String?,
    val companyProfileSummary: String?,
    val coinmarketcapAbout: String?,
    val description: String?,
    val logoUrl: String?,
    val category: String?,
    val tags: List<String>,
    val websiteUrl: String?,
    val twitterUrl: String?,
    val contracts: List<Contract>,
    val dividendYield: Double?,
    val marketData: MarketData?,
) {
    data class Contract(val network: String, val address: String)

    data class MarketData(
        val cmcRank: Int?,
        val circulatingSupply: Double?,
        val marketCap: Double?,
        val volume24h: Double?,
        val percentChange1h: Double?,
        val percentChange24h: Double?,
        val percentChange7d: Double?,
        val percentChange30d: Double?,
        val percentChange90d: Double?,
    )
}

/**
 * `POST /trade/quote` — a live-priced PancakeSwap route to buy or sell a
 * tokenized stock against USDT on BNB Smart Chain, including the unsigned
 * transaction the user's own wallet signs. `pay*` is always the input and
 * `receive*` the output — USDT→stock on a buy, stock→USDT on a sell.
 */
data class TradeQuote(
    val side: String,
    val ticker: String,
    /** The tokenized-stock symbol actually traded, e.g. "NVDAB". */
    val symbol: String,
    val payToken: String,
    val payAmount: Double,
    val receiveAmount: Double,
    val pricePerToken: Double,
    val priceImpactPercent: Double?,
    val minimumReceived: Double?,
    val slippagePercent: Double,
    val transaction: UnsignedTransaction?,
    /** The ERC-20 `approve` the wallet must send (and see confirmed) first. */
    val approval: ApprovalNeeded?,
) {
    data class UnsignedTransaction(val to: String, val data: String, val value: String, val gas: String?, val gasPrice: String?)
    data class ApprovalNeeded(val to: String, val data: String, val spender: String)
}

/** `GET /trade/balance` — what a wallet holds of one token on BNB Smart Chain. */
data class BnbBalance(val token: String, val amount: Double, val usd: Double?)

/** `GET /trade/receipt` — whether a broadcast transaction landed. */
data class BnbReceipt(val hash: String, val status: Status, val reason: String?) {
    enum class Status { PENDING, SUCCESS, REVERTED }
}

/** One fill recorded by `POST /wallet/track`, read back from the wallet routes. */
data class TrackedTrade(
    /** "bnb" for a bStocks fill. */
    val chain: String,
    /** "bstock" on this build. */
    val venue: String,
    val txId: String,
    val ticker: String,
    val symbol: String,
    val side: String,
    val inputSymbol: String,
    val inputAmount: Double,
    val outputSymbol: String,
    val outputAmount: Double,
    val pricePerToken: Double,
    val signature: String,
    val createdAt: Instant,
)

data class PortfolioResponse(val address: String, val trades: List<TrackedTrade>)

data class TransactionsPage(
    val address: String,
    val total: Int,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val transactions: List<TrackedTrade>,
)

/**
 * Net quantity held for one symbol — every buy's output minus every sell's
 * input. Same math `PortfolioScreen` groups every symbol with.
 */
fun List<TrackedTrade>.netQuantity(symbol: String): Double =
    fold(0.0) { total, t -> if (t.symbol != symbol) total else total + if (t.side == "buy") t.outputAmount else -t.inputAmount }

/** A Google Search result the answer was grounded in. */
data class AskSource(val id: String, val title: String, val publisher: String?, val url: String)

/** `POST /agent/ask-optimai` — the keyboard's "@optimai <question>". */
data class AskResult(val understood: Boolean, val summary: String?, val note: String?, val sources: List<AskSource>)

/**
 * `POST /agent/ask-optimai-pro` — the Agent tab. The server resolves stocks
 * against its own catalogs; this build re-resolves the ticker against bStocks
 * before showing a trade card.
 */
data class AskProResult(
    val previewType: PreviewType,
    val answer: String,
    val stock: MarketItem?,
    val trade: Trade?,
    val technical: AskProTechnical?,
    val sources: List<AskSource>,
) {
    enum class PreviewType { QUOTE_CARD, QUOTE, TECHNICAL, NONE }

    /** `inputAmount` is USD to spend on a buy, tokens to sell. */
    data class Trade(val side: String, val inputAmount: Double?)
}

// MARK: - Technicals

data class AskProTechnical(
    val kind: String,
    val indicator: String?,
    val snapshot: TechnicalSnapshot?,
    val liquidity: TechnicalLiquidity?,
    val scan: TechnicalScan?,
)

data class TechnicalSnapshot(
    val ticker: String,
    val symbol: String,
    val interval: String,
    val status: String,
    val candleCount: Int,
    val price: Double?,
    val summary: Summary?,
    val rsi: Rsi?,
    val macd: Macd?,
    val bollinger: Bollinger?,
    val levels: Levels?,
    val performance: Performance?,
    val offHours: OffHours?,
    val series: List<Point>,
) {
    val isReady: Boolean get() = status == "ok"

    data class Point(
        val t: Instant,
        val close: Double,
        val rsi: Double?,
        val macd: Double?,
        val macdSignal: Double?,
        val macdHistogram: Double?,
        val bbUpper: Double?,
        val bbMiddle: Double?,
        val bbLower: Double?,
        val ema20: Double?,
        val ema50: Double?,
    )

    data class Rsi(val value: Double, val state: String, val streak: Int)
    data class Macd(val macd: Double, val signal: Double, val histogram: Double, val cross: String?, val crossBarsAgo: Int?)
    data class Bollinger(val upper: Double, val middle: Double, val lower: Double, val percentB: Double, val bandwidthPercent: Double, val squeeze: Boolean)
    data class Levels(val supports: List<Double>, val resistances: List<Double>, val high: Double, val low: Double, val rangePosition: Double, val since: Instant)
    data class Performance(val change1d: Double?, val change7d: Double?, val change30d: Double?, val dailyMovePercent: Double?, val sparkline: List<Double>)
    data class OffHoursPoint(val t: Instant, val close: Double, val closed: Boolean)
    data class OffHours(val inProgress: Boolean, val changePercent: Double?, val fromPrice: Double?, val toPrice: Double?, val points: List<OffHoursPoint>)
    data class Signal(val name: String, val detail: String, val bias: String)
    data class Summary(val score: Int, val label: String, val signals: List<Signal>)
}

data class TechnicalLiquidity(val ticker: String, val symbol: String, val tiers: List<Tier>, val activityRatio: Double?) {
    data class Tier(val usd: Double, val available: Boolean, val priceImpactPercent: Double?)
}

data class TechnicalScan(val interval: String, val rows: List<Row>) {
    data class Row(val venue: String, val ticker: String, val symbol: String?, val logoUrl: String?, val price: Double?, val rsi: Double?, val state: String?)
}

/** `POST /catalyst/analyze-user-text` — which stock a shared post/link/text is about. */
data class SharedContentAnalysis(
    val requestedUrl: String?,
    val answer: String,
    val stocks: List<Match>,
    val preview: Preview,
) {
    data class Match(val ticker: String, val symbol: String, val name: String) {
        val displayName: String
            get() = name.replace(" Class A Common Stock", "").replace(" Common Stock", "")
                .replace(" Ordinary Shares", "").trim().ifEmpty { ticker }
    }

    data class Preview(val title: String?, val description: String?, val siteName: String?, val author: String?)
}
