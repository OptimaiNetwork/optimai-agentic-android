package com.test.agenttrade.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.test.agenttrade.BuildConfig
import com.test.agenttrade.data.AppPrefs
import com.test.agenttrade.share.ShareActivity
import com.test.agenttrade.ui.components.CircleBackButton
import com.test.agenttrade.ui.components.DSButton
import com.test.agenttrade.ui.components.DSButtonSize
import com.test.agenttrade.ui.components.DSButtonStyle
import com.test.agenttrade.ui.components.DSDivider
import com.test.agenttrade.ui.components.DSIconTile
import com.test.agenttrade.ui.components.DSMenuItem
import com.test.agenttrade.ui.components.dsCard
import com.test.agenttrade.ui.components.plainClickable
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import com.test.agenttrade.ui.theme.DSRadius
import com.test.agenttrade.ui.theme.DSSpacing
import com.test.agenttrade.ui.wallet.ConnectWalletSheet
import com.test.agenttrade.ui.wallet.ConnectedAccountCard
import com.test.agenttrade.ui.wallet.TabHeader
import com.test.agenttrade.wallet.WalletConnectManager

enum class SettingsPage { KEYBOARD_GUIDE, SHARE_GUIDE, HOW_IT_WORKS, DEBUG_CHART }

/**
 * The Settings tab: the connected wallet first, then setup guides for the
 * keyboard and the share action, How It Works, appearance, and (debug
 * builds) developer options.
 */
@Composable
fun SettingsScreen(onOpen: (SettingsPage) -> Unit) {
    val c = DS.colors
    val session by WalletConnectManager.session.collectAsState()
    val theme by AppPrefs.theme.collectAsState()
    var showConnect by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())
            .padding(horizontal = DSSpacing.lg).padding(top = DSSpacing.sm, bottom = DSSpacing.xl2),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.xl),
    ) {
        TabHeader()

        Section(if (session == null) "Active Wallets" else "Active Wallets (1)") {
            session?.let { ConnectedAccountCard(it) }
            if (session == null) {
                Text(
                    "Connect MetaMask or Trust Wallet for bStocks on BNB Chain. You approve every trade in your wallet.",
                    style = DSFont.xs(), color = c.mutedForeground, modifier = Modifier.padding(horizontal = DSSpacing.xs),
                )
            }
            session?.takeIf { !it.isOnRequiredChain }?.let { s ->
                Row(Modifier.fillMaxWidth().background(c.amber.copy(alpha = 0.1f), RoundedCornerShape(DSRadius.md)).padding(DSSpacing.smd), horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                    Icon(Icons.Filled.Warning, null, Modifier.size(14.dp), tint = c.amber)
                    Text("${s.walletName} is on ${s.chainDisplayName} — switch to BNB Smart Chain to sign orders.", style = DSFont.xs(), color = c.mutedForeground)
                }
            }
            if (session == null) {
                DSButton("Connect Wallet", { showConnect = true }, size = DSButtonSize.LG, icon = Icons.Filled.Add, modifier = Modifier.padding(top = DSSpacing.xxs))
            }
        }

        Section("Extensions") {
            Column(Modifier.fillMaxWidth().dsCard(padding = DSSpacing.xxs)) {
                LinkRow("OptimAI Keyboard", "Live tickers and trades in any app", Icons.Filled.Keyboard) { onOpen(SettingsPage.KEYBOARD_GUIDE) }
                DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                LinkRow("OptimAI Share Action", "Trade anything you share", Icons.Filled.Share) { onOpen(SettingsPage.SHARE_GUIDE) }
            }
        }

        Section("Explore") {
            Column(Modifier.fillMaxWidth().dsCard(padding = DSSpacing.xxs)) {
                LinkRow("How It Works", null, Icons.Filled.Bolt) { onOpen(SettingsPage.HOW_IT_WORKS) }
                if (BuildConfig.DEBUG) {
                    DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                    LinkRow("Debug Chart", null, Icons.Filled.ShowChart) { onOpen(SettingsPage.DEBUG_CHART) }
                }
            }
        }

        Section("Appearance") {
            Row(
                Modifier.fillMaxWidth().dsCard(padding = DSSpacing.sm).clip(RoundedCornerShape(DSRadius.sm)).background(c.secondary).padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AppPrefs.Theme.entries.forEach { t ->
                    val selected = t == theme
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(DSRadius.sm - 1.dp)).background(if (selected) c.card else c.secondary)
                            .plainClickable { AppPrefs.setTheme(t) }.padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            when (t) {
                                AppPrefs.Theme.SYSTEM -> Icons.Filled.SettingsBrightness
                                AppPrefs.Theme.LIGHT -> Icons.Filled.LightMode
                                AppPrefs.Theme.DARK -> Icons.Filled.DarkMode
                            }, null, Modifier.size(14.dp), tint = c.foreground,
                        )
                        Text(t.label, style = DSFont.sm(FontWeight.Medium), color = c.foreground)
                    }
                }
            }
        }

        Section("Developer") {
            DeveloperCard()
        }
    }
    if (showConnect) ConnectWalletSheet { showConnect = false }
}

