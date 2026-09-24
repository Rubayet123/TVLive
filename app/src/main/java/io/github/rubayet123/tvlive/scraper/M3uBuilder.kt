package io.github.rubayet123.tvlive.scraper

import io.github.rubayet123.tvlive.model.Channel

object M3uBuilder {

    fun build(channels: List<Channel>): String {
        val builder = StringBuilder()
        builder.append("#EXTM3U\n")

        for (channel in channels) {
            builder.append("#EXTINF:-1 tvg-name=\"${channel.name}\" tvg-logo=\"${channel.logoUrl ?: ""}\" group-title=\"${channel.group ?: ""}\",${channel.name}\n")
            channel.headers?.forEach { (key, value) ->
                val lowerKey = key.lowercase()
                builder.append("#EXTVLCOPT:http-$lowerKey=$value\n")
            }
            builder.append("${channel.streamUrl}\n")
        }

        return builder.toString()
    }
}
