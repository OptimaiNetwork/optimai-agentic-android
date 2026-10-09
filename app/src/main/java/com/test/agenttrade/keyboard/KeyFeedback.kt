package com.test.agenttrade.keyboard

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Key clicks and haptics. Both follow the system's own switches (Sound →
 * "Touch sounds"/keyboard sound, and the haptic setting) the way iOS's
 * keyboard follows Settings → Sounds & Haptics → Keyboard Feedback — this
 * keyboard never overrides what the user chose there.
 */
class KeyFeedback(private val context: Context) {
    var view: View? = null
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val soundsEnabled: Boolean
        get() = Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0

    fun press(action: KeyAction) {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (!soundsEnabled) return
        val effect = when (action) {
            KeyAction.Backspace -> AudioManager.FX_KEYPRESS_DELETE
            KeyAction.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyAction.Return -> AudioManager.FX_KEYPRESS_RETURN
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audio.playSoundEffect(effect, -1f)
    }

    fun repeatTick() {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (soundsEnabled) audio.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE, -1f)
    }

    fun longPress() {
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun selectionTick() {
        view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun trackpadStart() {
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun trackpadTick() {
        view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun tap() {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
