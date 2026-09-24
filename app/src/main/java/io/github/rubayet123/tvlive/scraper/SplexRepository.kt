package io.github.rubayet123.tvlive.scraper

import io.github.rubayet123.tvlive.data.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.regex.Pattern

class SplexRepository(private val client: OkHttpClient = NetworkClient.client) {

    private val BASE_URL = "https://splex.live"
    private val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

    private val streamPatterns = listOf(
        Pattern.compile("iframe src=\"(.*?)\""),
        Pattern.compile("source src=\"(.*?)\""),
        Pattern.compile("src: or src=\"(.*?)\""),
        Pattern.compile("'s*(http.*?m3u8.*?)'s*"),
        Pattern.compile("file: \"(.*?m3u8.*?)\""),
        Pattern.compile("primarySource = '(.*?)'")
    )

    suspend fun resolveStream(streamId: String): String? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/play.php?stream=$streamId"
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
            e.printStackTrace()
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
