package com.test.agenttrade.ui.stocks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.test.agenttrade.R
import com.test.agenttrade.data.Fmt
import com.test.agenttrade.data.MarketItem
import com.test.agenttrade.data.StockApiClient
import com.test.agenttrade.data.UserFacingError
import com.test.agenttrade.ui.components.CountBadge
import com.test.agenttrade.ui.components.DSButton
import com.test.agenttrade.ui.components.DSButtonSize
import com.test.agenttrade.ui.components.DSButtonStyle
import com.test.agenttrade.ui.components.DSDivider
import com.test.agenttrade.ui.components.DSMenuItem
import com.test.agenttrade.ui.components.RemoteIconCircle
import com.test.agenttrade.ui.components.SparklineView
import com.test.agenttrade.ui.components.TrendLabel
import com.test.agenttrade.ui.components.TrendTone
import com.test.agenttrade.ui.components.WithChainBadge
import com.test.agenttrade.ui.components.plainClickable
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import com.test.agenttrade.ui.theme.DSRadius
import com.test.agenttrade.ui.theme.DSRow
import com.test.agenttrade.ui.theme.DSSpacing
import com.test.agenttrade.ui.wallet.TabHeader
import kotlinx.coroutines.launch

/**
 * The Stocks tab: the bStocks catalog (BNB Smart Chain, live from the
 * server's `/stocks` router). The catalog's ~90 rows load at once and the
 * search field filters them locally, as on iOS.
 */
@Composable
fun StocksScreen(onOpenStock: (MarketItem) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<MarketItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(attempt) {
        loading = true
        error = null
        try {
            val response = StockApiClient.market(limit = 100)
            items = response.items
            total = response.total
        } catch (e: Exception) {
            error = UserFacingError.message(e, "Couldn't load bStocks. Please try again.")
        }
        loading = false
    }

    val rows = remember(items, search) { items.filter { it.matches(search) } }
    val c = DS.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.padding(horizontal = DSSpacing.lg)) {
            Spacer(Modifier.height(DSSpacing.sm))
            TabHeader()
            Spacer(Modifier.height(DSSpacing.md))
            CatalogPicker(count = total)
            Spacer(Modifier.height(DSSpacing.sm))
            StockSearchField(search) { search = it }
            Spacer(Modifier.height(DSSpacing.sm))
        }
        when {
            loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = c.mutedForeground, strokeWidth = 2.dp)
                    Text("Loading bStocks…", style = DSFont.sm(), color = c.mutedForeground)
                }
            }
            error != null && items.isEmpty() -> ErrorState(error!!) { scope.launch { attempt++ } }
            rows.isEmpty() && search.isNotBlank() -> NoSearchResults(search)
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = DSSpacing.sm)) {
                itemsIndexed(rows, key = { _, it -> it.id }) { index, item ->
                    if (index > 0) DSDivider(Modifier.padding(horizontal = DSSpacing.smd))
                    DSMenuItem(onClick = { onOpenStock(item) }) { MarketRow(item) }
                }
            }
        }
    }
}

/**
 * The catalog switcher — a card track with a mint-tinted pill. This build
 * has one catalog, so the pill is bStocks, with its token count.
 */
@Composable
private fun CatalogPicker(count: Int) {
    val c = DS.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(DSRadius.lg)).background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(DSRadius.lg)).padding(3.dp),
    ) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(DSRadius.md))
                .background(c.primary.copy(alpha = 0.14f))
                .border(1.dp, c.primary.copy(alpha = 0.45f), RoundedCornerShape(DSRadius.md))
                .padding(vertical = DSSpacing.smd),
            horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.tab_logo_bstocks), null, Modifier.size(16.dp).clip(CircleShape))
            Text("bStocks", style = DSFont.sm(FontWeight.SemiBold), color = c.foreground)
            CountBadge(count, isSelected = true)
        }
    }
}

@Composable
fun StockSearchField(text: String, onChange: (String) -> Unit) {
    val c = DS.colors
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(DSRadius.lg)).background(c.card)
            .border(1.dp, if (focused) c.primary.copy(alpha = 0.45f) else c.border, RoundedCornerShape(DSRadius.lg))
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.smd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm),
    ) {
        Icon(Icons.Filled.Search, null, Modifier.size(16.dp), tint = c.mutedForeground)
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text("Search name or ticker", style = DSFont.sm(), color = c.mutedForeground)
            BasicTextField(
                text, onChange,
                textStyle = DSFont.sm().copy(color = c.foreground),
                singleLine = true,
                cursorBrush = SolidColor(c.accentBrand),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
        if (text.isNotEmpty()) {
            Icon(Icons.Filled.Cancel, "Clear search", Modifier.size(17.dp).plainClickable { onChange("") }, tint = c.mutedForeground)
        }
    }
}

/** Logo with chain badge, ticker + symbol, name, sparkline, price + change. */
@Composable
fun MarketRow(item: MarketItem) {
    val c = DS.colors
    val tone = TrendTone.from(item.priceChangePct24h ?: 0.0)
    val displayName = (item.name ?: item.underlyingName)?.takeIf { it != item.ticker }
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
        WithChainBadge("bsc", ring = c.background) { RemoteIconCircle(item.logoUrl, DSRow.logo, item.ticker) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                Text(item.ticker, style = DSRow.title, color = c.foreground, maxLines = 1)
                Text(item.symbol, style = DSRow.subtitle, color = c.mutedForeground, maxLines = 1)
            }
            if (displayName != null) Text(displayName, style = DSRow.subtitle, color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val spark = item.sparkline
        if (spark != null && spark.size > 1) SparklineView(spark, tone, Modifier.width(56.dp).height(24.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.referencePrice?.let { Fmt.usd(it) } ?: "—", style = DSRow.value, color = if (item.referencePrice != null) c.foreground else c.mutedForeground)
            item.priceChangePct24h?.let { TrendLabel(tone, Fmt.fixed(kotlin.math.abs(it), 2) + "%", DSRow.change) }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    val c = DS.colors
    Box(Modifier.fillMaxSize().padding(horizontal = DSSpacing.xl2), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.md)) {
            Icon(Icons.Filled.WifiOff, null, Modifier.size(28.dp), tint = c.amber)
            Text(message, style = DSFont.xs(), color = c.mutedForeground, textAlign = TextAlign.Center)
            DSButton("Retry", onRetry, style = DSButtonStyle.OUTLINE, size = DSButtonSize.SM)
        }
    }
}

@Composable
fun NoSearchResults(query: String) {
    val c = DS.colors
    Box(Modifier.fillMaxSize().padding(vertical = DSSpacing.xl2), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.sm)) {
            Icon(Icons.Filled.Search, null, Modifier.size(24.dp), tint = c.mutedForeground)
            Text("No stocks match “${query.trim()}”", style = DSFont.sm(), color = c.mutedForeground, textAlign = TextAlign.Center)
        }
    }
}
