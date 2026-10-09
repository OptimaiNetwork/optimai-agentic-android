package com.test.agenttrade.keyboard

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

/** iOS 26 dark keyboard colors, sampled from the iOS app's screenshots. */
object KeyColors {
    val background = Color(0xFF171717)
    val key = Color(0xFF3D3D3D)
    val keyPressed = Color(0xFF6B6B6B)
    val label = Color(0xFFFFFFFF)
    /** The shift glyph while shifted — black on the gray key, as on iOS. */
    val shiftActiveGlyph = Color(0xFF000000)
    val blue = Color(0xFF0A84FF)
    val bluePressed = Color(0xFF0064D2)
    val callout = Color(0xFF3D3D3D)
}

/** What the callout layer draws above the keys (grid coordinates, dp). */
class CalloutState {
    /** The character key under a finger — its enlarged letter bubble. */
    var input by mutableStateOf<Pair<LaidOutKey, String>?>(null)
    /** A long-press alternates bubble. */
    var action by mutableStateOf<ActionCallout?>(null)
}

data class ActionCallout(val key: LaidOutKey, val options: List<String>, val selected: Int, val bubble: Rect, val itemWidth: Float)

/**
 * The key grid: four iOS-proportioned rows drawn in a single Canvas, with
 * one multi-touch handler for the whole grid (keys commit on release, a
 * second finger commits the first, a finger can slide between keys, the
 * mode key can be slid off to type one symbol, long-press alternates,
 * backspace auto-repeat and the space-bar trackpad).
 */
@Composable
fun KeyGrid(
    engine: KeyboardEngine,
    callouts: CalloutState,
    feedback: KeyFeedback,
    onReturnAction: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 96)
    BoxWithConstraints(modifier.fillMaxWidth().height((KeyMetrics.ROW_HEIGHT * 4).dp)) {
        val widthDp = maxWidth.value
        val touch = remember { KeyTouchHandler(engine, callouts, feedback, scope) }
        touch.width = widthDp
        touch.onReturnAction = onReturnAction
        // Read here so a mode or case change redraws the grid.
        val keys = remember(engine.mode, engine.uppercase, widthDp) { touch.keys }

        Canvas(
            Modifier.fillMaxWidth().height((KeyMetrics.ROW_HEIGHT * 4).dp).pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            val x = change.position.x / density.density
                            val y = change.position.y / density.density
                            when {
                                change.pressed && !change.previousPressed -> touch.down(change.id, x, y)
                                change.pressed && change.previousPressed -> if (change.position != change.previousPosition) touch.move(change.id, x, y)
                                !change.pressed && change.previousPressed -> touch.up(change.id, x, y)
                            }
                            change.consume()
                        }
                    }
                }
            },
        ) {
            drawKeys(keys, engine, touch, callouts, measurer, density)
        }
    }
}

// MARK: - Touch handling

