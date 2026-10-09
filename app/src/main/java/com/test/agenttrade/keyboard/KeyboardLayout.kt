package com.test.agenttrade.keyboard

import androidx.compose.ui.geometry.Rect

/**
 * The iOS system keyboard's geometry and layouts, measured off the iOS app's
 * own keyboard screenshots (iPhone 17, 402pt wide, iOS 26 dark): keys
 * 33.5pt × 43.3pt, 6.67pt between keys, 3.33pt outer margin, 56pt row pitch.
 * Horizontal sizes are derived from the real width the same way iOS does —
 * fixed gaps, flexible keys — so other widths scale the way an iPhone's
 * keyboard does.
 */
object KeyMetrics {
    const val SIDE_MARGIN = 3.33f
    const val KEY_GAP = 6.67f
    const val ROW_HEIGHT = 56f
    const val KEY_HEIGHT = 43.33f
    const val KEY_RADIUS = 8.5f
    /** Shift / delete on the third row, as a multiple of a letter key. */
    const val SHIFT_FACTOR = 1.373f
    /** The gap between shift/delete and the letters next to them. */
    const val WIDE_GAP = 14.33f
    /** "123" / "ABC" on the bottom row. */
    const val MODE_FACTOR = 1.29f
    /** Return on the bottom row. */
    const val RETURN_FACTOR = 2.8f
    /** The bottom row's own gap. */
    const val BOTTOM_GAP = 6.33f
}

/** What a key does. */
sealed interface KeyAction {
    data class Character(val text: String) : KeyAction
    data object Shift : KeyAction
    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Return : KeyAction
    /** Switch to a keyboard mode ("123", "#+=", "ABC"). */
    data class Mode(val target: KeyboardMode, val label: String) : KeyAction
}

enum class KeyboardMode { ALPHABETIC, NUMERIC, SYMBOLIC }

/** Shift state, with iOS semantics: one-shot shift vs. caps lock. */
enum class ShiftState { LOWERCASE, SHIFTED, CAPS_LOCK }

/** A laid-out key: its action and its frame inside the key grid (in dp). */
data class LaidOutKey(val action: KeyAction, val frame: Rect, val row: Int) {
    /** Character and space keys get the iOS input callout; system keys don't. */
    val isCharacter: Boolean get() = action is KeyAction.Character
    val isSystem: Boolean get() = !isCharacter && action !is KeyAction.Space
}

object KeyboardLayouts {
    private val alphaRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val numericRows = listOf("1234567890", "-/:;()$&@\"", ".,?!'")
    private val symbolicRows = listOf("[]{}#%^*+=", "_\\|~<>€£¥•", ".,?!'")

    /**
     * Long-press alternates for symbol keys: common typographic variants. The
     * first entry is the key itself. Letter keys have no alternates.
     */
    private val alternates: Map<String, List<String>> = mapOf(
        "0" to listOf("0", "°"),
        "-" to listOf("-", "–", "—", "•"),
        "/" to listOf("/", "\\"),
        "$" to listOf("$", "¢", "£", "¥", "€", "₩", "₽"),
        "&" to listOf("&", "§"),
        "\"" to listOf("\"", "“", "”", "„"),
        "." to listOf(".", "…"),
        "?" to listOf("?", "¿"),
        "!" to listOf("!", "¡"),
        "'" to listOf("'", "‘", "’", "`"),
        "%" to listOf("%", "‰"),
        "=" to listOf("=", "≈", "≠"),
    )

    /** Alternates for a typed character, cased to match it. */
    fun alternatesFor(text: String): List<String>? {
        val lower = text.lowercase()
        val list = alternates[lower] ?: return null
        return if (text != lower) list.map { it.uppercase() } else list
    }

