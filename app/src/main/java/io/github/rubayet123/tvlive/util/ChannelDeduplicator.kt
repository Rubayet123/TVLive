package io.github.rubayet123.tvlive.util

import android.content.Context
import android.content.SharedPreferences
import io.github.rubayet123.tvlive.model.Channel
import io.github.rubayet123.tvlive.model.StreamSource
import java.util.Locale

object ChannelDeduplicator {

    private const val PREFS_NAME = "tv_live_prefs"
    const val PREF_GROUP_DUPLICATES = "pref_group_duplicate_channels"

    fun isGroupDuplicatesEnabled(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_GROUP_DUPLICATES, true)
    }

    fun setGroupDuplicatesEnabled(context: Context, enabled: Boolean) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_GROUP_DUPLICATES, enabled).apply()
    }

    /**
     * Groups duplicate channels from multiple ISP/M3U sources into unified channels
     * with multi-source fallback streams, strictly isolating SD vs HD and distinct channel numbers.
     */
    fun deduplicateChannels(context: Context, channels: List<Channel>): List<Channel> {
        if (!isGroupDuplicatesEnabled(context)) {
            return channels.map { ch ->
                if (ch.sources.isEmpty()) {
                    ch.copy(sources = ch.effectiveSources)
                } else ch
            }
        }

        val groupedMap = LinkedHashMap<String, MutableList<Channel>>()

        for (channel in channels) {
            val fp = generateFingerprint(channel)
            groupedMap.getOrPut(fp) { mutableListOf() }.add(channel)
        }

        val result = mutableListOf<Channel>()

        for ((_, groupList) in groupedMap) {
            if (groupList.size == 1) {
                val single = groupList[0]
                val overrideCat = CategoryOverrideManager.getOverrideCategory(context, single)
                result.add(
                    single.copy(
                        group = overrideCat ?: single.group,
                        sources = single.effectiveSources
                    )
                )
            } else {
                // Merge multiple sources into one unified channel entity
                val merged = mergeChannelGroup(context, groupList)
                result.add(merged)
            }
        }

        return result
    }

    private fun mergeChannelGroup(context: Context, channels: List<Channel>): Channel {
        // Collect all distinct stream sources across duplicates
        val combinedSources = mutableListOf<StreamSource>()
        val seenStreamUrls = mutableSetOf<String>()

        for (channel in channels) {
            for (source in channel.effectiveSources) {
                val normalizedUrl = source.streamUrl.trim()
                if (normalizedUrl.isNotEmpty() && !seenStreamUrls.contains(normalizedUrl)) {
                    seenStreamUrls.add(normalizedUrl)
                    val cleanName = Channel.cleanProviderName(source.providerName, normalizedUrl, channel.name, channel.group)
                    combinedSources.add(source.copy(providerName = cleanName))
                }
            }
        }

        // Differentiate duplicate provider names if any
        val providerCount = combinedSources.groupingBy { it.providerName }.eachCount()
        val providerIndexTracker = mutableMapOf<String, Int>()
        val refinedSources = combinedSources.map { src ->
            val count = providerCount[src.providerName] ?: 1
            if (count > 1) {
                val currentIdx = (providerIndexTracker[src.providerName] ?: 0) + 1
                providerIndexTracker[src.providerName] = currentIdx
                src.copy(providerName = "${src.providerName} #$currentIdx")
            } else {
                src
            }
        }

        // Pick best title: prefer properly cased name with clean HD label
        val bestName = selectBestChannelName(channels.map { it.name })

        // Pick best logo: prefer valid web URL over fallback
        val bestLogo = channels.firstOrNull {
            !it.logoUrl.isNullOrBlank() && !it.logoUrl.contains("fallback")
        }?.logoUrl ?: channels.firstOrNull { !it.logoUrl.isNullOrBlank() }?.logoUrl

        val canonicalId = "unified_" + bestName.lowercase().replace(Regex("[^a-z0-9]"), "")

        // Sort sources by user's provider order in Settings (Provider priority first, then file priority)
        val activeSources = io.github.rubayet123.tvlive.data.SourceRepository(context).getSources()
        val sortedSources = ProviderPriorityHelper.sortSources(refinedSources, activeSources)

        val primary = sortedSources.firstOrNull()
        val primaryUrl = primary?.streamUrl ?: channels[0].streamUrl
        val primaryHeaders = primary?.headers ?: channels[0].headers
        val primaryLicenseType = primary?.licenseType ?: channels[0].licenseType
        val primaryLicenseKey = primary?.licenseKey ?: channels[0].licenseKey

        // Resolve canonical category: user override wins first, then best non-blank group
        var resolvedCategory: String? = null
        for (ch in channels) {
            val override = CategoryOverrideManager.getOverrideCategory(context, ch)
            if (!override.isNullOrBlank()) {
                resolvedCategory = override
                break
            }
        }
        if (resolvedCategory == null) {
            val dummyUnified = Channel(
                id = canonicalId,
                name = bestName,
                logoUrl = bestLogo,
                streamUrl = primaryUrl,
                sources = sortedSources
            )
            resolvedCategory = CategoryOverrideManager.getOverrideCategory(context, dummyUnified)
        }
        if (resolvedCategory == null) {
            resolvedCategory = channels.firstOrNull { !it.group.isNullOrBlank() }?.group
        }

        return Channel(
            id = canonicalId,
            name = bestName,
            logoUrl = bestLogo,
            streamUrl = primaryUrl,
            group = resolvedCategory,
            licenseType = primaryLicenseType,
            licenseKey = primaryLicenseKey,
            headers = primaryHeaders,
            sources = sortedSources,
            subtitle = channels.firstOrNull { !it.subtitle.isNullOrBlank() }?.subtitle
        )
    }

    private fun selectBestChannelName(names: List<String>): String {
        // Prefer names that have proper casing, or the cleanest representation
        return names.maxByOrNull { name ->
            var score = 0
            if (name.contains(" HD", ignoreCase = true) || name.endsWith("HD")) score += 5
            if (name.any { it.isUpperCase() } && name.any { it.isLowerCase() }) score += 10 // Mixed case
            if (!name.contains("[") && !name.contains("]")) score += 3 // Clean symbols
            if (!name.contains("(") && !name.contains(")")) score += 3
            if (!name.contains("-")) score += 2
            score - name.length / 10
        } ?: names.first()
    }

    fun generateFingerprint(channel: Channel): String {
        val rawName = channel.name.trim()
        val quality = extractQualityTier(rawName)
        val canonicalBase = extractCanonicalBaseName(rawName)

        return "${canonicalBase}__${quality}"
    }

    private fun extractQualityTier(name: String): String {
        val upper = name.uppercase()
        return when {
            upper.contains("4K") || upper.contains("UHD") -> "4k"
            upper.contains("FHD") || upper.contains("1080P") || upper.contains("1080") -> "fhd"
            upper.contains("720P") || upper.contains("720") -> "hd"
            upper.contains(" HD") || upper.endsWith("HD") || upper.contains("[HD]") || upper.contains("(HD)") || upper.contains("-HD") || upper.contains("_HD") -> "hd"
            upper.contains(" SD") || upper.endsWith("SD") || upper.contains("[SD]") || upper.contains("(SD)") -> "sd"
            else -> "sd" // Default is Standard Definition (SD)
        }
    }

    private fun extractCanonicalBaseName(name: String): String {
        var base = name.uppercase()

        // Remove quality tags
        base = base.replace(Regex("\\[(4K|UHD|FHD|1080P|720P|HD|SD)\\]", RegexOption.IGNORE_CASE), " ")
        base = base.replace(Regex("\\((4K|UHD|FHD|1080P|720P|HD|SD)\\)", RegexOption.IGNORE_CASE), " ")
        base = base.replace(Regex("\\b(4K|UHD|FHD|1080P|720P|HD|SD)\\b", RegexOption.IGNORE_CASE), " ")

        // Remove cosmetic live suffixes
        base = base.replace(Regex("\\[LIVE\\]", RegexOption.IGNORE_CASE), " ")
        base = base.replace(Regex("\\(LIVE\\)", RegexOption.IGNORE_CASE), " ")
        base = base.replace(Regex("\\b(LIVE TV|TV LIVE|LIVE)\\b", RegexOption.IGNORE_CASE), " ")

        // Remove common cosmetic symbols but keep digits & letters
        base = base.replace(Regex("[-_.:|/()\\[\\]&]"), " ")

        // Remove superfluous whitespace and collapse to canonical alphanumeric representation
        val alphanumeric = base.lowercase().replace(Regex("[^a-z0-9]"), "")
        return alphanumeric.ifBlank { name.lowercase().replace(Regex("[^a-z0-9]"), "") }
    }
}
