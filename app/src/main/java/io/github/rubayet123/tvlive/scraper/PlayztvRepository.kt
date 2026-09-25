package io.github.rubayet123.tvlive.scraper

import io.github.rubayet123.tvlive.data.network.NetworkClient
import io.github.rubayet123.tvlive.model.Channel
import io.github.rubayet123.tvlive.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

class PlayztvRepository(
    private val client: OkHttpClient = NetworkClient.client,
    private val context: android.content.Context? = null
) {

    private val fastClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val BASE_URL = "https://playztv.com"
    private val API_ENDPOINTS = listOf(
        "https://fifabd.site/OPLLX7/LIVE2.m3u",
        "https://playztv.com/api/v1/channels",
        "https://playztv.com/api/channels",
        "https://playztv.com/api/v1/sports",
        "http://playztv.live/api/channels"
    )
    private val PLAYER_URLS = listOf(
        "https://playztv.com/player?id=",
        "https://playztv.com/watch/",
        "https://playztv.com/live/",
        "http://playztv.live/player?id="
    )
    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0"

    private val fallbackStreamMap = mapOf(
        "tsports" to "https://playztv.com/live/hls/tsports/index.m3u8",
        "gtv" to "https://playztv.com/live/hls/gtv/index.m3u8",
        "ss1" to "https://playztv.com/live/hls/starsports1/index.m3u8",
        "ss_select1" to "https://playztv.com/live/hls/ss_select1/index.m3u8",
        "sony_ten1" to "https://playztv.com/live/hls/sony_ten1/index.m3u8",
        "sony_ten2" to "https://playztv.com/live/hls/sony_ten2/index.m3u8",
        "willow" to "https://playztv.com/live/hls/willow/index.m3u8",
        "sports18" to "https://playztv.com/live/hls/sports18/index.m3u8",
        "somoy" to "https://playztv.com/live/hls/somoy/index.m3u8",
        "jamuna" to "https://playztv.com/live/hls/jamuna/index.m3u8"
    )

    private val sportsKeywords = listOf(
        "sport", "tsport", "gtv", "cricket", "football", "star sports", "sony", "willow",
        "ten 1", "ten 2", "psl", "ipl", "bpl", "icc", "astro", "bein", "sports18", "eurospot", "skysports"
    )

    private val patterns = listOf(
        Pattern.compile("hls\\.loadSource\\s*\\(\\s*[\"'](.*?m3u8.*?)[\"']\\)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("source\\s*:\\s*[\"'](.*?m3u8.*?)[\"']", Pattern.CASE_INSENSITIVE),
        Pattern.compile("file\\s*:\\s*[\"'](.*?m3u8.*?)[\"']", Pattern.CASE_INSENSITIVE),
        Pattern.compile("url\\s*:\\s*[\"'](.*?m3u8.*?)[\"']", Pattern.CASE_INSENSITIVE),
        Pattern.compile("[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
        Pattern.compile("[\"'](https?://[^\"']+\\.ts[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
        Pattern.compile("[\"'](http://172\\.[^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
        Pattern.compile("[\"'](http://10\\.[^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
    )

    suspend fun fetchChannels(ctx: android.content.Context? = null): List<Channel> = withContext(Dispatchers.IO) {
        val effectiveCtx = ctx ?: context
        val resultChannels = mutableListOf<Channel>()

        // 1. Fetch live sports events and expose them under "Playz Live"
        val liveEventChannels = fetchLiveEventChannels(effectiveCtx)
        resultChannels.addAll(liveEventChannels)

        resultChannels
    }

    private suspend fun fetchLiveEventChannels(ctx: android.content.Context?): List<Channel> {
        val eventChannels = mutableListOf<Channel>()
        try {
            val events = PlayZTVProviderManager.fetchLiveEvents(ctx)
            val now = System.currentTimeMillis()
            val limit24h = now + 24 * 60 * 60 * 1000L
            
            // 1. Ongoing active live matches (🔴)
            val liveEvents = events.filter { event ->
                val status = getEventStatus(event)
                status == "🔴" || (status.isBlank() && isEventLive(event))
            }

            // 2. Upcoming matches within next 24 hours (🔜), sorted chronologically
            val upcomingEvents = events.filter { event ->
                if (isEventLive(event) || isEventEnded(event)) return@filter false
                val startMs = getEventStartTimeMs(event)
                startMs != null && startMs > now && startMs <= limit24h
            }.sortedBy { getEventStartTimeMs(it) ?: Long.MAX_VALUE }

            var combinedEvents = liveEvents + upcomingEvents
            if (combinedEvents.isEmpty()) {
                val fallbackUpcoming = events.filter { event ->
                    if (isEventLive(event) || isEventEnded(event)) return@filter false
                    val startMs = getEventStartTimeMs(event)
                    startMs == null || startMs > now
                }.sortedBy { getEventStartTimeMs(it) ?: Long.MAX_VALUE }.take(5)
                combinedEvents = fallbackUpcoming
            }

            for (event in combinedEvents) {
                val displayTitle = createDisplayTitle(event)
                val displaySubtitle = createDisplaySubtitle(event)
                val posterUrl = generateMatchCardUrl(event)
                val cleanSlug = event.slug.ifBlank { "event_${event.id}" }

                // Fetch multi-server streams for this event
                val streams = PlayZTVProviderManager.fetchChannelStreams(cleanSlug)
                var streamSources = if (!streams.isNullOrEmpty()) {
                    PlayZTVProviderManager.parseStreamSources(streams)
                } else emptyList()

                // Fallback to event.formats from the event itself (like senior dev python code)
                if (streamSources.isEmpty() && !event.formats.isNullOrEmpty()) {
                    val fallbackFromFormats = mutableListOf<StreamSource>()
                    for ((fIndex, f) in event.formats.withIndex()) {
                        val link = f.webLink?.trim()
                        if (!link.isNullOrBlank()) {
                            val fTitle = f.title?.trim()
                            val providerName = if (!fTitle.isNullOrBlank() && !fTitle.startsWith("Server", ignoreCase = true)) {
                                fTitle
                            } else {
                                PlayZTVProviderManager.inferChannelNameFromUrl(link, fIndex + 1)
                            }
                            fallbackFromFormats.add(
                                StreamSource(
                                    providerName = providerName,
                                    streamUrl = link,
                                    priority = fIndex
                                )
                            )
                        }
                    }
                    if (fallbackFromFormats.isNotEmpty()) {
                        streamSources = fallbackFromFormats
                    }
                }

                val primaryUrl = streamSources.firstOrNull()?.streamUrl ?: "playztv://$cleanSlug"
                val headers = streamSources.firstOrNull()?.headers ?: mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0",
                    "Referer" to "$BASE_URL/"
                )

                eventChannels.add(
                    Channel(
                        id = "playz_live_$cleanSlug",
                        name = displayTitle,
                        logoUrl = posterUrl,
                        streamUrl = primaryUrl,
                        group = "Playz Live",
                        headers = headers,
                        sources = streamSources,
                        subtitle = displaySubtitle
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("PlayztvRepo", "Failed fetching live events: ${e.message}")
        }

        // Return real live events only (never populate fake placeholder matches)
        return eventChannels
    }

    private suspend fun fetchLinearTvChannels(): List<Channel> {
        val channels = mutableListOf<Channel>()

        // 1. Try expanding categories.txt through each category api link
        try {
            val categories = PlayZTVProviderManager.fetchCategories()
            val m3uParser = io.github.rubayet123.tvlive.data.M3uParser(fastClient)
            for (cat in categories) {
                if (cat.api.isNotBlank()) {
                    try {
                        val req = Request.Builder()
                            .url(cat.api)
                            .header("User-Agent", USER_AGENT)
                            .build()
                        fastClient.newCall(req).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string() ?: ""
                                if (body.isNotBlank()) {
                                    val decrypted = PlayZTVCryptoUtils.decryptPlayZTV(body) ?: body
                                    val parsed = m3uParser.parseM3uChannels(decrypted, cat.name)
                                    for (ch in parsed) {
                                        val withGroup = if (ch.group.isNullOrBlank() || ch.group == "Uncategorized") {
                                            ch.copy(group = cat.name)
                                        } else ch
                                        channels.add(withGroup)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("PlayztvRepo", "Failed fetching playlist for category ${cat.name}: ${e.message}")
                    }
                }
            }
            if (channels.isNotEmpty()) {
                return channels
            }
        } catch (e: Exception) {
            android.util.Log.w("PlayztvRepo", "Category expansion failed: ${e.message}")
        }

        val failedHosts = mutableSetOf<String>()

        for (endpoint in API_ENDPOINTS) {
            val host = try { java.net.URI(endpoint).host } catch (_: Exception) { null }
            if (host != null && failedHosts.contains(host)) {
                continue
            }

            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", USER_AGENT)
                    .header("accept", "*/*")
                    .header("Cache-Control", "no-cache, no-store")
                    .header("Referer", "$BASE_URL/")
                    .build()

                fastClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            val decrypted = PlayZTVCryptoUtils.decryptPlayZTV(body) ?: body
                            if (decrypted.trim().startsWith("#EXTM3U") || decrypted.trim().startsWith("#EXTINF") || decrypted.trim().startsWith("#KODIPROP")) {
                                val m3uParser = io.github.rubayet123.tvlive.data.M3uParser(fastClient)
                                val parsedM3u = m3uParser.parseM3uChannels(decrypted, "PlayZ TV")
                                if (parsedM3u.isNotEmpty()) {
                                    channels.addAll(parsedM3u)
                                    return channels
                                }
                            } else {
                                parseChannelsJson(decrypted, channels)
                                if (channels.isNotEmpty()) {
                                    return channels
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (host != null) {
                    failedHosts.add(host)
                }
                android.util.Log.w("PlayztvRepo", "API endpoint unreachable ($endpoint): ${e.message}")
            }
        }

        return channels
    }

    private fun createDisplayTitle(event: PlayZLiveEventData): String {
        val info = event.eventInfo ?: return event.title
        val hasTeamA = !info.teamA.isNullOrBlank()
        val hasTeamB = !info.teamB.isNullOrBlank()
        val isIdenticalTeams = hasTeamA && hasTeamB && info.teamA!!.trim().equals(info.teamB!!.trim(), ignoreCase = true)

        val title = when {
            hasTeamA && hasTeamB && !isIdenticalTeams -> {
                "${info.teamA!!.trim()} vs ${info.teamB!!.trim()}"
            }
            !info.eventName.isNullOrBlank() -> {
                info.eventName!!.trim()
            }
            hasTeamA -> {
                info.teamA!!.trim()
            }
            else -> event.title.trim()
        }
        val status = getEventStatus(event)
        return if (status.isNotBlank()) "$status $title" else title
    }

    private fun createDisplaySubtitle(event: PlayZLiveEventData): String {
        val info = event.eventInfo
        val hasTeamA = !info?.teamA.isNullOrBlank()
        val hasTeamB = !info?.teamB.isNullOrBlank()
        val isIdenticalTeams = hasTeamA && hasTeamB && info?.teamA!!.trim().equals(info?.teamB!!.trim(), ignoreCase = true)

        // Tournament or competition name (if already used as title, fall back to category)
        val tournament = when {
            hasTeamA && hasTeamB && !isIdenticalTeams && !info?.eventName.isNullOrBlank() -> {
                info?.eventName?.trim()
            }
            !info?.eventCat.isNullOrBlank() -> {
                info?.eventCat?.trim()
            }
            !event.cat.isNullOrBlank() -> {
                event.cat.trim()
            }
            else -> null
        }

        // Kickoff time formatted for 10-foot readability
        val startMs = getEventStartTimeMs(event)
        val timeStr = if (startMs != null) {
            val calNow = java.util.Calendar.getInstance()
            val calEvent = java.util.Calendar.getInstance().apply { timeInMillis = startMs }
            val isToday = calNow.get(java.util.Calendar.YEAR) == calEvent.get(java.util.Calendar.YEAR) &&
                    calNow.get(java.util.Calendar.DAY_OF_YEAR) == calEvent.get(java.util.Calendar.DAY_OF_YEAR)
            val isTomorrow = calNow.get(java.util.Calendar.YEAR) == calEvent.get(java.util.Calendar.YEAR) &&
                    calNow.get(java.util.Calendar.DAY_OF_YEAR) + 1 == calEvent.get(java.util.Calendar.DAY_OF_YEAR)

            val timeFmt = SimpleDateFormat("hh:mm a", Locale.US)
            val formattedTime = timeFmt.format(java.util.Date(startMs))

            when {
                isToday -> formattedTime
                isTomorrow -> "Tomorrow, $formattedTime"
                else -> {
                    val dateFmt = SimpleDateFormat("MMM d, hh:mm a", Locale.US)
                    dateFmt.format(java.util.Date(startMs))
                }
            }
        } else null

        return when {
            !tournament.isNullOrBlank() && !timeStr.isNullOrBlank() -> "$tournament • $timeStr"
            !tournament.isNullOrBlank() -> tournament
            !timeStr.isNullOrBlank() -> timeStr
            else -> "Playz Live"
        }
    }

    private fun parseTimestampMs(timeStr: String?): Long? {
        if (timeStr.isNullOrBlank()) return null
        val patterns = arrayOf(
            "yyyy/MM/dd HH:mm:ss Z",
            "yyyy/MM/dd HH:mm Z",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy/MM/dd hh:mm:ss a Z",
            "yyyy/MM/dd hh:mm a Z",
            "yyyy/MM/dd hh:mm:ss a",
            "yyyy/MM/dd hh:mm a",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm"
        )
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.US)
                val parsed = fmt.parse(timeStr)
                if (parsed != null) return parsed.time
            } catch (_: Exception) { }
        }
        return null
    }

    private fun getEventStatus(event: PlayZLiveEventData): String {
        val info = event.eventInfo ?: return ""
        val now = System.currentTimeMillis()
        val start = parseTimestampMs(info.startTime)
        val end = parseTimestampMs(info.endTime)
        return when {
            end != null && now >= end -> "✅"
            start != null && now >= start -> "🔴"
            start != null && now < start -> "🔜"
            else -> ""
        }
    }

    private fun isEventLive(event: PlayZLiveEventData): Boolean {
        val info = event.eventInfo ?: return false
        val now = System.currentTimeMillis()
        val start = parseTimestampMs(info.startTime)
        val end = parseTimestampMs(info.endTime)
        return if (end != null && now >= end) false else start != null && now >= start
    }

    private fun isEventEnded(event: PlayZLiveEventData): Boolean {
        val info = event.eventInfo ?: return false
        val now = System.currentTimeMillis()
        val end = parseTimestampMs(info.endTime)
        return end != null && now >= end
    }

    private fun getEventStartTimeMs(event: PlayZLiveEventData): Long? {
        val info = event.eventInfo ?: return null
        return parseTimestampMs(info.startTime)
    }

    private fun generateMatchCardUrl(event: PlayZLiveEventData): String {
        val info = event.eventInfo
        val encode: (String) -> String = {
            try { java.net.URLEncoder.encode(it, "UTF-8") } catch (_: Exception) { it }
        }

        val title = encode(info?.eventName ?: event.title)
        val teamA = encode(info?.teamA ?: "Team A")
        val teamB = encode(info?.teamB ?: "Team B")
        val teamAImg = info?.teamAFlag ?: ""
        val teamBImg = info?.teamBFlag ?: ""
        val eventLogo = info?.eventLogo ?: ""
        val isLive = isEventLive(event)
        val isEnded = isEventEnded(event)

        val time = try {
            info?.startTime?.let {
                val df = SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z", Locale.US)
                val disp = SimpleDateFormat("MMM dd, hh:mm a", Locale.US)
                df.parse(it)?.let { d -> encode(disp.format(d)) } ?: ""
            } ?: ""
        } catch (_: Exception) { "" }

        return buildString {
            append("https://live-card-png.cricify.workers.dev/?")
            append("title=$title")
            append("&teamA=$teamA")
            append("&teamB=$teamB")
            if (teamAImg.isNotBlank()) append("&teamAImg=$teamAImg")
            if (teamBImg.isNotBlank()) append("&teamBImg=$teamBImg")
            if (eventLogo.isNotBlank()) append("&eventLogo=$eventLogo")
            if (time.isNotBlank()) append("&time=$time")
            append("&isLive=$isLive")
            append("&isEnded=$isEnded")
        }
    }

    private fun parseChannelsJson(jsonStr: String, outChannels: MutableList<Channel>) {
        try {
            val rootArray: JSONArray = when {
                jsonStr.trim().startsWith("[") -> JSONArray(jsonStr)
                else -> {
                    val obj = JSONObject(jsonStr)
                    obj.optJSONArray("channels")
                        ?: obj.optJSONArray("data")
                        ?: obj.optJSONArray("streams")
                        ?: obj.optJSONArray("sports")
                        ?: JSONArray()
                }
            }

            for (i in 0 until rootArray.length()) {
                val obj = rootArray.getJSONObject(i)
                val id = obj.optString("id", obj.optString("channel_id", obj.optString("slug", "playz_$i")))
                val name = obj.optString("title", obj.optString("name", "PlayZ Channel $i"))
                val logo = obj.optString("logo", obj.optString("logo_url", obj.optString("icon", "")))
                var group = obj.optString("category", obj.optString("group", obj.optString("genre", "")))
                var streamUrl = obj.optString("stream_url", obj.optString("url", obj.optString("link", "")))

                val isSports = sportsKeywords.any { kw ->
                    name.lowercase().contains(kw) || group.lowercase().contains(kw)
                }

                if (group.isBlank()) {
                    group = if (isSports) "Sports" else "PlayZ TV"
                } else if (isSports && group != "Sports") {
                    group = "Sports"
                }

                if (streamUrl.isEmpty() || (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://"))) {
                    streamUrl = "playztv://$id"
                }

                outChannels.add(
                    Channel(
                        id = "playz_$id",
                        name = name,
                        logoUrl = logo,
                        streamUrl = streamUrl,
                        group = group,
                        headers = mapOf(
                            "Referer" to "$BASE_URL/",
                            "Origin" to BASE_URL,
                            "User-Agent" to USER_AGENT
                        )
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayztvRepo", "Error parsing PlayZTV JSON", e)
        }
    }

    suspend fun resolveSourcesForChannel(channelId: String): List<StreamSource>? = withContext(Dispatchers.IO) {
        val cleanId = channelId.trim().removePrefix("event_")
        if (cleanId.startsWith("http://") || cleanId.startsWith("https://")) {
            return@withContext listOf(StreamSource(providerName = "Direct Stream", streamUrl = cleanId))
        }

        // 1. Check if it is a live event slug
        try {
            val streams = PlayZTVProviderManager.fetchChannelStreams(cleanId)
            if (!streams.isNullOrEmpty()) {
                val parsed = PlayZTVProviderManager.parseStreamSources(streams)
                if (parsed.isNotEmpty()) {
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("PlayztvRepo", "Live event stream resolution failed: ${e.message}")
        }

        // 2. High-reliability fallback: Resolve using active sports plugins (Roarzone / Splex / Redforce)
        val candidateTargets = when {
            cleanId.contains("tsport") || cleanId.contains("cricket") -> listOf("tsports", "tsport")
            cleanId.contains("gtv") -> listOf("gtv")
            cleanId.contains("star") || cleanId.contains("ss1") -> listOf("star_sports_1", "star-sports-1")
            cleanId.contains("ten1") || cleanId.contains("football") -> listOf("sony_ten1", "sony-ten-1")
            cleanId.contains("ten2") -> listOf("sony_ten2", "sony-ten-2")
            cleanId.contains("willow") -> listOf("willow")
            cleanId.contains("sports18") -> listOf("sports18", "sports-18")
            else -> listOf(cleanId)
        }

        val fallbackSources = mutableListOf<StreamSource>()

        try {
            val splexRepo = SplexRepository(client)
            for (target in candidateTargets) {
                val sUrl = splexRepo.resolveStream(target)
                if (!sUrl.isNullOrBlank()) {
                    fallbackSources.add(StreamSource(providerName = "Server 1 (Splex HD)", streamUrl = sUrl.replace(Regex("(?i)%2f"), "/"), priority = 0))
                    break
                }
            }
        } catch (_: Exception) { }

        try {
            val redRepo = RedforceRepository(client)
            for (target in candidateTargets) {
                val redUrl = redRepo.resolveStream(target)
                if (!redUrl.isNullOrBlank()) {
                    fallbackSources.add(StreamSource(providerName = "Server 2 (Redforce)", streamUrl = redUrl.replace(Regex("(?i)%2f"), "/"), priority = 1))
                    break
                }
            }
        } catch (_: Exception) { }

        if (fallbackSources.isNotEmpty()) {
            return@withContext fallbackSources
        }

        null
    }

    suspend fun resolveStream(channelId: String): String? = withContext(Dispatchers.IO) {
        resolveSourcesForChannel(channelId)?.firstOrNull()?.streamUrl
    }

    fun getSeedLiveMatches(): List<Channel> {
        // Return empty list so only real backend live events are shown
        return emptyList()
    }

    fun loadSeedChannels(): List<Channel> {
        val headers = mapOf(
            "Referer" to "$BASE_URL/",
            "Origin" to BASE_URL,
            "User-Agent" to USER_AGENT
        )

        val channels = mutableListOf<Channel>()
        channels.addAll(
            listOf(
                Channel(id = "playz_tsports", name = "T Sports HD", logoUrl = "https://playztv.com/logos/tsports.png", streamUrl = "playztv://tsports", group = "Sports", headers = headers),
                Channel(id = "playz_gtv", name = "GTV HD", logoUrl = "https://playztv.com/logos/gtv.png", streamUrl = "playztv://gtv", group = "Sports", headers = headers),
                Channel(id = "playz_ss1", name = "Star Sports 1 HD", logoUrl = "https://playztv.com/logos/starsports1.png", streamUrl = "playztv://star_sports_1", group = "Sports", headers = headers),
                Channel(id = "playz_ss_select1", name = "Star Sports Select 1 HD", logoUrl = "https://playztv.com/logos/ss_select1.png", streamUrl = "playztv://ss_select1", group = "Sports", headers = headers),
                Channel(id = "playz_sony_ten1", name = "Sony Sports Ten 1 HD", logoUrl = "https://playztv.com/logos/sony_ten1.png", streamUrl = "playztv://sony_ten1", group = "Sports", headers = headers),
                Channel(id = "playz_sony_ten2", name = "Sony Sports Ten 2 HD", logoUrl = "https://playztv.com/logos/sony_ten2.png", streamUrl = "playztv://sony_ten2", group = "Sports", headers = headers),
                Channel(id = "playz_willow", name = "Willow HD", logoUrl = "https://playztv.com/logos/willow.png", streamUrl = "playztv://willow", group = "Sports", headers = headers),
                Channel(id = "playz_sports18", name = "Sports18 1 HD", logoUrl = "https://playztv.com/logos/sports18.png", streamUrl = "playztv://sports18", group = "Sports", headers = headers)
            )
        )
        return channels
    }
}
