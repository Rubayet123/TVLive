package io.github.rubayet123.tvlive.scraper

import io.github.rubayet123.tvlive.data.network.NetworkClient
import io.github.rubayet123.tvlive.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.regex.Pattern

class RedforceRepository(private val client: OkHttpClient = NetworkClient.client) {

    private val BASE_URL = "http://redforce.live"
    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"

    private val streamPatterns = listOf(
        Pattern.compile("primarySource\\s*=\\s*['\"](.*?)['\"]"),
        Pattern.compile("iframe src=\"(.*?)\""),
        Pattern.compile("source src=\"(.*?)\""),
        Pattern.compile("src: or src=\"(.*?)\""),
        Pattern.compile("['\"](http.*?m3u8.*?)['\"]"),
        Pattern.compile("file: \"(.*?m3u8.*?)\"")
    )

    suspend fun fetchChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BASE_URL)
            .header("User-Agent", USER_AGENT)
            .header("Referer", BASE_URL)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Failed to fetch Redforce: ${response.code}")
                val html = response.body?.string() ?: throw IOException("Empty body from Redforce")
                parseChannels(html)
            }
        } catch (e: Exception) {
            android.util.Log.e("RedforceRepo", "Error fetching channels: ${e.message}")
            emptyList()
        }
    }

    private fun parseChannels(html: String): List<Channel> {
        val document = Jsoup.parse(html)
        val channelAnchors = document.select("a.channel[onclick*='stream=']")
        val channels = mutableListOf<Channel>()

        for (anchor in channelAnchors) {
            val onclickAttr = anchor.attr("onclick")
            val streamId = onclickAttr.substringAfter("stream=").substringBefore("'")

            val img = anchor.selectFirst("img")
            val name = img?.attr("alt") ?: img?.attr("title") ?: ""
            var logoUrl = img?.attr("src") ?: ""

            if (logoUrl.isNotEmpty() && !logoUrl.startsWith("http")) {
                logoUrl = if (logoUrl.startsWith("/")) "$BASE_URL$logoUrl" else "$BASE_URL/$logoUrl"
            }

            val parentLi = anchor.parent()
            val category = parentLi?.attr("class")?.split(" ")?.firstOrNull { it != "All" && it != "channel" } ?: "Uncategorized"

            if (streamId.isNotEmpty() && name.isNotEmpty()) {
                channels.add(
                    Channel(
                        id = streamId,
                        name = name,
                        logoUrl = logoUrl,
                        group = category,
                        streamUrl = "redforce://$streamId",
                        headers = mapOf(
                            "Referer" to "$BASE_URL/player.php?stream=$streamId",
                            "Origin" to BASE_URL,
                            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36",
                            "X-Requested-With" to "com.android.chrome",
                            "Accept" to "*/*",
                            "Accept-Language" to "en-US,en;q=0.9"
                        )
                    )
                )
            }
        }
        return channels
    }

    suspend fun resolveStream(streamId: String): String? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/player.php?stream=$streamId"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", BASE_URL)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: return@withContext null
                parseStreamUrl(html)
            }
        } catch (e: Exception) {
            android.util.Log.e("RedforceRepo", "Error resolving stream $streamId: ${e.message}")
            null
        }
    }

    private fun parseStreamUrl(html: String): String? {
        for (pattern in streamPatterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val url = matcher.group(1)
                if (url != null && url.contains(".m3u8")) {
                    return url.trim()
                }
            }
        }
        return null
    }
}
