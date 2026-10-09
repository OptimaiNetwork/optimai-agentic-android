package com.test.agenttrade.ui.wallet

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.agenttrade.ui.AppRouter
import com.test.agenttrade.ui.AppTab
import com.test.agenttrade.ui.components.ChainLogoImage
import com.test.agenttrade.ui.components.CircleCloseButton
import com.test.agenttrade.ui.components.DSButton
import com.test.agenttrade.ui.components.DSButtonSize
import com.test.agenttrade.ui.components.DSButtonStyle
import com.test.agenttrade.ui.components.OptimAIWordmark
import com.test.agenttrade.ui.components.RemoteIconCircle
import com.test.agenttrade.ui.components.dsCard
import com.test.agenttrade.ui.components.plainClickable
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import com.test.agenttrade.ui.theme.DSRadius
import com.test.agenttrade.ui.theme.DSSpacing
import com.test.agenttrade.wallet.WalletApp
import com.test.agenttrade.wallet.WalletConnectManager
import com.test.agenttrade.wallet.WalletSession
import kotlinx.coroutines.launch

/**
 * The header every main tab shares: the OptimAI wordmark top-left and the
 * account chip top-right, pinned to one height so the logo sits in exactly
 * the same spot on every tab.
 */
@Composable
fun TabHeader() {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        OptimAIWordmark(glints = true)
        Spacer(Modifier.weight(1f))
        AccountSwitcherChip()
    }
}

/**
 * Top-right account chip: a connection dot, the BNB Chain logo and the
 * connected wallet's short address. Tapping it opens the wallets sheet.
 */
@Composable
fun AccountSwitcherChip() {
    val session by WalletConnectManager.session.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    val c = DS.colors
    Row(
        Modifier.clip(CircleShape).background(c.secondary).plainClickable { showSheet = true }
            .padding(horizontal = DSSpacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(if (session != null) c.positive else c.amber, CircleShape))
        if (session != null) ChainLogoImage("BSC", 16.dp)
        Text(
            session?.shortAddress ?: "No Wallet Connected",
            style = if (session != null) DSFont.xs(FontWeight.Medium) else DSFont.sized(10.5f, FontWeight.Medium),
            color = c.foreground, maxLines = 1, softWrap = false,
        )
        Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(13.dp), tint = c.mutedForeground)
    }
    if (showSheet) WalletsSheet(onDismiss = { showSheet = false })
}

/** The connected account (if any) plus quick connects for the rest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsSheet(onDismiss: () -> Unit, title: String = "Wallets") {
    val session by WalletConnectManager.session.collectAsState()
    DSSheet(onDismiss = onDismiss, title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.lg)) {
            val current = session
            if (current == null) {
                Text("Connect a wallet to trade and track your positions.", style = DSFont.sm(), color = DS.colors.mutedForeground)
            } else {
                Text("Active Wallet", style = DSFont.xs(FontWeight.SemiBold), color = DS.colors.mutedForeground)
                ConnectedAccountCard(current)
            }
            WalletConnectList(showsConnected = false)
            ErrorLine()
        }
    }
}

/**
 * Bottom sheet listing every wallet the app can connect. Closes itself as
 * soon as a new account connects.
 */
@Composable
fun ConnectWalletSheet(onDismiss: () -> Unit) {
    val session by WalletConnectManager.session.collectAsState()
    val initial = remember { session?.address }
    LaunchedEffect(session?.address) {
        if (session != null && session?.address != initial) onDismiss()
    }
    DSSheet(onDismiss = onDismiss, title = "Connect Wallet") {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.lg)) {
            Text(
                "Choose a wallet to connect. You approve every trade in your wallet — OptimAI never holds your keys.",
                style = DSFont.sm(), color = DS.colors.mutedForeground,
            )
            WalletConnectList()
            ErrorLine()
        }
    }
}

