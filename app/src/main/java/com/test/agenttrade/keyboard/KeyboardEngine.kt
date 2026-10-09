package com.test.agenttrade.keyboard

import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.BreakIterator

/**
 * The text side of the keyboard, matching the iOS system keyboard: inserting, deleting (characters, then
 * whole words on a long hold), shift / caps lock, iOS autocapitalization,
 * the double-space period, mode switching, return-key actions and the
 * space-bar trackpad. UI-free; the key grid calls into it.
 */
class KeyboardEngine(private val connection: () -> InputConnection?, private val editorInfo: () -> EditorInfo?) {
    var mode by mutableStateOf(KeyboardMode.ALPHABETIC)
    var shift by mutableStateOf(ShiftState.LOWERCASE)
    /** Space-bar trackpad: the keys go blank while it's dragging the caret. */
    var isTrackpadActive by mutableStateOf(false)

    private var lastShiftTap = 0L
    private var lastSpaceTap = 0L

    val uppercase: Boolean get() = shift != ShiftState.LOWERCASE

    // MARK: - Editor info

    private val inputType: Int get() = editorInfo()?.inputType ?: InputType.TYPE_CLASS_TEXT

    val isPasswordField: Boolean
        get() {
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val cls = inputType and InputType.TYPE_MASK_CLASS
            return cls == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                ) || cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

    /** What the return key reads and does for this field. */
    enum class ReturnStyle(val label: String?, val isPrimary: Boolean) {
        NEWLINE(null, false), GO("go", true), SEARCH("search", true), SEND("send", true),
        NEXT("next", false), DONE("done", true), PREVIOUS("previous", false),
    }

