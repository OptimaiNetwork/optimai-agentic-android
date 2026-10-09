package com.test.agenttrade.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.test.agenttrade.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The OptimAI keyboard, the Android port of the iOS `OptimAIKeyboard`
 * extension: an iOS-style QWERTY keyboard with the live bStocks ticker
 * toolbar above it. Runs in the app's own process, so it reads the same wallet session and
 * server address the app does, and hands trades to it by deep link.
 */
class OptimAIKeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var engine: KeyboardEngine
        private set
    lateinit var toolbar: TickerToolbarModel
        private set
    lateinit var feedback: KeyFeedback
        private set
    val callouts = CalloutState()

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        engine = KeyboardEngine({ currentInputConnection }, { currentInputEditorInfo })
        feedback = KeyFeedback(this)
        toolbar = TickerToolbarModel(this, scope, ::readText, ::openInApp)
    }

    override fun onCreateInputView(): View {
        // Compose finds its lifecycle/saved-state owners up the view tree;
        // an IME's window has none of its own, so the service provides them.
        window.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OptimAIKeyboardService)
            setViewTreeViewModelStoreOwner(this@OptimAIKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@OptimAIKeyboardService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            feedback.view = this
            setContent { KeyboardRoot(this@OptimAIKeyboardService) }
        }
    }

    /** No full-screen extract mode in landscape — the iOS keyboard never does that. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        engine.onStartInput(restarting)
        toolbar.isSuppressed = engine.isPasswordField
        toolbar.onShow()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        callouts.input = null
        callouts.action = null
        engine.isTrackpadActive = false
        toolbar.onHide()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        engine.onSelectionChanged()
        // Every keystroke the editor reports — the iOS `textDidChangeAsync`.
        toolbar.onTextChanged()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        scope.cancel()
        store.clear()
        super.onDestroy()
    }

    // MARK: - Host field

    private fun readText(): Pair<String, String>? {
        val ic = currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(1000, 0)?.toString() ?: return null
        val after = ic.getTextAfterCursor(1000, 0)?.toString().orEmpty()
        return before to after
    }

    fun performEditorAction(action: Int) {
        currentInputConnection?.performEditorAction(action)
    }

    /** `agenttrade://buy?...` — always into this app, never another handler. */
    private fun openInApp(uri: Uri) {
        // The keyboard steps aside as the app comes forward, as on iOS.
        requestHideSelf(0)
        val intent = Intent(Intent.ACTION_VIEW, uri, this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(intent) }.onFailure { Log.w("OptimAIKeyboard", "open app failed", it) }
    }

    // MARK: - Globe / mic

    /** 🌐 tap: the next keyboard, like iOS. */
    fun switchToNextKeyboard() {
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) switchToNextInputMethod(false) else false
        if (!switched) showKeyboardPicker()
    }

    /** 🌐 long-press: the keyboard list. */
    fun showKeyboardPicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    /** 🎙: hands off to the system's voice-typing keyboard. */
    fun startDictation() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        for (imi in imm.enabledInputMethodList) {
            for (subtype in imm.getEnabledInputMethodSubtypeList(imi, true)) {
                if (subtype.mode == "voice" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    switchInputMethod(imi.id, subtype)
                    return
                }
            }
        }
        Toast.makeText(this, "Voice typing isn't set up on this device.", Toast.LENGTH_SHORT).show()
    }
}
