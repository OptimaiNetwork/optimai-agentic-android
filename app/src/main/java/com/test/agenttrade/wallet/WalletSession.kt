package com.test.agenttrade.wallet

import com.test.agenttrade.R
import com.test.agenttrade.data.AppPrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A wallet the user can pick to connect with. WalletConnect works with any
 * compliant wallet — this list is which ones the app offers a direct deep
 * link for; picking one decides which app later requests bring forward.
 */
enum class WalletApp(
    val displayName: String,
    val iconRes: Int,
    /** Android package, for launching it directly and checking it's installed. */
    val packageName: String,
    /** Universal link base — the iOS app's `deepLinkBase`, kept as the session's identity. */
    val deepLinkBase: String,
    /** Native scheme the wallet registers for WalletConnect URIs. */
    val nativeScheme: String,
) {
    METAMASK("MetaMask", R.drawable.wallet_metamask, "io.metamask", "https://metamask.app.link", "metamask"),
    TRUST_WALLET("Trust Wallet", R.drawable.wallet_trust, "com.wallet.crypto.trustapp", "https://link.trustwallet.com", "trust");

    companion object {
        fun fromDeepLinkBase(base: String?): WalletApp? = entries.firstOrNull { it.deepLinkBase == base }
    }
}

/** A connected WalletConnect session, persisted so the keyboard and share flow see it too. */
@Serializable
data class WalletSession(
    val address: String,
    /** EIP-155 chain id, e.g. 56 for BNB Smart Chain. */
    val chainId: Int,
    /** The WalletConnect session topic, used to route requests. */
    val topic: String,
    val walletName: String,
    val connectedAt: Long,
    /** Which wallet app the user picked (`WalletApp.deepLinkBase`). */
    val walletDeepLinkBase: String? = null,
    /** Debug builds only: a simulated wallet that signs nothing real. */
    val isSimulated: Boolean = false,
) {
    val walletApp: WalletApp? get() = WalletApp.fromDeepLinkBase(walletDeepLinkBase)

    val shortAddress: String get() = if (address.length > 10) "${address.take(4)}…${address.takeLast(4)}" else address

    /** The wallet approves whatever chain is active in it at connect time. */
    val chainDisplayName: String
        get() = when (chainId) {
            REQUIRED_CHAIN_ID -> "BNB Smart Chain"
            97 -> "BNB Testnet"
            1 -> "Ethereum"
            8453 -> "Base"
            137 -> "Polygon"
            else -> "Chain $chainId"
        }

    val isOnRequiredChain: Boolean get() = chainId == REQUIRED_CHAIN_ID

    companion object {
        /** AgentTrade only signs orders on BNB Smart Chain. */
        const val REQUIRED_CHAIN_ID = 56
    }
}

object WalletSessionStore {
    private const val KEY = "walletSession"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): WalletSession? = AppPrefs.getString(KEY)?.let { runCatching { json.decodeFromString<WalletSession>(it) }.getOrNull() }

    fun save(session: WalletSession?) = AppPrefs.putString(KEY, session?.let { json.encodeToString(WalletSession.serializer(), it) })
}

class WalletException(message: String) : Exception(message) {
    companion object {
        val notConnected get() = WalletException("No wallet is connected.")
        val rejected get() = WalletException("The wallet rejected the request.")
        val timedOut get() = WalletException("Timed out waiting for the wallet to respond.")
        val wrongChain get() = WalletException("Switch your wallet to BNB Smart Chain, then try again.")
        val notInstalled get() = WalletException("Install the wallet app, then try again.")
    }
}
