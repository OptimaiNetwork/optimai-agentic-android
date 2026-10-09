package com.test.agenttrade.ui.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.test.agenttrade.data.ActivityBadgeStore
import com.test.agenttrade.data.Fmt
import com.test.agenttrade.data.StockApiClient
import com.test.agenttrade.data.TrackedTrade
import com.test.agenttrade.data.UserFacingError
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import com.test.agenttrade.ui.theme.DSSpacing
import com.test.agenttrade.ui.wallet.TabHeader
import com.test.agenttrade.wallet.WalletConnectManager
import kotlinx.coroutines.launch

/**
 * The Activity tab: every bStocks fill the server tracked for the connected
 * BNB wallet, newest first. Opening it clears the tab's badge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen() {
    val c = DS.colors
    val session by WalletConnectManager.session.collectAsState()
    val address = session?.address
    var txs by remember { mutableStateOf<List<TrackedTrade>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var logos by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    suspend fun load(reset: Boolean) {
        val a = address ?: return
        loading = true
        error = null
        try {
            val page = StockApiClient.walletTransactions(a, offset = if (reset) 0 else txs.size, limit = 20)
            if (a != address) return
            txs = if (reset) page.transactions else txs + page.transactions
            total = page.total
            hasMore = page.hasMore
            if (reset) ActivityBadgeStore.markSeen(a, page.transactions.firstOrNull()?.createdAt?.toEpochMilli())
        } catch (e: Exception) {
            error = UserFacingError.message(e, "Couldn't load your transactions. Please try again.")
        }
        loading = false
    }

    LaunchedEffect(address) {
        txs = emptyList()
        total = 0
        hasMore = false
        if (address == null) return@LaunchedEffect
        launch {
            logos = runCatching { StockApiClient.market(limit = 100).items.associate { it.symbol to it.logoUrl } }.getOrDefault(emptyMap())
        }
        load(true)
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        Box(Modifier.padding(start = DSSpacing.lg, end = DSSpacing.lg, top = DSSpacing.sm, bottom = DSSpacing.xs)) { TabHeader() }
        if (address == null) {
            EmptyState(Icons.Outlined.AccountBalanceWallet, "No Wallet Connected", "Connect a wallet to see its transactions here.")
            return@Column
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    load(true)
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DSSpacing.lg)) {
                TransactionsList(
                    txs, logos, loading, error, hasMore,
                    onRetry = { scope.launch { load(true) } },
                    onMore = { scope.launch { load(false) } },
                    caption = {
                        Row(Modifier.fillMaxWidth().padding(horizontal = DSSpacing.xxs), horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                            Text("BNB Chain", style = DSFont.xs(), color = c.mutedForeground)
                            Text("·", style = DSFont.xs(), color = c.mutedForeground)
                            Text(Fmt.shortAddress(address), style = DSFont.mono(11), color = c.mutedForeground)
                            Spacer(Modifier.weight(1f))
                            Text("$total transaction${if (total == 1) "" else "s"}", style = DSFont.xs(), color = c.mutedForeground)
                        }
                    },
                )
            }
        }
    }
}