    val returnStyle: ReturnStyle
        get() {
            val info = editorInfo() ?: return ReturnStyle.NEWLINE
            if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return ReturnStyle.NEWLINE
            return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_GO -> ReturnStyle.GO
                EditorInfo.IME_ACTION_SEARCH -> ReturnStyle.SEARCH
                EditorInfo.IME_ACTION_SEND -> ReturnStyle.SEND
                EditorInfo.IME_ACTION_NEXT -> ReturnStyle.NEXT
                EditorInfo.IME_ACTION_DONE -> ReturnStyle.DONE
                EditorInfo.IME_ACTION_PREVIOUS -> ReturnStyle.PREVIOUS
                else -> ReturnStyle.NEWLINE
            }
        }

    // MARK: - Lifecycle

    /** A new field: back to letters, cased for wherever the caret is. */
    fun onStartInput(restarting: Boolean) {
        // Number, phone and date fields open on 123, the way iOS's keyboard
        // shows its numbers layout for a numeric field.
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        if (!restarting || numeric) mode = if (numeric) KeyboardMode.NUMERIC else KeyboardMode.ALPHABETIC
        isTrackpadActive = false
        if (shift != ShiftState.CAPS_LOCK || !restarting) shift = ShiftState.LOWERCASE
        refreshAutocapitalization()
    }

    /** The caret moved (or the host changed the text) — re-case like iOS does. */
    fun onSelectionChanged() {
        if (!isTrackpadActive) refreshAutocapitalization()
    }

    /**
     * Sentence-case autocapitalization, as the field asks for it: Android
     * fields opt in with `textCapSentences`/`textCapWords`/`textCapCharacters`
     * and the editor itself says whether the caret is at a sentence start.
     */
    fun refreshAutocapitalization() {
        if (shift == ShiftState.CAPS_LOCK || mode != KeyboardMode.ALPHABETIC) return
        val ic = connection() ?: return
        val type = inputType
        val capsFlags = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT || type and capsFlags == 0) {
            shift = ShiftState.LOWERCASE
            return
        }
        shift = if (ic.getCursorCapsMode(type) != 0) ShiftState.SHIFTED else ShiftState.LOWERCASE
    }

    // MARK: - Keys

    fun tapShift() {
        val now = SystemClock.uptimeMillis()
        shift = when (shift) {
            ShiftState.CAPS_LOCK -> ShiftState.LOWERCASE
            ShiftState.SHIFTED -> if (now - lastShiftTap < DOUBLE_TAP_MS) ShiftState.CAPS_LOCK else ShiftState.LOWERCASE
            ShiftState.LOWERCASE -> ShiftState.SHIFTED
        }
        lastShiftTap = now
    }

    /** Double-tap on shift from lowercase → caps lock (first tap shifted, second locks). */
    fun doubleTapShift() {
        shift = ShiftState.CAPS_LOCK
        lastShiftTap = 0
    }

    fun insert(text: String) {
        val ic = connection() ?: return
        ic.commitText(text, 1)
        lastSpaceTap = 0
        if (shift == ShiftState.SHIFTED) shift = ShiftState.LOWERCASE
        // iOS goes back to letters after an apostrophe typed from 123 / #+=.
        if (mode != KeyboardMode.ALPHABETIC && text == "'") mode = KeyboardMode.ALPHABETIC
        refreshAutocapitalization()
    }

    fun space() {
        val ic = connection() ?: return
        val now = SystemClock.uptimeMillis()
        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        // Double-tap space → ". " after a word, like iOS's "." shortcut.
        if (now - lastSpaceTap < DOUBLE_SPACE_MS && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit() && !isPasswordField) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
            lastSpaceTap = 0
        } else {
            ic.commitText(" ", 1)
            lastSpaceTap = now
        }
        if (mode != KeyboardMode.ALPHABETIC) mode = KeyboardMode.ALPHABETIC
        if (shift == ShiftState.SHIFTED) shift = ShiftState.LOWERCASE
        refreshAutocapitalization()
    }

    /** One backspace: the selection, else the last whole character (grapheme). */
    fun backspace() {
        val ic = connection() ?: return
        lastSpaceTap = 0
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            val before = ic.getTextBeforeCursor(32, 0)?.toString().orEmpty()
            if (before.isEmpty()) {
                // Nothing we can see — let the editor decide (some fields
                // only react to key events).
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            } else {
                val it = BreakIterator.getCharacterInstance()
                it.setText(before)
                val end = before.length
                val start = it.preceding(end).takeIf { p -> p != BreakIterator.DONE } ?: (end - 1)
                ic.deleteSurroundingText(end - start, 0)
            }
        }
        refreshAutocapitalization()
    }

    /** A held backspace's later repeats delete whole words, as on iOS. */
    fun backspaceWord() {
        val ic = connection() ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            refreshAutocapitalization()
            return
        }
        val before = ic.getTextBeforeCursor(96, 0)?.toString().orEmpty()
        if (before.isEmpty()) return backspace()
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        ic.deleteSurroundingText(maxOf(1, before.length - i), 0)
        refreshAutocapitalization()
    }

    fun returnKey(performAction: (Int) -> Unit) {
        val ic = connection() ?: return
        lastSpaceTap = 0
        when (val style = returnStyle) {
            ReturnStyle.NEWLINE -> ic.commitText("\n", 1)
            else -> {
                val action = editorInfo()?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_UNSPECIFIED
                if (style == ReturnStyle.NEXT || style == ReturnStyle.PREVIOUS || action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    performAction(action)
                } else {
                    ic.commitText("\n", 1)
                }
            }
        }
        if (mode != KeyboardMode.ALPHABETIC) mode = KeyboardMode.ALPHABETIC
        refreshAutocapitalization()
    }

    fun switchMode(target: KeyboardMode) {
        mode = target
        if (target == KeyboardMode.ALPHABETIC) refreshAutocapitalization()
    }

    /** Moves the caret one step for the space-bar trackpad. */
    fun moveCaret(keyCode: Int) {
        val ic = connection() ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    companion object {
        const val DOUBLE_TAP_MS = 300L
        const val DOUBLE_SPACE_MS = 450L
    }
}
