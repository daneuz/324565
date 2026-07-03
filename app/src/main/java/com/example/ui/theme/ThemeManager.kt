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
    DARK_BLACK_GREEN("Black-Green", true),
    DARK_NEON_CYAN("Neon-Cyan", true),
    DARK_NEON_MAGENTA("Neon-Magenta", true),
    DARK_NEON_YELLOW("Neon-Yellow", true),
    DARK_NEON_CRIMSON("Neon-Crimson", true),
    LIGHT_BLUE_WHITE("Blue-White", false),
    LIGHT_ORANGE_WHITE("Orange-White", false),
    DARK_GREY_TEAL("Grey-Teal", true),
    DARK_SPACE_PURPLE("Space-Purple", true),
    DARK_FOREST_GREEN("Forest-Green", true),
    DARK_CYBERPUNK("Cyberpunk", true),
    LIGHT_ROSE_GOLD("Rose-Gold", false),
    LIGHT_OCEAN_BLUE("Ocean-Blue", false),
    DARK_DRACULA("Dracula", true),
    DARK_STEALTH("Stealth", true),
    LIGHT_PASTEL("Pastel-Dream", false),
    LIGHT_CHERRY("Cherry-Blossom", false),
    DARK_SUNSET("Neon-Sunset", true),
    DARK_BLOOD_MOON("Blood-Moon", true),
    LIGHT_MINT("Mint", false),
    LIGHT_LAVENDER("Lavender", false),
    DARK_HACKER("Hacker-Term", true),
    DARK_GALAXY("Galaxy", true),
    DARK_ABYSS("Abyss", true),
    LIGHT_CREAM("Cream", false),
    LIGHT_SUNFLOWER("Sunflower", false),
    DARK_SYNTHWAVE("Synthwave", true),
    DARK_GOLDEN_NIGHT("Golden-Night", true),
    DARK_AURORA("Aurora", true),
    LIGHT_FROST("Frost", false),
    LIGHT_EARTH("Earth", false),
    DARK_NINJA("Ninja", true)
}

class ThemeManager(context: Context) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _currentTheme = MutableStateFlow(
        AppTheme.valueOf(prefs.getString("selected_theme", AppTheme.DARK_BLACK_BLUE.name) ?: AppTheme.DARK_BLACK_BLUE.name)
    )
    val currentTheme: StateFlow<AppTheme> = _currentTheme

    private val _language = MutableStateFlow(
        prefs.getString("selected_language", "en") ?: "en"
    )
    val language: StateFlow<String> = _language

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("selected_theme", theme.name).apply()
        _currentTheme.value = theme
    }
    
    fun setLanguage(lang: String) {
        prefs.edit().putString("selected_language", lang).apply()
        _language.value = lang
    }
}
