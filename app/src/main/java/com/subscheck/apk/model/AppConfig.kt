package com.subscheck.apk.model

import android.content.Context

/**
 * App configuration managed via SharedPreferences.
 */
data class AppConfig(
    val subscriptionUrls: List<String> = DEFAULT_URLS,
    val concurrent: Int = 50,
    val speedConcurrent: Int = 20,
    val mediaConcurrent: Int = 20,
    val shuffleTestOrder: Boolean = true,
    val aliveTestUrl: String = "http://gstatic.com/generate_204",
    val speedTestUrl: String = "https://github.com/AaronFeng753/Waifu2x-Extension-GUI/releases/download/v2.21.12/Waifu2x-Extension-GUI-v2.21.12-Portable.7z",
    val timeoutMs: Long = 5000,
    val mediaTimeoutMs: Long = 5000,
    val speedTimeoutMs: Long = 10000,
    val downloadMB: Int = 20,
    val minSpeed: Int = 512,
    val filterRegex: String = "",
    val mediaCheck: Boolean = true,
    val fileName: String = ""
) {
    fun toPipelineConfig() = PipelineConfig(
        concurrent = concurrent,
        speedConcurrent = speedConcurrent,
        mediaConcurrent = mediaConcurrent,
        shuffleTestOrder = shuffleTestOrder,
        aliveTestUrl = aliveTestUrl,
        speedTestUrl = speedTestUrl,
        timeoutMs = timeoutMs,
        mediaTimeoutMs = mediaTimeoutMs,
        speedTimeoutMs = speedTimeoutMs,
        downloadMB = downloadMB,
        minSpeed = minSpeed,
        filterRegex = filterRegex,
        mediaCheck = mediaCheck
    )

    companion object {
        private const val PREFS_NAME = "subs_check_prefs"
        private const val KEY_URLS = "urls"
        private const val KEY_CONCURRENT = "concurrent"
        private const val KEY_SPEED_CONCURRENT = "speed_concurrent"
        private const val KEY_MEDIA_CONCURRENT = "media_concurrent"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_ALIVE_URL = "alive_url"
        private const val KEY_SPEED_URL = "speed_url"
        private const val KEY_TIMEOUT = "timeout"
        private const val KEY_MEDIA_TIMEOUT = "media_timeout"
        private const val KEY_SPEED_TIMEOUT = "speed_timeout"
        private const val KEY_DOWNLOAD_MB = "download_mb"
        private const val KEY_MIN_SPEED = "min_speed"
        private const val KEY_FILTER = "filter"
        private const val KEY_MEDIA_CHECK = "media_check"
        private const val KEY_FILE_NAME = "file_name"

        // Default subscription URLs from the original Go project (config.example.yaml)
        val DEFAULT_URLS = listOf(
            "https://raw.githubusercontent.com/firefoxmmx2/v2rayshare_subcription/main/subscription/clash_sub.yaml",
            "https://raw.githubusercontent.com/Q3dlaXpoaQ/V2rayN_Clash_Node_Getter/refs/heads/main/APIs/sc0.yaml",
            "https://raw.githubusercontent.com/mahdibland/SSAggregator/master/sub/sub_merge_yaml.yml",
            "https://raw.githubusercontent.com/snakem982/proxypool/main/source/clash-meta.yaml",
            "https://raw.githubusercontent.com/chengaopan/AutoMergePublicNodes/master/list.yml",
            "https://raw.githubusercontent.com/zhangkaiitugithub/passcro/main/speednodes.yaml",
            "https://raw.githubusercontent.com/aiboboxx/v2rayfree/refs/heads/main/README.md",
            "https://raw.githubusercontent.com/peasoft/NoMoreWalls/master/snippets/nodes.meta.yml",
            "https://raw.githubusercontent.com/Ruk1ng001/freeSub/main/clash.yaml",
            "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/all_configs.txt",
            "https://raw.githubusercontent.com/ripaojiedian/freenode/main/clash",
            "https://raw.githubusercontent.com/go4sharing/sub/main/sub.yaml",
            "https://raw.githubusercontent.com/actionsfz/v2ray/refs/heads/master/all.yaml",
            "https://raw.githubusercontent.com/Pawdroid/Free-servers/refs/heads/main/sub",
            "https://raw.githubusercontent.com/acymz/AutoVPN/main/data/V2.txt",
            "https://raw.githubusercontent.com/Barabama/FreeNodes/main/nodes/wenode.txt",
            "https://raw.githubusercontent.com/Barabama/FreeNodes/main/nodes/v2rayshare.txt",
            "https://raw.githubusercontent.com/Barabama/FreeNodes/main/nodes/nodefree.txt",
            "https://raw.githubusercontent.com/Barabama/FreeNodes/main/nodes/ndnode.txt",
            "https://raw.githubusercontent.com/Barabama/FreeNodes/main/nodes/clashmeta.txt",
            "https://raw.githubusercontent.com/xiaoji235/airport-free/main/v2ray.txt"
        )

        fun save(context: Context, config: AppConfig) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_URLS, config.subscriptionUrls.joinToString("\n"))
                putInt(KEY_CONCURRENT, config.concurrent)
                putInt(KEY_SPEED_CONCURRENT, config.speedConcurrent)
                putInt(KEY_MEDIA_CONCURRENT, config.mediaConcurrent)
                putBoolean(KEY_SHUFFLE, config.shuffleTestOrder)
                putString(KEY_ALIVE_URL, config.aliveTestUrl)
                putString(KEY_SPEED_URL, config.speedTestUrl)
                putLong(KEY_TIMEOUT, config.timeoutMs)
                putLong(KEY_MEDIA_TIMEOUT, config.mediaTimeoutMs)
                putLong(KEY_SPEED_TIMEOUT, config.speedTimeoutMs)
                putInt(KEY_DOWNLOAD_MB, config.downloadMB)
                putInt(KEY_MIN_SPEED, config.minSpeed)
                putString(KEY_FILTER, config.filterRegex)
                putBoolean(KEY_MEDIA_CHECK, config.mediaCheck)
                putString(KEY_FILE_NAME, config.fileName)
            }.apply()
        }

        fun load(context: Context): AppConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppConfig(
                subscriptionUrls = prefs.getString(KEY_URLS, "")?.split("\n")?.filter { it.isNotBlank() } ?: DEFAULT_URLS,
                concurrent = prefs.getInt(KEY_CONCURRENT, 50),
                speedConcurrent = prefs.getInt(KEY_SPEED_CONCURRENT, 20),
                mediaConcurrent = prefs.getInt(KEY_MEDIA_CONCURRENT, 20),
                shuffleTestOrder = prefs.getBoolean(KEY_SHUFFLE, true),
                aliveTestUrl = prefs.getString(KEY_ALIVE_URL, "http://gstatic.com/generate_204") ?: "http://gstatic.com/generate_204",
                speedTestUrl = prefs.getString(KEY_SPEED_URL, "https://github.com/AaronFeng753/Waifu2x-Extension-GUI/releases/download/v2.21.12/Waifu2x-Extension-GUI-v2.21.12-Portable.7z") ?: "https://github.com/AaronFeng753/Waifu2x-Extension-GUI/releases/download/v2.21.12/Waifu2x-Extension-GUI-v2.21.12-Portable.7z",
                timeoutMs = prefs.getLong(KEY_TIMEOUT, 5000),
                mediaTimeoutMs = prefs.getLong(KEY_MEDIA_TIMEOUT, 5000),
                speedTimeoutMs = prefs.getLong(KEY_SPEED_TIMEOUT, 10000),
                downloadMB = prefs.getInt(KEY_DOWNLOAD_MB, 20),
                minSpeed = prefs.getInt(KEY_MIN_SPEED, 512),
                filterRegex = prefs.getString(KEY_FILTER, "") ?: "",
                mediaCheck = prefs.getBoolean(KEY_MEDIA_CHECK, true),
                fileName = prefs.getString(KEY_FILE_NAME, "") ?: ""
            )
        }
    }
}
