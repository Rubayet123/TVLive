package io.github.rubayet123.tvlive.scraper

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.github.rubayet123.tvlive.data.network.NetworkClient
import io.github.rubayet123.tvlive.model.Channel
import io.github.rubayet123.tvlive.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class DamitvRepository(
    private val client: OkHttpClient = NetworkClient.client,
    private val context: Context? = null
) {

    companion object {
        private const val TAG = "DamitvRepository"
        const val CHANNELS_URL = "https://damitv.st/data/ts-channels.json"
        const val STREAM_BASE = "https://messi.damitv.st/papi/ts2/%s.m3u8"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        const val REFERER = "https://damitv.st/"
        const val ORIGIN = "https://damitv.st"
        const val CACHE_FILE_NAME = "damitv_cached_catalog.json"
        const val ASSET_FALLBACK_NAME = "damitv_channels_fallback.json"
    }

    private data class ChannelsResponse(
        @SerializedName("channels") val channels: List<ChannelItem>?
    )

    private data class ChannelItem(
        @SerializedName("daddyId") val daddyId: String?,
        @SerializedName("name") val name: String?,
        @SerializedName("image") val image: String?,
        @SerializedName("category") val category: String?
    )

    suspend fun fetchChannels(ctx: Context? = context): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(CHANNELS_URL)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .header("Origin", ORIGIN)
            .header("Accept", "*/*")
            .build()

        // 1. Attempt live network fetch
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val channels = parseChannelsJson(body)
                        if (channels.isNotEmpty()) {
                            // Save to local cache on success
                            saveToDiskCache(ctx, body)
                            Log.i(TAG, "Successfully fetched and cached ${channels.size} DAMITV channels from network")
                            return@withContext channels
                        }
                    }
                } else {
                    Log.w(TAG, "Network fetch returned HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network fetch failed: ${e.message}. Attempting fallback...")
        }

        // 2. Fallback to cached JSON file from previous successful sync
        val cachedJson = loadFromDiskCache(ctx)
        if (!cachedJson.isNullOrBlank()) {
            val channels = parseChannelsJson(cachedJson)
            if (channels.isNotEmpty()) {
                Log.i(TAG, "Loaded ${channels.size} DAMITV channels from local disk cache")
                return@withContext channels
            }
        }

        // 3. Fallback to pre-bundled asset JSON (for fresh installs / offline start)
        val assetJson = loadFromAssets(ctx)
        if (!assetJson.isNullOrBlank()) {
            val channels = parseChannelsJson(assetJson)
            if (channels.isNotEmpty()) {
                Log.i(TAG, "Loaded ${channels.size} DAMITV channels from bundled assets fallback")
                return@withContext channels
            }
        }

        Log.e(TAG, "All DAMITV fetch strategies exhausted.")
        emptyList()
    }

    fun loadSeedChannels(ctx: Context?): List<Channel> {
        val assetJson = loadFromAssets(ctx)
        if (!assetJson.isNullOrBlank()) {
            return parseChannelsJson(assetJson)
        }
        return emptyList()
    }

    private fun saveToDiskCache(ctx: Context?, json: String) {
        if (ctx == null) return
        try {
            val file = File(ctx.filesDir, CACHE_FILE_NAME)
            file.writeText(json, Charsets.UTF_8)
            Log.d(TAG, "Saved raw catalog to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save raw catalog cache: ${e.message}")
        }
    }

    private fun loadFromDiskCache(ctx: Context?): String? {
        if (ctx == null) return null
        return try {
            val file = File(ctx.filesDir, CACHE_FILE_NAME)
            if (file.exists() && file.length() > 0) {
                file.readText(Charsets.UTF_8)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading disk cache: ${e.message}")
            null
        }
    }

    private fun loadFromAssets(ctx: Context?): String? {
        if (ctx == null) return null
        return try {
            ctx.assets.open(ASSET_FALLBACK_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading assets fallback: ${e.message}")
            null
        }
    }

    fun parseChannelsJson(json: String): List<Channel> {
        val gson = Gson()
        val data = gson.fromJson(json, ChannelsResponse::class.java)
        val items = data.channels ?: return emptyList()

        val channels = mutableListOf<Channel>()
        val usedSlugs = mutableSetOf<String>()

        val defaultHeaders = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to REFERER,
            "Origin" to ORIGIN,
            "Accept" to "*/*"
        )

        for (item in items) {
            val daddyId = item.daddyId?.trim() ?: continue
            if (daddyId.isEmpty()) continue

            val slug = if (daddyId.startsWith("ts-", ignoreCase = true)) {
                daddyId.substring(3)
            } else {
                daddyId
            }.trim()

            if (slug.isEmpty() || !usedSlugs.add(slug)) continue

            val name = item.name?.trim()?.ifEmpty { slug } ?: slug
            val logo = item.image?.trim() ?: ""
            val category = item.category?.trim()?.ifEmpty { "Live TV" } ?: "Live TV"
            val streamUrl = "damitv://$slug"

            channels.add(
                Channel(
                    id = "damitv_$slug",
                    name = name,
                    logoUrl = logo,
                    group = category,
                    streamUrl = streamUrl,
                    headers = defaultHeaders,
                    sources = listOf(
                        StreamSource(
                            providerName = "DAMITV",
                            streamUrl = streamUrl,
                            headers = defaultHeaders
                        )
                    )
                )
            )
        }

        Log.i(TAG, "Parsed ${channels.size} unique channels from DAMITV catalog")
        return channels
    }

    fun resolveStream(slug: String): String {
        val cleanSlug = if (slug.startsWith("ts-", ignoreCase = true)) {
            slug.substring(3)
        } else {
            slug
        }.trim()
        return String.format(STREAM_BASE, cleanSlug)
    }
}
