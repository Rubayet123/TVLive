package io.github.rubayet123.tvlive.scraper

import io.github.rubayet123.tvlive.model.Channel

object M3uBuilder {

    fun build(channels: List<Channel>): String {
        val builder = StringBuilder()
        builder.append("#EXTM3U\n")

        for (channel in channels) {
            val sources = if (channel.sources.isNotEmpty()) channel.sources else channel.effectiveSources
            if (sources.size > 1) {
                for (source in sources) {
                    val provAttr = if (!source.providerName.isNullOrBlank()) " tvg-provider=\"${source.providerName}\"" else ""
                    builder.append("#EXTINF:-1 tvg-id=\"${channel.id}\" tvg-name=\"${channel.name}\" tvg-logo=\"${channel.logoUrl ?: ""}\"$provAttr group-title=\"${channel.group ?: ""}\",${channel.name}\n")
                    val licType = source.licenseType ?: channel.licenseType
                    val licKey = source.licenseKey ?: channel.licenseKey
                    if (!licType.isNullOrBlank() && !licKey.isNullOrBlank()) {
                        builder.append("#KODIPROP:inputstream.adaptive.license_type=$licType\n")
                        builder.append("#KODIPROP:inputstream.adaptive.license_key=$licKey\n")
                    }
                    val hdrs = source.headers ?: channel.headers
                    hdrs?.forEach { (key, value) ->
                        val lowerKey = key.lowercase()
                        builder.append("#EXTVLCOPT:http-$lowerKey=$value\n")
                    }
                    builder.append("${source.streamUrl}\n")
                }
            } else {
                val singleProv = sources.firstOrNull()?.providerName ?: ""
                val provAttr = if (singleProv.isNotBlank()) " tvg-provider=\"$singleProv\"" else ""
                builder.append("#EXTINF:-1 tvg-id=\"${channel.id}\" tvg-name=\"${channel.name}\" tvg-logo=\"${channel.logoUrl ?: ""}\"$provAttr group-title=\"${channel.group ?: ""}\",${channel.name}\n")
                val licType = channel.licenseType ?: channel.sources.firstOrNull()?.licenseType
                val licKey = channel.licenseKey ?: channel.sources.firstOrNull()?.licenseKey
                if (!licType.isNullOrBlank() && !licKey.isNullOrBlank()) {
                    builder.append("#KODIPROP:inputstream.adaptive.license_type=$licType\n")
                    builder.append("#KODIPROP:inputstream.adaptive.license_key=$licKey\n")
                }
                val hdrs = channel.headers ?: channel.sources.firstOrNull()?.headers
                hdrs?.forEach { (key, value) ->
                    val lowerKey = key.lowercase()
                    builder.append("#EXTVLCOPT:http-$lowerKey=$value\n")
                }
                builder.append("${channel.streamUrl}\n")
            }
        }

        return builder.toString()
    }
}