class KeyTouchHandler(
    private val engine: KeyboardEngine,
    private val callouts: CalloutState,
    private val feedback: KeyFeedback,
    private val scope: CoroutineScope,
) {
    var width: Float = 0f

    private var cachedKeys: List<LaidOutKey> = emptyList()
    private var cacheKey: Triple<KeyboardMode, Boolean, Float>? = null

    /**
     * The layout for the engine's current mode and case — computed here, not
     * handed down from composition, so a touch that switches the mode (the
     * "123" key) hit-tests the new layout on its very next move event.
     */
    val keys: List<LaidOutKey>
        get() {
            val k = Triple(engine.mode, engine.uppercase, width)
            if (k != cacheKey) {
                cachedKeys = KeyboardLayouts.layout(engine.mode, width, engine.uppercase)
                cacheKey = k
            }
            return cachedKeys
        }
    var onReturnAction: (Int) -> Unit = {}

    /** System keys currently held down, drawn lighter. */
    var pressed by mutableStateOf<Set<KeyAction>>(emptySet())

    private class Touch(
        val id: PointerId,
        var key: LaidOutKey?,
        val startX: Float,
        val startY: Float,
        /** Set when this touch started on a mode key and switched layouts. */
        val switchedFrom: KeyboardMode?,
    ) {
        var longPress: Job? = null
        var repeat: Job? = null
        var trackpad = false
        var lastX = startX
        var lastY = startY
        var accX = 0f
        var accY = 0f
        var alternates: ActionCallout? = null
        var committed = false
    }

    private val touches = mutableMapOf<PointerId, Touch>()

    fun down(id: PointerId, x: Float, y: Float) {
        // A second finger landing commits a character still under the first —
        // fast two-thumb typing on iOS never waits for the first release.
        touches.values.filter { it.key?.isCharacter == true && it.alternates == null && !it.committed }.forEach { other ->
            commitCharacter(other)
            finish(other)
        }
        val key = KeyboardLayouts.hitTest(keys, x, y) ?: return
        val touch = Touch(id, key, x, y, null)
        touches[id] = touch
        feedback.press(key.action)

        when (val action = key.action) {
            is KeyAction.Character -> {
                callouts.input = key to action.text
                val alts = KeyboardLayouts.alternatesFor(action.text)
                if (alts != null && alts.size > 1) {
                    touch.longPress = scope.launch {
                        delay(LONG_PRESS_MS)
                        showAlternates(touch, key, alts)
                    }
                }
            }
            KeyAction.Shift -> {
                engine.tapShift()
                markPressed(action, true)
            }
            KeyAction.Backspace -> {
                markPressed(action, true)
                engine.backspace()
                touch.repeat = scope.launch {
                    delay(REPEAT_DELAY_MS)
                    var count = 0
                    while (true) {
                        if (count < WORD_DELETE_AFTER) engine.backspace() else engine.backspaceWord()
                        feedback.repeatTick()
                        count++
                        delay(if (count < WORD_DELETE_AFTER) REPEAT_INTERVAL_MS else WORD_REPEAT_INTERVAL_MS)
                    }
                }
            }
            KeyAction.Space -> {
                markPressed(action, true)
                touch.longPress = scope.launch {
                    delay(TRACKPAD_DELAY_MS)
                    touch.trackpad = true
                    engine.isTrackpadActive = true
                    feedback.trackpadStart()
                }
            }
            is KeyAction.Mode -> {
                markPressed(action, true)
                // Switches on touch-down, like iOS — so the finger can slide
                // straight onto a symbol and release to type just that one.
                val from = engine.mode
                engine.switchMode(action.target)
                touches[id] = Touch(id, key, x, y, from)
            }
            KeyAction.Return -> markPressed(action, true)
        }
    }

    fun move(id: PointerId, x: Float, y: Float) {
        val touch = touches[id] ?: return
        if (touch.trackpad) {
            touch.accX += x - touch.lastX
            touch.accY += y - touch.lastY
            touch.lastX = x
            touch.lastY = y
            while (touch.accX >= TRACKPAD_STEP_X) { engine.moveCaret(KeyEvent.KEYCODE_DPAD_RIGHT); touch.accX -= TRACKPAD_STEP_X; feedback.trackpadTick() }
            while (touch.accX <= -TRACKPAD_STEP_X) { engine.moveCaret(KeyEvent.KEYCODE_DPAD_LEFT); touch.accX += TRACKPAD_STEP_X; feedback.trackpadTick() }
            while (touch.accY >= TRACKPAD_STEP_Y) { engine.moveCaret(KeyEvent.KEYCODE_DPAD_DOWN); touch.accY -= TRACKPAD_STEP_Y; feedback.trackpadTick() }
            while (touch.accY <= -TRACKPAD_STEP_Y) { engine.moveCaret(KeyEvent.KEYCODE_DPAD_UP); touch.accY += TRACKPAD_STEP_Y; feedback.trackpadTick() }
            return
        }
        touch.lastX = x
        touch.lastY = y

        touch.alternates?.let { callout ->
            val index = floor((x - callout.bubble.left) / callout.itemWidth).toInt().coerceIn(0, callout.options.size - 1)
            if (index != callout.selected) {
                val updated = callout.copy(selected = index)
                touch.alternates = updated
                callouts.action = updated
                feedback.selectionTick()
            }
            return
        }

        val start = touch.key ?: return
        when (start.action) {
            KeyAction.Space -> {
                // Dragging away before the trackpad engages cancels it.
                if (abs(x - touch.startX) > 12 || abs(y - touch.startY) > 20) touch.longPress?.cancel()
                return
            }
            KeyAction.Backspace, KeyAction.Shift, KeyAction.Return -> return
            else -> {}
        }
        // Character keys (and a mode key just switched from) follow the finger.
        val now = KeyboardLayouts.hitTest(keys, x, y)
        if (now == null || now == touch.key) return
        if (start.action is KeyAction.Mode && now.action is KeyAction.Mode && touch.switchedFrom != null) return
        touch.longPress?.cancel()
        touch.key = now
        if (start.action is KeyAction.Mode) markPressed(start.action, false)
        if (now.isCharacter) {
            val text = (now.action as KeyAction.Character).text
            callouts.input = now to text
            val alts = KeyboardLayouts.alternatesFor(text)
            if (alts != null && alts.size > 1) {
                touch.longPress = scope.launch {
                    delay(LONG_PRESS_MS)
                    showAlternates(touch, now, alts)
                }
            }
        } else {
            callouts.input = null
        }
    }

    fun up(id: PointerId, x: Float, y: Float) {
        val touch = touches[id] ?: return
        touch.longPress?.cancel()
        touch.repeat?.cancel()
        when {
            touch.trackpad -> {
                engine.isTrackpadActive = false
                engine.refreshAutocapitalization()
            }
            touch.alternates != null -> {
                val callout = touch.alternates!!
                engine.insert(callout.options[callout.selected])
                if (touch.switchedFrom != null) engine.switchMode(touch.switchedFrom)
            }
            touch.committed -> {}
            else -> when (val action = touch.key?.action) {
                is KeyAction.Character -> {
                    commitCharacter(touch)
                    // Slid off "123" onto a symbol: type it and go back.
                    if (touch.switchedFrom != null) engine.switchMode(touch.switchedFrom)
                }
                KeyAction.Space -> if (isOver(touch.key, x, y)) engine.space()
                KeyAction.Return -> if (isOver(touch.key, x, y)) engine.returnKey(onReturnAction)
                else -> {}
            }
        }
        finish(touch)
    }

    private fun isOver(key: LaidOutKey?, x: Float, y: Float): Boolean = key != null && KeyboardLayouts.hitTest(keys, x, y) == key

    private fun commitCharacter(touch: Touch) {
        val action = touch.key?.action as? KeyAction.Character ?: return
        touch.committed = true
        engine.insert(action.text)
    }

    private fun showAlternates(touch: Touch, key: LaidOutKey, options: List<String>) {
        // The base character sits nearest the finger: options run rightward
        // from a key on the left half, leftward (reversed) on the right half.
        val leftward = key.frame.center.x > width / 2
        val ordered = if (leftward) options.reversed() else options
        val itemW = key.frame.width + 4f
        val bubbleW = itemW * ordered.size
        var left = if (leftward) key.frame.right + 2f - bubbleW else key.frame.left - 2f
        left = left.coerceIn(2f, width - 2f - bubbleW)
        val bottom = key.frame.top - 6f
        val bubble = Rect(left, bottom - 50f, left + bubbleW, bottom)
        val callout = ActionCallout(key, ordered, ordered.indexOf(options.first()), bubble, itemW)
        touch.alternates = callout
        callouts.input = null
        callouts.action = callout
        feedback.longPress()
    }

    private fun markPressed(action: KeyAction, on: Boolean) {
        pressed = if (on) pressed + action else pressed - action
    }

    private fun finish(touch: Touch) {
        touch.longPress?.cancel()
        touch.repeat?.cancel()
        touches.remove(touch.id)
        touch.key?.action?.let { if (!touch.isStillHeld(it)) markPressed(it, false) }
        if (touches.values.none { it.key?.isCharacter == true }) callouts.input = null
        if (touch.alternates != null || touches.values.none { it.alternates != null }) callouts.action = null
        // A mode key's own press highlight.
        pressed = pressed.filter { a -> touches.values.any { it.key?.action == a } }.toSet()
    }

    private fun Touch.isStillHeld(action: KeyAction): Boolean = touches.values.any { it !== this && it.key?.action == action }

    companion object {
        const val LONG_PRESS_MS = 450L
        const val TRACKPAD_DELAY_MS = 500L
        const val REPEAT_DELAY_MS = 500L
        const val REPEAT_INTERVAL_MS = 100L
        const val WORD_REPEAT_INTERVAL_MS = 220L
        const val WORD_DELETE_AFTER = 20
        const val TRACKPAD_STEP_X = 9f
        const val TRACKPAD_STEP_Y = 22f
    }
}

