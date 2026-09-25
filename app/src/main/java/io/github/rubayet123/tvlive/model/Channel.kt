package io.github.rubayet123.tvlive.model

import java.io.Serializable

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val group: String? = null,
    val licenseType: String? = null,
    val licenseKey: String? = null,
    val headers: Map<String, String>? = null,
    val sources: List<StreamSource> = emptyList(),
    val subtitle: String? = null
) : Serializable {

    val effectiveSources: List<StreamSource>
        get() = if (sources.isNotEmpty()) {
            sources
        } else {
            listOf(
                StreamSource(
                    providerName = detectProviderName(streamUrl, name, group),
                    streamUrl = streamUrl,
                    headers = headers,
                    licenseType = licenseType,
                    licenseKey = licenseKey,
                    priority = 0
                )
            )
        }

    companion object {
        fun cleanProviderName(rawProvider: String?, url: String, name: String? = null, group: String? = null): String {
            val raw = rawProvider?.trim()
            if (!raw.isNullOrBlank() && raw != "Stream Server" && raw != "M3U") {
                val r = raw.lowercase()
                return when {
                    r.contains("redforce") -> "Redforce TV"
                    r.contains("roarzone") -> "Roarzone TV"
                    r.contains("playz") -> "PlayZ TV"
                    r.contains("ideal") -> "Ideal TV"
                    r.contains("orbit") -> "Orbit TV"
                    r.contains("splex") -> "Splex TV"
                    r.contains("bas tv") || r.contains("local isp") -> "BAS TV"
                    r.contains("bdix") -> raw
                    else -> raw
                }
            }
            return detectProviderName(url, name, group)
        }

        fun detectProviderName(url: String, name: String? = null, group: String? = null): String {
            val u = url.lowercase().trim()
            val g = group?.lowercase() ?: ""
            val n = name?.lowercase() ?: ""

            // 1. Direct custom schemes or known keywords
            if (u.startsWith("orbittv://") || u.contains("172.19.17.3") || g.contains("orbit tv") || n.contains("orbit tv")) return "Orbit TV"
            if (u.startsWith("roarzone://") || u.contains("roarzone") || g.contains("roarzone") || n.contains("roarzone")) return "Roarzone TV"
            if (u.startsWith("idealtv://") || u.contains("172.16.60.2") || g.contains("ideal tv") || n.contains("ideal tv")) return "Ideal TV"
            if (u.startsWith("damitv://") || u.contains("damitv") || u.contains("ondemand.st") || g.contains("damitv") || n.contains("damitv")) return "DAMITV"
            if (u.startsWith("playztv://") || u.contains("playztv") || u.contains("playz.tv") || g.contains("playz") || n.contains("playz")) return "PlayZ TV"
            if (u.startsWith("splex://") || u.contains("splex") || g.contains("splex") || n.contains("splex")) return "Splex TV"
            if (u.startsWith("redforce://") || u.contains("redforce") || g.contains("redforce") || n.contains("redforce")) return "Redforce TV"
            if (u.contains("10.99.99.99") || u.contains("10.99.99.") || g.contains("bas tv") || n.contains("bas tv") || g.contains("local isp")) return "BAS TV"

            // 2. Parse host/domain/IP from URL
            try {
                val uri = android.net.Uri.parse(url)
                val host = uri.host
                if (!host.isNullOrBlank()) {
                    val h = host.lowercase()
                    when {
                        h.contains("roarzone") -> return "Roarzone TV"
                        h.contains("redforce") -> return "Redforce TV"
                        h.contains("splex") -> return "Splex TV"
                        h.contains("orbit") -> return "Orbit TV"
                        h.contains("idealtv") || h.contains("ideal") -> return "Ideal TV"
                        h.contains("youtube") || h.contains("youtu.be") -> return "YouTube Live"
                        h == "172.19.17.3" -> return "Orbit TV"
                        h == "172.16.60.2" -> return "Ideal TV"
                        h.startsWith("10.99.99.") -> return "BAS TV"
                        h.startsWith("10.") -> return "BDIX ($h)"
                        h.startsWith("172.16.") || h.startsWith("172.17.") || h.startsWith("172.18.") ||
                        h.startsWith("172.19.") || h.startsWith("172.20.") || h.startsWith("172.31.") -> return "BDIX ($h)"
                        h.startsWith("192.168.") -> return "Local IP ($h)"
                        h.startsWith("103.") || h.startsWith("118.") || h.startsWith("45.") || h.startsWith("182.") -> return "BDIX Server ($h)"
                        h.contains("bdix") -> return "BDIX ($h)"
                        else -> {
                            val cleanHost = host.removePrefix("www.").removePrefix("tv.").removePrefix("live.")
                            return if (cleanHost.length <= 22) cleanHost else "Server ($cleanHost)"
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3. Fallbacks
            return when {
                g.contains("bdix") || n.contains("bdix") -> "BDIX TV"
                u.contains("web.m3u") || g.contains("web tv") || n.contains("web tv") -> "Web TV"
                u.contains("raw.githubusercontent.com") -> "GitHub Stream"
                else -> "BDIX Server"
            }
        }
    }
}

