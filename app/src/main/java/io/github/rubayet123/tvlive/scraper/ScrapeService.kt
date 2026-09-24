package io.github.rubayet123.tvlive.scraper

import io.github.rubayet123.tvlive.data.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class ScrapeService(private val client: OkHttpClient = NetworkClient.client) {

    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"

    @Throws(IOException::class)
    suspend fun fetchMainPage(baseUrl: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", baseUrl)
            .build()

        try {
            val response: Response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string() ?: throw IOException("Empty response body from main page.")
            } else {
                throw IOException("Failed to fetch main page. HTTP status: ${response.code}")
            }
        } catch (e: Exception) {
            throw IOException("Network error fetching main page: ${e.message}", e)
        }
    }

    @Throws(IOException::class)
    suspend fun fetchPlayerPage(baseUrl: String, streamId: String): String = withContext(Dispatchers.IO) {
        val url = if (baseUrl.endsWith("/")) "${baseUrl}play.php?stream=$streamId" else "$baseUrl/play.php?stream=$streamId"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", baseUrl)
            .build()

        try {
            val response: Response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string() ?: throw IOException("Empty response body from player page.")
            } else {
                throw IOException("Failed to fetch player page. HTTP status: ${response.code}")
            }
        } catch (e: Exception) {
            throw IOException("Network error fetching player page: ${e.message}", e)
        }
    }
}