// MARK: - Drawing

private fun DrawScope.drawKeys(
    keys: List<LaidOutKey>,
    engine: KeyboardEngine,
    touch: KeyTouchHandler,
    callouts: CalloutState,
    measurer: TextMeasurer,
    density: Density,
) {
    val dp = density.density
    val radius = CornerRadius(KeyMetrics.KEY_RADIUS * dp)
    val blank = engine.isTrackpadActive
    val returnStyle = engine.returnStyle
    val calloutKey = callouts.input?.first ?: callouts.action?.key
    keys.forEach { key ->
        val r = Rect(key.frame.left * dp, key.frame.top * dp, key.frame.right * dp, key.frame.bottom * dp)
        val isPressed = key.action in touch.pressed
        val isReturnPrimary = key.action == KeyAction.Return && returnStyle.isPrimary
        val bg = when {
            isReturnPrimary -> if (isPressed) KeyColors.bluePressed else KeyColors.blue
            isPressed && key.isSystem -> KeyColors.keyPressed
            else -> KeyColors.key
        }
        // The key under an open callout is part of the callout's own shape.
        if (key == calloutKey) return@forEach
        drawRoundRect(bg, r.topLeft, r.size, radius)
        if (blank) return@forEach
        drawKeyLabel(key, r, engine, returnStyle, measurer, dp)
    }
}

