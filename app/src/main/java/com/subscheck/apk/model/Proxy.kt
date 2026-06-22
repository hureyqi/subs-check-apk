package com.subscheck.apk.model

/**
 * Represents a proxy node parsed from subscription data.
 */
data class Proxy(
    val type: String,           // e.g. ss, vmess, trojan, hysteria2, etc.
    var name: String,
    val server: String,
    val port: Int,
    val rawData: Map<String, Any>
) {
    var isAlive: Boolean = false
    var delay: Long = 0L        // ms
    var speed: Int = 0          // KB/s
    val mediaInfo = mutableMapOf<String, String>()
    var country: String = ""
    var ipRisk: String = ""

    // Deduplication key: type+server+port
    val dedupKey: String
        get() = "$type-$server-$port"
}

/**
 * Represents the final check result for a proxy.
 */
data class CheckResult(
    val proxy: Proxy,
    val isAlive: Boolean = false,
    val delayMs: Long = 0L,
    val speedKBps: Int = 0,
    val openai: String? = null,
    val youtube: String? = null,
    val netflix: String? = null,
    val disney: Boolean = false,
    val google: Boolean = false,
    val cloudflare: Boolean = false,
    val gemini: String? = null,
    val tiktok: String? = null,
    val claude: String? = null,
    val spotify: String? = null,
    val ip: String = "",
    val ipRisk: String = "",
    val country: String = ""
) {
    // Build the display name with media tags (similar to RenderName in Go)
    fun renderName(): String {
        val tags = mutableListOf<String>()
        if (openai == "yes") tags.add("OpenAI")
        if (youtube == "yes") tags.add("YouTube")
        if (netflix != null && netflix != "no") tags.add("Netflix")
        if (disney) tags.add("Disney")
        if (gemini == "yes") tags.add("Gemini")
        if (tiktok != "no" && !tiktok.isNullOrEmpty()) tags.add("TikTok")
        if (claude == "yes") tags.add("Claude")
        if (spotify == "yes") tags.add("Spotify")
        if (country.isNotBlank()) tags.add(country)

        val tagStr = if (tags.isNotEmpty()) tags.joinToString(" | ") else ""
        val speedStr = if (speedKBps > 0) "${speedKBps} KB/s" else ""

        return buildString {
            append(proxy.name)
            if (tagStr.isNotBlank()) append(" [$tagStr]")
            if (speedStr.isNotBlank()) append(" [$speedStr]")
        }
    }
}