@Composable
private fun ErrorLine() {
    val error by WalletConnectManager.lastError.collectAsState()
    error?.let {
        Text(it, style = DSFont.xs(), color = DS.colors.destructive, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Every wallet the app can connect, each with its own Connect / Disconnect
 * — MetaMask and Trust Wallet, both on BNB Smart Chain.
 */
@Composable
fun WalletConnectList(showsConnected: Boolean = true) {
    val session by WalletConnectManager.session.collectAsState()
    val connecting by WalletConnectManager.isConnecting.collectAsState()
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        WalletApp.entries.forEach { app ->
            val connected = session?.takeIf { it.walletApp == app || (it.walletApp == null && app == WalletApp.METAMASK) }
            if (showsConnected || connected == null) {
                WalletRow(
                    iconRes = app.iconRes,
                    name = app.displayName,
                    connectedAddress = connected?.shortAddress,
                    isConnecting = connecting == app,
                    enabled = session == null || connected != null,
                    onConnect = { scope.launch { WalletConnectManager.connect(app) } },
                    onDisconnect = { scope.launch { WalletConnectManager.disconnect() } },
                )
            }
        }
    }
}

/** A connected account as a wallet card, with its own Disconnect. */
@Composable
fun ConnectedAccountCard(session: WalletSession, isSelected: Boolean = false) {
    val scope = rememberCoroutineScope()
    val app = session.walletApp
    WalletRow(
        iconRes = app?.iconRes,
        name = app?.displayName ?: session.walletName,
        connectedAddress = session.shortAddress,
        isConnecting = false,
        isSelected = isSelected,
        onConnect = {},
        onDisconnect = { scope.launch { WalletConnectManager.disconnect() } },
    )
}

/**
 * One wallet per card: brand mark, name, and either "Not connected" or the
 * connected short address in green between the chain logo and a check.
 */
@Composable
private fun WalletRow(
    iconRes: Int?,
    name: String,
    connectedAddress: String?,
    isConnecting: Boolean,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val c = DS.colors
    Row(
        Modifier.fillMaxWidth()
            .then(if (isSelected) Modifier.border(1.dp, c.accentBrand.copy(alpha = 0.6f), RoundedCornerShape(DSRadius.lg)) else Modifier)
            .dsCard(padding = DSSpacing.smd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.md),
    ) {
        Box(Modifier.size(42.dp).background(c.secondary, RoundedCornerShape(DSRadius.md)), contentAlignment = Alignment.Center) {
            if (iconRes != null) Image(painterResource(iconRes), null, Modifier.size(24.dp)) else ChainLogoImage("BSC", 24.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
            if (connectedAddress != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.xxs)) {
                    ChainLogoImage("BSC", 12.dp)
                    Text(connectedAddress, style = DSFont.mono(11), color = c.accentBrand)
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(12.dp), tint = c.accentBrand)
                }
            } else {
                Text("Not connected", style = DSFont.xs(), color = c.mutedForeground)
            }
        }
        if (connectedAddress != null) {
            DSButton("Disconnect", onDisconnect, style = DSButtonStyle.OUTLINE, size = DSButtonSize.XS, fullWidth = false)
        } else {
            DSButton("Connect", onConnect, size = DSButtonSize.XS, fullWidth = false, enabled = enabled && !isConnecting, loading = isConnecting)
        }
    }
}

/** A Material bottom sheet in the app's colors, with a title bar and Done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DSSheet(onDismiss: () -> Unit, title: String? = null, skipPartiallyExpanded: Boolean = false, content: @Composable () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val c = DS.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.background,
        contentColor = c.foreground,
        dragHandle = { Box(Modifier.padding(top = 8.dp).size(36.dp, 5.dp).background(c.mutedForeground.copy(alpha = 0.4f), CircleShape)) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (title != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = DSSpacing.lg, vertical = DSSpacing.sm)) {
                    Text(title, style = DSFont.base(FontWeight.SemiBold), color = c.foreground, modifier = Modifier.align(Alignment.Center))
                    Text(
                        "Done", style = DSFont.base(FontWeight.SemiBold), color = c.accentBrand,
                        modifier = Modifier.align(Alignment.CenterStart).plainClickable(onClick = onDismiss),
                    )
                }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(DSSpacing.lg).padding(bottom = DSSpacing.xl)) {
                content()
            }
        }
    }
}

/**
 * The quote screen's top bar: close, the stock's logo with ticker and
 * company name, and the wallet chip.
 */
