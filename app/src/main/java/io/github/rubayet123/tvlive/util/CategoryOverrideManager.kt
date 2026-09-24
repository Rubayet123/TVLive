package io.github.rubayet123.tvlive.util

import android.content.Context
import io.github.rubayet123.tvlive.model.Channel
import org.json.JSONObject

object CategoryOverrideManager {

    private const val PREF_NAME = "channel_category_overrides_prefs"
    private const val KEY_OVERRIDES = "category_overrides_json"
    private const val KEY_PROVIDERS = "provider_associations_json"

    // In-memory cache for fast O(1) lookups and zero lag during scrolling & UI updates
    @Volatile
    private var cachedOverrides: MutableMap<String, String>? = null
    @Volatile
    private var cachedProviders: MutableMap<String, String>? = null

    fun getChannelKey(channel: Channel): String {
        return if (channel.id.isNotBlank()) {
            channel.id
        } else {
            "${channel.name}_${channel.streamUrl.hashCode()}"
        }
    }

    /**
     * Resolves custom category override for a channel using CanonicalKeyHelper.
     * Matches across Canonical Slug ("colorsbangla"), Clean Name ("colorsbanglahd"),
     * Unified ID ("unified_colorsbanglahd"), Raw Display Name ("Colors Bangla HD"), ID, and stream URLs.
     */
    fun getOverrideCategory(context: Context, channel: Channel): String? {
        val overrides = getOverridesMap(context)
        if (overrides.isEmpty()) return null

        val lookupKeys = CanonicalKeyHelper.getLookupKeys(channel)
        for (key in lookupKeys) {
            val cat = overrides[key]
            if (!cat.isNullOrBlank()) return cat.trim()
        }

        // Case-insensitive fallback scan across all stored keys
        val channelSlug = CanonicalKeyHelper.toCanonicalSlug(channel.name)
        val channelClean = CanonicalKeyHelper.toCleanName(channel.name)
        val channelIdSlug = if (channel.id.isNotBlank()) CanonicalKeyHelper.toCanonicalSlug(channel.id) else ""
        val channelIdClean = if (channel.id.isNotBlank()) CanonicalKeyHelper.toCleanName(channel.id) else ""

        for ((storedKey, storedCat) in overrides) {
            if (storedCat.isBlank()) continue
            if (storedKey.equals(channel.name, ignoreCase = true) ||
                storedKey.equals(channel.id, ignoreCase = true)
            ) {
                return storedCat.trim()
            }
            val storedSlug = CanonicalKeyHelper.toCanonicalSlug(storedKey)
            if (storedSlug.isNotBlank() && (storedSlug == channelSlug || (channelIdSlug.isNotBlank() && storedSlug == channelIdSlug))) {
                return storedCat.trim()
            }
            val storedClean = CanonicalKeyHelper.toCleanName(storedKey)
            if (storedClean.isNotBlank() && (storedClean == channelClean || (channelIdClean.isNotBlank() && storedClean == channelIdClean))) {
                return storedCat.trim()
            }
        }

        return null
    }

