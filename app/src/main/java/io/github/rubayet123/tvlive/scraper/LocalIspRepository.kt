package io.github.rubayet123.tvlive.scraper

import io.github.rubayet123.tvlive.data.network.NetworkClient
import io.github.rubayet123.tvlive.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class LocalIspRepository(private val client: OkHttpClient = NetworkClient.client) {

    private val BASE_URL = "http://10.99.99.99"
    private val CHANNELS_API = "$BASE_URL/api/channels"
    private val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

    suspend fun fetchChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(CHANNELS_API)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$BASE_URL/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to fetch Local ISP channels (${response.code})")
            val body = response.body?.string() ?: "{}"
            val jsonArray = try {
                JSONArray(body)
            } catch (e: Exception) {
                val jsonObj = JSONObject(body)
                jsonObj.optJSONArray("channels") ?: JSONArray()
            }

            val channels = mutableListOf<Channel>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", obj.optString("stream_name", "ch_$i"))
                val name = obj.optString("name", obj.optString("title", "Channel $i"))
                val category = obj.optString("category", obj.optString("group", "Local ISP"))
                var logo = obj.optString("logo", obj.optString("poster", ""))
                if (logo.isNotEmpty() && !logo.startsWith("http")) {
                    logo = if (logo.startsWith("/")) "$BASE_URL$logo" else "$BASE_URL/$logo"
                }
                var streamUrl = obj.optString("url", obj.optString("stream_url", ""))
                if (streamUrl.isNotEmpty() && !streamUrl.startsWith("http")) {
                    streamUrl = if (streamUrl.startsWith("/")) "$BASE_URL$streamUrl" else "$BASE_URL/$streamUrl"
                }

                channels.add(
                    Channel(
                        id = id,
                        name = name,
                        logoUrl = logo,
                        streamUrl = streamUrl.ifEmpty { "$BASE_URL:8282/$id/index.m3u8" },
                        group = category,
                        headers = mapOf(
                            "Referer" to "$BASE_URL/",
                            "Origin" to BASE_URL,
                            "User-Agent" to USER_AGENT
                        )
                    )
                )
            }
            channels
        }
    }
}
