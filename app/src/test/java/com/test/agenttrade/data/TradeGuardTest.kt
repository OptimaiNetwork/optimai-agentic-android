package com.test.agenttrade.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class TradeGuardTest {
    private val router = PancakeSwapRouters.AGGREGATOR
    private val token = "0x55d398326f99059fF775485246999027B3197955"
    private val other = "0x000000000000000000000000000000000000dEaD"

    private fun approveData(spender: String) =
        "0x095ea7b3" + spender.removePrefix("0x").lowercase().padStart(64, '0') + "f".repeat(64)

    private fun quote(swapTo: String? = router, spender: String? = router, data: String? = null) = TradeQuote(
        side = "buy", ticker = "NVDA", symbol = "NVDAB", payToken = "USDT",
        payAmount = 10.0, receiveAmount = 0.05, pricePerToken = 200.0,
        priceImpactPercent = null, minimumReceived = null, slippagePercent = 0.5,
        transaction = swapTo?.let { TradeQuote.UnsignedTransaction(it, "0x01", "0x0", null, null) },
        approval = spender?.let { TradeQuote.ApprovalNeeded(token, data ?: approveData(it), it) },
    )

    @Test fun acceptsKnownRouterSwapAndApproval() = assertNull(TradeGuard.violation(quote()))

    @Test fun acceptsRouterRegardlessOfCase() =
        assertNull(TradeGuard.violation(quote(swapTo = router.lowercase(), spender = router.uppercase().replace("0X", "0x"))))

    @Test fun rejectsUnknownSwapTarget() = assertNotNull(TradeGuard.violation(quote(swapTo = other, spender = null)))

    @Test fun rejectsUnknownSpender() = assertNotNull(TradeGuard.violation(quote(spender = other)))

    @Test fun rejectsSpenderThatDiffersFromSwapTarget() = assertNotNull(TradeGuard.violation(quote(swapTo = router, spender = other)))

    @Test fun rejectsApprovalCalldataForAnotherSpender() =
        assertNotNull(TradeGuard.violation(quote(data = approveData(other))))

    @Test fun rejectsNonApproveCalldata() =
        assertNotNull(TradeGuard.violation(quote(data = "0xa9059cbb" + "0".repeat(128))))

    @Test fun rejectsOtherChains() = assertEquals("Orders can only be signed on BNB Smart Chain.", TradeGuard.violation(quote(), chainId = 1))

    @Test fun txHashMustBe32Bytes() {
        assertTrue(TradeGuard.txHash.matches("0x" + "a".repeat(64)))
        assertFalse(TradeGuard.txHash.matches("0xSIMULATEDTX123"))
        assertFalse(TradeGuard.txHash.matches("0x" + "a".repeat(63)))
        assertFalse(TradeGuard.txHash.matches("pending"))
    }
}
