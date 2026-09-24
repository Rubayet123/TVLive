package io.github.rubayet123.tvlive.scraper

import io.github.rubayet123.tvlive.model.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

class ScrapeRepository(private val scrapeService: ScrapeService) {

    data class TempChannel(val streamId: String, val name: String, val logoUrl: String, val category: String)

    @Throws(IOException::class)
    suspend fun scrapeChannels(
        baseUrl: String,
        onProgress: suspend (total: Int, completed: Int) -> Unit
    ): List<Channel> {
        val mainPageHtml = scrapeService.fetchMainPage(baseUrl)
        val channels = parseChannels(baseUrl, mainPageHtml)
        val totalChannels = channels.size

        if (totalChannels == 0) {
            throw IOException("Parsing failed: No channels found. Website structure might have changed.")
        }

        onProgress(totalChannels, 0)

        val semaphore = Semaphore(4)
        val completedCount = AtomicInteger(0)

        val resolvedChannels = coroutineScope {
            channels.map { channel ->
                async {
                    var resolvedChannel: Channel? = null
                    try {
                        if (baseUrl.contains("splex.live")) {
                            // For Splex, use dynamic resolution at playback time because URLs expire quickly
                            resolvedChannel = Channel(
                                id = channel.streamId,
                                name = channel.name,
                                logoUrl = channel.logoUrl,
                                group = when(channel.category) {
                                    "Indian" -> "Hindi"
                                    "Kolkata" -> "Inbangla"
                                    else -> channel.category
                                },
                                streamUrl = "splex://${channel.streamId}",
                                headers = mapOf(
                                    "Referer" to "$baseUrl/play.php?stream=${channel.streamId}",
                                    "Origin" to baseUrl.trimEnd('/'),
                                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36",
                                    "X-Requested-With" to "com.android.chrome"
                                )
                            )
                        } else {
                            // Original logic for Redforce (resolve now) - Use semaphore for concurrency control
                            semaphore.withPermit {
                                val playerPageHtml = scrapeService.fetchPlayerPage(baseUrl, channel.streamId)
                                val streamUrl = parseStreamUrl(playerPageHtml)
                                if (streamUrl != null) {
                                    val playerUrl = if (baseUrl.endsWith("/")) "${baseUrl}play.php?stream=${channel.streamId}" else "$baseUrl/play.php?stream=${channel.streamId}"
                                    val origin = baseUrl.trimEnd('/')
                                    
                                    resolvedChannel = Channel(
                                        id = channel.streamId,
                                        name = channel.name,
                                        logoUrl = channel.logoUrl,
                                        group = channel.category,
                                        streamUrl = streamUrl,
                                        headers = mapOf(
                                            "Referer" to playerUrl,
                                            "Origin" to origin,
                                            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36",
                                            "X-Requested-With" to "com.android.chrome",
                                            "Accept" to "*/*",
                                            "Accept-Language" to "en-US,en;q=0.9"
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Log or handle individual channel failure
                    } finally {
                        val currentCompleted = completedCount.incrementAndGet()
                        onProgress(totalChannels, currentCompleted)
                    }
                    resolvedChannel
                }
            }.awaitAll().filterNotNull()
        }

        if (resolvedChannels.isEmpty()) {
            throw IOException("Scraped initial channels, but failed to resolve any stream URLs.")
        }

        return resolvedChannels
    }

    private fun parseChannels(baseUrl: String, html: String): List<TempChannel> {
        val document = Jsoup.parse(html)
        // Adjusting selector to be slightly more generic if needed, 
        // but both sources use similar a.channel[onclick*='stream=']
        val channelAnchors = document.select("a.channel[onclick*='stream=']")
        val channels = mutableListOf<TempChannel>()

        for (anchor in channelAnchors) {
            val onclickAttr = anchor.attr("onclick")
            val streamId = onclickAttr.substringAfter("stream=").substringBefore("'")

            val img = anchor.selectFirst("img")
            val name = img?.attr("alt") ?: img?.attr("title") ?: ""
            var logoUrl = img?.attr("src") ?: ""

            if (logoUrl.isNotEmpty() && !logoUrl.startsWith("http")) {
                val base = if (baseUrl.endsWith("/")) baseUrl.substring(0, baseUrl.length - 1) else baseUrl
                logoUrl = if (logoUrl.startsWith("/")) "$base$logoUrl" else "$base/$logoUrl"
            }

            val parentLi = anchor.parent()
            val category = parentLi?.attr("class")?.split(" ")?.firstOrNull { it != "All" && it != "channel" } ?: "Uncategorized"

            if (streamId.isNotEmpty() && name.isNotEmpty()) {
                channels.add(TempChannel(streamId, name, logoUrl, category))
            }
        }
        return channels
    }

    private fun parseStreamUrl(html: String): String? {
        val patterns = listOf(
            Pattern.compile("iframe src=\"(.*?)\""),
            Pattern.compile("source src=\"(.*?)\""),
            Pattern.compile("src: or src=\"(.*?)\""),
            Pattern.compile("'s*(http.*?m3u8.*?)'s*"),
            Pattern.compile("file: \"(.*?m3u8.*?)\"")
        )

        for (pattern in patterns) {
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
