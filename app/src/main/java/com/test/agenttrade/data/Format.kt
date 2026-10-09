package com.test.agenttrade.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/**
 * Number formatting that matches Foundation's `.formatted(...)` output on
 * the iOS app (en_US): grouped thousands, fixed fraction digits, "$" prefix.
 */
object Fmt {
    private val symbols = DecimalFormatSymbols(Locale.US)

    private fun pattern(minFraction: Int, maxFraction: Int = minFraction, grouping: Boolean = true): DecimalFormat {
        val df = DecimalFormat(if (grouping) "#,##0" else "0", symbols)
        df.minimumFractionDigits = minFraction
        df.maximumFractionDigits = maxFraction
        df.roundingMode = java.math.RoundingMode.HALF_EVEN
        return df
    }

    /** `value.formatted(.number.precision(.fractionLength(n)))` */
    fun number(value: Double, fraction: Int): String = pattern(fraction).format(value)

    /** `.number.precision(.fractionLength(min...max))` */
    fun number(value: Double, minFraction: Int, maxFraction: Int): String = pattern(minFraction, maxFraction).format(value)

    /** `value.formatted(.currency(code: "USD"))` — e.g. "$1,234.56", "-$3.10". */
    fun usd(value: Double, fraction: Int = 2): String {
        val body = pattern(fraction).format(abs(value))
        return if (value < 0 && body.any { it in '1'..'9' }) "-$$body" else "$$body"
    }

    fun usd(value: Double, minFraction: Int, maxFraction: Int): String {
        val body = pattern(minFraction, maxFraction).format(abs(value))
        return if (value < 0) "-$$body" else "$$body"
    }

    fun usdOrDash(value: Double?): String = value?.let { usd(it) } ?: "—"

    /** `value.formatted(.percent.precision(.fractionLength(n)))` for a ratio (0.12 → "12.00%"). */
    fun percentOfRatio(ratio: Double, fraction: Int = 2): String = pattern(fraction).format(ratio * 100) + "%"

    /** "%.2f" — no grouping, like `String(format:)`. */
    fun fixed(value: Double, fraction: Int): String = String.format(Locale.US, "%.${fraction}f", value)

    /** "+1.23%" / "-0.45%" */
    fun signedPercent(value: Double, fraction: Int = 2): String = String.format(Locale.US, "%+.${fraction}f%%", value)

    /**
     * A clean, editable number — no trailing zeros, no scientific notation.
     * Mirrors the iOS quote screens' `trimmedAmountString`.
     */
    fun trimmedAmount(value: Double): String {
        if (value == Math.rint(value) && abs(value) < 1e15) return String.format(Locale.US, "%.0f", value)
        var text = String.format(Locale.US, "%.6f", value)
        while (text.endsWith("0")) text = text.dropLast(1)
        if (text.endsWith(".")) text = text.dropLast(1)
        return text
    }

    /** Like `trimmedAmount`, but rounded down — for amounts taken from a balance. */
    fun flooredAmount(value: Double): String = trimmedAmount(floor(value * 1_000_000) / 1_000_000)

    /** `$1.2B` / `$34.5M` / `$12.3K` — CoinMarketCap-style compact figures. */
    fun compactNumber(value: Double): String = when {
        abs(value) >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", value / 1_000_000_000)
        abs(value) >= 1_000_000 -> String.format(Locale.US, "%.2fM", value / 1_000_000)
        abs(value) >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000)
        else -> String.format(Locale.US, "%.0f", value)
    }

    /** `.number.notation(.compactName).precision(.fractionLength(0...2))` with a "$". */
    fun compactCurrency(value: Double?): String {
        if (value == null) return "—"
        val (scaled, suffix) = when {
            abs(value) >= 1e12 -> value / 1e12 to "T"
            abs(value) >= 1e9 -> value / 1e9 to "B"
            abs(value) >= 1e6 -> value / 1e6 to "M"
            abs(value) >= 1e3 -> value / 1e3 to "K"
            else -> value to ""
        }
        return "$" + pattern(0, 2, grouping = false).format(scaled) + suffix
    }

    @Suppress("unused")
    private val currency: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

    /** Parses a typed amount, accepting either decimal separator. */
    fun parseAmount(text: String): Double = text.replace(",", ".").toDoubleOrNull() ?: 0.0

    fun shortAddress(address: String, head: Int = 4, tail: Int = 4): String =
        if (address.length <= head + tail + 2) address else "${address.take(head)}…${address.takeLast(tail)}"
}
