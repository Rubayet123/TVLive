package io.github.rubayet123.tvlive.scraper

import android.util.Base64
import io.github.rubayet123.tvlive.data.network.NetworkClient
import io.github.rubayet123.tvlive.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PlayZCategory(
    val name: String,
    val logo: String = "",
    val type: String = "custom",
    val api: String = "",
    val visible: Boolean = true
)

data class PlayZLiveEventData(
    val id: Int,
    val title: String,
    val image: String?,
    val slug: String,
    val cat: String?,
    val eventInfo: PlayZLiveEventInfo?,
    val publish: Int,
    val formats: List<PlayZLiveEventFormat>?
)

data class PlayZLiveEventInfo(
    val teamA: String?,
    val teamB: String?,
    val teamAFlag: String?,
    val teamBFlag: String?,
    val eventCat: String?,
    val eventName: String?,
    val eventLogo: String?,
    val isHot: String?,
    val eventType: String?,
    val startTime: String?,
    val endTime: String?
)

data class PlayZLiveEventFormat(
    val title: String?,
    val webLink: String?
)

data class PlayZStreamUrl(
    val name: String?,
    val link: String?,
    val scheme: Int?,
    val api: String?,
    val tokenApi: String?
)

object PlayZTVProviderManager {

    private const val TAG = "PlayZProviderManager"

    private val DEFAULT_BASE_URLS = listOf(
        "https://tourniquest.site",
        "https://adsflw.xyz",
        "https://playztv2828.store"
    )

    private var cachedBaseUrl: String? = null

    private val client: OkHttpClient by lazy {
        NetworkClient.client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private fun parseDateTime(date: String?, time: String?): String? {
        if (date.isNullOrBlank() || time.isNullOrBlank()) return null
        return try {
            val cleanDate = date.trim()
            val cleanTime = time.trim()
            val sep = if (cleanDate.contains("/")) "/" else if (cleanDate.contains("-")) "-" else ""
            if (sep.isNotEmpty()) {
                val parts = cleanDate.split(sep)
                if (parts.size == 3) {
                    val (year, month, day) = if (parts[0].length == 4) {
                        Triple(parts[0], parts[1], parts[2])
                    } else {
                        Triple(parts[2], parts[1], parts[0])
                    }
                    "$year/$month/$day $cleanTime +0000"
                } else {
                    "$cleanDate $cleanTime"
                }
            } else {
                "$cleanDate $cleanTime"
            }
        } catch (_: Exception) { null }
    }

    suspend fun getBaseUrl(): String {
        cachedBaseUrl?.let { return it }

        val firebaseUrl = PlayZTVFirebaseFetcher.getBaseApiUrl()
        if (!firebaseUrl.isNullOrBlank()) {
            cachedBaseUrl = firebaseUrl
            return firebaseUrl
        }

        for (url in DEFAULT_BASE_URLS) {
            try {
                val req = Request.Builder()
                    .url("$url/categories.txt")
                    .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 10; SM-A505F)")
                    .head()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.code < 500) {
                    cachedBaseUrl = url
                    return url
                }
            } catch (_: Exception) { }
        }

