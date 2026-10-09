package com.test.agenttrade.data

/**
 * Checks the transactions a server hands back before a wallet is asked to sign
 * them. The server is trusted for prices, not for where the money goes: a
 * compromised or misconfigured server must not be able to point a swap or an
 * approval at an arbitrary contract.
 */
object TradeGuard {
    /** ERC-20 `approve(address,uint256)`. */
    private const val APPROVE_SELECTOR = "0x095ea7b3"
    private val address = Regex("^0x[0-9a-fA-F]{40}$")

    /** Matches a real transaction hash: 0x followed by 64 hex digits. */
    val txHash = Regex("^0x[0-9a-fA-F]{64}$")

    const val REJECTED_MESSAGE =
        "This order was blocked because it does not point at PancakeSwap on BNB Smart Chain. Nothing was sent to your wallet."

    /** A user-facing reason to refuse the quote's transactions, or null when they are safe to sign. */
    fun violation(quote: TradeQuote, chainId: Int = PancakeSwapRouters.BSC_CHAIN_ID): String? {
        if (chainId != PancakeSwapRouters.BSC_CHAIN_ID) {
            return "Orders can only be signed on BNB Smart Chain."
        }
        val swap = quote.transaction
        if (swap != null && !PancakeSwapRouters.isKnown(swap.to)) return REJECTED_MESSAGE
        val approval = quote.approval ?: return null
        if (!address.matches(approval.to)) return REJECTED_MESSAGE
        if (!PancakeSwapRouters.isKnown(approval.spender)) return REJECTED_MESSAGE
        if (swap != null && !approval.spender.equals(swap.to, ignoreCase = true)) return REJECTED_MESSAGE
        // The calldata itself must approve that spender, not just claim to.
        val data = approval.data.lowercase()
        if (!data.startsWith(APPROVE_SELECTOR) || data.length < APPROVE_SELECTOR.length + 64) return REJECTED_MESSAGE
        val encodedSpender = "0x" + data.substring(APPROVE_SELECTOR.length + 24, APPROVE_SELECTOR.length + 64)
        if (encodedSpender != approval.spender.lowercase()) return REJECTED_MESSAGE
        return null
    }
}
