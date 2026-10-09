package com.test.agenttrade.keyboard

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Offline ticker lookup for "@optimai" mention matching, built from the alias
 * table the server generates at `data/stock_aliases.json`
 * (ticker/symbol/company-name variants → canonical ticker) — bundled as an
 * asset, so matching a mention needs no fuzzy scoring and no network call.
 */
class StockAliasCatalog(val aliasMap: Map<String, String>) {
    companion object {
        @Volatile private var shared: StockAliasCatalog? = null

        fun get(context: Context): StockAliasCatalog = shared ?: synchronized(this) {
            shared ?: load(context).also { shared = it }
        }

        private fun load(context: Context): StockAliasCatalog = try {
            val text = context.assets.open("stock_aliases.json").bufferedReader().use { it.readText() }
            val root = Json.parseToJsonElement(text) as JsonObject
            val map = (root["alias_map"] as JsonObject).mapValues { it.value.jsonPrimitive.content }
            StockAliasCatalog(map)
        } catch (e: Exception) {
            Log.w("OptimAIKeyboard", "alias catalog failed to load", e)
            StockAliasCatalog(emptyMap())
        }
    }
}

/**
 * Matches lowercased word tokens against the alias table — a ticker
 * ("nvda"), a symbol ("nvdab"), or a run of words from the company's name.
 * Longest phrase wins.
 */
object StockAliasMatcher {
    /** Alias phrases are at most this many words in the catalog. */
    private const val MAX_PHRASE_WORDS = 8

    fun matchTicker(tokens: List<String>, catalog: StockAliasCatalog): String? {
        if (tokens.isEmpty() || catalog.aliasMap.isEmpty()) return null
        val maxN = minOf(MAX_PHRASE_WORDS, tokens.size)
        for (n in maxN downTo 1) {
            for (start in 0..(tokens.size - n)) {
                val phrase = tokens.subList(start, start + n).joinToString(" ")
                catalog.aliasMap[phrase]?.let { return it }
            }
        }
        return null
    }
}

/** Which asset the trading card's "From" box is spending. */
enum class TradingCardSide(val raw: String) { BUY("buy"), SELL("sell") }

/**
 * One offline-recognized "@optimai" mention — a stock, and optionally a
 * buy/sell instruction for it. Nothing typed near "@optimai" ever leaves the
 * keyboard to be parsed; only the resolved ticker is looked up.
 */
data class TradeIntentMatch(val ticker: String, val intent: Intent?) {
    data class Intent(val side: TradingCardSide, val quantity: Double, val isDollarAmount: Boolean = false) {
        /**
         * The card's "From" amount: USDT when buying, shares when selling.
         * "buy 100 nvidia" names a share count, so buying converts it to an
         * estimated USDT amount; "buy $50 nvidia" is already USDT. Selling is
         * the mirror image.
         */
        fun payAmount(price: Double?): Double {
            if (price == null || price <= 0) return quantity
            return when {
                side == TradingCardSide.BUY && !isDollarAmount -> quantity * price
                side == TradingCardSide.SELL && isDollarAmount -> quantity / price
                else -> quantity
            }
        }
    }

    companion object {
        fun parse(text: String, catalog: StockAliasCatalog): TradeIntentMatch? {
            val ticker = StockAliasMatcher.matchTicker(wordTokens(text), catalog) ?: return null
            return TradeIntentMatch(ticker, matchIntent(text))
        }

        /** Lowercased alphanumeric runs (Unicode letters and digits, like `CharacterSet.alphanumerics`). */
        fun wordTokens(text: String): List<String> {
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            text.lowercase().forEach { ch ->
                if (ch.isLetterOrDigit()) current.append(ch) else if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.clear()
                }
            }
            if (current.isNotEmpty()) tokens += current.toString()
            return tokens
        }

        private val buyWords = setOf("buy", "purchase")
        private val sellWords = setOf("sell")
        private val dollarWords = setOf("usd", "usdc", "usdt", "dollar", "dollars")

        private fun matchIntent(text: String): Intent? {
            val tokens = wordTokens(text).toSet()
            val side = when {
                tokens.any { it in buyWords } -> TradingCardSide.BUY
                tokens.any { it in sellWords } -> TradingCardSide.SELL
                else -> return null
            }
            val amount = firstAmount(text) ?: return Intent(side, 100.0)
            return Intent(side, amount.first, amount.second)
        }

        /**
         * The first plain number in the raw text (tokenizing would split "1.5"
         * on the "."). Dollar when a "$" sits right before or after it, or the
         * next word is a dollar word ("50 usdt").
         */
        private fun firstAmount(text: String): Pair<Double, Boolean>? {
            val chars = text.toList()
            val start = chars.indexOfFirst { it.isDigit() }
            if (start < 0) return null
            var end = start
            val digits = StringBuilder()
            while (end < chars.size && (chars[end].isDigit() || (chars[end] == '.' && !digits.contains('.')))) {
                digits.append(chars[end])
                end++
            }
            if (digits.endsWith(".")) digits.setLength(digits.length - 1)
            val value = digits.toString().toDoubleOrNull() ?: return null
            val before = chars.subList(0, start).lastOrNull { it != ' ' }
            val after = chars.subList(end, chars.size).dropWhile { it == ' ' }
            val nextWord = after.takeWhile { it.isLetter() }.joinToString("").lowercase()
            val isDollar = before == '$' || after.firstOrNull() == '$' || nextWord in dollarWords
            return value to isDollar
        }

        /**
         * From the last "@optimai" (case-insensitive) onward only, so text
         * typed before the mention is never considered. Null when there's no
         * mention, or nothing follows it yet.
         */
        fun extractMention(text: String): String? {
            val index = text.lastIndexOf("@optimai", ignoreCase = true)
            if (index < 0) return null
            val mention = text.substring(index).trim()
            if (mention.length <= "@optimai".length) return null
            return mention.take(300)
        }
    }
}
