package io.github.rubayet123.tvlive.scraper

import io.github.rubayet123.tvlive.data.network.NetworkClient
import io.github.rubayet123.tvlive.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.regex.Pattern

class RoarzoneRepository(private val client: OkHttpClient = NetworkClient.client) {

    private val CHANNELS_API = "https://tv.roarzone.info/api/android/channels.php"
    private val STREAM_API = "https://tv.roarzone.info/api/android/stream.php?channel="
    private val PLAYER_URL = "https://tv.roarzone.info/player.php?stream="
    private val USER_AGENT = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"

    private val patterns = listOf(
        Pattern.compile("[\"'](http.*?m3u8.*?)[\"']"),
        Pattern.compile("file:\\s*[\"'](.*?m3u8.*?)[\"']"),
        Pattern.compile("source:\\s*[\"'](.*?m3u8.*?)[\"']"),
        Pattern.compile("url:\\s*[\"'](.*?m3u8.*?)[\"']")
    )

    suspend fun fetchChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(CHANNELS_API).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to fetch Roarzone channels")
            val json = JSONObject(response.body?.string() ?: "{}")
            if (!json.optBoolean("success", false)) return@withContext emptyList()

            val channelArray = json.getJSONArray("channels")
            val channels = mutableListOf<Channel>()
            for (i in 0 until channelArray.length()) {
                val obj = channelArray.getJSONObject(i)
                channels.add(
                    Channel(
                        id = obj.getString("id"),
                        name = obj.getString("title"),
                        logoUrl = obj.getString("logo"),
                        // Use a custom scheme to signal dynamic resolution in PlaybackActivity
                        streamUrl = "roarzone://${obj.getString("stream_name")}",
                        group = obj.optString("category", "Uncategorized"),
                        headers = mapOf(
                            "Referer" to "https://tv.roarzone.info/",
                            "Origin" to "https://tv.roarzone.info",
                            "User-Agent" to USER_AGENT,
                            "sec-ch-ua" to "\"Not:A-Brand\";v=\"99\", \"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\"",
                            "sec-ch-ua-mobile" to "?1",
                            "sec-ch-ua-platform" to "\"Android\"",
                            "sec-fetch-dest" to "empty",
                            "sec-fetch-mode" to "cors",
                            "sec-fetch-site" to "same-site",
                            "Accept-Language" to "en-US,en;q=0.9,bn-BD;q=0.8,bn;q=0.7"
                        )
                    )
                )
            }
            channels
        }
    }

    suspend fun resolveStream(streamName: String): String? = withContext(Dispatchers.IO) {
        // Prioritize: Fetch the player page and parse for stream URL (Browser-like behavior)
        android.util.Log.d("RoarzoneRepo", "Attempting Player Page resolution for: $streamName")
        val playerRequest = Request.Builder()
            .url(PLAYER_URL + streamName)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://tv.roarzone.info/")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .header("Accept-Language", "en-US,en;q=0.9,bn-BD;q=0.8,bn;q=0.7")
            .header("Upgrade-Insecure-Requests", "1")
            .header("sec-ch-ua", "\"Not:A-Brand\";v=\"99\", \"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\"")
            .header("sec-ch-ua-mobile", "?1")
            .header("sec-ch-ua-platform", "\"Android\"")
            .header("sec-fetch-dest", "iframe")
            .header("sec-fetch-mode", "navigate")
            .header("sec-fetch-site", "same-origin")
            .build()
            
        try {
            client.newCall(playerRequest).execute().use { response ->
                android.util.Log.d("RoarzoneRepo", "Player Page Response Code: ${response.code}")
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    android.util.Log.d("RoarzoneRepo", "Player Page Body Length: ${html.length}")
                    // Look for common m3u8 patterns in JS or HTML
                    for (pattern in patterns) {
                        val matcher = pattern.matcher(html)
                        if (matcher.find()) {
                            var match = matcher.group(1)?.replace("\\/", "/")
                            if (match != null && match.startsWith("//")) {
                                match = "https:$match"
                            }
                            android.util.Log.d("RoarzoneRepo", "Browser-like Match Found: $match")
                            return@withContext match
                        }
                    }
                    android.util.Log.e("RoarzoneRepo", "No m3u8 pattern found in player page")
                }
            }
        } catch (e: Exception) { 
            android.util.Log.e("RoarzoneRepo", "Player Page resolution error: ${e.message}")
            e.printStackTrace() 
        }

        null
    }
}
