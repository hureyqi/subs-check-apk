package com.subscheck.apk.pipeline

import com.subscheck.apk.checker.AliveChecker
import com.subscheck.apk.checker.IPChecker
import com.subscheck.apk.checker.MediaChecker
import com.subscheck.apk.checker.SpeedChecker
import com.subscheck.apk.model.CheckResult
import com.subscheck.apk.model.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Orchestrates the full check pipeline:
 * 1. Deduplication
 * 2. Shuffle (optional)
 * 3. Alive check
 * 4. IP/Geo check
 * 5. Media unlock check
 * 6. Speed test
 * 7. Filtering
 * 
 * Mirrors the Go project's pipeline in check/check.go.
 */
class CheckPipeline(
    private val config: PipelineConfig = PipelineConfig()
) {
    private val aliveChecker = AliveChecker(
        testUrl = config.aliveTestUrl,
        timeoutMs = config.timeoutMs,
        maxConcurrent = config.concurrent
    )
    private val ipChecker = IPChecker(timeoutMs = config.timeoutMs, maxConcurrent = config.mediaConcurrent)
    private val mediaChecker = MediaChecker(
        timeoutMs = config.mediaTimeoutMs,
        maxConcurrent = config.mediaConcurrent
    )
    private val speedChecker = SpeedChecker(
        speedTestUrl = config.speedTestUrl,
        timeoutMs = config.speedTimeoutMs,
        maxConcurrent = config.speedConcurrent,
        downloadMB = config.downloadMB
    )

    /**
     * Run the full pipeline and emit progress updates.
     */
    fun run(proxies: List<Proxy>): Flow<PipelineProgress> = flow {
        val total = proxies.size
        emit(PipelineProgress.Started(total))

        // Step 1: Deduplication
        emit(PipelineProgress.Phase("去重", total, 0))
        val deduplicated = withContext(Dispatchers.Default) {
            val seen = mutableSetOf<String>()
            proxies.filter { proxy ->
                if (seen.contains(proxy.dedupKey)) false
                else { seen.add(proxy.dedupKey); true }
            }
        }
        emit(PipelineProgress.Phase("去重", total, total - deduplicated.size))

        // Step 2: Shuffle if enabled
        val shuffled = if (config.shuffleTestOrder) {
            deduplicated.shuffled()
        } else {
            deduplicated
        }

        // Step 3: Alive check
        emit(PipelineProgress.Phase("测活", deduplicated.size, 0))
        val aliveResults = aliveChecker.checkAlive(shuffled)
        val aliveProxies = aliveResults.filter { it.isAlive }
        emit(PipelineProgress.Phase("测活", deduplicated.size, aliveProxies.size))

        // Convert to CheckResult
        var results = aliveProxies.map { ar ->
            CheckResult(
                proxy = ar.proxy,
                isAlive = true,
                delayMs = ar.delayMs
            )
        }.also {
                emit(PipelineProgress.ResultsReady(it.size))
            }

        // Step 4: IP check (only if media check is enabled)
        if (config.mediaCheck && results.isNotEmpty()) {
            emit(PipelineProgress.Phase("IP检测", results.size, 0))
            results = ipChecker.checkIP(results)
            emit(PipelineProgress.Phase("IP检测", results.size, results.size))
        }

        // Step 5: Media check
        if (config.mediaCheck && results.isNotEmpty()) {
            emit(PipelineProgress.Phase("流媒体检测", results.size, 0))
            results = mediaChecker.checkMedia(results)
            emit(PipelineProgress.Phase("流媒体检测", results.size, results.size))
        }

        // Step 6: Speed test (only if speed test URL is provided)
        if (config.speedTestUrl.isNotBlank() && results.isNotEmpty()) {
            emit(PipelineProgress.Phase("测速", results.size, 0))
            results = speedChecker.checkSpeed(results)

            // Filter by min speed
            if (config.minSpeed > 0) {
                val before = results.size
                results = results.filter { it.speedKBps >= config.minSpeed }
                emit(PipelineProgress.Phase("测速过滤", before, before - results.size))
            } else {
                emit(PipelineProgress.Phase("测速", results.size, results.size))
            }
        }

        // Step 7: Filter by regex
        if (config.filterRegex.isNotBlank()) {
            emit(PipelineProgress.Phase("过滤", results.size, 0))
            val before = results.size
            val regex = Regex(config.filterRegex, RegexOption.IGNORE_CASE)
            results = results.filter { !regex.containsMatchIn(it.renderName()) }
            emit(PipelineProgress.Phase("过滤", before, before - results.size))
        }

        emit(PipelineProgress.Completed(results))
    }

    /**
     * Generate TXT output from results.
     */
    fun generateTxt(results: List<CheckResult>): String {
        return buildString {
            appendLine("=== Subs-Check APK - 节点检测报告 ===")
            appendLine("生成时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date())}")
            appendLine("总计节点: ${results.size}")
            appendLine("")

            // Summary
            appendLine("--- 统计 ---")
            appendLine("OpenAI: ${results.count { it.openai == "yes" }}")
            appendLine("YouTube: ${results.count { it.youtube == "yes" }}")
            appendLine("Netflix: ${results.count { it.netflix == "yes" }}")
            appendLine("Disney+: ${results.count { it.disney }}")
            appendLine("Gemini: ${results.count { it.gemini == "yes" }}")
            appendLine("Claude: ${results.count { it.claude == "yes" }}")
            appendLine("Spotify: ${results.count { it.spotify == "yes" }}")
            appendLine("TikTok: ${results.count { it.tiktok == "yes" }}")
            appendLine("")

            // Detailed node list
            appendLine("--- 节点列表 ---")
            results.forEachIndexed { index, result ->
                appendLine("${index + 1}. ${result.renderName()}")
                appendLine("   类型: ${result.proxy.type}")
                appendLine("   地址: ${result.proxy.server}:${result.proxy.port}")
                appendLine("   延迟: ${result.delayMs}ms")
                if (result.speedKBps > 0) appendLine("   速度: ${result.speedKBps} KB/s")
                if (result.ip.isNotBlank()) appendLine("   IP: ${result.ip}")
                if (result.country.isNotBlank()) appendLine("   地区: ${result.country}")
                appendLine("")
            }
        }
    }
}

/**
 * Pipeline configuration matching the Go project's config.yaml.
 */
data class PipelineConfig(
    val concurrent: Int = 20,
    val speedConcurrent: Int = 5,
    val mediaConcurrent: Int = 10,
    val shuffleTestOrder: Boolean = true,
    val aliveTestUrl: String = "http://gstatic.com/generate_204",
    val speedTestUrl: String = "",
    val timeoutMs: Long = 5000,
    val mediaTimeoutMs: Long = 10000,
    val speedTimeoutMs: Long = 15000,
    val downloadMB: Int = 10,
    val minSpeed: Int = 0,
    val filterRegex: String = "",
    val mediaCheck: Boolean = true
)

/**
 * Progress updates emitted during pipeline execution.
 */
sealed class PipelineProgress {
    data class Started(val total: Int) : PipelineProgress()
    data class Phase(val name: String, val total: Int, val processed: Int) : PipelineProgress()
    data class ResultsReady(val count: Int) : PipelineProgress()
    data class Completed(val results: List<CheckResult>) : PipelineProgress()
}
