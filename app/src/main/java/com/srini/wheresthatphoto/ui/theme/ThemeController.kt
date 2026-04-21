package com.srini.wheresthatphoto.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Theme modes the user can pick between.
 *
 * - [System]: follow the OS dark-mode setting.
 * - [Light]/[Dark]: override the system setting.
 */
enum class ThemeMode { System, Light, Dark }

/**
 * Lightweight, disk-persisted theme-mode holder. The design calls for a
 * top-right moon/sun toggle that overrides the system theme, but we keep
 * `System` as the default so new installs match Android's dark-mode setting.
 *
 * Backed by SharedPreferences (no extra deps). Call [setMode] or [toggle]
 * from UI — changes trigger recomposition because [mode] is Compose state.
 */
class ThemeController(private val prefs: SharedPreferences) {

    private val state: MutableState<ThemeMode> = mutableStateOf(load())

    val mode: ThemeMode
        get() = state.value

    fun setMode(newMode: ThemeMode) {
        if (state.value == newMode) return
        state.value = newMode
        prefs.edit().putString(KEY, newMode.name).apply()
    }

    /**
     * Cycle between Light and Dark. If the current mode is System, flip to
     * the opposite of the system's current preference as determined by
     * [systemIsDark].
     */
    fun toggle(systemIsDark: Boolean) {
        val next = when (state.value) {
            ThemeMode.System -> if (systemIsDark) ThemeMode.Light else ThemeMode.Dark
            ThemeMode.Light -> ThemeMode.Dark
            ThemeMode.Dark -> ThemeMode.Light
        }
        setMode(next)
    }

    private fun load(): ThemeMode = try {
        ThemeMode.valueOf(prefs.getString(KEY, ThemeMode.System.name) ?: ThemeMode.System.name)
    } catch (_: IllegalArgumentException) {
        ThemeMode.System
    }

    companion object {
        private const val PREFS_NAME = "wtp_theme_prefs"
        private const val KEY = "theme_mode"

        fun from(context: Context): ThemeController {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ThemeController(prefs)
        }
    }
}

/**
 * Convenience composable that creates (or retrieves) a [ThemeController]
 * scoped to the current [LocalContext]. Remembered across recompositions.
 */
@Composable
fun rememberThemeController(): ThemeController {
    val context = LocalContext.current
    return remember(context) { ThemeController.from(context) }
}