        cachedBaseUrl = DEFAULT_BASE_URLS.first()
        return cachedBaseUrl!!
    }

    private suspend fun fetchDecrypted(path: String): String? = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val url = "$baseUrl/$path"
        try {
            android.util.Log.d(TAG, "[TRACE] Fetching encrypted path: $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 10; SM-A505F)")
                .build()
            client.newCall(request).execute().use { response ->
                android.util.Log.d(TAG, "[TRACE] HTTP ${response.code} from $url (content-length: ${response.body?.contentLength()})")
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    android.util.Log.d(TAG, "[TRACE] Raw body length for $path: ${body.length}, preview: ${body.take(60)}")
                    if (body.isNotBlank()) {
                        val decrypted = PlayZTVCryptoUtils.decryptPlayZTV(body.trim())
                        if (!decrypted.isNullOrBlank()) {
                            android.util.Log.i(TAG, "[TRACE] Decryption SUCCEEDED for $path. Length: ${decrypted.length}, preview: ${decrypted.take(120)}")
                            decrypted
                        } else {
                            android.util.Log.e(TAG, "[TRACE] Decryption FAILED (returned null/blank) for $path")
                            null
                        }
                    } else {
                        android.util.Log.w(TAG, "[TRACE] Received empty body for $path")
                        null
                    }
                } else {
                    android.util.Log.w(TAG, "[TRACE] Request unsuccessful: HTTP ${response.code} for $url")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "[TRACE] Network/Decryption exception for $url: ${e.message}", e)
            null
        }
    }

    suspend fun fetchLiveEvents(context: android.content.Context? = null): List<PlayZLiveEventData> = withContext(Dispatchers.IO) {
        android.util.Log.d(TAG, "[TRACE] fetchLiveEvents() initiated")
        try {
            val decrypted = fetchDecrypted("events.txt")
            if (!decrypted.isNullOrBlank()) {
                val jsonArray = JSONArray(decrypted)
                val events = mutableListOf<PlayZLiveEventData>()
                android.util.Log.d(TAG, "[TRACE] Parsing ${jsonArray.length()} raw event items from decrypted JSON")

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val ev = item.optJSONObject("event") ?: try {
                        val innerStr = item.optString("event")
                        if (innerStr.isNotBlank() && innerStr.startsWith("{")) JSONObject(innerStr) else item
                    } catch (_: Exception) { item }

                    try {
                        val category = ev.optString("category", null)
                        val eventName = ev.optString("eventName", null)
                        val eventLogo = ev.optString("eventLogo", null)
                        val teamAName = ev.optString("teamAName", null)
                        val teamBName = ev.optString("teamBName", null)
                        val teamAFlag = ev.optString("teamAFlag", null)
                        val teamBFlag = ev.optString("teamBFlag", null)
                        val date = ev.optString("date", null)
                        val time = ev.optString("time", null)
                        val endDate = ev.optString("end_date", null)
                        val endTime = ev.optString("end_time", null)
                        val links = ev.optString("links", ev.optString("link", null))
                        val visible = ev.optBoolean("visible", true)

                        val linkNamesArray = ev.optJSONArray("link_names")
                        val formats = mutableListOf<PlayZLiveEventFormat>()
                        if (linkNamesArray != null && linkNamesArray.length() > 0) {
                            for (j in 0 until linkNamesArray.length()) {
                                formats.add(PlayZLiveEventFormat(title = linkNamesArray.getString(j), webLink = links))
                            }
                        } else if (!links.isNullOrBlank()) {
                            formats.add(PlayZLiveEventFormat(title = "Main Stream", webLink = links))
                        }

                        val slug = (links?.substringBeforeLast(".") ?: "event_$i").trim()

                        events.add(
                            PlayZLiveEventData(
                                id = i + 1,
                                title = eventName ?: "${teamAName ?: "Team A"} vs ${teamBName ?: "Team B"}",
                                image = eventLogo,
                                slug = slug,
                                cat = category,
                                eventInfo = PlayZLiveEventInfo(
                                    teamA = teamAName,
                                    teamB = teamBName,
                                    teamAFlag = teamAFlag,
                                    teamBFlag = teamBFlag,
                                    eventCat = category,
                                    eventName = eventName,
                                    eventLogo = eventLogo,
                                    isHot = null,
                                    eventType = category,
                                    startTime = parseDateTime(date, time),
                                    endTime = parseDateTime(endDate, endTime)
                                ),
                                publish = if (visible) 1 else 0,
                                formats = formats
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "[TRACE] Error parsing event at $i: ${e.message}")
                    }
                }

                val publishedEvents = events.filter { it.publish == 1 }
                android.util.Log.i(TAG, "[TRACE] Successfully parsed ${publishedEvents.size} visible events (out of ${events.size} total)")

                return@withContext publishedEvents
            } else {
                android.util.Log.w(TAG, "[TRACE] Decrypted events.txt was empty or null")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "[TRACE] fetchLiveEvents exception: ${e.message}", e)
        }
        emptyList()
    }

    suspend fun fetchCategories(): List<PlayZCategory> = withContext(Dispatchers.IO) {
        try {
            val decrypted = fetchDecrypted("categories.txt")
            if (!decrypted.isNullOrBlank()) {
                val cats = mutableListOf<PlayZCategory>()
                val array = JSONArray(decrypted)
                for (i in 0 until array.length()) {
                    val w = array.getJSONObject(i)
                    val inner = w.optJSONObject("cat") ?: try {
                        val s = w.optString("cat")
                        if (s.isNotBlank() && s.startsWith("{")) JSONObject(s) else w
                    } catch (_: Exception) { w }

                    if (inner.optBoolean("visible", true)) {
                        cats.add(
                            PlayZCategory(
                                name = inner.optString("name", "Category"),
                                logo = inner.optString("logo", ""),
                                type = inner.optString("type", "custom"),
                                api = inner.optString("api", ""),
                                visible = true
                            )
                        )
                    }
                }
                android.util.Log.i(TAG, "Successfully parsed ${cats.size} categories from categories.txt")
                return@withContext cats
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "fetchCategories failed: ${e.message}")
        }
        emptyList()
    }

    private fun showDebugToast(context: android.content.Context, message: String) {
        // Debug toast disabled
    }

    suspend fun fetchChannelStreams(slug: String): List<PlayZStreamUrl>? = withContext(Dispatchers.IO) {
        val cleanSlug = slug.removePrefix("event_").trim()
        try {
            val decrypted = fetchDecrypted("$cleanSlug.txt")
            if (!decrypted.isNullOrBlank()) {
                val jsonArray = JSONArray(decrypted)
                val streams = mutableListOf<PlayZStreamUrl>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val rawLink = obj.optString("link", "")
                    val rawName = obj.optString("name", "").trim()
                    val channelName = if (rawName.isNotBlank() && !rawName.startsWith("Server", ignoreCase = true)) {
                        rawName
                    } else {
                        inferChannelNameFromUrl(rawLink, i + 1)
                    }
                    streams.add(
                        PlayZStreamUrl(
                            name = channelName,
                            link = rawLink.ifBlank { null },
                            scheme = obj.optInt("scheme", 0),
                            api = obj.optString("api", null),
                            tokenApi = obj.optString("tokenApi", null)
                        )
                    )
                }
                return@withContext streams
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "fetchChannelStreams failed for $cleanSlug: ${e.message}")
        }
        null
    }

    fun inferChannelNameFromUrl(url: String, index: Int): String {
        val lower = url.lowercase()
        return when {
            lower.contains("tsports") || lower.contains("t_sports") -> "T Sports HD"
            lower.contains("gtv") -> "GTV HD"
            lower.contains("starsports1") || lower.contains("star_sports_1") || lower.contains("ss1") -> "Star Sports 1 HD"
            lower.contains("ss_select1") || lower.contains("star_select1") -> "Star Sports Select 1 HD"
            lower.contains("sony_ten1") || lower.contains("sonyten1") || lower.contains("sony1") -> "Sony Sports Ten 1 HD"
            lower.contains("sony_ten2") || lower.contains("sonyten2") || lower.contains("sony2") -> "Sony Sports Ten 2 HD"
            lower.contains("sony_ten3") || lower.contains("sonyten3") || lower.contains("sony3") -> "Sony Sports Ten 3 HD"
            lower.contains("willow") -> "Willow Cricket HD"
            lower.contains("sports18") -> "Sports18 1 HD"
            lower.contains("astro") -> "Astro Cricket HD"
            lower.contains("bein") -> "beIN Sports HD"
            else -> "Channel $index"
        }
    }

    fun parseStreamSources(streams: List<PlayZStreamUrl>): List<StreamSource> {
        val sources = mutableListOf<StreamSource>()
        for (stream in streams) {
            val link = stream.link ?: continue
            val parts = link.split("|")
            var rawUrl = parts.firstOrNull()?.trim() ?: ""
            rawUrl = rawUrl.replace("%2F", "/").replace("%2f", "/")
            if (rawUrl.isBlank()) continue

            val headers = mutableMapOf<String, String>()
            for (part in parts.drop(1)) {
                val eq = part.indexOf('=')
                if (eq > 0) {
                    val k = part.substring(0, eq).trim()
                    val v = part.substring(eq + 1).trim()
                    headers[k] = v
                }
            }
            if (!headers.containsKey("User-Agent")) {
                headers["User-Agent"] = "Mozilla/5.0 (Linux; Android 10; Mobile) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            var licenseType: String? = null
            var licenseKey: String? = null

            val apiStr = stream.api?.trim()
            if (!apiStr.isNullOrBlank()) {
                if (apiStr.startsWith("http://") || apiStr.startsWith("https://")) {
                    licenseType = "clearkey"
                    licenseKey = apiStr
                } else {
                    val drmInfo = apiStr.split(":")
                    if (drmInfo.size == 2) {
                        val kid = if (drmInfo[0].length == 32) hexToBase64(drmInfo[0]) else drmInfo[0]
                        val key = if (drmInfo[1].length == 32) hexToBase64(drmInfo[1]) else drmInfo[1]
                        if (kid.isNotEmpty() && key.isNotEmpty()) {
                            licenseType = "clearkey"
                            licenseKey = "$kid:$key"
                        }
                    }
                }
            }

            val channelName = if (!stream.name.isNullOrBlank() && !stream.name.startsWith("Server", ignoreCase = true)) {
                stream.name
            } else {
                inferChannelNameFromUrl(rawUrl, sources.size + 1)
            }

            sources.add(
                StreamSource(
                    providerName = channelName,
                    streamUrl = rawUrl,
                    headers = if (headers.isNotEmpty()) headers else null,
                    licenseType = licenseType,
                    licenseKey = licenseKey,
                    priority = sources.size
                )
            )
        }
        return sources
    }

    private fun hexToBase64(hex: String): String {
        return try {
            val bytes = hex.replace("-", "").chunked(2)
                .map { it.toInt(16).toByte() }.toByteArray()
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).trim()
        } catch (_: Exception) { "" }
    }
}
