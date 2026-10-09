package com.test.agenttrade.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.test.agenttrade.ui.theme.DsTheme

/**
 * The whole keyboard: toolbar, key grid (dropped while the trading card's
 * numpad or the ask answer is open — those *are* the keyboard then), and
 * the globe / mic row iOS keeps under the keys. Always dark, whatever the
 * host app's theme, like the iOS extension.
 */
@Composable
fun KeyboardRoot(service: OptimAIKeyboardService) {
    val model = service.toolbar
    val engine = service.engine
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 16)
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    var gridWidthDp by remember { mutableFloatStateOf(0f) }

    DsTheme(dark = true) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(KeyColors.background)
                .drawWithContent {
                    drawContent()
                    // The faint lit edge along the top of iOS's keyboard chrome.
                    drawLine(Color.White.copy(alpha = 0.07f), Offset(26.dp.toPx(), 0.5f), Offset(size.width - 26.dp.toPx(), 0.5f), 1f)
                }
                // Android draws its own row under an IME — hide-keyboard and
                // the keyboard switcher — exactly where iOS keeps its globe
                // and mic, so the keys stop above it rather than adding a
                // second globe row of our own.
                .padding(bottom = imeNavBarHeight()),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                if (!model.isSuppressed) TickerToolbar(model, service.feedback)
                if (!model.isKeypadActive && !model.isNewsPreviewActive) {
                    Spacer(Modifier.height(2.dp))
                    KeyGrid(
                        engine = engine,
                        callouts = service.callouts,
                        feedback = service.feedback,
                        onReturnAction = service::performEditorAction,
                        modifier = Modifier.onGloballyPositioned {
                            gridOrigin = it.positionInRoot()
                            gridWidthDp = it.size.width / density.density
                        },
                    )
                }
            }
            // Callouts float over everything, so a top-row bubble can rise
            // over the toolbar the way it does on iOS.
            Canvas(Modifier.matchParentSize()) {
                drawCallouts(service.callouts, gridOrigin, gridWidthDp, measurer, density.density)
            }
        }
    }
}

/**
 * The band at the bottom of an IME window that the system fills: the
 * navigation bar inset, and at least the 48dp row Android 13+ draws its
 * back/switcher buttons in under a keyboard in gesture navigation.
 */
@Composable
private fun imeNavBarHeight(): androidx.compose.ui.unit.Dp {
    val density = LocalDensity.current
    val inset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val systemRow = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) 48.dp else 0.dp
    return maxOf(inset, systemRow) + 4.dp
}
