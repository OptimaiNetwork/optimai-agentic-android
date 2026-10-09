package com.test.agenttrade.data

/**
 * The PancakeSwap contract on BNB Smart Chain (chain id 56) that a server-built
 * swap or approval may target. The app refuses to hand a wallet any transaction
 * whose destination is not on this list (see [TradeGuard]).
 *
 * Source: measured live. The server's `POST /trade/quote` for a bStock buy
 * (USDT in) and sell (stock token in) returned this address as both the swap
 * `to` and the approval `spender`; it is the `aggregatorAddress` that the
 * PancakeSwap Unified Swap API (https://swap.pancakeswap.com, `sources=agg`)
 * returns for BSC. See the PancakeSwap developer docs
 * (https://developer.pancakeswap.finance) for their router contracts. If
 * PancakeSwap rotates this address, trades fail with a clear "blocked" message
 * until the list is updated, so re-check a live quote before extending it.
 */
object PancakeSwapRouters {
    /** BNB Smart Chain mainnet. */
    const val BSC_CHAIN_ID = 56

    /** The router the Unified Swap API uses for `sources=agg` quotes. */
    const val AGGREGATOR = "0x2f68417A18dA681589F4eA64B9Cc9839209acfF7"

    private val known: Set<String> = setOf(AGGREGATOR).map { it.lowercase() }.toSet()

    fun isKnown(address: String): Boolean = address.trim().lowercase() in known
}
