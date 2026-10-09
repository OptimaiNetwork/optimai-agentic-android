package com.test.agenttrade.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.test.agenttrade.R
import com.test.agenttrade.ui.theme.DS
import kotlinx.coroutines.delay

/**
 * Real chain/token logos from Trust Wallet's public assets repo — the same
 * source the iOS app cites.
 */
object TrustWalletAssets {
    private const val BASE = "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains"

    fun chainLogoUrl(network: String): String? = chainSlug(network)?.let { "$BASE/$it/info/logo.png" }

    fun tokenLogoUrl(network: String, address: String): String? = chainSlug(network)?.let { "$BASE/$it/assets/$address/logo.png" }

    private fun chainSlug(network: String): String? = when (network.uppercase()) {
        "ETHEREUM" -> "ethereum"
        "BSC", "BNB", "SMARTCHAIN" -> "smartchain"
        "HYPEREVM" -> "hyperevm"
        "SOLANA" -> "solana"
        else -> null
    }

    /** USDT on BSC — the exact contract `/trade/quote`'s approvals target. */
    val usdtOnBsc: String? = tokenLogoUrl("BSC", "0x55d398326f99059fF775485246999027B3197955")
}

/**
 * A small circular remote logo with an initial-letter fallback. A failed load
 * retries twice, a moment apart — at launch the network isn't always up yet.
 */
@Composable
fun RemoteIconCircle(url: String?, diameter: Dp = 20.dp, placeholderText: String = "?", modifier: Modifier = Modifier) {
    var attempt by remember(url) { mutableIntStateOf(0) }
    var failed by remember(url, attempt) { mutableStateOf(false) }
    var loaded by remember(url, attempt) { mutableStateOf(false) }
    Box(modifier.size(diameter).clip(CircleShape), contentAlignment = Alignment.Center) {
        if (!loaded) Placeholder(diameter, placeholderText)
        if (url != null && !failed) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(diameter),
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> loaded = true
                        is AsyncImagePainter.State.Error -> failed = true
                        else -> {}
                    }
                },
            )
        }
    }
    if (failed && attempt < 2) {
        LaunchedEffect(url, attempt) {
            delay((1500L * (attempt + 1)))
            attempt += 1
        }
    }
}

@Composable
private fun Placeholder(diameter: Dp, text: String) {
    val density = LocalDensity.current
    Box(Modifier.size(diameter).background(DS.colors.secondary, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            text.take(1),
            fontSize = with(density) { (diameter * 0.45f).toSp() },
            fontWeight = FontWeight.Bold,
            color = DS.colors.mutedForeground,
        )
    }
}

/**
 * A chain's logo in a circle. BNB Chain — the one chain this build trades
 * on — is bundled, so it draws from the first frame with no network.
 */
@Composable
fun ChainLogoImage(network: String, diameter: Dp = 16.dp, modifier: Modifier = Modifier) {
    when (network.uppercase()) {
        "BSC", "BNB", "SMARTCHAIN" -> Image(
            painterResource(R.drawable.chain_bnb), null,
            modifier.size(diameter).clip(CircleShape), contentScale = ContentScale.Fit,
        )
        else -> RemoteIconCircle(TrustWalletAssets.chainLogoUrl(network), diameter, network, modifier)
    }
}

/**
 * Pins a small logo of the chain a token lives on to the icon's bottom-right
 * corner, ringed in `ring` (the surface behind the icon).
 */
@Composable
fun WithChainBadge(network: String, diameter: Dp = 16.dp, ring: Color = DS.colors.card, content: @Composable () -> Unit) {
    Box {
        content()
        Box(
            Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp)
                .size(diameter + 4.dp).background(ring, CircleShape).border(0.dp, Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { ChainLogoImage(network, diameter) }
    }
}
