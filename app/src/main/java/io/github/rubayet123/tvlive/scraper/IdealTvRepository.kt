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

class IdealTvRepository(private val client: OkHttpClient = NetworkClient.client) {

    private val BASE_URL = "http://172.16.60.2"
    private val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

    private val streamPatterns = listOf(
        Pattern.compile("primarySource\\s*=\\s*['\"](.*?)['\"]"),
        Pattern.compile("iframe\\s+.*?src=[\"'](.*?)[\"']"),
        Pattern.compile("source\\s+.*?src=[\"'](.*?)[\"']"),
        Pattern.compile("src\\s*:\\s*[\"'](.*?)[\"']"),
        Pattern.compile("['\"](http.*?m3u8.*?)['\"]"),
        Pattern.compile("file\\s*:\\s*[\"'](.*?m3u8.*?)[\"']"),
        Pattern.compile("url\\s*:\\s*[\"'](.*?m3u8.*?)[\"']")
    )

    companion object {
        const val EMBEDDED_SOURCE_HTML = """<!DOCTYPE html>
<html>
<meta http-equiv="content-type" content="text/html;charset=UTF-8">
<head>
    <meta charset="utf-8">
    <title> Live TV | JUST ENJOY !</title>
</head>
<body id="body">
<div class="container-fluid p-3">
    <div class="row justify-content-center">
        <div class="col-12 col-lg-4 mt-4" >
<div class="channel-list mx-auto">
                <ul id="vidlink" class="thumbnail-slider flex-container">
                    <div class="row">
		 <li class="Sports"><a id="myLink" onclick="view.location.href='img/play.php?stream=101'" class='channel flex-item'><img src="assets/images/live.png" alt="Live"></a></li>
		 <li class="Sports"><a id="myLink" onclick="view.location.href='img/play.php?stream=102'" class='channel flex-item'><img src="assets/images/Tsports.png" alt="Live"></a></li>
		 <li class="Sports"><a id="myLink" onclick="view.location.href='img/play.php?stream=106'" class='channel flex-item'><img src="assets/images/GTV.png" alt="Live"></a></li>
		 <li class="Sports"><a id="myLink" onclick="view.location.href='img/play.php?stream=110'" class='channel flex-item'><img src="assets/images/Star-sports1.png" alt="Live"></a></li>
		 <li class="Sports"><a id="myLink" onclick="view.location.href='img/play.php?stream=115'" class='channel flex-item'><img src="assets/images/Star-Sports2.png" alt="Live"></a></li>
		 <li class="Sports"><a id="myLink" onclick="view.location.href='img/play.php?stream=119'" class='channel flex-item'><img src="assets/images/Sony-Ten1.png" alt="Live"></a></li>
		 <li class="Sports"><a id="myLink" onclick="view.location.href='img/play.php?stream=120'" class='channel flex-item'><img src="assets/images/Sony-Ten2.png" alt="Live"></a></li>
		 <li class="Sports"><a id="myLink" onclick="view.location.href='img/play.php?stream=121'" class='channel flex-item'><img src="assets/images/Sony-Ten3.png" alt="Live"></a></li>
		 <li class="Sports"><a id="myLink" onclick="view.location.href='img/play.php?stream=123'" class='channel flex-item'><img src="assets/images/Star-Sports-Select1.png" alt="Live"></a></li>
		 <li class="Sports"><a id="myLink" onclick="view.location.href='img/play.php?stream=124'" class='channel flex-item'><img src="assets/images/Star-Sports-Select2.png" alt="Live"></a></li>
		 <li class="Sports"><a id="myLink" onclick="view.location.href='img/play.php?stream=127'" class='channel flex-item'><img src="assets/images/Asports.png" alt="Live"></a></li>
		 <li class="Sports"><a id="myLink" onclick="view.location.href='img/play.php?stream=128'" class='channel flex-item'><img src="assets/images/PTV.JPG" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=301'" class='channel flex-item'><img src="assets/images/Zee-Bangla.png" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=302'" class='channel flex-item'><img src="assets/images/Star-Jalsha.png" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=303'" class='channel flex-item'><img src="assets/images/Jalsha-Movies.png" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=304'" class='channel flex-item'><img src="assets/images/Colors-Bangla.png" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=306'" class='channel flex-item'><img src="assets/images/Sony-Aath.png" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=201'" class='channel flex-item'><img src="assets/images/independenttv.png" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=202'" class='channel flex-item'><img src="assets/images/atnbangla.png" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=203'" class='channel flex-item'><img src="assets/images/atnnews.png" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=204'" class='channel flex-item'><img src="assets/images/Dbc.png" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=206'" class='channel flex-item'><img src="assets/images/Masranga.png" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=207'" class='channel flex-item'><img src="assets/images/ChannelI.png" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=209'" class='channel flex-item'><img src="assets/images/Jamunatv.png" alt="Live"></a></li>
		 <li class="Bangla"><a id="myLink" onclick="view.location.href='img/play.php?stream=205'" class='channel flex-item'><img src="assets/images/SomoyTV1689411430.png" alt="Live"></a></li>
		 <li class="Hindi"><a id="myLink" onclick="view.location.href='img/play.php?stream=401'" class='channel flex-item'><img src="assets/images/Star-plus.png" alt="Live"></a></li>
		 <li class="Hindi"><a id="myLink" onclick="view.location.href='img/play.php?stream=402'" class='channel flex-item'><img src="assets/images/Star-Gold.png" alt="Live"></a></li>
		 <li class="Hindi"><a id="myLink" onclick="view.location.href='img/play.php?stream=403'" class='channel flex-item'><img src="assets/images/Sony Entertainment.png" alt="Live"></a></li>
		 <li class="Hindi"><a id="myLink" onclick="view.location.href='img/play.php?stream=405'" class='channel flex-item'><img src="assets/images/Colors-Hindi.png" alt="Live"></a></li>
		 <li class="Hindi"><a id="myLink" onclick="view.location.href='img/play.php?stream=406'" class='channel flex-item'><img src="assets/images/Zee-Cinema.png" alt="Live"></a></li>
		 <li class="Hindi"><a id="myLink" onclick="view.location.href='img/play.php?stream=407'" class='channel flex-item'><img src="assets/images/Sony Max HD1667728133.png" alt="Live"></a></li>
		 <li class="English"><a id="myLink" onclick="view.location.href='img/play.php?stream=501'" class='channel flex-item'><img src="assets/images/Star-Movie.png" alt="Live"></a></li>
		 <li class="English"><a id="myLink" onclick="view.location.href='img/play.php?stream=503'" class='channel flex-item'><img src="assets/images/sony-Pix.png" alt="Live"></a></li>
		 <li class="English"><a id="myLink" onclick="view.location.href='img/play.php?stream=801'" class='channel flex-item'><img src="assets/images/Discovery.png" alt="Live"></a></li>
		 <li class="English"><a id="myLink" onclick="view.location.href='img/play.php?stream=802'" class='channel flex-item'><img src="assets/images/National Geography.png" alt="Live"></a></li>
		 <li class="others"><a id="myLink" onclick="view.location.href='img/play.php?stream=601'" class='channel flex-item'><img src="assets/images/NICK.JPG" alt="Live"></a></li>
		 <li class="others"><a id="myLink" onclick="view.location.href='img/play.php?stream=602'" class='channel flex-item'><img src="assets/images/disneyJR.png" alt="Live"></a></li>
		 <li class="others"><a id="myLink" onclick="view.location.href='img/play.php?stream=703'" class='channel flex-item'><img src="assets/images/9X_Jalwa.png" alt="Live"></a></li>
		 <li class="others"><a id="myLink" onclick="view.location.href='img/play.php?stream=702'" class='channel flex-item'><img src="assets/images/sangeetbangla.png" alt="Live"></a></li>
                    </div>
                </ul>
            </div>
        </div>
    </div>
</div>
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
            android.util.Log.w("IdealTvRepo", "Direct network fetch failed, using fallback catalog: ${e.message}")
        }

        val channels = parseChannels(html ?: EMBEDDED_SOURCE_HTML)
        if (channels.isEmpty() && html != null) {
            return@withContext parseChannels(EMBEDDED_SOURCE_HTML)
        }
        channels
    }

    private fun parseChannels(html: String): List<Channel> {
        val document = Jsoup.parse(html)
        val channelAnchors = document.select("a[onclick*='stream=']")
        val channels = mutableListOf<Channel>()

        for (anchor in channelAnchors) {
            val onclickAttr = anchor.attr("onclick")
            val streamId = onclickAttr.substringAfter("stream=").substringBefore("'").substringBefore("\"").trim()
            if (streamId.isEmpty()) continue

            val img = anchor.selectFirst("img")
            val rawImgSrc = img?.attr("src") ?: ""
            val rawAlt = img?.attr("alt") ?: ""

            val channelName = formatChannelName(rawImgSrc, rawAlt, streamId)
            var logoUrl = rawImgSrc
            if (logoUrl.isNotEmpty() && !logoUrl.startsWith("http")) {
                logoUrl = if (logoUrl.startsWith("/")) "$BASE_URL$logoUrl" else "$BASE_URL/$logoUrl"
            }

            val parentLi = anchor.parents().firstOrNull { it.tagName().equals("li", ignoreCase = true) }
            val rawClass = parentLi?.attr("class") ?: ""
            val category = formatCategory(rawClass, streamId)

            channels.add(
                Channel(
                    id = "idealtv_$streamId",
                    name = channelName,
                    logoUrl = logoUrl,
                    group = category,
                    streamUrl = "http://172.16.60.2:8088/$streamId/index.m3u8",
                    headers = mapOf(
                        "Referer" to "$BASE_URL/img/play.php?stream=$streamId",
                        "Origin" to BASE_URL,
                        "User-Agent" to USER_AGENT,
                        "Accept" to "*/*",
                        "Accept-Language" to "en-US,en;q=0.9,bn-BD;q=0.8,bn;q=0.7"
                    )
                )
            )
        }
        return channels
    }

    private fun formatCategory(rawClass: String, streamId: String): String {
        val cleaned = rawClass.replace("\"", "").trim()
        val primaryClass = cleaned.split(" ").firstOrNull { it.isNotBlank() && it != "channel" } ?: ""
        return when (primaryClass.lowercase()) {
            "sports" -> "Sports"
            "bangla" -> "Bangla"
            "hindi" -> "Hindi"
            "english" -> "English"
            "others" -> "Others"
            else -> {
                when {
                    streamId.startsWith("1") -> "Sports"
                    streamId.startsWith("2") || streamId.startsWith("3") -> "Bangla"
                    streamId.startsWith("4") -> "Hindi"
                    streamId.startsWith("5") || streamId.startsWith("8") -> "English"
                    streamId.startsWith("6") || streamId.startsWith("7") -> "Others"
                    else -> "Ideal TV"
                }
            }
        }
    }

    private fun formatChannelName(imgSrc: String, alt: String, streamId: String): String {
        val fileName = imgSrc.substringAfterLast("/").substringBeforeLast(".")
            .replace(Regex("\\d{10,}$"), "") // Remove timestamp suffixes
            .replace("-", " ")
            .replace("_", " ")
            .trim()

        val normalized = when (fileName.lowercase().replace(" ", "")) {
            "live" -> if (streamId == "101") "Sports Live" else "Live TV"
            "tsports" -> "T Sports"
            "gtv" -> "GTV"
            "starsports1" -> "Star Sports 1"
            "starsports2" -> "Star Sports 2"
            "sonyten1" -> "Sony Ten 1"
            "sonyten2" -> "Sony Ten 2"
            "sonyten3" -> "Sony Ten 3"
            "starsportsselect1" -> "Star Sports Select 1"
            "starsportsselect2" -> "Star Sports Select 2"
            "asports" -> "A Sports"
            "ptv" -> "PTV Sports"
            "zeebangla" -> "Zee Bangla"
            "starjalsha" -> "Star Jalsha"
            "jalshamovies" -> "Jalsha Movies"
            "colorsbangla" -> "Colors Bangla"
            "sonyaath" -> "Sony Aath"
            "enterr10bangla" -> "Enterr10 Bangla"
            "independenttv" -> "Independent TV"
            "atnbangla" -> "ATN Bangla"
            "atnnews" -> "ATN News"
            "dbc" -> "DBC News"
            "masranga" -> "Maasranga TV"
            "channeli" -> "Channel i"
            "jamunatv" -> "Jamuna TV"
            "somoytv" -> "Somoy TV"
            "starplus" -> "Star Plus"
            "stargold" -> "Star Gold"
            "sonyentertainment" -> "Sony Entertainment TV"
            "colorshindi" -> "Colors Hindi"
            "zeecinema" -> "Zee Cinema"
            "sonymaxhd" -> "Sony Max HD"
            "starmovie" -> "Star Movies"
            "sonypix" -> "Sony Pix"
            "discovery" -> "Discovery"
            "nationalgeography" -> "National Geographic"
            "nick" -> "Nickelodeon"
            "disneyjr" -> "Disney Junior"
            "9xjalwa" -> "9X Jalwa"
            "sangeetbangla" -> "Sangeet Bangla"
            else -> {
                if (alt.isNotBlank() && alt != "Live") {
                    alt.replace("-", " ").replace("_", " ").trim()
                } else if (fileName.isNotBlank()) {
                    fileName.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                } else {
                    "Channel $streamId"
                }
            }
        }

        return normalized
    }

    suspend fun resolveStream(streamId: String): String? = withContext(Dispatchers.IO) {
        val cleanStreamId = streamId.filter { it.isDigit() }
        val playerUrls = listOf(
            "$BASE_URL/img/play.php?stream=$cleanStreamId",
            "$BASE_URL/play.php?stream=$cleanStreamId"
        )

        for (playerUrl in playerUrls) {
            val request = Request.Builder()
                .url(playerUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        val resolved = parseStreamUrl(html)
                        if (resolved != null) {
                            return@withContext resolved
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("IdealTvRepo", "Error resolving $playerUrl: ${e.message}")
            }
        }

        // Fallback standard direct HLS stream URL on port 8088
        "http://172.16.60.2:8088/$cleanStreamId/index.m3u8"
    }

    private fun parseStreamUrl(html: String): String? {
        for (pattern in streamPatterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                var url = matcher.group(1)?.replace("\\/", "/")?.trim()
                if (url != null) {
                    if (url.startsWith("//")) {
                        url = "http:$url"
                    } else if (url.startsWith("/") && !url.startsWith("http")) {
                        url = "http://172.16.60.2:8088$url"
                    }
                    if (url.contains(".m3u8") || url.contains(".mpd") || url.contains("http")) {
                        return url
                    }
                }
            }
        }
        return null
    }
}
