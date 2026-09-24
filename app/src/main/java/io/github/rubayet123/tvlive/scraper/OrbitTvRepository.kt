package io.github.rubayet123.tvlive.scraper

import android.util.Log
import io.github.rubayet123.tvlive.data.network.NetworkClient
import io.github.rubayet123.tvlive.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

class OrbitTvRepository(private val client: OkHttpClient = NetworkClient.client) {

    private val BASE_URL = "http://172.19.17.3:8090"
    private val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

    companion object {
        private const val TAG = "OrbitTvRepo"

        const val EMBEDDED_SOURCE_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>Live TV Player</title>
</head>
<body>
  <script>
    const channels = {
      SPORTS: [
        {name: "T SPORTS", url: 'http://172.19.17.3:8090/hls/tsportshd.m3u8', logo: "images/TSports.jpg"},
        {name: "PTV SPORTS", url: 'http://172.19.17.3:8090/hls/ptvsportshd.m3u8', logo: "images/ptvsports.png"},
        {name: "A SPORTS", url: 'http://172.19.17.3:8090/hls/ASportsHD.m3u8', logo: "images/asportse.png"},
        {name: "STAR SPORTS SELECT", url: 'http://172.19.17.3:8090/hls/StarSportsSelect1HD.m3u8', logo: "images/starsportsselect1.png"},
        {name: "STAR SPORTS SELECT 2", url: 'http://172.19.17.3:8090/hls/StarSportsSelect2HD.m3u8', logo: "images/starsportsselect2hd.png"},        
        {name: "STAR SPORTS 2", url: 'http://172.19.17.3:8090/hls/StarSports2HD.m3u8', logo: "images/starsports2.png"},
        {name: "STAR SPORTS 1", url: 'http://172.19.17.3:8090/hls/SonyTen5HD.m3u8', logo: "images/starsports1.png"},
        {name: "SONY SPORTS 3", url: 'http://172.19.17.3:8090/hls/SonyTen2hd.m3u8', logo: "images/sonysports3.png"},
        {name: "SONY SPORTS 5", url: 'http://172.19.17.3:8090/hls/SonyTen3hd.m3u8', logo: "images/sonysports5.png"},  
        {name: "BEIN SPORTS1ENG", url: 'http://172.19.17.3:8090/hls/BeinSports1ENG.m3u8', logo: "images/bein1.png"},
      ],
      BANGLA: [
        {name: "SOMOY TV", url: 'http://172.19.17.3:8090/hls/somoytv.m3u8', logo: "images/somoytv.jpg"},
        {name: "BTV", url: 'http://172.19.17.3:8090/hls/BTVNationalHD.m3u8', logo: "images/BTV.png"},
        {name: "JALSHA MOVIES", url: 'http://172.19.17.3:8090/hls/JalshaMoviesHD.m3u8', logo: "images/jalshamovies.png"},
        {name: "SONY AATH", url: 'http://172.19.17.3:8090/hls/SonyAtth.m3u8', logo: "images/sonyaath.png"},
        {name: "STAR JALSHA", url: 'http://172.19.17.3:8090/hls/StarJalshaHD.m3u8', logo: "images/starjalsha.png"}
      ],
      HINDI: [
        {name: "SONY HD", url: 'http://172.19.17.3:8090/hls/SonyHD.m3u8', logo: "images/sony.png"},
        {name: "COLORS HD", url: 'http://172.19.17.3:8090/hls/ColorsHD.m3u8', logo: "images/colors.png"},
        {name: "SONY MAX", url: 'http://172.19.17.3:8090/hls/sonymaxhd.m3u8', logo: "images/sonymax.png"},
        {name: "STAR PLUS", url: 'http://172.19.17.3:8090/hls/StarPlusHD.m3u8', logo: "images/starplus.png"}
      ],
      NEWS: [
        {name: "ALJAZEERA NEWS", url: 'http://172.19.17.3:8090/hls/ALjazeeraNews.m3u8', logo: "images/aljazeranews.png"},
        {name: "BBC WORLD", url: 'http://172.19.17.3:8090/hls/bbc_world.m3u8', logo: "images/bbcworldnews.png"},
      ],
      MUSIC: [
        {name: "SANGEET BANGLA", url:'http://172.19.17.3:8090/hls/SangeetBangla.m3u8', logo: "images/sangeetbangla.png"},
        {name: "9XM", url:'http://172.19.17.3:8090/hls/9xm.m3u8', logo: "images/9xm.png"}
      ],
      KIDS: [
        {name: "DISCOVERY KIDS", url: 'http://172.19.17.3:8090/hls/DiscoveryKids.m3u8', logo: "images/discoveryhd.png"},
        {name: "CARTOON NETWORK", url: 'http://172.19.17.3:8090/hls/CartoonNetworkHD.m3u8', logo: "images/cnhd.png"}
      ],
      ENGLISH: [
        {name: "AND PICTURE", url: 'http://172.19.17.3:8090/hls/AndPictureHD.m3u8', logo: "images/andpic.png"},
        {name: "STAR MOVIES HD", url: 'http://172.19.17.3:8090/hls/StarMoviesHD.m3u8', logo: "images/star-movies-hd.jpg"},            
        {name: "DISCOVERY", url: 'http://172.19.17.3:8090/hls/DiscoveryHD.m3u8', logo: "images/discoveryhd.png"}, 
        {name: " NAT GEO", url: 'http://172.19.17.3:8090/hls/NatGeoWild.m3u8', logo: "images/natgeo.png"},    
      ]
    };
  </script>
</body>
</html>"""
    }

    suspend fun fetchChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$BASE_URL/")
            .build()

        var html: String? = null
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    html = response.body?.string()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct network fetch failed from $BASE_URL, using fallback catalog: ${e.message}")
        }

        val channels = parseChannels(html ?: EMBEDDED_SOURCE_HTML)
        if (channels.isEmpty() && html != null) {
            return@withContext parseChannels(EMBEDDED_SOURCE_HTML)
        }
        channels
    }

    private fun parseChannels(html: String): List<Channel> {
        val channels = mutableListOf<Channel>()

        // Find the channels object block
        val channelsBlockPattern = Pattern.compile("const\\s+channels\\s*=\\s*\\{([\\s\\S]*?)\\};")
        val blockMatcher = channelsBlockPattern.matcher(html)
        val jsContent = if (blockMatcher.find()) blockMatcher.group(1) ?: html else html

        // Match each category section e.g. SPORTS: [ ... ]
        val categoryPattern = Pattern.compile("([A-Za-z0-9_]+)\\s*:\\s*\\[([\\s\\S]*?)\\]")
        val catMatcher = categoryPattern.matcher(jsContent)

        val channelItemPattern = Pattern.compile("name\\s*:\\s*[\"'](.*?)[\"'].*?url\\s*:\\s*[\"'](.*?)[\"'].*?logo\\s*:\\s*[\"'](.*?)[\"']")

        while (catMatcher.find()) {
            val rawCategory = catMatcher.group(1)?.trim() ?: "General"
            val categoryListContent = catMatcher.group(2) ?: ""
            val categoryName = formatCategoryName(rawCategory)

            // Split line by line to ignore commented lines
            val lines = categoryListContent.split("\n")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("/*")) continue

                val itemMatcher = channelItemPattern.matcher(trimmed)
                if (itemMatcher.find()) {
                    val rawName = itemMatcher.group(1)?.trim() ?: ""
                    val rawUrl = itemMatcher.group(2)?.trim() ?: ""
                    val rawLogo = itemMatcher.group(3)?.trim() ?: ""

                    if (rawName.isNotEmpty() && rawUrl.isNotEmpty()) {
                        val formattedName = formatChannelName(rawName)
                        var logoUrl = rawLogo
                        if (logoUrl.isNotEmpty() && !logoUrl.startsWith("http")) {
                            logoUrl = if (logoUrl.startsWith("/")) "$BASE_URL$logoUrl" else "$BASE_URL/$logoUrl"
                        }

                        var streamUrl = rawUrl
                        if (streamUrl.isNotEmpty() && !streamUrl.startsWith("http")) {
                            streamUrl = if (streamUrl.startsWith("/")) "$BASE_URL$streamUrl" else "$BASE_URL/$streamUrl"
                        }

                        val idKey = formattedName.lowercase().replace(Regex("[^a-z0-9]"), "")

                        channels.add(
                            Channel(
                                id = "orbittv_$idKey",
                                name = formattedName,
                                logoUrl = logoUrl,
                                group = categoryName,
                                streamUrl = streamUrl,
                                headers = mapOf(
                                    "Referer" to "$BASE_URL/",
                                    "Origin" to BASE_URL,
                                    "User-Agent" to USER_AGENT,
                                    "Accept" to "*/*"
                                )
                            )
                        )
                    }
                }
            }
        }

        // If block regex didn't find any, fallback to line-by-line parsing
        if (channels.isEmpty()) {
            val lines = html.split("\n")
            var currentCategory = "Orbit TV"
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("/*")) continue

                for (cat in listOf("SPORTS", "BANGLA", "HINDI", "NEWS", "MUSIC", "KIDS", "ENGLISH")) {
                    if (trimmed.contains("$cat:")) {
                        currentCategory = formatCategoryName(cat)
                    }
                }

                val itemMatcher = channelItemPattern.matcher(trimmed)
                if (itemMatcher.find()) {
                    val rawName = itemMatcher.group(1)?.trim() ?: ""
                    val rawUrl = itemMatcher.group(2)?.trim() ?: ""
                    val rawLogo = itemMatcher.group(3)?.trim() ?: ""

                    if (rawName.isNotEmpty() && rawUrl.isNotEmpty()) {
                        val formattedName = formatChannelName(rawName)
                        var logoUrl = rawLogo
                        if (logoUrl.isNotEmpty() && !logoUrl.startsWith("http")) {
                            logoUrl = if (logoUrl.startsWith("/")) "$BASE_URL$logoUrl" else "$BASE_URL/$logoUrl"
                        }

                        var streamUrl = rawUrl
                        if (streamUrl.isNotEmpty() && !streamUrl.startsWith("http")) {
                            streamUrl = if (streamUrl.startsWith("/")) "$BASE_URL$streamUrl" else "$BASE_URL/$streamUrl"
                        }

                        val idKey = formattedName.lowercase().replace(Regex("[^a-z0-9]"), "")

                        channels.add(
                            Channel(
                                id = "orbittv_$idKey",
                                name = formattedName,
                                logoUrl = logoUrl,
                                group = currentCategory,
                                streamUrl = streamUrl,
                                headers = mapOf(
                                    "Referer" to "$BASE_URL/",
                                    "Origin" to BASE_URL,
                                    "User-Agent" to USER_AGENT,
                                    "Accept" to "*/*"
                                )
                            )
                        )
                    }
                }
            }
        }

        return channels
    }

    private fun formatCategoryName(category: String): String {
        return when (category.uppercase().trim()) {
            "SPORTS" -> "Sports"
            "BANGLA" -> "Bangla"
            "HINDI" -> "Hindi"
            "NEWS" -> "News"
            "MUSIC" -> "Music"
            "KIDS" -> "Kids"
            "ENGLISH" -> "English"
            else -> category.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun formatChannelName(rawName: String): String {
        val trimmed = rawName.trim()
        return when (trimmed.uppercase()) {
            "T SPORTS" -> "T Sports"
            "PTV SPORTS" -> "PTV Sports"
            "A SPORTS" -> "A Sports"
            "STAR SPORTS SELECT" -> "Star Sports Select 1"
            "STAR SPORTS SELECT 2" -> "Star Sports Select 2"
            "STAR SPORTS 2" -> "Star Sports 2"
            "STAR SPORTS 1" -> "Star Sports 1"
            "SONY SPORTS 3" -> "Sony Sports 3"
            "SONY SPORTS 5" -> "Sony Sports 5"
            "BEIN SPORTS1ENG" -> "beIN Sports 1 ENG"
            "SOMOY TV" -> "Somoy TV"
            "BTV" -> "BTV National"
            "JALSHA MOVIES" -> "Jalsha Movies"
            "SONY AATH" -> "Sony Aath"
            "STAR JALSHA" -> "Star Jalsha"
            "SONY HD" -> "Sony HD"
            "COLORS HD" -> "Colors HD"
            "SONY MAX" -> "Sony Max"
            "STAR PLUS" -> "Star Plus"
            "ALJAZEERA NEWS" -> "Al Jazeera News"
            "BBC WORLD" -> "BBC World News"
            "SANGEET BANGLA" -> "Sangeet Bangla"
            "9XM" -> "9XM"
            "DISCOVERY KIDS" -> "Discovery Kids"
            "CARTOON NETWORK" -> "Cartoon Network"
            "AND PICTURE" -> "&Pictures"
            "STAR MOVIES HD" -> "Star Movies HD"
            "DISCOVERY" -> "Discovery HD"
            "NAT GEO" -> "Nat Geo Wild"
            else -> {
                trimmed.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }
    }
}