/** The server address (the Mac's LAN IP changes with the network), and — debug only — a simulated wallet. */
@Composable
private fun DeveloperCard() {
    val c = DS.colors
    var url by remember { mutableStateOf(AppPrefs.baseUrl) }
    var saved by remember { mutableStateOf(false) }
    var simulated by remember { mutableStateOf(WalletConnectManager.simulatedMode) }
    Column(Modifier.fillMaxWidth().dsCard(padding = DSSpacing.md), verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
            DSIconTile(Icons.Filled.Dns, c.accentBrand)
            Column(Modifier.weight(1f)) {
                Text("Server", style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
                Text("OptimAI Agentic server address", style = DSFont.xs(), color = c.mutedForeground)
            }
        }
        Box(Modifier.fillMaxWidth().background(c.secondary, RoundedCornerShape(DSRadius.sm)).padding(DSSpacing.sm)) {
            BasicTextField(
                url, {
                    url = it
                    saved = false
                },
                textStyle = DSFont.mono(13).copy(color = c.foreground), singleLine = true, cursorBrush = SolidColor(c.accentBrand),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            DSButton(if (saved) "Saved" else "Save", {
                AppPrefs.baseUrl = url
                saved = true
            }, size = DSButtonSize.SM, modifier = Modifier.weight(1f))
            DSButton("Reset", {
                AppPrefs.baseUrl = ""
                url = AppPrefs.baseUrl
                saved = true
            }, style = DSButtonStyle.OUTLINE, size = DSButtonSize.SM, modifier = Modifier.weight(1f))
        }
        if (BuildConfig.DEBUG) {
            DSDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
                DSIconTile(Icons.Filled.Science, c.amber)
                Column(Modifier.weight(1f)) {
                    Text("Simulated wallet", style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
                    Text("Debug builds only — signs nothing, records nothing.", style = DSFont.xs(), color = c.mutedForeground)
                }
                Switch(
                    simulated, {
                        simulated = it
                        WalletConnectManager.simulatedMode = it
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.primaryForeground),
                )
            }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
        Text(title, style = DSFont.sm(FontWeight.SemiBold), color = DS.colors.mutedForeground)
        content()
    }
}

@Composable
private fun LinkRow(title: String, detail: String?, icon: ImageVector, onClick: () -> Unit) {
    val c = DS.colors
    DSMenuItem(onClick = onClick) {
        DSIconTile(icon, c.accentBrand)
        Spacer(Modifier.width(DSSpacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
            if (detail != null) Text(detail, style = DSFont.xs(), color = c.mutedForeground)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = c.mutedForeground)
    }
}

// MARK: - Guides

data class GuideStep(val icon: ImageVector, val title: String, val detail: String, val action: (() -> Unit)? = null)

@Composable
fun GuidePage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val c = DS.colors
    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = DSSpacing.lg, vertical = DSSpacing.sm)) {
            CircleBackButton(onBack)
            Text(title, style = DSFont.base(FontWeight.SemiBold), color = c.foreground, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DSSpacing.lg), verticalArrangement = Arrangement.spacedBy(DSSpacing.xl2)) {
            content()
        }
    }
}

@Composable
fun GradientHeading(text: String) {
    val c = DS.colors
    Text(text, style = DSFont.xl2().copy(brush = Brush.verticalGradient(listOf(c.foreground, c.foreground.copy(alpha = 0.75f)))))
}

@Composable
fun StepRow(step: GuideStep) {
    val c = DS.colors
    Row(
        Modifier.fillMaxWidth().then(if (step.action != null) Modifier.plainClickable(onClick = step.action) else Modifier)
            .padding(horizontal = DSSpacing.smd, vertical = DSSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.md),
    ) {
        DSIconTile(step.icon, c.accentBrand)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(step.title, style = DSFont.sm(FontWeight.SemiBold), color = if (step.action != null) c.accentBrand else c.foreground)
            Text(step.detail, style = DSFont.xs(), color = c.mutedForeground)
        }
        if (step.action != null) Icon(Icons.Filled.NorthEast, null, Modifier.padding(top = 2.dp).size(13.dp), tint = c.accentBrand)
    }
}

@Composable
fun GuideStepsCard(title: String, steps: List<GuideStep>) {
    Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        Text(title, style = DSFont.base(FontWeight.SemiBold), color = DS.colors.foreground)
        Column(Modifier.fillMaxWidth().dsCard(padding = DSSpacing.sm)) {
            steps.forEachIndexed { i, s ->
                if (i > 0) DSDivider(Modifier.padding(horizontal = DSSpacing.lg))
                StepRow(s)
            }
        }
    }
}

