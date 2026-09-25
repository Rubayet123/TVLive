package io.github.rubayet123.tvlive.util

import android.content.Context
import io.github.rubayet123.tvlive.data.SourceRepository
import io.github.rubayet123.tvlive.model.Channel
import io.github.rubayet123.tvlive.model.Source
import io.github.rubayet123.tvlive.model.StreamSource

object ProviderPriorityHelper {

    fun normalizeKey(name: String): String {
        return name.lowercase()
            .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "")
            .replace(Regex("#\\d+"), "")
            .replace("isp", "")
            .replace("tv", "")
            .replace("m3u8", "")
            .replace("m3u", "")
            .replace("playlist", "")
            .replace("server", "")
            .replace("plugin", "")
            .replace("stream", "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    /**
     * Calculates the priority index for a given stream provider name against the user's active providers in Settings.
     * Lower score = higher priority (0 is first).
     */
    fun getProviderPriority(providerName: String, activeSources: List<Source>): Int {
        val normStreamProvider = normalizeKey(providerName)
        if (normStreamProvider.isEmpty()) return 9999

        // 1. Direct or fuzzy match against user's configured sources in order
        for ((index, source) in activeSources.withIndex()) {
            if (!source.isActive) continue
            val normSourceName = normalizeKey(source.name)

            // Exact normalized key match (e.g. "redforce" == "redforce")
            if (normStreamProvider == normSourceName) {
                return index
            }

            // Keyword containment match
            if (normSourceName.isNotEmpty() &&
                (normStreamProvider.contains(normSourceName) || normSourceName.contains(normStreamProvider))) {
                return index
            }

            // Alias special cases
            if (isAliasMatch(normStreamProvider, normSourceName)) {
                return index
            }
        }

        // 2. Fallbacks for well-known prefixes if not matched directly
        return when {
            normStreamProvider.contains("redforce") -> findIndexByKeyword(activeSources, "redforce") ?: 100
            normStreamProvider.contains("orbit") -> findIndexByKeyword(activeSources, "orbit") ?: 101
            normStreamProvider.contains("roarzone") -> findIndexByKeyword(activeSources, "roarzone") ?: 102
            normStreamProvider.contains("playz") -> findIndexByKeyword(activeSources, "playz") ?: 103
            normStreamProvider.contains("ideal") -> findIndexByKeyword(activeSources, "ideal") ?: 104
            normStreamProvider.contains("splex") -> findIndexByKeyword(activeSources, "splex") ?: 105
            normStreamProvider.contains("bas") || normStreamProvider.contains("local") ->
                findIndexByKeyword(activeSources, "bas") ?: findIndexByKeyword(activeSources, "local") ?: 106
            normStreamProvider.contains("bdix") -> findIndexByKeyword(activeSources, "bdix") ?: 107
            normStreamProvider.contains("web") -> findIndexByKeyword(activeSources, "web") ?: 108
            else -> 999
        }
    }

    private fun isAliasMatch(streamKey: String, sourceKey: String): Boolean {
        if ((streamKey.contains("bas") || streamKey.contains("local")) &&
            (sourceKey.contains("bas") || sourceKey.contains("local"))) return true
        if (streamKey.contains("bdix") && sourceKey.contains("bdix")) return true
        return false
    }

    private fun findIndexByKeyword(sources: List<Source>, keyword: String): Int? {
        val idx = sources.indexOfFirst { it.isActive && normalizeKey(it.name).contains(keyword) }
        return if (idx >= 0) idx else null
    }

    /**
     * Sorts stream sources according to the active provider priority from Settings.
     */
    fun sortSources(sources: List<StreamSource>, activeSources: List<Source>): List<StreamSource> {
        if (sources.size <= 1) return sources
        return sources.sortedWith(
            compareBy<StreamSource> { getProviderPriority(it.providerName, activeSources) }
                .thenBy { it.priority }
        )
    }

    /**
     * Re-sorts all channels in a playlist (and their internal stream sources) according to the new provider ranking.
     */
    fun reorderChannelSources(channels: List<Channel>, context: Context): List<Channel> {
        val activeSources = SourceRepository(context).getSources()
        return channels.map { channel ->
            if (channel.sources.size > 1) {
                val sorted = sortSources(channel.sources, activeSources)
                val primary = sorted.firstOrNull()
                channel.copy(
                    streamUrl = primary?.streamUrl ?: channel.streamUrl,
                    headers = primary?.headers ?: channel.headers,
                    licenseType = primary?.licenseType ?: channel.licenseType,
                    licenseKey = primary?.licenseKey ?: channel.licenseKey,
                    sources = sorted
                )
            } else {
                channel
            }
        }
    }
}
