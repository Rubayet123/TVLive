package io.github.rubayet123.tvlive.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.rubayet123.tvlive.model.Source
import androidx.work.*
import io.github.rubayet123.tvlive.data.network.M3uRefreshWorker
import io.github.rubayet123.tvlive.data.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class SourceRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tv_live_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val SOURCES_KEY = "saved_sources"
    private val GLOBAL_SYNC_INTERVAL_KEY = "global_sync_interval_hours"

    fun getSources(): List<Source> {
        val defaultBdix = Source(
            name = "BDIX TV",
            url = "https://raw.githubusercontent.com/Rubayet123/BDLive/refs/heads/main/bdix.m3u",
            isActive = true,
            type = "M3U",
            refreshIntervalHours = 0,
            isUserAdded = false
        )
        val defaultWeb = Source(
            name = "Web TV",
            url = "https://raw.githubusercontent.com/Rubayet123/BDLive/refs/heads/main/web.m3u",
            isActive = true,
            type = "M3U",
            refreshIntervalHours = 0,
            isUserAdded = false
        )

        val json = prefs.getString(SOURCES_KEY, null)
        val youtubeUrlToRemove = "https://raw.githubusercontent.com/Rubayet123/BDLive/refs/heads/main/youtube/ytlive.txt"

        if (json == null) {
            val defaults = listOf(defaultBdix, defaultWeb)
            saveSources(defaults)
            return defaults
        }
        val type = object : TypeToken<List<Source>>() {}.type
        val saved: MutableList<Source> = gson.fromJson(json, type) ?: mutableListOf()
        
        val removed = saved.removeAll { it.url == youtubeUrlToRemove }
        
        var modified = false
        if (saved.none { it.url == defaultBdix.url }) {
            saved.add(0, defaultBdix)
            modified = true
        }
        if (saved.none { it.url == defaultWeb.url }) {
            saved.add(defaultWeb)
            modified = true
        }

        // Migrate previously defaulted 24h intervals to Manual (0) if user hasn't explicitly customized
        val isMigratedToManual = prefs.getBoolean("manual_sync_default_migrated", false)
        if (!isMigratedToManual) {
            saved.forEachIndexed { i, src ->
                if (!src.isUserAdded && src.refreshIntervalHours == 24) {
                    saved[i] = src.copy(refreshIntervalHours = 0)
                    modified = true
                }
            }
            prefs.edit().putBoolean("manual_sync_default_migrated", true).apply()
        }
        
        if (removed || modified) {
            saveSources(saved)
        }
        return saved
    }

    fun addSource(source: Source) {
        val current = getSources().toMutableList()
        current.add(source)
        saveSources(current)
        scheduleAllRefreshes()
    }
    
    fun removeSource(source: Source) {
        val current = getSources().toMutableList()
        current.removeAll { it.url == source.url }
        saveSources(current)
        deleteCacheForSource(source.url)
        scheduleAllRefreshes()
    }
    
    fun updateSource(source: Source) {
        updateSource(source.url, source)
    }

    fun updateSource(oldUrl: String, updatedSource: Source) {
        val current = getSources().toMutableList()
        val index = current.indexOfFirst { it.url == oldUrl }
        if (index != -1) {
            current[index] = updatedSource
            saveSources(current)
            scheduleAllRefreshes()
        }
    }

    fun getCacheFileForUrl(url: String): File {
        val hash = url.hashCode().toString().replace("-", "n")
        return File(context.filesDir, "cache_source_$hash.m3u")
    }

    private fun deleteCacheForSource(url: String) {
        try {
            val file = getCacheFileForUrl(url)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncSource(source: Source): Boolean = withContext(Dispatchers.IO) {
        if (!source.url.startsWith("http")) return@withContext false
        try {
            val request = Request.Builder()
                .url(source.url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            
            val response = NetworkClient.client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val file = getCacheFileForUrl(source.url)
                    FileOutputStream(file).use { out ->
                        out.write(body.toByteArray())
                    }
                    val updated = source.copy(lastRefreshedAt = System.currentTimeMillis())
                    updateSource(updated)
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }

    suspend fun syncAllSources(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val httpSources = getSources().filter { it.isActive && it.url.startsWith("http") }
        if (httpSources.isEmpty()) return@withContext Pair(0, 0)
        
        var successCount = 0
        var failCount = 0

        coroutineScope {
            val tasks = httpSources.map { src ->
                async {
                    val success = syncSource(src)
                    if (success) 1 else 0
                }
            }
            val results = tasks.awaitAll()
            successCount = results.sum()
            failCount = httpSources.size - successCount
        }

        Pair(successCount, failCount)
    }

    fun setAllSourcesInterval(intervalHours: Int) {
        val current = getSources().toMutableList()
        val updated = current.map { src ->
            if (src.url.startsWith("http")) src.copy(refreshIntervalHours = intervalHours) else src
        }
        saveSources(updated)
        prefs.edit().putInt(GLOBAL_SYNC_INTERVAL_KEY, intervalHours).apply()
        scheduleAllRefreshes()
    }

    fun getGlobalSyncInterval(): Int {
        return prefs.getInt(GLOBAL_SYNC_INTERVAL_KEY, 0)
    }

    fun scheduleAllRefreshes() {
        val workManager = WorkManager.getInstance(context)
        
        // 1. Handle Periodic Refreshes
        val sourcesWithInterval = getSources().filter { it.isActive && it.url.startsWith("http") && it.refreshIntervalHours > 0 }
        
        if (sourcesWithInterval.isEmpty()) {
            workManager.cancelUniqueWork("m3u_periodic_refresh")
        } else {
            val minInterval = sourcesWithInterval.minOf { it.refreshIntervalHours }.toLong().coerceAtLeast(1L)
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<M3uRefreshWorker>(minInterval, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "m3u_periodic_refresh",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    fun triggerAppStartRefreshes() {
        val appStartSources = getSources().filter { it.isActive && it.url.startsWith("http") && it.refreshIntervalHours == -1 }
        if (appStartSources.isNotEmpty()) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<M3uRefreshWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    fun moveSourceUp(source: Source) {
        val current = getSources().toMutableList()
        val index = current.indexOfFirst { it.url == source.url }
        if (index > 0) {
            val prev = current[index - 1]
            current[index - 1] = current[index]
            current[index] = prev
            saveSources(current)
        }
    }

    fun moveSourceDown(source: Source) {
        val current = getSources().toMutableList()
        val index = current.indexOfFirst { it.url == source.url }
        if (index != -1 && index < current.size - 1) {
            val next = current[index + 1]
            current[index + 1] = current[index]
            current[index] = next
            saveSources(current)
        }
    }

    private fun saveSources(sources: List<Source>) {
        val json = gson.toJson(sources)
        prefs.edit().putString(SOURCES_KEY, json).apply()
    }
}
