package com.test.agenttrade.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.agenttrade.ui.theme.DS
import com.test.agenttrade.ui.theme.DSFont
import com.test.agenttrade.ui.theme.DSRadius
import com.test.agenttrade.ui.theme.DSSpacing

// MARK: - Card

/** `bg-card border-border rounded-xl` — the elevated surface used everywhere. */
@Composable
fun Modifier.dsCard(radius: Dp = DSRadius.lg, padding: Dp = DSSpacing.lg): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(DS.colors.card, shape)
        .border(1.dp, DS.colors.border, shape)
        .padding(padding)
}

/** A list of menu rows straight on the page, pulled into the 16dp margin. */
fun Modifier.dsPlainList(): Modifier = this.padding(horizontal = 0.dp)

// MARK: - Buttons

enum class DSButtonSize(val height: Dp, val horizontalPadding: Dp) {
    XS(32.dp, DSSpacing.smd), SM(36.dp, DSSpacing.md), REGULAR(44.dp, DSSpacing.lg), LG(50.dp, DSSpacing.xl);

    val font: TextStyle
        get() = when (this) {
            XS -> DSFont.xs(FontWeight.SemiBold)
            SM, REGULAR -> DSFont.sm(FontWeight.SemiBold)
            LG -> DSFont.base(FontWeight.SemiBold)
        }
}

enum class DSButtonStyle { PRIMARY, SECONDARY, OUTLINE, GHOST, DESTRUCTIVE }

@Composable
fun DSButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: DSButtonStyle = DSButtonStyle.PRIMARY,
    size: DSButtonSize = DSButtonSize.REGULAR,
    fullWidth: Boolean = style != DSButtonStyle.GHOST,
    enabled: Boolean = true,
    foreground: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = DS.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (fg, bg, pressedBg, border) = when (style) {
        DSButtonStyle.PRIMARY -> Quad(c.primaryForeground, c.primary, c.primary.copy(alpha = 0.85f), null)
        DSButtonStyle.SECONDARY -> Quad(c.foreground, c.secondary, c.accent, null)
        DSButtonStyle.OUTLINE -> Quad(c.foreground, Color.Transparent, c.accent, c.border)
        DSButtonStyle.GHOST -> Quad(foreground ?: c.foreground, Color.Transparent, c.accent, null)
        DSButtonStyle.DESTRUCTIVE -> Quad(c.destructiveForeground, c.destructive, c.destructive.copy(alpha = 0.85f), null)
    }
    val shape = RoundedCornerShape(DSRadius.sm)
    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(size.height)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(if (pressed && enabled) pressedBg else bg, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = size.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            androidx.compose.material3.ProvideTextStyle(size.font.copy(color = fg)) { content() }
        }
    }
}

@Composable
fun DSButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: DSButtonStyle = DSButtonStyle.PRIMARY,
    size: DSButtonSize = DSButtonSize.REGULAR,
    fullWidth: Boolean = style != DSButtonStyle.GHOST,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
    foreground: Color? = null,
) {
    DSButton(onClick, modifier, style, size, fullWidth, enabled, foreground) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
        } else {
            if (icon != null) Icon(icon, null, Modifier.size(16.dp))
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class Quad(val fg: Color, val bg: Color, val pressed: Color, val border: Color?)

// MARK: - Divider

@Composable
fun DSDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(DS.colors.border))
}

// MARK: - Trend

enum class TrendTone {
    UP, DOWN, FLAT;

    companion object {
        fun from(value: Double): TrendTone = if (value > 0) UP else if (value < 0) DOWN else FLAT
    }
}

@Composable
fun TrendTone.color(): Color = when (this) {
    TrendTone.UP -> DS.colors.positive
    TrendTone.DOWN -> DS.colors.negative
    TrendTone.FLAT -> DS.colors.mutedForeground
}

/** arrow.up / arrow.down (never the "-right" variants), no icon when flat. */
@Composable
fun TrendLabel(tone: TrendTone, text: String, style: TextStyle = DSFont.sm(FontWeight.SemiBold)) {
    val color = tone.color()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        when (tone) {
            TrendTone.UP -> Icon(Icons.Filled.ArrowUpward, null, Modifier.size(12.dp), tint = color)
            TrendTone.DOWN -> Icon(Icons.Filled.ArrowDownward, null, Modifier.size(12.dp), tint = color)
            TrendTone.FLAT -> {}
        }
        Text(text, style = style, color = color, maxLines = 1)
    }
}