private val letterStyle = TextStyle(fontSize = 25.5.sp, fontWeight = FontWeight.Normal, color = KeyColors.label)
private val systemStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, color = KeyColors.label)

private fun DrawScope.drawKeyLabel(
    key: LaidOutKey,
    r: Rect,
    engine: KeyboardEngine,
    returnStyle: KeyboardEngine.ReturnStyle,
    measurer: TextMeasurer,
    dp: Float,
) {
    when (val action = key.action) {
        is KeyAction.Character -> drawCentered(measurer.measure(action.text, letterStyle), r, yNudge = -1.5f * dp)
        is KeyAction.Mode -> drawCentered(measurer.measure(action.label, systemStyle), r)
        KeyAction.Space -> drawCentered(measurer.measure("space", systemStyle), r)
        KeyAction.Return -> {
            val label = returnStyle.label
            if (label != null) drawCentered(measurer.measure(label, systemStyle), r) else drawReturnGlyph(r.center, dp, KeyColors.label)
        }
        KeyAction.Shift -> drawShiftGlyph(r.center, dp, engine.shift)
        KeyAction.Backspace -> drawDeleteGlyph(r.center, dp, KeyColors.label)
    }
}

private fun DrawScope.drawCentered(layout: TextLayoutResult, r: Rect, yNudge: Float = 0f) {
    drawText(
        layout,
        topLeft = Offset(r.center.x - layout.size.width / 2f, r.center.y - layout.size.height / 2f + yNudge),
    )
}

