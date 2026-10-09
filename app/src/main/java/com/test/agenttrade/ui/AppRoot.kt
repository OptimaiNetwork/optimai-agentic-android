package com.test.agenttrade.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.test.agenttrade.data.ActivityBadgeStore
import com.test.agenttrade.data.MarketItem
import com.test.agenttrade.ui.agent.AgentChatScreen
import com.test.agenttrade.ui.components.DSDivider
import com.test.agenttrade.ui.components.plainClickable
import com.test.agenttrade.ui.portfolio.ActivityScreen
import com.test.agenttrade.ui.portfolio.PortfolioScreen
import com.test.agenttrade.ui.quote.QuoteBuyScreen
import com.test.agenttrade.ui.settings.HowItWorksScreen
import com.test.agenttrade.ui.settings.KeyboardGuideScreen
import com.test.agenttrade.ui.settings.SettingsPage
import com.test.agenttrade.ui.settings.SettingsScreen
import com.test.agenttrade.ui.settings.ShareGuideScreen
import com.test.agenttrade.ui.stocks.StockDetailScreen
import com.test.agenttrade.ui.stocks.StocksScreen
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.wallet.WalletConnectManager

/**
 * The app: splash, then the five tabs (Stocks · Agent · Portfolio ·
 * Activity · Settings), with the quote screen presented over everything
 * whenever `AppRouter` has one — from a list, a chat card, the keyboard's
 * deep link or the share flow.
 */
@Composable
fun AppRoot() {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var showContent by rememberSaveable { mutableStateOf(false) }
    val c = DS.colors
    Box(Modifier.fillMaxSize().background(c.background)) {
        if (showContent) MainTabs()
        AnimatedVisibility(showSplash, enter = fadeIn(), exit = fadeOut(tween(400))) {
            SplashScreen(onContentReady = { showContent = true }, onFinished = {
                showContent = true
                showSplash = false
            })
        }
    }
}

private data class TabSpec(val tab: AppTab, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec(AppTab.STOCKS, "Stocks", Icons.Filled.ShowChart),
    TabSpec(AppTab.AGENT, "Agent", Icons.Filled.AutoAwesome),
    TabSpec(AppTab.PORTFOLIO, "Portfolio", Icons.Filled.PieChart),
    TabSpec(AppTab.ACTIVITY, "Activity", Icons.Filled.History),
    TabSpec(AppTab.SETTINGS, "Settings", Icons.Filled.Settings),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainTabs() {
    val c = DS.colors
    val selected by AppRouter.selectedTab.collectAsState()
    val quote by AppRouter.pendingQuote.collectAsState()
    val unseen by ActivityBadgeStore.unseenCount.collectAsState()
    val session by WalletConnectManager.session.collectAsState()
    var openStock by remember { mutableStateOf<MarketItem?>(null) }
    var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }

    // The badge counts whichever tab is showing; refreshed on resume.
    LaunchedEffect(session?.address) { ActivityBadgeStore.setActiveWallet(session?.address) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { ActivityBadgeStore.refresh() } }

    // A quote arriving (e.g. from the keyboard's Buy) takes the focus — the
    // field underneath shouldn't keep the keyboard up over it.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(quote?.id) {
        if (quote != null) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible

    BackHandler(enabled = quote == null && (selected == AppTab.STOCKS && openStock != null || selected == AppTab.SETTINGS && settingsPage != null)) {
        if (selected == AppTab.STOCKS) openStock = null else settingsPage = null
    }

    Box(Modifier.fillMaxSize()) {
        // Content rises with the keyboard; the tab bar hides under it, as on iOS.
        Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
                when (selected) {
                    AppTab.STOCKS -> AnimatedContent(openStock, transitionSpec = { pushTransition(targetState != null) }, label = "stocks") { stock ->
                        if (stock == null) StocksScreen { openStock = it }
                        else StockDetailScreen(stock.ticker, stock.logoUrl, onClose = { openStock = null }) { price ->
                            AppRouter.presentQuote(QuoteTarget(stock.ticker, currentPrice = price ?: stock.referencePrice))
                        }
                    }
                    AppTab.AGENT -> AgentChatScreen()
                    AppTab.PORTFOLIO -> PortfolioScreen()
                    AppTab.ACTIVITY -> ActivityScreen()
                    AppTab.SETTINGS -> AnimatedContent(settingsPage, transitionSpec = { pushTransition(targetState != null) }, label = "settings") { page ->
                        when (page) {
                            null -> SettingsScreen { settingsPage = it }
                            SettingsPage.KEYBOARD_GUIDE -> KeyboardGuideScreen { settingsPage = null }
                            SettingsPage.SHARE_GUIDE -> ShareGuideScreen { settingsPage = null }
                            SettingsPage.HOW_IT_WORKS -> HowItWorksScreen { settingsPage = null }
                            SettingsPage.DEBUG_CHART -> StockDetailScreen("NVDA", null, onClose = { settingsPage = null }) {}
                        }
                    }
                }
            }
            if (!imeVisible) TabBar(selected, unseen)
        }

        // The quote screen, presented over everything like an iOS sheet.
        AnimatedVisibility(
            visible = quote != null,
            enter = slideInVertically(tween(320)) { it },
            exit = slideOutVertically(tween(260)) { it },
        ) {
            val target = remember(quote?.id) { quote } ?: return@AnimatedVisibility
            BackHandler { AppRouter.dismissQuote() }
            Box(Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
                androidx.compose.runtime.key(target.id) {
                    QuoteBuyScreen(target.ticker, target.currentPrice, target.side, target.payAmount) { AppRouter.dismissQuote() }
                }
            }
        }
    }
}

private fun pushTransition(forward: Boolean) =
    if (forward) slideInHorizontally(tween(280)) { it } togetherWith slideOutHorizontally(tween(280)) { -it / 4 }
    else slideInHorizontally(tween(280)) { -it / 4 } togetherWith slideOutHorizontally(tween(280)) { it }

/** iOS-style tab bar: icon over label, sidebar background, primary tint. */
@Composable
private fun TabBar(selected: AppTab, unseen: Int) {
    val c = DS.colors
    Column(Modifier.fillMaxWidth().background(c.sidebar).windowInsetsPadding(WindowInsets.navigationBars)) {
        DSDivider()
        Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { spec ->
                val isSel = spec.tab == selected
                val tint = if (isSel) c.primary else c.mutedForeground
                Column(
                    Modifier.weight(1f).plainClickable { AppRouter.select(spec.tab) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box {
                        Icon(spec.icon, spec.label, Modifier.size(24.dp), tint = tint)
                        if (spec.tab == AppTab.ACTIVITY && unseen > 0) {
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp).size(18.dp).background(c.negative, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (unseen > 99) "99+" else "$unseen", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White) }
                        }
                    }
                    Text(spec.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = tint)
                }
            }
        }
    }
}
