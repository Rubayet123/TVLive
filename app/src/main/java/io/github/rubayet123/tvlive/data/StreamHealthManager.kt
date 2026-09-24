package io.github.rubayet123.tvlive.data

import android.content.Context
import android.util.Log
import io.github.rubayet123.tvlive.model.Channel
import io.github.rubayet123.tvlive.model.StreamSource
import java.util.concurrent.ConcurrentHashMap

object StreamHealthManager {
    private const val TAG = "StreamHealthManager"

    // Map of streamUrl -> timestamp when penalty cooldown expires (System.currentTimeMillis())
    private val penalizedStreams = ConcurrentHashMap<String, Long>()
    
    // Map of channelId or streamUrl -> consecutive failure count
    private val failureCounts = ConcurrentHashMap<String, Int>()

    fun recordFailure(context: Context, url: String) {
        if (url.isEmpty()) return
        val cooldownMinutes = StreamHealthConfig.getBlacklistCooldownMin(context)
        if (cooldownMinutes <= 0) return

        val count = (failureCounts[url] ?: 0) + 1
        failureCounts[url] = count
        
        val cooldownDurationMs = cooldownMinutes * 60 * 1000L
        val expiry = System.currentTimeMillis() + cooldownDurationMs
        penalizedStreams[url] = expiry
        Log.d(TAG, "Penalized stream $url for $cooldownMinutes mins (Fail count: $count)")
    }

    fun recordSuccess(url: String) {
        if (url.isEmpty()) return
        penalizedStreams.remove(url)
        failureCounts.remove(url)
    }

    fun isPenalized(url: String): Boolean {
        val expiry = penalizedStreams[url] ?: return false
        if (System.currentTimeMillis() > expiry) {
            penalizedStreams.remove(url)
            return false
        }
        return true
    }

    fun rankCandidates(context: Context, sources: List<StreamSource>): List<StreamSource> {
        if (sources.size <= 1) return sources
        val strategy = StreamHealthConfig.getFallbackStrategy(context)

        val nonPenalized = sources.filter { !isPenalized(it.streamUrl) }
        val penalized = sources.filter { isPenalized(it.streamUrl) }

        val comparator = when (strategy) {
            "QUALITY" -> Comparator<StreamSource> { s1, s2 ->
                val q1 = parseQualityScore(s1.providerName)
                val q2 = parseQualityScore(s2.providerName)
                if (q1 != q2) q2.compareTo(q1) else s1.priority.compareTo(s2.priority)
            }
            "PROVIDER_ORDER" -> Comparator<StreamSource> { s1, s2 ->
                s1.priority.compareTo(s2.priority)
            }
            else -> Comparator<StreamSource> { s1, s2 ->
                s1.priority.compareTo(s2.priority)
            }
        }

        val sortedNonPenalized = nonPenalized.sortedWith(comparator)
        val sortedPenalized = penalized.sortedWith(comparator)

        return sortedNonPenalized + sortedPenalized
    }

    private fun parseQualityScore(text: String?): Int {
        if (text == null) return 50
        val q = text.uppercase()
        return when {
            q.contains("4K") || q.contains("2160") -> 100
            q.contains("FHD") || q.contains("1080") -> 90
            q.contains("HD") || q.contains("720") -> 80
            q.contains("SD") || q.contains("576") || q.contains("480") -> 60
            q.contains("360") || q.contains("240") -> 40
            else -> 50
        }
    }

    fun isChannelAllDead(channel: Channel): Boolean {
        val sources = channel.effectiveSources
        if (sources.isEmpty()) {
            return isPenalized(channel.streamUrl)
        }
        return sources.all { isPenalized(it.streamUrl) }
    }

    fun resetAllStats() {
        penalizedStreams.clear()
        failureCounts.clear()
    }
}
