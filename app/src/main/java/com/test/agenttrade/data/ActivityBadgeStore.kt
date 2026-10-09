package com.test.agenttrade.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * How many of the active wallet's transactions arrived since the user last
 * opened Activity — the number on the tab's badge.
 *
 * "Seen" is a timestamp per wallet, taken from the newest transaction the
 * server returned when Activity was last opened (server time on both sides).
 * A just-submitted order counts at once via `noteSubmitted`, before the
 * server has recorded it.
 */
object ActivityBadgeStore {
    private val _unseen = MutableStateFlow(0)
    val unseenCount: StateFlow<Int> = _unseen.asStateFlow()

    private var activeWallet: String? = null
    private var serverUnseen = 0
    private val pending = mutableMapOf<String, MutableSet<String>>()
    private var lastRefresh = 0L
    private val mutex = Mutex()

    private const val REFRESH_INTERVAL_MS = 20_000L
    private const val PAGE_SIZE = 50

    private fun seenKey(wallet: String) = "activityLastSeen.$wallet"

    suspend fun setActiveWallet(wallet: String?) {
        if (wallet != activeWallet) {
            activeWallet = wallet
            serverUnseen = 0
            lastRefresh = 0
            publish()
        }
        refresh()
    }

    suspend fun refresh(force: Boolean = false) {
        val wallet = activeWallet ?: return
        if (!mutex.tryLock()) return
        try {
            if (!force && System.currentTimeMillis() - lastRefresh < REFRESH_INTERVAL_MS) return
            val page = runCatching { StockApiClient.walletTransactions(wallet, limit = PAGE_SIZE) }.getOrNull() ?: return
            if (wallet != activeWallet) return
            lastRefresh = System.currentTimeMillis()
            val listed = page.transactions.map { it.signature }.toSet()
            pending[wallet]?.removeAll(listed)
            val seen = AppPrefs.getLong(seenKey(wallet))
            if (seen != null) {
                serverUnseen = page.transactions.count { it.createdAt.toEpochMilli() > seen }
            } else {
                // First look at this wallet: its existing history isn't "new".
                AppPrefs.putLong(seenKey(wallet), page.transactions.firstOrNull()?.createdAt?.toEpochMilli() ?: 0L)
                serverUnseen = 0
            }
            publish()
        } finally {
            mutex.unlock()
        }
    }

    /** Right after an order is submitted, before the server lists it. */
    fun noteSubmitted(wallet: String, signature: String) {
        pending.getOrPut(wallet) { mutableSetOf() }.add(signature)
        publish()
    }

    /** Activity was opened on this wallet's list, newest first. */
    fun markSeen(wallet: String, newestMillis: Long?) {
        val current = AppPrefs.getLong(seenKey(wallet)) ?: 0L
        if (newestMillis != null && newestMillis > current) AppPrefs.putLong(seenKey(wallet), newestMillis)
        else if (AppPrefs.getLong(seenKey(wallet)) == null) AppPrefs.putLong(seenKey(wallet), 0L)
        pending[wallet]?.clear()
        if (wallet == activeWallet) serverUnseen = 0
        publish()
    }

    private fun publish() {
        val wallet = activeWallet
        _unseen.value = if (wallet == null) 0 else serverUnseen + (pending[wallet]?.size ?: 0)
    }

    @Suppress("unused")
    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock { block() }
}