    /**
     * Lays out one mode's four rows in a `width`-dp-wide grid.
     */
    fun layout(mode: KeyboardMode, width: Float, uppercase: Boolean, returnWide: Boolean = true): List<LaidOutKey> {
        val m = KeyMetrics
        val keyW = (width - 2 * m.SIDE_MARGIN - 9 * m.KEY_GAP) / 10f
        val pitch = keyW + m.KEY_GAP
        val keys = mutableListOf<LaidOutKey>()
        fun rowRect(row: Int, x: Float, w: Float): Rect {
            val top = row * m.ROW_HEIGHT + (m.ROW_HEIGHT - m.KEY_HEIGHT) / 2
            return Rect(x, top, x + w, top + m.KEY_HEIGHT)
        }
        val shiftW = keyW * m.SHIFT_FACTOR

        when (mode) {
            KeyboardMode.ALPHABETIC -> {
                fun ch(c: Char) = KeyAction.Character(if (uppercase) c.uppercase() else c.toString())
                alphaRows[0].forEachIndexed { i, c -> keys += LaidOutKey(ch(c), rowRect(0, m.SIDE_MARGIN + i * pitch, keyW), 0) }
                alphaRows[1].forEachIndexed { i, c -> keys += LaidOutKey(ch(c), rowRect(1, m.SIDE_MARGIN + pitch / 2 + i * pitch, keyW), 1) }
                keys += LaidOutKey(KeyAction.Shift, rowRect(2, m.SIDE_MARGIN, shiftW), 2)
                alphaRows[2].forEachIndexed { i, c -> keys += LaidOutKey(ch(c), rowRect(2, m.SIDE_MARGIN + pitch / 2 + (i + 1) * pitch, keyW), 2) }
                keys += LaidOutKey(KeyAction.Backspace, rowRect(2, width - m.SIDE_MARGIN - shiftW, shiftW), 2)
            }
            KeyboardMode.NUMERIC, KeyboardMode.SYMBOLIC -> {
                val rows = if (mode == KeyboardMode.NUMERIC) numericRows else symbolicRows
                rows[0].forEachIndexed { i, c -> keys += LaidOutKey(KeyAction.Character(c.toString()), rowRect(0, m.SIDE_MARGIN + i * pitch, keyW), 0) }
                rows[1].forEachIndexed { i, c -> keys += LaidOutKey(KeyAction.Character(c.toString()), rowRect(1, m.SIDE_MARGIN + i * pitch, keyW), 1) }
                val toggle = if (mode == KeyboardMode.NUMERIC) KeyAction.Mode(KeyboardMode.SYMBOLIC, "#+=") else KeyAction.Mode(KeyboardMode.NUMERIC, "123")
                keys += LaidOutKey(toggle, rowRect(2, m.SIDE_MARGIN, shiftW), 2)
                // Five wide punctuation keys fill the space between, iOS-style.
                val left = m.SIDE_MARGIN + shiftW + m.WIDE_GAP
                val right = width - m.SIDE_MARGIN - shiftW - m.WIDE_GAP
                val puncW = (right - left - 4 * m.KEY_GAP) / 5f
                rows[2].forEachIndexed { i, c ->
                    keys += LaidOutKey(KeyAction.Character(c.toString()), rowRect(2, left + i * (puncW + m.KEY_GAP), puncW), 2)
                }
                keys += LaidOutKey(KeyAction.Backspace, rowRect(2, width - m.SIDE_MARGIN - shiftW, shiftW), 2)
            }
        }

        // Bottom row: mode key, space, return.
        val modeW = keyW * m.MODE_FACTOR
        val returnW = keyW * m.RETURN_FACTOR
        val modeKey = if (mode == KeyboardMode.ALPHABETIC) KeyAction.Mode(KeyboardMode.NUMERIC, "123") else KeyAction.Mode(KeyboardMode.ALPHABETIC, "ABC")
        keys += LaidOutKey(modeKey, rowRect(3, m.SIDE_MARGIN, modeW), 3)
        val spaceX = m.SIDE_MARGIN + modeW + m.BOTTOM_GAP
        val returnX = width - m.SIDE_MARGIN - returnW
        keys += LaidOutKey(KeyAction.Space, rowRect(3, spaceX, returnX - m.BOTTOM_GAP - spaceX), 3)
        keys += LaidOutKey(KeyAction.Return, rowRect(3, returnX, returnW), 3)
        return keys
    }

    /**
     * The key a touch at (x, y) belongs to. Every key's touch area reaches
     * halfway into the gaps around it, and a touch in the side margins or
     * a row's empty edge goes to the nearest key in that row — the way an
     * iPhone keyboard never lets a tap fall "between" keys.
     */
    fun hitTest(keys: List<LaidOutKey>, x: Float, y: Float): LaidOutKey? {
        if (keys.isEmpty()) return null
        val row = (y / KeyMetrics.ROW_HEIGHT).toInt().coerceIn(0, 3)
        val inRow = keys.filter { it.row == row }
        if (inRow.isEmpty()) return null
        return inRow.minByOrNull { key ->
            when {
                x < key.frame.left -> key.frame.left - x
                x > key.frame.right -> x - key.frame.right
                else -> 0f
            }
        }
    }
}