    private fun getOverridesMap(context: Context): MutableMap<String, String> {
        cachedOverrides?.let { return it }
        synchronized(this) {
            cachedOverrides?.let { return it }
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_OVERRIDES, null)
            val map = mutableMapOf<String, String>()
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val jsonObject = JSONObject(jsonStr)
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = jsonObject.getString(key)
                    }
                } catch (_: Exception) {}
            }
            cachedOverrides = map
            return map
        }
    }

    fun getProviderName(channel: Channel): String {
        val stream = channel.streamUrl
        val customSource = channel.headers?.get("source_name") ?: channel.headers?.get("source")
        if (!customSource.isNullOrBlank()) return customSource

        return when {
            stream.startsWith("damitv://") || stream.contains("damitv") || stream.contains("ondemand.st") -> "DAMITV Global Live TV"
            stream.startsWith("roarzone://") -> "RoarZone Plugin"
            stream.contains("redforce") -> "Redforce Plugin"
            stream.contains("splex") -> "Splex Plugin"
            stream.contains("103.145") || stream.contains("local") -> "Local ISP Plugin"
            else -> "M3U Playlist Provider"
        }
    }

    fun applyOverrides(context: Context, channels: List<Channel>): List<Channel> {
        return channels.map { channel ->
            val customCategory = getOverrideCategory(context, channel)
            if (!customCategory.isNullOrBlank()) {
                channel.copy(group = customCategory)
            } else {
                channel
            }
        }
    }

    private fun getProvidersMap(context: Context): MutableMap<String, String> {
        cachedProviders?.let { return it }
        synchronized(this) {
            cachedProviders?.let { return it }
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_PROVIDERS, null)
            val map = mutableMapOf<String, String>()
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val jsonObject = JSONObject(jsonStr)
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = jsonObject.getString(key)
                    }
                } catch (_: Exception) {}
            }
            cachedProviders = map
            return map
        }
    }

    fun saveOverride(context: Context, channel: Channel, newCategory: String, providerName: String = getProviderName(channel)) {
        val overrides = getOverridesMap(context)
        val providers = getProvidersMap(context)
        val provider = providerName.ifBlank { "M3U Playlist Provider" }
        val cat = newCategory.trim()

        val allKeys = CanonicalKeyHelper.getLookupKeys(channel)
        for (k in allKeys) {
            overrides[k] = cat
            providers[k] = provider
        }

        saveMaps(context, overrides, providers)
    }

    fun removeOverride(context: Context, channel: Channel) {
        val overrides = getOverridesMap(context)
        val providers = getProvidersMap(context)

        val allKeys = CanonicalKeyHelper.getLookupKeys(channel).toMutableSet()
        val channelSlug = CanonicalKeyHelper.toCanonicalSlug(channel.name)
        val channelClean = CanonicalKeyHelper.toCleanName(channel.name)
        val channelIdClean = if (channel.id.isNotBlank()) CanonicalKeyHelper.toCleanName(channel.id) else ""

        overrides.keys.toList().forEach { k ->
            if (allKeys.contains(k) ||
                CanonicalKeyHelper.toCanonicalSlug(k) == channelSlug ||
                CanonicalKeyHelper.toCleanName(k) == channelClean ||
                (channelIdClean.isNotBlank() && CanonicalKeyHelper.toCleanName(k) == channelIdClean) ||
                k.equals(channel.name, ignoreCase = true) ||
                (channel.id.isNotBlank() && k.equals(channel.id, ignoreCase = true))
            ) {
                overrides.remove(k)
                providers.remove(k)
            }
        }

        saveMaps(context, overrides, providers)
    }

    private fun saveMaps(context: Context, overrides: Map<String, String>, providers: Map<String, String>) {
        cachedOverrides = overrides.toMutableMap()
        cachedProviders = providers.toMutableMap()

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonOverrides = JSONObject()
        overrides.forEach { (k, v) -> jsonOverrides.put(k, v) }

        val jsonProviders = JSONObject()
        providers.forEach { (k, v) -> jsonProviders.put(k, v) }

        prefs.edit()
            .putString(KEY_OVERRIDES, jsonOverrides.toString())
            .putString(KEY_PROVIDERS, jsonProviders.toString())
            .apply()
    }

    fun getOverrideCountPerProvider(context: Context): Map<String, Int> {
        val providers = getProvidersMap(context)
        val counts = mutableMapOf<String, Int>()
        providers.values.forEach { provider ->
            counts[provider] = (counts[provider] ?: 0) + 1
        }
        return counts
    }

    fun resetOverridesForProvider(context: Context, providerName: String) {
        val overrides = getOverridesMap(context)
        val providers = getProvidersMap(context)

        val keysToRemove = providers.filter { it.value == providerName }.keys
        keysToRemove.forEach { key ->
            overrides.remove(key)
            providers.remove(key)
        }

        saveMaps(context, overrides, providers)
    }

    fun resetAllOverrides(context: Context) {
        cachedOverrides = mutableMapOf()
        cachedProviders = mutableMapOf()
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_OVERRIDES).remove(KEY_PROVIDERS).apply()
    }

    fun getAllOverrideCategories(context: Context): Set<String> {
        val overrides = getOverridesMap(context)
        return overrides.values.filter { it.isNotBlank() }.toSet()
    }

    fun isChannelOverridden(context: Context, channel: Channel): Boolean {
        return getOverrideCategory(context, channel) != null
    }
}
