package com.admin.siliconpulse.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ViewMode { SIMPLE, PRO }

/** Vista Sencilla (usuario comun) vs Pro (tecnicos). Persistida en SharedPreferences. */
class ViewModePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("siliconpulse", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(ViewMode.valueOf(prefs.getString(KEY, ViewMode.SIMPLE.name) ?: ViewMode.SIMPLE.name))
    val mode: StateFlow<ViewMode> = _mode

    fun set(mode: ViewMode) {
        _mode.value = mode
        prefs.edit().putString(KEY, mode.name).apply()
    }

    private companion object { const val KEY = "view_mode" }
}
