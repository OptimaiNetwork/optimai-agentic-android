package com.test.agenttrade.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Client for the OptimAI Agentic FastAPI server. The base URL comes from
 * `BuildConfig.API_BASE_URL` (set in `local.properties`) and
 * can be changed in Settings → Developer; the keyboard runs in this same
 * process, so it always reads the same value.
 *
 * bStocks-only: every catalog call is under `/stocks`, trades under `/trade`.
 */
object StockApiClient {
    /** The one catalog this build trades. */
    const val BSTOCK_PREFIX = "/stocks"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()

    val baseUrl: String get() = AppPrefs.baseUrl.trimEnd('/')

    /**
     * Some logos are served by the server itself as a path
     * (`/static/stock-logos/…`) — join those onto the base URL. Absolute
     * URLs and `data:` URIs pass through untouched.
     */
    fun absoluteUrl(value: String?): String? {
        if (value == null || !value.startsWith("/")) return value
        return baseUrl + value
    }

    // MARK: - Catalog

    suspend fun quote(ticker: String, prefix: String = BSTOCK_PREFIX): StockQuote =
        get("$prefix/${enc(ticker)}").asObject.let(Decode::quote)

    suspend fun candles(ticker: String, interval: String, limit: Int, prefix: String = BSTOCK_PREFIX): CandleSeries =
        get("$prefix/${enc(ticker)}/candles", "interval" to interval, "limit" to "$limit").asObject.let(Decode::candleSeries)

    /** Live rows for a browse list; `query` filters by ticker/symbol server-side. */
    suspend fun market(prefix: String = BSTOCK_PREFIX, query: String = "", limit: Int = 100): MarketResponse =
        get("$prefix/market", "q" to query, "limit" to "$limit").asObject.let(Decode::marketResponse)

    /** The catalog's size (`total`). */
    suspend fun catalogTotal(prefix: String = BSTOCK_PREFIX): Int =
        get(prefix, "limit" to "1").asObject.int("total") ?: 0

    suspend fun infoDetails(symbolOrTicker: String): InfoDetails =
        get("/stocks/${enc(symbolOrTicker)}/info-details").asObject.let(Decode::infoDetails)

    // MARK: - BNB Chain trading

    /**
     * Prices buying (`payAmount` of USDT in) or selling (`payAmount` of the
     * stock token in) `ticker` via PancakeSwap. Nothing is signed or sent
     * server-side.
     */
    suspend fun quoteTrade(
        ticker: String,
        side: String = "buy",
        payAmount: Double,
        wallet: String,
        payToken: String = "USDT",
        // "bnbchain" is the disabled legacy RFQ route; "pancakeswap" is live.
        venue: String = "pancakeswap",
        slippagePercent: String = "0.5",
    ): TradeQuote {
        val body = buildJsonObject {
            put("ticker", ticker)
            put("venue", venue)
            put("side", side)
            put("pay_token", payToken)
            put("pay_amount", payAmount)
            put("wallet", wallet)
            put("slippage_percent", slippagePercent)
        }
        return post("/trade/quote", body).asObject.let(Decode::tradeQuote)
    }

    /** `token` is a settlement token ("USDT") or a stock ticker ("NVDA"). */
    suspend fun bnbBalance(wallet: String, token: String): BnbBalance =
        get("/trade/balance", "wallet" to wallet, "pay_token" to token).asObject.let(Decode::bnbBalance)

    suspend fun bnbReceipt(hash: String): BnbReceipt =
        get("/trade/receipt", "hash" to hash).asObject.let(Decode::receipt)

    /**
     * Records a confirmed bStocks swap, identified by its transaction hash —
     * what Portfolio and Activity read back.
     */
    suspend fun trackBnbTrade(
        address: String,
        ticker: String,
        symbol: String,
        side: String,
        inputSymbol: String,
        inputAmount: Double,
        outputSymbol: String,
        outputAmount: Double,
        pricePerToken: Double,
        txHash: String,
    ): TrackedTrade {
        val body = buildJsonObject {
            put("chain", "bnb")
            put("venue", "bstock")
            put("address", address)
            put("ticker", ticker)
            put("symbol", symbol)
            put("side", side)
            put("input_symbol", inputSymbol)
            put("input_amount", inputAmount)
            put("output_symbol", outputSymbol)
            put("output_amount", outputAmount)
            put("price_per_token", pricePerToken)
            put("signature", txHash)
            put("tx_id", txHash)
        }
        return post("/wallet/track", body).asObject.let(Decode::trackedTrade)
    }

    suspend fun walletPortfolio(wallet: String): PortfolioResponse =
        get("/wallet/portfolio", "wallet" to wallet).asObject.let(Decode::portfolio)

    suspend fun walletTransactions(wallet: String, offset: Int = 0, limit: Int = 20): TransactionsPage =
        get("/wallet/transactions", "wallet" to wallet, "offset" to "$offset", "limit" to "$limit").asObject.let(Decode::transactionsPage)

    // MARK: - Agent

    suspend fun askOptimAI(text: String, limit: Int = 6): AskResult {
        val body = buildJsonObject {
            put("text", text)
            put("limit", limit)
        }
        return post("/agent/ask-optimai", body).asObject.let(Decode::askResult)
    }

    /** `history` is the chat so far, oldest first, as (role, text). */
    suspend fun askOptimAIPro(text: String, history: List<Pair<String, String>>, wallet: String?): AskProResult {
        val body = buildJsonObject {
            put("text", text)
            put("history", buildJsonArray {
                history.takeLast(12).forEach { (role, t) ->
                    add(buildJsonObject {
                        put("role", role)
                        put("text", t.take(1000))
                    })
                }
            })
            if (wallet != null) put("wallet", wallet)
        }
        return post("/agent/ask-optimai-pro", body).asObject.let(Decode::askProResult)
    }

    suspend fun analyzeUserText(text: String): SharedContentAnalysis =
        post("/catalyst/analyze-user-text", buildJsonObject { put("text", text) }).asObject.let(Decode::analysis)

    // MARK: - Transport

    private fun enc(segment: String): String = java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private suspend fun get(path: String, vararg query: Pair<String, String>): JsonElement {
        val url = (baseUrl + path).toHttpUrlOrNull()?.newBuilder()?.apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }?.build() ?: throw ApiException(-1, "Invalid request URL")
        return execute(Request.Builder().url(url).get().build())
    }

    private suspend fun post(path: String, body: JsonObject): JsonElement {
        val url = (baseUrl + path).toHttpUrlOrNull() ?: throw ApiException(-1, "Invalid request URL")
        val request = Request.Builder().url(url).post(body.toString().toRequestBody(jsonMedia)).build()
        return execute(request)
    }

    private suspend fun execute(request: Request): JsonElement = withContext(Dispatchers.IO) {
        val response = http.newCall(request).await()
        response.use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                // The trade endpoints always send `{"detail": "..."}` — the
                // sentence meant to be shown, not just the status code.
                val detail = runCatching { (json.parseToJsonElement(text) as? JsonObject)?.str("detail") }.getOrNull()
                throw ApiException(res.code, detail)
            }
            json.parseToJsonElement(text)
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { _, _, _ -> response.close() }
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }
}

class ApiException(val code: Int, val detail: String?) : Exception(detail ?: "Server returned HTTP $code")

/** Price impact as the quote screens show it — tiny favorable impacts read "<0.01%". */
fun priceImpactText(percent: Double?): String {
    if (percent == null) return "—"
    if (kotlin.math.abs(percent) < 0.01) return "<0.01%"
    return String.format(Locale.US, "%.2f%%", percent)
}