// MARK: - Badge

enum class DSBadgeStyle { SECONDARY, BRAND, AMBER, POSITIVE, NEGATIVE }

@Composable
fun DSBadge(text: String, style: DSBadgeStyle = DSBadgeStyle.SECONDARY, uppercase: Boolean = false, minTextWidth: Dp? = null) {
    val c = DS.colors
    val (fg, bg) = when (style) {
        DSBadgeStyle.SECONDARY -> c.mutedForeground to c.secondary
        DSBadgeStyle.BRAND -> c.accentBrand to c.accentBrand.copy(alpha = 0.1f)
        DSBadgeStyle.AMBER -> c.amber to c.amber.copy(alpha = 0.12f)
        DSBadgeStyle.POSITIVE -> c.positive to c.positive.copy(alpha = 0.14f)
        DSBadgeStyle.NEGATIVE -> c.negative to c.negative.copy(alpha = 0.14f)
    }
    Box(
        Modifier.background(bg, CircleShape).padding(horizontal = DSSpacing.sm, vertical = 4.dp)
            .then(if (minTextWidth != null) Modifier.defaultMinSize(minWidth = minTextWidth) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (uppercase) text.uppercase() else text,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg,
            letterSpacing = if (uppercase) 0.6.sp else 0.sp, maxLines = 1,
        )
    }
}

/** A pill-shaped count, shared by every tab/segment switcher. */
@Composable
fun CountBadge(count: Int, isSelected: Boolean) {
    val c = DS.colors
    Box(
        Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 18.dp)
            .background(if (isSelected) c.primary else c.secondary, CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) c.primaryForeground else c.mutedForeground)
    }
}

// MARK: - Icon tiles

/** Icon-in-box: a card-colored square sitting inside a secondary tile. */
@Composable
fun DSIconTile(icon: ImageVector, tint: Color = DS.colors.mutedForeground) {
    Box(
        Modifier.background(DS.colors.secondary, RoundedCornerShape(DSRadius.sm + 4.dp)).padding(6.dp),
    ) {
        Box(
            Modifier.size(28.dp).background(DS.colors.card, RoundedCornerShape(DSRadius.sm)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(15.dp), tint = tint) }
    }
}

@Composable
fun DSImageIconTile(res: Int) {
    Box(Modifier.background(DS.colors.secondary, RoundedCornerShape(DSRadius.sm + 4.dp)).padding(6.dp)) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(DSRadius.sm)).background(DS.colors.card),
            contentAlignment = Alignment.Center,
        ) { Image(painterResource(res), null, Modifier.size(20.dp)) }
    }
}

// MARK: - Small round buttons

/** The circular ✕ used by sheets and the quote header. */
@Composable
fun CircleCloseButton(onClick: () -> Unit, size: Dp = 30.dp, iconSize: Dp = 13.dp, tint: Color = DS.colors.mutedForeground) {
    Box(
        Modifier.size(size).clip(CircleShape).background(DS.colors.secondary).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Close, "Close", Modifier.size(iconSize), tint = tint) }
}

@Composable
fun CircleBackButton(onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(DS.colors.secondary).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(18.dp), tint = DS.colors.foreground) }
}

/** A tap target without the Material ripple — the iOS `.plain` button style. */
fun Modifier.plainClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = null,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        ),
    )

/** List row button: pressed → `bg-accent`, rounded 10. Mirrors `DSMenuItemStyle`. */
@Composable
fun DSMenuItem(onClick: (() -> Unit)?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(DSRadius.md), content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(if (pressed) DS.colors.accent else Color.Transparent, shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = DSSpacing.smd, vertical = DSSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = DSFont.sm(FontWeight.SemiBold), color = DS.colors.mutedForeground)
}

@Composable
fun HSpacer(width: Dp) = Spacer(Modifier.width(width))

@Composable
fun VSpacer(height: Dp) = Spacer(Modifier.height(height))
