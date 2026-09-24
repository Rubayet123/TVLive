package io.github.rubayet123.tvlive.model

data class Source(
    val name: String,
    val url: String, // Can be local path or HTTP URL
    val isActive: Boolean = true,
    val type: String? = "M3U",
    val refreshIntervalHours: Int = 0, // 0 = off/manual, -1 = app start, or hours
    val lastRefreshedAt: Long = 0,
    val isUserAdded: Boolean = false
) {
    fun getActualType(): String = type ?: "M3U"

    fun getIntervalLabel(): String = when (refreshIntervalHours) {
        0 -> "Manual Only"
        -1 -> "On App Start"
        1 -> "Every 1 Hour"
        2 -> "Every 2 Hours"
        6 -> "Every 6 Hours"
        12 -> "Every 12 Hours"
        24 -> "Every 24 Hours"
        48 -> "Every 48 Hours"
        168 -> "Every 7 Days"
        else -> "Every $refreshIntervalHours Hours"
    }

    fun getFormattedLastRefreshed(): String {
        if (lastRefreshedAt <= 0L) return "Never synced"
        val diffMs = System.currentTimeMillis() - lastRefreshedAt
        if (diffMs < 0L) return "Just now"
        val diffSec = diffMs / 1000
        val diffMin = diffSec / 60
        val diffHours = diffMin / 60
        val diffDays = diffHours / 24

        return when {
            diffMin < 1 -> "Just now"
            diffMin < 60 -> "$diffMin min${if (diffMin > 1) "s" else ""} ago"
            diffHours < 24 -> "$diffHours hour${if (diffHours > 1) "s" else ""} ago"
            diffDays == 1L -> "Yesterday"
            else -> "$diffDays days ago"
        }
    }
}
