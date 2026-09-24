package io.github.rubayet123.tvlive.data

import io.github.rubayet123.tvlive.data.network.NetworkClient
import io.github.rubayet123.tvlive.model.Category
import io.github.rubayet123.tvlive.model.Channel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class M3uParser(
    private val client: OkHttpClient = NetworkClient.client,
    private val context: android.content.Context? = null
) {

    suspend fun parse(
        url: String,
        defaultProviderName: String? = null,
        priority: Int = 0,
        contextOverride: android.content.Context? = null
    ): List<Category> = withContext(Dispatchers.IO) {
        val effectiveCtx = contextOverride ?: context
        val content = if (url.startsWith("http")) {
            fetchUrlWithCacheFallback(url, effectiveCtx)
        } else {
            readFile(url)
        }
        parseM3uContent(content, defaultProviderName, priority)
    }

    private fun fetchUrlWithCacheFallback(url: String, ctx: android.content.Context?): String {
        val cacheFile = if (ctx != null) {
            val hash = url.hashCode().toString().replace("-", "n")
            File(ctx.filesDir, "cache_source_$hash.m3u")
        } else null

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        if (cacheFile != null) {
                            try {
                                cacheFile.writeText(body)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        return body
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If network request failed or returned empty, fallback to cached file if present
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile.readText()
        }

        throw Exception("Failed to load playlist from $url and no local cache available.")
    }

    private fun readFile(path: String): String {
        return File(path).readText()
    }

    private fun parseM3uContent(content: String, defaultProviderName: String? = null, priority: Int = 0): List<Category> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(StringReader(content))
        var line: String? = reader.readLine()
        
        var currentChannelName: String? = null
        var currentLogo: String? = null
        var currentGroup: String? = null
        var currentId: String? = null
        
        var currentLicenseType: String? = null
        var currentLicenseKey: String? = null
        val currentHeaders = mutableMapOf<String, String>()

        while (line != null) {
            line = line.trim()
            if (line.startsWith("#KODIPROP:")) {
                val prop = line.substringAfter("#KODIPROP:").trim()
                val key = prop.substringBefore("=").trim()
                val value = prop.substringAfter("=").trim()
                if (key == "inputstream.adaptive.license_type") currentLicenseType = value
                if (key == "inputstream.adaptive.license_key") currentLicenseKey = value
            } else if (line.startsWith("#EXTVLCOPT:")) {
                val opt = line.substringAfter("#EXTVLCOPT:").trim()
                if (opt.startsWith("http-")) {
                    val pair = opt.substringAfter("http-").trim()
                    val key = pair.substringBefore("=").trim()
                    val value = pair.substringAfter("=").trim()
                    
                    // Normalize standard headers
                    val normalizedKey = when(key.lowercase()) {
                        "user-agent" -> "User-Agent"
                        "referrer" -> "Referer"
                        "referer" -> "Referer"
                        "origin" -> "Origin"
                        "x-requested-with" -> "X-Requested-With"
                        "accept" -> "Accept"
                        "accept-language" -> "Accept-Language"
                        else -> key
                    }
                    currentHeaders[normalizedKey] = value
                }
            } else if (line.startsWith("#EXTINF:")) {
                // Parse attributes
                val info = line.substringAfter("#EXTINF:")
                val titleParams = info.substringBeforeLast(",")
                currentChannelName = info.substringAfterLast(",").trim()
                
                currentGroup = extractAttribute(titleParams, "group-title") ?: "Uncategorized"
                currentLogo = extractAttribute(titleParams, "tvg-logo")
                currentId = extractAttribute(titleParams, "tvg-id") ?: currentChannelName
                
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                // Stream URL
                if (currentChannelName != null) {
                    val headersMap = if (currentHeaders.isNotEmpty()) HashMap(currentHeaders) else null
                    val provider = Channel.cleanProviderName(defaultProviderName, line, currentChannelName, currentGroup)
                    val initialSource = io.github.rubayet123.tvlive.model.StreamSource(
                        providerName = provider,
                        streamUrl = line,
                        headers = headersMap,
                        licenseType = currentLicenseType,
                        licenseKey = currentLicenseKey,
                        priority = priority
                    )
                    channels.add(
                        Channel(
                            id = currentId ?: currentChannelName!!,
                            name = currentChannelName!!,
                            logoUrl = currentLogo,
                            streamUrl = line,
                            group = currentGroup,
                            licenseType = currentLicenseType,
                            licenseKey = currentLicenseKey,
                            headers = headersMap,
                            sources = listOf(initialSource)
                        )
                    )
                    // Reset for next channel
                    currentChannelName = null
                    currentLogo = null
                    currentGroup = null
                    currentId = null
                    currentLicenseType = null
                    currentLicenseKey = null
                    currentHeaders.clear()
                }
            }
            line = reader.readLine()
        }

        return channels.groupBy { it.group ?: "Uncategorized" }
            .map { Category(it.key, it.value) }
    }

    private fun extractAttribute(line: String, attribute: String): String? {
        val key = "$attribute=\""
        val start = line.indexOf(key)
        if (start == -1) return null
        val valueStart = start + key.length
        val end = line.indexOf('"', valueStart)
        if (end == -1) return null
        return line.substring(valueStart, end)
    }
}
