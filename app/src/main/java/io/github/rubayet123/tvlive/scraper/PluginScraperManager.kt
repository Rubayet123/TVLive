package io.github.rubayet123.tvlive.scraper

import android.content.Context
import android.util.Log
import io.github.rubayet123.tvlive.data.LiveTvManager
import io.github.rubayet123.tvlive.data.M3uParser
import io.github.rubayet123.tvlive.data.SourceRepository
import io.github.rubayet123.tvlive.data.network.NetworkClient
import io.github.rubayet123.tvlive.model.Channel
import io.github.rubayet123.tvlive.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PluginScraperManager {

    private const val TAG = "PluginScraperManager"
    private const val PREFS_NAME = "plugin_prefs"

    data class PluginInfo(
        val key: String,
        val displayName: String,
        val fileName: String,
        val autoScrapePrefKey: String,
        val lastScrapePrefKey: String
    )

    val PLUGINS = listOf(
        PluginInfo("redforce", "Redforce ISP TV", "redforce.m3u", "auto_scrape_redforce", "last_scrape_redforce"),
        PluginInfo("splex", "Splex ISP TV", "splex.m3u", "auto_scrape_splex", "last_scrape_splex"),
        PluginInfo("roarzone", "Roarzone ISP TV", "roarzone.m3u", "auto_scrape_roarzone", "last_scrape_roarzone"),
        PluginInfo("idealtv", "Ideal TV (172.16.60.2)", "idealtv.m3u", "auto_scrape_idealtv", "last_scrape_idealtv"),
        PluginInfo("orbittv", "Orbit TV (172.19.17.3)", "orbittv.m3u", "auto_scrape_orbittv", "last_scrape_orbittv"),
        PluginInfo("local_isp", "BAS TV (10.99.99.99)", "local_isp.m3u", "auto_scrape_local_isp", "last_scrape_local_isp"),
        PluginInfo("damitv", "DAMITV Global Live TV", "damitv.m3u", "auto_scrape_damitv", "last_scrape_damitv"),
        PluginInfo("playztv", "PlayZ TV (BDIX)", "playztv.m3u", "auto_scrape_playztv", "last_scrape_playztv")
    )

    fun getPluginInfo(key: String): PluginInfo? {
        return PLUGINS.find { it.key == key || it.key == key.lowercase() }
    }

    fun isAutoScrapeEnabled(context: Context, key: String): Boolean {
        val info = getPluginInfo(key) ?: return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultValue = (key.lowercase() == "playztv")
        return prefs.getBoolean(info.autoScrapePrefKey, defaultValue)
    }

    fun ensureDefaultPluginsInitialized(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val sourceRepo = SourceRepository(context)
            val sources = sourceRepo.getSources()

            // Ensure damitv is disabled by default
            val damitvInfo = getPluginInfo("damitv")
            if (damitvInfo != null && !prefs.contains(damitvInfo.autoScrapePrefKey)) {
                prefs.edit().putBoolean(damitvInfo.autoScrapePrefKey, false).apply()
                val existing = sources.find { it.name == damitvInfo.displayName || it.url.contains("damitv.m3u") }
                if (existing != null && existing.isActive) {
                    sourceRepo.updateSource(existing.copy(isActive = false))
                }
            }

            // Default plugin list to ensure enabled
            val defaultPluginKeys = listOf("playztv")

            for (key in defaultPluginKeys) {
                val info = getPluginInfo(key) ?: continue
                val file = File(context.filesDir, info.fileName)

                // If auto_scrape pref was never set, default to true
                if (!prefs.contains(info.autoScrapePrefKey)) {
                    prefs.edit().putBoolean(info.autoScrapePrefKey, true).apply()
                }

                // If file is missing, empty, or contains dead URLs, populate with seed channels
                val needsSeedUpdate = !file.exists() || file.length() < 100 || (key == "playztv" && try {
                    val text = file.readText()
                    text.contains("playztv.com/live/hls")
                } catch (_: Exception) { true })
                if (needsSeedUpdate) {
                    val seedChannels = when (key) {
                        "playztv" -> PlayztvRepository(NetworkClient.client).loadSeedChannels()
                        else -> emptyList()
                    }

                    if (seedChannels.isNotEmpty()) {
                        val m3uContent = M3uBuilder.build(seedChannels)
                        FileOutputStream(file).use {
                            it.write(m3uContent.toByteArray(Charsets.UTF_8))
                        }
                        val existing = sources.find { it.name == info.displayName || it.url == file.absolutePath }
                        if (existing == null) {
                            sourceRepo.addSource(Source(info.displayName, file.absolutePath, true, "M3U"))
                        } else if (!existing.isActive) {
                            sourceRepo.updateSource(existing.copy(isActive = true, url = file.absolutePath))
                        }
                        updateLastScrapeTime(context, key)
                        Log.i(TAG, "Pre-seeded ${seedChannels.size} $key channels instantly from plugin repository")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure default plugins initialization", e)
        }
    }

    fun getLastScrapeTime(context: Context, key: String): Long {
        val info = getPluginInfo(key) ?: return 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(info.lastScrapePrefKey, 0L)
    }

    fun updateLastScrapeTime(context: Context, key: String, time: Long = System.currentTimeMillis()) {
        val info = getPluginInfo(key) ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(info.lastScrapePrefKey, time).apply()
    }

    suspend fun scrapeAndSavePlugin(context: Context, key: String): Boolean = withContext(Dispatchers.IO) {
        val info = getPluginInfo(key) ?: return@withContext false
        val client = NetworkClient.client
        Log.i(TAG, "Starting scrape for plugin: ${info.displayName}")

        try {
            val channels: List<Channel> = when (info.key) {
                "redforce" -> {
                    val repo = RedforceRepository(client)
                    repo.fetchChannels()
                }
                "splex" -> {
                    val repo = ScrapeRepository(ScrapeService(client))
                    repo.scrapeChannels("https://splex.live") { _, _ -> }
                }
                "roarzone" -> {
                    val repo = RoarzoneRepository(client)
                    repo.fetchChannels()
                }
                "idealtv", "ideal_tv" -> {
                    val repo = IdealTvRepository(client)
                    repo.fetchChannels()
                }
                "orbittv", "orbit_tv" -> {
                    val repo = OrbitTvRepository(client)
                    repo.fetchChannels()
                }
                "local_isp" -> {
                    val repo = LocalIspRepository(client)
                    repo.fetchChannels()
                }
                "damitv" -> {
                    val repo = DamitvRepository(client, context)
                    repo.fetchChannels(context)
                }
                "playztv" -> {
                    val repo = PlayztvRepository(client, context)
                    repo.fetchChannels(context)
                }
                else -> emptyList()
            }

            if (channels.isEmpty()) {
                Log.w(TAG, "No channels fetched for ${info.displayName}")
                return@withContext false
            }

            val m3uContent = M3uBuilder.build(channels)
            val file = File(context.filesDir, info.fileName)
            FileOutputStream(file).use {
                it.write(m3uContent.toByteArray(Charsets.UTF_8))
            }

            // Register/update source
            val sourceRepo = SourceRepository(context)
            val sources = sourceRepo.getSources()
            val existing = sources.find { it.name == info.displayName }
            if (existing == null) {
                sourceRepo.addSource(Source(info.displayName, file.absolutePath, true, "M3U"))
            } else {
                sourceRepo.updateSource(Source(info.displayName, file.absolutePath, true, "M3U"))
            }

            updateLastScrapeTime(context, info.key)
            Log.i(TAG, "Successfully scraped ${channels.size} channels for ${info.displayName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scrape ${info.displayName}", e)
            false
        }
    }

    suspend fun autoScrapeActivePluginsIfNeeded(context: Context, maxAgeMs: Long = 1 * 60 * 60 * 1000L): Boolean = withContext(Dispatchers.IO) {
        var anyScraped = false
        val now = System.currentTimeMillis()

        for (plugin in PLUGINS) {
            if (isAutoScrapeEnabled(context, plugin.key)) {
                val last = getLastScrapeTime(context, plugin.key)
                val file = File(context.filesDir, plugin.fileName)
                val effectiveMaxAge = if (plugin.key == "playztv") 0L else maxAgeMs
                val isExpired = (now - last) >= effectiveMaxAge
                val fileMissing = !file.exists() || file.length() < 10

                if (isExpired || fileMissing) {
                    Log.i(TAG, "Plugin ${plugin.displayName} needs refresh (isExpired=$isExpired, fileMissing=$fileMissing)")
                    val success = scrapeAndSavePlugin(context, plugin.key)
                    if (success) {
                        anyScraped = true
                    }
                }
            }
        }
        anyScraped
    }

    suspend fun forceScrapeForChannel(context: Context, channel: Channel): Boolean = withContext(Dispatchers.IO) {
        val key = detectPluginKey(channel)
        if (key != null) {
            Log.i(TAG, "Detected channel plugin key: $key for ${channel.name}")
            return@withContext scrapeAndSavePlugin(context, key)
        }

        // If unknown, re-scrape all enabled plugins or all plugins
        Log.i(TAG, "Unknown plugin key for channel ${channel.name}, attempting full refresh of active scrapers")
        var anyScraped = false
        for (plugin in PLUGINS) {
            if (isAutoScrapeEnabled(context, plugin.key)) {
                if (scrapeAndSavePlugin(context, plugin.key)) {
                    anyScraped = true
                }
            }
        }
        anyScraped
    }

    fun detectPluginKey(channel: Channel): String? {
        val url = channel.streamUrl.lowercase()
        val group = channel.group?.lowercase() ?: ""
        val name = channel.name.lowercase()

        return when {
            url.startsWith("redforce://") || group.contains("redforce") || name.contains("redforce") -> "redforce"
            url.startsWith("splex://") || group.contains("splex") || name.contains("splex") -> "splex"
            url.startsWith("roarzone://") || group.contains("roarzone") || name.contains("roarzone") -> "roarzone"
            url.startsWith("idealtv://") || url.contains("172.16.60.2") || group.contains("ideal tv") || name.contains("ideal tv") -> "idealtv"
            url.startsWith("orbittv://") || url.contains("172.19.17.3") || group.contains("orbit tv") || name.contains("orbit tv") -> "orbittv"
            url.startsWith("damitv://") || url.contains("damitv") || url.contains("ondemand.st") || group.contains("damitv") || name.contains("damitv") -> "damitv"
            url.startsWith("playztv://") || url.contains("playztv") || url.contains("playz.tv") || group.contains("playz") || name.contains("playz") -> "playztv"
            url.contains("10.99.99.") || url.contains("10.200.") || url.contains("172.16.") || group.contains("bas tv") || name.contains("bas tv") || group.contains("local isp") || name.contains("local isp") || group.contains("isp tv") -> "local_isp"
            else -> null
        }
    }

    suspend fun reloadMasterPlaylist(context: Context): List<Channel> = withContext(Dispatchers.IO) {
        val repository = SourceRepository(context)
        val sources = repository.getSources()
        val parser = M3uParser(NetworkClient.client)

        val allChannels = mutableListOf<Channel>()
        sources.filter { it.isActive }.forEachIndexed { index, source ->
            try {
                val categories = parser.parse(source.url, defaultProviderName = source.name, priority = index)
                categories.forEach { category ->
                    allChannels.addAll(category.channels)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading source: ${source.name}", e)
            }
        }

        val deduped = io.github.rubayet123.tvlive.util.ChannelDeduplicator.deduplicateChannels(context, allChannels)
        LiveTvManager.setMasterPlaylist(deduped)
        deduped
    }
}
