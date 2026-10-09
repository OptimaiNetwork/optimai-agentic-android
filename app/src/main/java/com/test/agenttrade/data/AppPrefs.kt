package com.test.agenttrade.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.test.agenttrade.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide persisted settings. The keyboard service runs in this same
 * process, so — unlike iOS's App Group container — a plain
 * `SharedPreferences` file is already shared between the app and the
 * keyboard.
 */
object AppPrefs {
    private lateinit var prefs: SharedPreferences

    enum class Theme(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

    private val _theme = MutableStateFlow(Theme.DARK)
    val theme: StateFlow<Theme> = _theme.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("optimai", Context.MODE_PRIVATE)
        _theme.value = prefs.getString(KEY_THEME, null)?.let { runCatching { Theme.valueOf(it) }.getOrNull() } ?: Theme.DARK
    }

    fun setTheme(theme: Theme) {
        _theme.value = theme
        prefs.edit { putString(KEY_THEME, theme.name) }
    }

    /** The OptimAI Agentic server, overridable in Settings → Developer. */
    var baseUrl: String
        get() = if (::prefs.isInitialized) prefs.getString(KEY_BASE_URL, null) ?: BuildConfig.API_BASE_URL else BuildConfig.API_BASE_URL
        set(value) = prefs.edit { if (value.isBlank()) remove(KEY_BASE_URL) else putString(KEY_BASE_URL, value.trim()) }

    val isBaseUrlOverridden: Boolean get() = prefs.contains(KEY_BASE_URL)

    fun getString(key: String): String? = prefs.getString(key, null)
    fun putString(key: String, value: String?) = prefs.edit { if (value == null) remove(key) else putString(key, value) }
    fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null
    fun putLong(key: String, value: Long) = prefs.edit { putLong(key, value) }

    private const val KEY_THEME = "appTheme"
    private const val KEY_BASE_URL = "apiBaseUrl"
}