/** SF Symbols' `shift` / `shift.fill` (plus the caps-lock bar). */
fun DrawScope.drawShiftGlyph(center: Offset, dp: Float, state: ShiftState) {
    val w = 21f * dp
    val h = 19f * dp
    val left = center.x - w / 2
    val top = center.y - h / 2 - (if (state == ShiftState.CAPS_LOCK) 2.5f * dp else 0f)
    val stemL = left + w * 0.29f
    val stemR = left + w * 0.71f
    val shoulder = top + h * 0.52f
    val path = Path().apply {
        moveTo(center.x, top)
        lineTo(left + w, shoulder)
        lineTo(stemR, shoulder)
        lineTo(stemR, top + h)
        lineTo(stemL, top + h)
        lineTo(stemL, shoulder)
        lineTo(left, shoulder)
        close()
    }
    val rounded = PathEffect.cornerPathEffect(2.6f * dp)
    if (state == ShiftState.LOWERCASE) {
        drawPath(path, KeyColors.label, style = Stroke(width = 1.7f * dp, join = StrokeJoin.Round, pathEffect = rounded))
    } else {
        drawPath(path, KeyColors.shiftActiveGlyph, style = Stroke(width = 1.7f * dp, join = StrokeJoin.Round, pathEffect = rounded))
        drawPath(path, KeyColors.shiftActiveGlyph, style = Fill)
        if (state == ShiftState.CAPS_LOCK) {
            drawRoundRect(
                KeyColors.shiftActiveGlyph,
                topLeft = Offset(stemL, top + h + 3f * dp),
                size = Size(stemR - stemL, 2.4f * dp),
                cornerRadius = CornerRadius(1.2f * dp),
            )
        }
    }
}

/** SF Symbols' `delete.left`. */
fun DrawScope.drawDeleteGlyph(center: Offset, dp: Float, color: Color) {
    val w = 23f * dp
    val h = 16.5f * dp
    val left = center.x - w / 2
    val top = center.y - h / 2
    val point = left + w * 0.3f
    val body = Path().apply {
        moveTo(left, center.y)
        lineTo(point, top)
        lineTo(left + w, top)
        lineTo(left + w, top + h)
        lineTo(point, top + h)
        close()
    }
    drawPath(body, color, style = Stroke(width = 1.6f * dp, join = StrokeJoin.Round, pathEffect = PathEffect.cornerPathEffect(2.8f * dp)))
    val cx = left + w * 0.63f
    val s = 3.4f * dp
    drawLine(color, Offset(cx - s, center.y - s), Offset(cx + s, center.y + s), 1.6f * dp, StrokeCap.Round)
    drawLine(color, Offset(cx - s, center.y + s), Offset(cx + s, center.y - s), 1.6f * dp, StrokeCap.Round)
}

/** SF Symbols' `return.left` (↵). */
fun DrawScope.drawReturnGlyph(center: Offset, dp: Float, color: Color) {
    val w = 17f * dp
    val h = 13f * dp
    val left = center.x - w / 2
    val top = center.y - h / 2
    val stroke = Stroke(width = 1.8f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val baseY = top + h * 0.78f
    val path = Path().apply {
        moveTo(left + w, top)
        lineTo(left + w, baseY - 3f * dp)
        quadraticTo(left + w, baseY, left + w - 3f * dp, baseY)
        lineTo(left, baseY)
    }
    drawPath(path, color, style = stroke)
    val head = Path().apply {
        moveTo(left + 4.6f * dp, baseY - 4.6f * dp)
        lineTo(left, baseY)
        lineTo(left + 4.6f * dp, baseY + 4.6f * dp)
    }
    drawPath(head, color, style = stroke)
}

// MARK: - Callouts

/**
 * Draws the open callouts in `gridOrigin`-offset coordinates of the whole
 * keyboard, so a top-row bubble can rise over the toolbar as on iOS.
 */
fun DrawScope.drawCallouts(callouts: CalloutState, gridOrigin: Offset, gridWidthDp: Float, measurer: TextMeasurer, dp: Float) {
    translate(gridOrigin.x, gridOrigin.y) {
        callouts.input?.let { (key, text) -> drawInputCallout(key, text, gridWidthDp, measurer, dp) }
        callouts.action?.let { drawActionCallout(it, measurer, dp) }
    }
}

private val calloutLetterStyle = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Normal, color = KeyColors.label)
private val calloutOptionStyle = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Normal, color = KeyColors.label)

/**
 * The iOS input callout: the pressed key's own rect, widening through a
 * curved neck into a taller bubble above it that shows the letter large.
 */
