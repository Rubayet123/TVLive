package io.github.rubayet123.tvlive.data

import android.content.Context
import android.content.SharedPreferences

object StreamHealthConfig {
    private const val PREFS_NAME = "stream_health_prefs"

    const val KEY_AUTO_SWITCH = "pref_stream_auto_switch"
    const val KEY_FAILOVER_MODE = "pref_stream_failover_mode" // "SEQUENTIAL", "CONCURRENT"
    const val KEY_FALLBACK_STRATEGY = "pref_stream_fallback_strategy" // "QUALITY", "FASTEST", "PROVIDER_ORDER"
    const val KEY_FAILOVER_HUD = "pref_stream_failover_hud" // "BANNER", "TOAST", "SILENT"
    const val KEY_AUTO_SKIP_DEAD = "pref_stream_auto_skip_dead"
    
    const val KEY_WATCHDOG_ENABLED = "pref_stream_watchdog_enabled"
    const val KEY_STALL_TIMEOUT_SEC = "pref_stream_stall_timeout_sec" // 3, 6, 10, 15
    
    const val KEY_AUTO_RECONNECT = "pref_stream_auto_reconnect"
    const val KEY_MAX_RECONNECT_ATTEMPTS = "pref_stream_max_reconnect_attempts" // 1, 3, 5
    const val KEY_FAILURE_ACTION = "pref_stream_failure_action" // "DIALOG", "NEXT_CHANNEL", "EXIT"

    const val KEY_BUFFER_PROFILE = "pref_stream_buffer_profile" // "FAST_ZAPPING", "BALANCED", "HIGH_STABILITY"
    const val KEY_CONNECT_TIMEOUT_SEC = "pref_stream_connect_timeout_sec" // 3, 5, 10
    const val KEY_BLACKLIST_COOLDOWN_MIN = "pref_stream_blacklist_cooldown_min" // 0, 1, 5, 15

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAutoSwitchEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_AUTO_SWITCH, true)

    fun setAutoSwitchEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_AUTO_SWITCH, enabled).apply()

    fun getFailoverMode(context: Context): String =
        getPrefs(context).getString(KEY_FAILOVER_MODE, "SEQUENTIAL") ?: "SEQUENTIAL"

    fun setFailoverMode(context: Context, mode: String) =
        getPrefs(context).edit().putString(KEY_FAILOVER_MODE, mode).apply()

    fun getFallbackStrategy(context: Context): String =
        getPrefs(context).getString(KEY_FALLBACK_STRATEGY, "PROVIDER_ORDER") ?: "PROVIDER_ORDER"

    fun setFallbackStrategy(context: Context, strategy: String) =
        getPrefs(context).edit().putString(KEY_FALLBACK_STRATEGY, strategy).apply()

    fun getFailoverHudMode(context: Context): String =
        getPrefs(context).getString(KEY_FAILOVER_HUD, "BANNER") ?: "BANNER"

    fun setFailoverHudMode(context: Context, mode: String) =
        getPrefs(context).edit().putString(KEY_FAILOVER_HUD, mode).apply()

    fun isAutoSkipDeadEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_AUTO_SKIP_DEAD, false)

    fun setAutoSkipDeadEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_AUTO_SKIP_DEAD, enabled).apply()

    fun isWatchdogEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_WATCHDOG_ENABLED, true)

    fun setWatchdogEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_WATCHDOG_ENABLED, enabled).apply()

    fun getStallTimeoutSec(context: Context): Int =
        getPrefs(context).getInt(KEY_STALL_TIMEOUT_SEC, 4)

    fun setStallTimeoutSec(context: Context, seconds: Int) =
        getPrefs(context).edit().putInt(KEY_STALL_TIMEOUT_SEC, seconds).apply()

    fun isAutoReconnectEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_AUTO_RECONNECT, true)

    fun setAutoReconnectEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()

    fun getMaxReconnectAttempts(context: Context): Int =
        getPrefs(context).getInt(KEY_MAX_RECONNECT_ATTEMPTS, 1)

    fun setMaxReconnectAttempts(context: Context, attempts: Int) =
        getPrefs(context).edit().putInt(KEY_MAX_RECONNECT_ATTEMPTS, attempts).apply()

    fun getFailureAction(context: Context): String =
        getPrefs(context).getString(KEY_FAILURE_ACTION, "DIALOG") ?: "DIALOG"

    fun setFailureAction(context: Context, action: String) =
        getPrefs(context).edit().putString(KEY_FAILURE_ACTION, action).apply()

    fun getBufferProfile(context: Context): String =
        getPrefs(context).getString(KEY_BUFFER_PROFILE, "BALANCED") ?: "BALANCED"

    fun setBufferProfile(context: Context, profile: String) =
        getPrefs(context).edit().putString(KEY_BUFFER_PROFILE, profile).apply()

    fun getConnectTimeoutSec(context: Context): Int =
        getPrefs(context).getInt(KEY_CONNECT_TIMEOUT_SEC, 4)

    fun setConnectTimeoutSec(context: Context, seconds: Int) =
        getPrefs(context).edit().putInt(KEY_CONNECT_TIMEOUT_SEC, seconds).apply()

    fun getBlacklistCooldownMin(context: Context): Int =
        getPrefs(context).getInt(KEY_BLACKLIST_COOLDOWN_MIN, 5)

    fun setBlacklistCooldownMin(context: Context, minutes: Int) =
        getPrefs(context).edit().putInt(KEY_BLACKLIST_COOLDOWN_MIN, minutes).apply()

    fun applyPreset(context: Context, preset: String) {
        when (preset) {
            "FAST_ZAPPING" -> {
                setAutoSwitchEnabled(context, true)
                setWatchdogEnabled(context, true)
                setStallTimeoutSec(context, 3)
                setMaxReconnectAttempts(context, 1)
                setBufferProfile(context, "FAST_ZAPPING")
                setConnectTimeoutSec(context, 4)
                setAutoSkipDeadEnabled(context, true)
            }
            "BALANCED" -> {
                setAutoSwitchEnabled(context, true)
                setWatchdogEnabled(context, true)
                setStallTimeoutSec(context, 6)
                setMaxReconnectAttempts(context, 3)
                setBufferProfile(context, "BALANCED")
                setConnectTimeoutSec(context, 8)
                setAutoSkipDeadEnabled(context, false)
            }
            "HIGH_STABILITY" -> {
                setAutoSwitchEnabled(context, true)
                setWatchdogEnabled(context, true)
                setStallTimeoutSec(context, 10)
                setMaxReconnectAttempts(context, 5)
                setBufferProfile(context, "HIGH_STABILITY")
                setConnectTimeoutSec(context, 12)
                setAutoSkipDeadEnabled(context, false)
            }
        }
    }
}
