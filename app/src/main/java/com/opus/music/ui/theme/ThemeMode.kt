package com.opus.music.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-wide theme switch. Holds "dark" or "light"; MainActivity seeds it from
 * SharedPreferences at startup, Settings writes to it so the whole app
 * re-composes instantly without a restart.
 */
object ThemeController {
    const val DARK = "dark"
    const val LIGHT = "light"

    private val _mode = MutableStateFlow(DARK)
    val mode: StateFlow<String> = _mode

    fun init(mode: String) {
        _mode.value = if (mode == LIGHT) LIGHT else DARK
    }

    fun set(mode: String) {
        _mode.value = if (mode == LIGHT) LIGHT else DARK
    }

    fun label(mode: String): String = if (mode == LIGHT) "Light" else "Dark"
}
