package io.github.rubayet123.tvlive.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.rubayet123.tvlive.model.Channel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object ChannelCacheManager {
    private const val TAG = "ChannelCacheManager"
    private const val CACHE_FILE_NAME = "channels_master_cache.gz"
    private const val TMP_FILE_NAME = "channels_master_cache.tmp"
    private val gson = Gson()
    private val channelListType = object : TypeToken<List<Channel>>() {}.type

    @Synchronized
    fun saveChannelsToDisk(context: Context, channels: List<Channel>) {
        if (channels.isEmpty()) return
        try {
            val cacheDir = context.filesDir
            val tmpFile = File(cacheDir, TMP_FILE_NAME)
            val destFile = File(cacheDir, CACHE_FILE_NAME)

            FileOutputStream(tmpFile).use { fos ->
                GZIPOutputStream(fos).use { gzos ->
                    OutputStreamWriter(gzos, StandardCharsets.UTF_8).use { writer ->
                        gson.toJson(channels, channelListType, writer)
                        writer.flush()
                    }
                }
            }

            if (tmpFile.exists()) {
                if (destFile.exists()) {
                    destFile.delete()
                }
                tmpFile.renameTo(destFile)
                Log.d(TAG, "Successfully cached ${channels.size} channels to disk (${destFile.length()} bytes)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save channels to disk cache", e)
        }
    }

    @Synchronized
    fun loadChannelsFromDisk(context: Context): List<Channel> {
        try {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                return emptyList()
            }

            FileInputStream(cacheFile).use { fis ->
                GZIPInputStream(fis).use { gzis ->
                    InputStreamReader(gzis, StandardCharsets.UTF_8).use { reader ->
                        val result: List<Channel>? = gson.fromJson(reader, channelListType)
                        if (result != null && result.isNotEmpty()) {
                            Log.d(TAG, "Loaded ${result.size} channels from persistent disk cache")
                            return result
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load channels from disk cache", e)
        }
        return emptyList()
    }

    fun hasValidCache(context: Context): Boolean {
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        return cacheFile.exists() && cacheFile.length() > 0L
    }
}
