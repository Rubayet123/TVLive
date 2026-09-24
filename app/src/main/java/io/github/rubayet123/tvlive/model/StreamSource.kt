package io.github.rubayet123.tvlive.model

import java.io.Serializable

data class StreamSource(
    val providerName: String,
    val streamUrl: String,
    val headers: Map<String, String>? = null,
    val licenseType: String? = null,
    val licenseKey: String? = null,
    val priority: Int = 0
) : Serializable
