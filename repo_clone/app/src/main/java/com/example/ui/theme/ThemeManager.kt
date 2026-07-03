package com.example.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppTheme(val displayName: String, val isDark: Boolean) {
    LIGHT_GREEN_WHITE("Green-White", false),
    LIGHT_PURPLE_WHITE("Purple-White", false),
    LIGHT_BLACK_WHITE("Black-White (Light)", false),
    LIGHT_PINK_WHITE("Pink-White", false),
    DARK_BLACK_PURPLE("Black-Purple", true),
    DARK_BLACK_BLUE("Black-Blue", true),
    DARK_BLACK_RED("Black-Red", true),
    DARK_BLACK_WHITE("Black-White (Dark)", true),
    DARK_BLACK_GREEN("Black-Green", true)
}

class ThemeManager(context: Context) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _currentTheme = MutableStateFlow(
        AppTheme.valueOf(prefs.getString("selected_theme", AppTheme.DARK_BLACK_BLUE.name) ?: AppTheme.DARK_BLACK_BLUE.name)
    )
    val currentTheme: StateFlow<AppTheme> = _currentTheme

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("selected_theme", theme.name).apply()
        _currentTheme.value = theme
    }
}
