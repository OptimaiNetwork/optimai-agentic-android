package com.test.agenttrade.data

import com.test.agenttrade.wallet.WalletException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns any error into a sentence fit for the UI. Raw exception messages are
 * never shown: server `detail` strings and network errors can name hosts,
 * paths, upstream vendors or configuration, none of which a user should see.
 */
object UserFacingError {
    /** `fallback` is the screen's own "what failed" sentence. */
    fun message(error: Throwable, fallback: String): String = when (error) {
        is UnknownHostException, is ConnectException -> "You're offline. Check your connection and try again."
        is SocketTimeoutException -> "This is taking longer than usual. Please try again."
        is ApiException -> when (error.code) {
            429 -> "Too many requests. Wait a moment and try again."
            in 400..499 -> clean(error.detail, fallback)
            else -> fallback
        }
        // Wallet errors are written for the user.
        is WalletException -> clean(error.message, fallback)
        else -> fallback
    }

    private val technicalSymbols = listOf("://", "/", "{", "[", "_")
    private val technicalWords = listOf(
        "http", "https", "server", "api", "jupiter", "binance", "pancakeswap",
        "upstream", "configured", "exception", "traceback", "json", "localhost",
        "mongodb", "status code", "error code",
    )

    /**
     * Passes a message through only when it reads like plain language —
     * short, and free of the markers of an internal detail.
     */
    fun clean(message: String?, fallback: String): String {
        val text = message?.trim().orEmpty()
        if (text.isEmpty() || text.length > 140) return fallback
        val lowered = text.lowercase()
        val technical = technicalSymbols.any { lowered.contains(it) } ||
            technicalWords.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lowered) }
        return if (technical) fallback else text
    }
}