private fun DrawScope.drawInputCallout(key: LaidOutKey, text: String, gridWidthDp: Float, measurer: TextMeasurer, dp: Float) {
    val k = Rect(key.frame.left * dp, key.frame.top * dp, key.frame.right * dp, key.frame.bottom * dp)
    val pad = 11f * dp
    val bubbleH = 52f * dp
    var bLeft = k.left - pad
    var bRight = k.right + pad
    // Stay inside the keyboard: edge keys get a lopsided bubble.
    val maxRight = gridWidthDp * dp - 1f * dp
    if (bLeft < 1f * dp) { bRight += 1f * dp - bLeft; bLeft = 1f * dp }
    if (bRight > maxRight) { bLeft -= bRight - maxRight; bRight = maxRight }
    val bBottom = k.top - 4f * dp
    val bubble = Rect(bLeft, bBottom - bubbleH, bRight, bBottom)
    val path = calloutPath(k, bubble, dp)
    drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(width = 3f * dp))
    drawPath(path, KeyColors.callout)
    val layout = measurer.measure(text, calloutLetterStyle)
    drawText(layout, topLeft = Offset(bubble.center.x - layout.size.width / 2f, bubble.center.y - layout.size.height / 2f - 1f * dp))
}

/** Long-press alternates: a row of options above the key, one highlighted. */
private fun DrawScope.drawActionCallout(callout: ActionCallout, measurer: TextMeasurer, dp: Float) {
    val k = Rect(callout.key.frame.left * dp, callout.key.frame.top * dp, callout.key.frame.right * dp, callout.key.frame.bottom * dp)
    val bubble = Rect(callout.bubble.left * dp, callout.bubble.top * dp, callout.bubble.right * dp, callout.bubble.bottom * dp)
    val path = calloutPath(k, bubble, dp)
    drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(width = 3f * dp))
    drawPath(path, KeyColors.callout)
    val itemW = callout.itemWidth * dp
    callout.options.forEachIndexed { i, option ->
        val cell = Rect(bubble.left + i * itemW, bubble.top, bubble.left + (i + 1) * itemW, bubble.bottom)
        if (i == callout.selected) {
            drawRoundRect(KeyColors.blue, Offset(cell.left + 3f * dp, cell.top + 5f * dp), Size(cell.width - 6f * dp, cell.height - 10f * dp), CornerRadius(7f * dp))
        }
        val layout = measurer.measure(option, calloutOptionStyle)
        drawText(layout, topLeft = Offset(cell.center.x - layout.size.width / 2f, cell.center.y - layout.size.height / 2f))
    }
}

/** Key rect + bubble, joined by a smooth neck — one outline (a union). */
private fun calloutPath(key: Rect, bubble: Rect, dp: Float): Path {
    val kr = KeyMetrics.KEY_RADIUS * dp
    val br = 10f * dp
    val neck = 9f * dp
    val top = Path().apply { addRoundRect(RoundRect(bubble, CornerRadius(br))) }
    // The neck meets the bubble just outside the key, not at the bubble's
    // corners — a long alternates bubble mustn't drag it across the row.
    val leftX = maxOf(bubble.left, key.left - 11f * dp)
    val rightX = minOf(bubble.right, key.right + 11f * dp)
    val bridge = Path().apply {
        moveTo(leftX, bubble.bottom - br)
        lineTo(leftX, bubble.bottom - 1f)
        cubicTo(leftX, bubble.bottom + neck * 0.6f, key.left, key.top - neck * 0.2f, key.left, key.top + kr)
        lineTo(key.left, key.bottom - kr)
        quadraticTo(key.left, key.bottom, key.left + kr, key.bottom)
        lineTo(key.right - kr, key.bottom)
        quadraticTo(key.right, key.bottom, key.right, key.bottom - kr)
        lineTo(key.right, key.top + kr)
        cubicTo(key.right, key.top - neck * 0.2f, rightX, bubble.bottom + neck * 0.6f, rightX, bubble.bottom - 1f)
        lineTo(rightX, bubble.bottom - br)
        close()
    }
    return Path().apply { op(top, bridge, PathOperation.Union) }
}
