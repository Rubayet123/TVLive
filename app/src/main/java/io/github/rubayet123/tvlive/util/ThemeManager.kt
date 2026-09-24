package io.github.rubayet123.tvlive.util

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class AppTheme(
    val id: String,
    val displayName: String,
    val primaryBgHex: String,
    val cardBgHex: String,
    val accentHex: String,
    val accentSecondaryHex: String,
    val topBarHex: String,
    val primaryTextHex: String,
    val secondaryTextHex: String,
    val description: String
) {
    CLASSIC_LEANBACK(
        id = "classic_leanback",
        displayName = "Classic Leanback (Android TV Default)",
        primaryBgHex = "#616161",
        cardBgHex = "#212121",
        accentHex = "#FFA000",
        accentSecondaryHex = "#FF8F00",
        topBarHex = "#383838",
        primaryTextHex = "#FFFFFF",
        secondaryTextHex = "#E0E0E0",
        description = "Original TV Live scheme with charcoal sidebar & grey grid background"
    ),
    MIDNIGHT_EMERALD(
        id = "midnight_emerald",
        displayName = "Midnight Emerald (Mobile Default)",
        primaryBgHex = "#0F1117",
        cardBgHex = "#1A1F2C",
        accentHex = "#10B981",
        accentSecondaryHex = "#059669",
        topBarHex = "#161A23",
        primaryTextHex = "#FFFFFF",
        secondaryTextHex = "#9CA3AF",
        description = "Sleek dark gray with modern emerald accents"
    ),
    OCEAN_WAVE(
        id = "ocean_wave",
        displayName = "Ocean Breeze",
        primaryBgHex = "#0A1128",
        cardBgHex = "#1C2541",
        accentHex = "#00B4D8",
        accentSecondaryHex = "#0077B6",
        topBarHex = "#101F42",
        primaryTextHex = "#FFFFFF",
        secondaryTextHex = "#A5B1C2",
        description = "Deep navy blue with refreshing cyan accents"
    ),
    AMETHYST_SUNSET(
        id = "amethyst_sunset",
        displayName = "Amethyst Sunset",
        primaryBgHex = "#160F29",
        cardBgHex = "#241B35",
        accentHex = "#D149FF",
        accentSecondaryHex = "#8B5CF6",
        topBarHex = "#1F163D",
        primaryTextHex = "#FFFFFF",
        secondaryTextHex = "#C3C7DB",
        description = "Charming deep plum with radiant purple accents"
    ),
    SOLARIZED_EMBER(
        id = "solarized_ember",
        displayName = "Solarized Ember",
        primaryBgHex = "#1A1412",
        cardBgHex = "#2E1F1B",
        accentHex = "#F95738",
        accentSecondaryHex = "#EE964B",
        topBarHex = "#261B18",
        primaryTextHex = "#FFFFFF",
        secondaryTextHex = "#D4C5C1",
        description = "Cozy charcoal gray with warm ember orange accents"
    ),
    CLASSIC_MIDNIGHT(
        id = "classic_midnight",
        displayName = "Classic Midnight",
        primaryBgHex = "#000000",
        cardBgHex = "#121212",
        accentHex = "#2563EB",
        accentSecondaryHex = "#1D4ED8",
        topBarHex = "#080808",
        primaryTextHex = "#FFFFFF",
        secondaryTextHex = "#9EA3B0",
        description = "True OLED black with professional neon blue accents"
    ),
    CLASSIC_ORANGE(
        id = "classic_orange",
        displayName = "Classic Orange & Dark",
        primaryBgHex = "#0F1117",
        cardBgHex = "#1C1F2E",
        accentHex = "#FF9800",
        accentSecondaryHex = "#F57C00",
        topBarHex = "#161922",
        primaryTextHex = "#FFFFFF",
        secondaryTextHex = "#94A3B8",
        description = "Premium black and dark gray with vibrant orange accents"
    ),
    GREEN_DARK(
        id = "green_dark",
        displayName = "Green & Dark",
        primaryBgHex = "#0A0E0C",
        cardBgHex = "#15201A",
        accentHex = "#10B981",
        accentSecondaryHex = "#059669",
        topBarHex = "#101814",
        primaryTextHex = "#FFFFFF",
        secondaryTextHex = "#94A3B8",
        description = "Rich OLED dark canvas with vibrant green accents"
    );

    val primaryBg: Color get() = Color(android.graphics.Color.parseColor(primaryBgHex))
    val cardBg: Color get() = Color(android.graphics.Color.parseColor(cardBgHex))
    val accent: Color get() = Color(android.graphics.Color.parseColor(accentHex))
    val accentSecondary: Color get() = Color(android.graphics.Color.parseColor(accentSecondaryHex))
    val topBar: Color get() = Color(android.graphics.Color.parseColor(topBarHex))
    val primaryText: Color get() = Color(android.graphics.Color.parseColor(primaryTextHex))
    val secondaryText: Color get() = Color(android.graphics.Color.parseColor(secondaryTextHex))
    
    val primaryBgInt: Int get() = android.graphics.Color.parseColor(primaryBgHex)
    val cardBgInt: Int get() = android.graphics.Color.parseColor(cardBgHex)
    val accentInt: Int get() = android.graphics.Color.parseColor(accentHex)
    val accentSecondaryInt: Int get() = android.graphics.Color.parseColor(accentSecondaryHex)
    val topBarInt: Int get() = android.graphics.Color.parseColor(topBarHex)
    val primaryTextInt: Int get() = android.graphics.Color.parseColor(primaryTextHex)
    val secondaryTextInt: Int get() = android.graphics.Color.parseColor(secondaryTextHex)
}

object ThemeManager {
    private const val PREFS_NAME = "tv_live_theme_prefs"
    private const val KEY_THEME = "selected_theme_id"

    fun getSelectedTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultTheme = if (DeviceUtils.isTvDevice(context)) {
            AppTheme.CLASSIC_LEANBACK
        } else {
            AppTheme.MIDNIGHT_EMERALD
        }
        val savedId = prefs.getString(KEY_THEME, defaultTheme.id)
        return AppTheme.values().find { it.id == savedId } ?: defaultTheme
    }

    fun setSelectedTheme(context: Context, theme: AppTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme.id).apply()
    }
}
