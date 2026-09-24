package io.github.rubayet123.tvlive.util

import io.github.rubayet123.tvlive.model.Channel

object CanonicalKeyHelper {

    /**
     * Converts any raw channel name, ID, or title into a clean, normalized canonical slug.
     * Examples:
     * - "unified_discoveryhd" -> "discovery"
     * - "Discovery HD" -> "discovery"
     * - "DISCOVERY HD [1080p]" -> "discovery"
     * - "Colors Bangla HD" -> "colorsbangla"
     * - "unified_colorsbanglahd" -> "colorsbangla"
     * - "Sony AAT" -> "sonyaat"
     * - "Sony Aath HD" -> "sonyaath"
     */
    fun toCanonicalSlug(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw.lowercase().trim()

        // Strip prefixes like "unified_"
        if (s.startsWith("unified_")) {
            s = s.removePrefix("unified_")
        }

        // Strip bracketed quality / info tags
        s = s.replace(Regex("\\[.*?\\]|\\(.*?\\)"), " ")

        // Strip common technical / transport words and quality suffixes with word boundaries
        s = s.replace(Regex("\\b(bdix|fhd|uhd|4k|1080p|720p|576p|480p|360p|hd|sd|hevc|h264|h265|live|stream|backup|tv)\\b", RegexOption.IGNORE_CASE), " ")

        // Strip special punctuation
        s = s.replace(Regex("[-_.:|/()\\[\\]&+]"), " ")

        // Collapse to alphanumeric
        var alphanumeric = s.replace(Regex("[^a-z0-9]"), "")
        if (alphanumeric.isNotBlank()) {
            alphanumeric = stripQualitySuffix(alphanumeric)
            return alphanumeric
        }

        // Fallback: if everything was stripped, return alphanumeric of original stripped of quality
        return stripQualitySuffix(raw.lowercase().replace(Regex("[^a-z0-9]"), ""))
    }

    /**
     * Strips trailing quality designations (like 'hd', 'sd', 'fhd', '4k', '1080p') from the end of a slug.
     * Example: "colorsbanglahd" -> "colorsbangla", "discoveryfhd" -> "discovery"
     */
    fun stripQualitySuffix(raw: String): String {
        var s = raw.lowercase().trim()
        if (s.startsWith("unified_")) {
            s = s.removePrefix("unified_")
        }
        val clean = s.replace(Regex("[^a-z0-9]"), "")
        val qualities = listOf("1080p", "720p", "576p", "480p", "fhd", "uhd", "4k", "hevc", "hd", "sd")
        for (q in qualities) {
            if (clean.endsWith(q) && clean.length > q.length + 2) {
                return clean.removeSuffix(q)
            }
        }
        return clean
    }

    /**
     * Converts to clean alphanumeric representation without stripping 'hd' or 'tv'
     * (for secondary precision match, e.g. "colorsbanglahd", "discoveryhd")
     */
    fun toCleanName(raw: String): String {
        var s = raw.lowercase().trim()
        if (s.startsWith("unified_")) {
            s = s.removePrefix("unified_")
        }
        s = s.replace(Regex("\\[.*?\\]|\\(.*?\\)"), " ")
        s = s.replace(Regex("[-_.:|/()\\[\\]&+]"), " ")
        val alphanumeric = s.replace(Regex("[^a-z0-9]"), "")
        return alphanumeric.ifBlank { raw.lowercase().replace(Regex("[^a-z0-9]"), "") }
    }

    /**
     * Generates all alias lookup keys for a channel to ensure 100% matching against any override key format.
     */
    fun getLookupKeys(channel: Channel): List<String> {
        val keys = mutableListOf<String>()

        // 1. Direct ID matches
        if (channel.id.isNotBlank()) {
            keys.add(channel.id) // e.g. "unified_colorsbanglahd"
            keys.add(channel.id.lowercase())
            val idNoPrefix = channel.id.removePrefix("unified_").removePrefix("UNIFIED_")
            keys.add(idNoPrefix)
            keys.add(idNoPrefix.lowercase())

            val slugFromId = toCanonicalSlug(channel.id)
            if (slugFromId.isNotBlank()) keys.add(slugFromId)
            val cleanFromId = toCleanName(channel.id)
            if (cleanFromId.isNotBlank()) keys.add(cleanFromId)
            val strippedFromId = stripQualitySuffix(channel.id)
            if (strippedFromId.isNotBlank()) keys.add(strippedFromId)
        }

        // 2. Direct Name matches
        if (channel.name.isNotBlank()) {
            keys.add(channel.name) // e.g. "Colors Bangla HD"
            keys.add(channel.name.lowercase().trim())
            val slugFromName = toCanonicalSlug(channel.name)
            if (slugFromName.isNotBlank()) keys.add(slugFromName)
            val cleanFromName = toCleanName(channel.name)
            if (cleanFromName.isNotBlank()) keys.add(cleanFromName)
            val strippedFromName = stripQualitySuffix(channel.name)
            if (strippedFromName.isNotBlank()) keys.add(strippedFromName)

            // Handle common regional channel spelling variants (e.g. Sony AAT / Sony AATH)
            if (cleanFromName.contains("sonyaat")) {
                keys.add("sonyaat")
                keys.add("sonyaath")
            }
        }

        // 3. Primary and multi-source stream URLs
        if (channel.streamUrl.isNotBlank()) {
            keys.add(channel.streamUrl.trim())
            keys.add("${channel.name}_${channel.streamUrl.hashCode()}")
        }
        for (source in channel.effectiveSources) {
            if (source.streamUrl.isNotBlank()) {
                keys.add(source.streamUrl.trim())
                keys.add("${channel.name}_${source.streamUrl.hashCode()}")
            }
        }

        return keys.filter { it.isNotBlank() }.distinct()
    }

    /**
     * Generates lookup keys from a raw string key (such as one found in backup overrides)
     */
    fun getLookupKeysForRawKey(rawKey: String): List<String> {
        val keys = mutableListOf<String>()
        if (rawKey.isNotBlank()) {
            keys.add(rawKey)
            keys.add(rawKey.lowercase().trim())
            val rawNoPrefix = rawKey.removePrefix("unified_").removePrefix("UNIFIED_")
            keys.add(rawNoPrefix)
            keys.add(rawNoPrefix.lowercase())

            val slug = toCanonicalSlug(rawKey)
            if (slug.isNotBlank()) keys.add(slug)
            val clean = toCleanName(rawKey)
            if (clean.isNotBlank()) keys.add(clean)
            val stripped = stripQualitySuffix(rawKey)
            if (stripped.isNotBlank()) keys.add(stripped)

            if (clean.contains("sonyaat")) {
                keys.add("sonyaat")
                keys.add("sonyaath")
            }
        }
        return keys.filter { it.isNotBlank() }.distinct()
    }
}

