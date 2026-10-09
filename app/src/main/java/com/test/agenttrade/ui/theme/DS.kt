package com.test.agenttrade.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The OptimAI design system, ported 1:1 from the iOS `DSColor`/`DSMetrics`
 * (themselves ported from the web system's Tailwind tokens). Views go
 * through these tokens, never raw hex.
 */
@Immutable
data class DsColors(
    val background: Color,
    val card: Color,
    val sidebar: Color,
    val secondary: Color,
    val foreground: Color,
    val mutedForeground: Color,
    /** bg-primary — a fill token only. */
    val primary: Color,
    val primaryForeground: Color,
    /** text-accent-brand — an ink token only. */
    val accentBrand: Color,
    val border: Color,
    val borderInput: Color,
    val positive: Color,
    val negative: Color,
    val amber: Color,
    val destructive: Color,
    val destructiveForeground: Color,
    val isDark: Boolean,
) {
    val accent: Color get() = secondary
    val popover: Color get() = card
}

private object Raw {
    val zinc50 = Color(0xFFFAFAFA)
    val zinc100 = Color(0xFFF4F4F5)
    val zinc200 = Color(0xFFE4E4E7)
    val zinc300 = Color(0xFFD4D4D8)
    val zinc400 = Color(0xFFA1A1AA)
    val zinc500 = Color(0xFF71717A)
    val zinc800 = Color(0xFF27272A)
    val zinc900 = Color(0xFF18181B)
    val zinc950 = Color(0xFF09090B)
    val nearBlack = Color(0xFF0A0A0A)
    val nearWhite = Color(0xFFF5F5F5)
    val brandMint = Color(0xFF2ECC71)
    val brandForestInk = Color(0xFF178A4E)
    val brandMintInk = Color(0xFF36D399)
    val brandDeepText = Color(0xFF0C3B22)
    val green500 = Color(0xFF22C55E)
    val red500 = Color(0xFFEF4444)
    val amber600 = Color(0xFFD97706)
    val amber400 = Color(0xFFFBBF24)
    val red600 = Color(0xFFDC2626)
    val red400 = Color(0xFFF87171)
}

val DarkDsColors = DsColors(
    background = Raw.zinc950,
    card = Raw.zinc900,
    sidebar = Raw.zinc950,
    secondary = Raw.zinc800,
    foreground = Raw.nearWhite,
    mutedForeground = Raw.zinc400,
    primary = Raw.brandMint,
    primaryForeground = Raw.brandDeepText,
    accentBrand = Raw.brandMintInk,
    border = Color.White.copy(alpha = 0.14f),
    borderInput = Color.White.copy(alpha = 0.20f),
    positive = Raw.green500,
    negative = Raw.red500,
    amber = Raw.amber400,
    destructive = Raw.red400,
    destructiveForeground = Color.White,
    isDark = true,
)

val LightDsColors = DsColors(
    background = Raw.zinc50,
    card = Color.White,
    sidebar = Raw.zinc100,
    secondary = Raw.zinc100,
    foreground = Raw.nearBlack,
    mutedForeground = Raw.zinc500,
    primary = Raw.brandMint,
    primaryForeground = Raw.brandDeepText,
    accentBrand = Raw.brandForestInk,
    border = Raw.zinc200,
    borderInput = Raw.zinc300,
    positive = Raw.green500,
    negative = Raw.red500,
    amber = Raw.amber600,
    destructive = Raw.red600,
    destructiveForeground = Color.White,
    isDark = false,
)

val LocalDsColors = staticCompositionLocalOf { DarkDsColors }

object DS {
    val colors: DsColors
        @Composable @ReadOnlyComposable get() = LocalDsColors.current
}

/** Corner radius scale (`DSRadius`). */
object DSRadius {
    val sm = 8.dp
    val md = 10.dp
    val lg = 14.dp
    val xl = 18.dp
    val xl2 = 24.dp
    val pill = 999.dp
}

/** Spacing scale (`DSSpacing`). */
object DSSpacing {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 8.dp
    val smd = 10.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xl2 = 24.dp
    val xl3 = 32.dp
}

/** Typography scale (`DSFont`). The system sans stands in for SF Pro. */
object DSFont {
    private fun style(size: TextUnit, weight: FontWeight) = TextStyle(fontSize = size, fontWeight = weight, lineHeight = size * 1.22f)

    fun xs(weight: FontWeight = FontWeight.Normal) = style(12.sp, weight)
    fun sm(weight: FontWeight = FontWeight.Normal) = style(14.sp, weight)
    fun base(weight: FontWeight = FontWeight.Normal) = style(16.sp, weight)
    fun lg(weight: FontWeight = FontWeight.Medium) = style(18.sp, weight)
    fun xl(weight: FontWeight = FontWeight.SemiBold) = style(20.sp, weight)
    fun xl2(weight: FontWeight = FontWeight.SemiBold) = style(24.sp, weight)
    fun xl3(weight: FontWeight = FontWeight.SemiBold) = style(30.sp, weight)

    /** Large numeric text — prices, totals, quote amounts — with tight tracking. */
    fun display(size: Int, weight: FontWeight = FontWeight.SemiBold) =
        TextStyle(fontSize = size.sp, fontWeight = weight, letterSpacing = (-0.03).em, lineHeight = (size * 1.15f).sp)

    fun heading(size: Int, weight: FontWeight = FontWeight.Bold) =
        TextStyle(fontSize = size.sp, fontWeight = weight, letterSpacing = (-0.025).em, lineHeight = (size * 1.2f).sp)

    /** Geist Mono equivalent — ids, hashes. */
    fun mono(size: Int = 12, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontSize = size.sp, fontWeight = weight, fontFamily = FontFamily.Monospace)

    fun sized(size: Float, weight: FontWeight = FontWeight.Normal) = style(size.sp, weight)
}

/** One scale for every token list row (`DSRow`). */
object DSRow {
    val logo = 44.dp
    val title get() = DSFont.base(FontWeight.SemiBold)
    val subtitle get() = DSFont.sized(13f)
    val detail get() = DSFont.xs(FontWeight.SemiBold)
    val value get() = DSFont.base(FontWeight.SemiBold)
    val change get() = DSFont.sized(13f, FontWeight.SemiBold)
}

@Composable
fun OptimAITheme(theme: com.test.agenttrade.data.AppPrefs.Theme, content: @Composable () -> Unit) {
    val dark = when (theme) {
        com.test.agenttrade.data.AppPrefs.Theme.SYSTEM -> isSystemInDarkTheme()
        com.test.agenttrade.data.AppPrefs.Theme.LIGHT -> false
        com.test.agenttrade.data.AppPrefs.Theme.DARK -> true
    }
    DsTheme(dark, content)
}

@Composable
fun DsTheme(dark: Boolean, content: @Composable () -> Unit) {
    val ds = if (dark) DarkDsColors else LightDsColors
    val material = if (dark) {
        darkColorScheme(
            primary = ds.primary, onPrimary = ds.primaryForeground, background = ds.background, surface = ds.card,
            onBackground = ds.foreground, onSurface = ds.foreground, surfaceVariant = ds.secondary,
            onSurfaceVariant = ds.mutedForeground, outline = ds.border, error = ds.destructive,
            surfaceContainer = ds.card, surfaceContainerHigh = ds.card, surfaceContainerLow = ds.card,
        )
    } else {
        lightColorScheme(
            primary = ds.primary, onPrimary = ds.primaryForeground, background = ds.background, surface = ds.card,
            onBackground = ds.foreground, onSurface = ds.foreground, surfaceVariant = ds.secondary,
            onSurfaceVariant = ds.mutedForeground, outline = ds.border, error = ds.destructive,
            surfaceContainer = ds.card, surfaceContainerHigh = ds.card, surfaceContainerLow = ds.card,
        )
    }
    CompositionLocalProvider(LocalDsColors provides ds) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
