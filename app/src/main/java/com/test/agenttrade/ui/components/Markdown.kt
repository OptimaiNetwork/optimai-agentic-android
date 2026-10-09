package com.test.agenttrade.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Inline Markdown — `**bold**`, `*italic*` / `_italic_`, `` `code` `` and
 * `[links](url)` — the subset the agent's answers use, rendered instead of
 * showing raw asterisks (iOS: `.inlineOnlyPreservingWhitespace`). Anything
 * unmatched is kept as literal text, so a parse hiccup never blanks an answer.
 */
fun markdown(text: String, linkColor: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)
        when {
            rest.startsWith("**") -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(markdown(text.substring(i + 2, end), linkColor)) }
                    i = end + 2
                    continue
                }
            }
            rest.startsWith("`") -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                    continue
                }
            }
            rest.startsWith("[") -> {
                val match = Regex("^\\[([^\\]]+)]\\(([^)\\s]+)\\)").find(rest)
                if (match != null) {
                    val (label, url) = match.destructured
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) { append(label) }
                    i += match.value.length
                    continue
                }
            }
            (rest.startsWith("*") || rest.startsWith("_")) && rest.length > 1 && !rest[1].isWhitespace() -> {
                val marker = rest[0]
                val end = text.indexOf(marker, i + 1)
                if (end > i + 1 && !text[end - 1].isWhitespace()) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                    continue
                }
            }
        }
        append(text[i])
        i++
    }
}