@Composable
fun QuoteScreenHeader(ticker: String, logoUrl: String?, companyName: String?, onClose: () -> Unit) {
    val c = DS.colors
    Row(
        Modifier.fillMaxWidth().padding(start = DSSpacing.lg, end = DSSpacing.lg, top = DSSpacing.sm, bottom = DSSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.smd),
    ) {
        CircleCloseButton(onClose, size = 32.dp, iconSize = 14.dp, tint = c.foreground)
        Box(Modifier.size(36.dp).border(1.dp, c.border, CircleShape)) { RemoteIconCircle(logoUrl, 36.dp, ticker) }
        Column(Modifier.weight(1f)) {
            Text(ticker, style = DSFont.base(FontWeight.Bold), color = c.foreground, maxLines = 1)
            Text(companyName ?: "Tokenized Stock", style = DSFont.xs(), color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        WalletAccountChip(onClose)
    }
}

/** The quote screen's account chip — no wallet → go connect one in Settings. */
@Composable
private fun WalletAccountChip(onClose: () -> Unit) {
    val session by WalletConnectManager.session.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    val c = DS.colors
    Row(
        Modifier.clip(CircleShape).background(c.secondary).plainClickable {
            if (session == null) {
                onClose()
                AppRouter.select(AppTab.SETTINGS)
            } else {
                showSheet = true
            }
        }.padding(horizontal = DSSpacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (session != null) ChainLogoImage("BSC", 16.dp)
        Box(Modifier.size(8.dp).background(if (session == null) c.amber else c.positive, CircleShape))
        Text(session?.shortAddress ?: "No Wallet Connected", style = DSFont.xs(FontWeight.Medium), color = c.foreground, maxLines = 1, softWrap = false)
        Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(13.dp), tint = c.mutedForeground)
    }
    if (showSheet) WalletsSheet(onDismiss = { showSheet = false }, title = "Account")
}

/**
 * A completed order's transaction id: shortened, accent-colored and
 * tappable (opens BscScan), with a copy button for the full id.
 */
@Composable
fun TransactionIdCard(txId: String) {
    val c = DS.colors
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val shortId = if (txId.length > 16) "${txId.take(8)}…${txId.takeLast(8)}" else txId
    Column(Modifier.fillMaxWidth().dsCard(padding = DSSpacing.md), verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
        Text("Transaction ID", style = DSFont.xs(), color = c.mutedForeground)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.plainClickable { openUrl(context, "https://bscscan.com/tx/$txId") },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(shortId, style = DSFont.mono(14, FontWeight.Medium).copy(textDecoration = TextDecoration.Underline), color = c.accentBrand)
                Icon(Icons.Filled.NorthEast, null, Modifier.size(11.dp), tint = c.accentBrand)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.height(30.dp).clip(CircleShape).background(c.secondary).plainClickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Transaction ID", txId))
                    copied = true
                }.padding(horizontal = DSSpacing.smd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(if (copied) Icons.Filled.CheckCircle else Icons.Filled.ContentCopy, null, Modifier.size(12.dp), tint = if (copied) c.positive else c.foreground)
                Text(if (copied) "Copied" else "Copy", style = DSFont.xs(FontWeight.SemiBold), color = if (copied) c.positive else c.foreground)
            }
        }
        Text("Opens on BscScan", fontSize = 11.sp, color = c.mutedForeground)
    }
}

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun SmallSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier.size(20.dp).width(20.dp), color = DS.colors.mutedForeground, strokeWidth = 2.dp)
}