/** Whether the OptimAI keyboard is enabled, and whether it's the current one. */
private fun keyboardStatus(context: Context): Pair<Boolean, Boolean> {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabled = imm.enabledInputMethodList.any { it.packageName == context.packageName }
    val current = if (android.os.Build.VERSION.SDK_INT >= 34) {
        imm.currentInputMethodInfo?.packageName == context.packageName
    } else {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty().startsWith(context.packageName)
    }
    return enabled to current
}

/**
 * Settings → Extensions → OptimAI Keyboard: turning it on in Android's
 * keyboard settings, switching to it, and how to use it.
 */
@Composable
fun KeyboardGuideScreen(onBack: () -> Unit) {
    val c = DS.colors
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val (enabled, current) = remember(refresh) { keyboardStatus(context) }
    var tryText by remember { mutableStateOf("") }

    GuidePage("OptimAI Keyboard", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
            GradientHeading("Trade From Any Text Field")
            Text("OptimAI Keyboard puts live stock prices and a trading card above your keys — in Messages, Notes, X, or any app you type in.", style = DSFont.sm(), color = c.mutedForeground)
        }
        StatusPill(enabled, current)
        GuideStepsCard(
            "How to enable OptimAI Keyboard",
            listOf(
                GuideStep(Icons.Outlined.Settings, "Open keyboard settings", "Tap here to open Android's on-screen keyboard list.") {
                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                GuideStep(Icons.Outlined.ToggleOn, "Turn on the keyboard", "Switch on \"OptimAI Keyboard\" and confirm. It needs network access for live prices and answers."),
                GuideStep(Icons.Outlined.Language, "Switch to it", "Tap here — or the keyboard button at the bottom of any keyboard — and pick \"OptimAI Keyboard\".") {
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                },
            ),
        )
        GuideStepsCard(
            "How to use OptimAI Keyboard",
            listOf(
                GuideStep(Icons.Outlined.ShowChart, "Browse live tickers", "The strip above the keys scrolls live bStocks prices. Tap one to see its chart and price."),
                GuideStep(Icons.Outlined.SwapVert, "Trade from the card", "Tapping a ticker opens a trading card — tap the amount to type it, ⇄ to switch Buy / Sell, then Buy to finish in OptimAI Agentic."),
                GuideStep(Icons.Outlined.AlternateEmail, "Mention @optimai", "Type \"@optimai buy 100 NVDA\" and the card opens already filled in. Naming just a stock shows its price."),
                GuideStep(Icons.Outlined.AutoAwesome, "Ask a question", "Type \"@optimai what's new with Tesla?\" and tap the glowing OptimAI logo for an answer right in the keyboard."),
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
            Text("Try OptimAI Keyboard", style = DSFont.base(FontWeight.SemiBold), color = c.foreground)
            Column(Modifier.fillMaxWidth().dsCard(), verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
                Text("Tap below, switch to it, and type \"@optimai buy 50 AAPL\".", style = DSFont.xs(), color = c.mutedForeground)
                Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(c.secondary, RoundedCornerShape(DSRadius.sm)).padding(DSSpacing.sm)) {
                    if (tryText.isEmpty()) Text("Type here to try the keyboard", style = DSFont.sm(), color = c.mutedForeground)
                    BasicTextField(
                        tryText, { tryText = it }, textStyle = DSFont.sm().copy(color = c.foreground), cursorBrush = SolidColor(c.accentBrand),
                        keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(enabled: Boolean, current: Boolean) {
    val c = DS.colors
    val (text, color) = when {
        current -> "OptimAI Keyboard is on and selected" to c.positive
        enabled -> "Enabled — switch to it with the keyboard button" to c.amber
        else -> "Not enabled yet" to c.mutedForeground
    }
    Row(
        Modifier.clip(RoundedCornerShape(DSRadius.pill)).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(DSRadius.pill))
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (current) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle, null, Modifier.size(14.dp), tint = color)
        Text(text, style = DSFont.xs(FontWeight.SemiBold), color = color)
    }
}

private const val SAMPLE_POST = "Nvidia \$NVDA hits a new all-time high"

private fun shareSample(context: Context) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, SAMPLE_POST)
    context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Settings → Extensions → OptimAI Share Action. */
@Composable
fun ShareGuideScreen(onBack: () -> Unit) {
    val c = DS.colors
    val context = LocalContext.current
    GuidePage("OptimAI Share Action", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
            GradientHeading("Share → Trade")
            Text("OptimAI Share Action turns anything you share into a trade: the agent reads it, finds the stock, and gets you a live quote.", style = DSFont.sm(), color = c.mutedForeground)
        }
        GuideStepsCard(
            "How to enable OptimAI Share Action",
            listOf(
                GuideStep(Icons.Filled.Share, "Open the share sheet", "In Chrome, X, Notes, or any app, tap Share on a post, page, or text."),
                GuideStep(Icons.Outlined.TouchApp, "Pin it", "Long-press \"Trade with OptimAI\" in the share sheet and choose Pin, so it stays at the top."),
                GuideStep(Icons.Outlined.Description, "Or select text", "Select any text and pick \"Trade with OptimAI\" from the selection menu."),
            ),
        )
        GuideStepsCard(
            "How to use OptimAI Share Action",
            listOf(
                GuideStep(Icons.Outlined.Description, "Share what you're reading", "A post on X, a news article, a Reddit thread, or any text."),
                GuideStep(Icons.Filled.Bolt, "Tap Trade with OptimAI", "It's in the share sheet's app list."),
                GuideStep(Icons.Outlined.AutoAwesome, "The agent finds the stock", "OptimAI reads the content and matches the company it's about."),
                GuideStep(Icons.Outlined.MonetizationOn, "Pick an amount", "Choose $50 / $100 / $250 / $500 and see a live, auto-refreshing bStocks quote."),
                GuideStep(Icons.Outlined.Verified, "Sign in OptimAI Agentic", "Tap Buy — the quote screen opens in the app for you to approve in your wallet."),
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
            Text("Try OptimAI Share Action", style = DSFont.base(FontWeight.SemiBold), color = c.foreground)
            Column(Modifier.fillMaxWidth().dsCard(), verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
                Text("Opens the real share sheet on a sample X post — pick \"Trade with OptimAI\".", style = DSFont.xs(), color = c.mutedForeground)
                DSButton("Share a Sample X Post", { shareSample(context) }, icon = Icons.Filled.Share)
            }
        }
    }
}

/** Settings → Explore → How It Works. */
@Composable
fun HowItWorksScreen(onBack: () -> Unit) {
    val c = DS.colors
    val context = LocalContext.current
    var sample by remember { mutableStateOf("NVIDIA just unveiled its next-gen AI chip lineup at the keynote 🚀") }
    GuidePage("How It Works", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
            GradientHeading("Discover → Identify → Trade")
            Text("Prices are live market quotes. Every order is signed in your own wallet and settles on-chain — OptimAI Agentic never holds your funds.", style = DSFont.sm(), color = c.mutedForeground)
        }
        GuideStepsCard(
            "Flow",
            listOf(
                GuideStep(Icons.Outlined.AccountBalanceWallet, "Connect your wallet", "Once, from Settings — links MetaMask or Trust Wallet via WalletConnect on BNB Smart Chain."),
                GuideStep(Icons.Outlined.Description, "Read anything", "An article in Chrome, a post on X, Reddit, or any app."),
                GuideStep(Icons.Filled.Share, "Tap Share", "Use Android's share sheet on the text or page."),
                GuideStep(Icons.Filled.Bolt, "Trade with OptimAI", "Pick it from the share sheet."),
                GuideStep(Icons.Outlined.AutoAwesome, "The agent finds the stock", "OptimAI Agent reads the post, article, or text and matches the company it's about."),
                GuideStep(Icons.Outlined.MonetizationOn, "See a live quote", "Pick $50 / $100 / $250 / $500 and get a real, auto-refreshing on-chain quote."),
                GuideStep(Icons.Outlined.Verified, "Sign with your wallet", "Review the order in OptimAI Agentic and approve it in your wallet."),
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
            Text("Try It", style = DSFont.base(FontWeight.SemiBold), color = c.foreground)
            Column(Modifier.fillMaxWidth().dsCard(), verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
                Text("Opens the real share sheet on a sample X post — look for \"Trade with OptimAI\".", style = DSFont.xs(), color = c.mutedForeground)
                DSButton("Share a Sample X Post", { shareSample(context) }, icon = Icons.Filled.Share)
            }
            Column(Modifier.fillMaxWidth().dsCard(), verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
                Text("Or run the same share → analyze → quote screen directly.", style = DSFont.xs(), color = c.mutedForeground)
                Box(Modifier.fillMaxWidth().heightIn(min = 72.dp).background(c.secondary, RoundedCornerShape(DSRadius.sm)).padding(DSSpacing.sm)) {
                    BasicTextField(sample, { sample = it }, textStyle = DSFont.sm().copy(color = c.foreground), cursorBrush = SolidColor(c.accentBrand), modifier = Modifier.fillMaxWidth())
                }
                DSButton("Try It In-App", {
                    context.startActivity(Intent(context, ShareActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sample))
                }, style = DSButtonStyle.OUTLINE, icon = Icons.Filled.Bolt)
            }
        }
    }
}

@Suppress("unused")
private val unusedStyle = TextStyle()
