package com.subscheck.apk

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subscheck.apk.model.AppConfig
import com.subscheck.apk.model.CheckResult
import com.subscheck.apk.model.Proxy
import com.subscheck.apk.output.OutputWriter
import com.subscheck.apk.parser.SubscriptionParser
import com.subscheck.apk.pipeline.CheckPipeline
import com.subscheck.apk.pipeline.PipelineProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

class MainViewModel : ViewModel() {

    private val _progressFlow = MutableSharedFlow<PipelineProgress>(extraBufferCapacity = 1)
    val progressFlow: SharedFlow<PipelineProgress> = _progressFlow.asSharedFlow()

    private val _logFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val logFlow: SharedFlow<String> = _logFlow.asSharedFlow()

    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    private val _saveResultFlow = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val saveResultFlow: SharedFlow<String?> = _saveResultFlow.asSharedFlow()

    var totalCount: Int = 0
    private var lastResults: List<CheckResult> = emptyList()
    private var currentJob: kotlinx.coroutines.Job? = null

    private val pipeline = CheckPipeline()

    fun runCheck(context: Context, urls: List<String>, config: AppConfig) {
        currentJob = viewModelScope.launch {
            try {
                emitLog("开始获取订阅链接...")

                // Step 1: Fetch all subscriptions
                val allProxies = fetchAllSubscriptions(context, urls)
                if (allProxies.isEmpty()) {
                    _errorFlow.emit("未解析到任何节点，请检查订阅链接")
                    return@launch
                }

                totalCount = allProxies.size
                emitLog("共解析到 $totalCount 个节点")

                // Step 2: Run the check pipeline
                emitLog("开始检测流程...")
                val checkPipeline = CheckPipeline(config.toPipelineConfig())

                checkPipeline.run(allProxies).collect { progress ->
                    _progressFlow.emit(progress)

                    when (progress) {
                        is PipelineProgress.Started -> {
                            emitLog("开始检测，共 ${progress.total} 个节点")
                        }
                        is PipelineProgress.Phase -> {
                            emitLog("阶段 [${progress.name}] ${progress.processed}/${progress.total}")
                        }
                        is PipelineProgress.Completed -> {
                            lastResults = progress.results
                            emitLog("检测完成，最终保留 ${progress.results.size} 个节点")
                        }
                        else -> {}
                    }
                }
            } catch (e: CancellationException) {
                emitLog("检测已取消")
            } catch (e: Exception) {
                _errorFlow.emit("检测出错: ${e.message}")
                Log.e("MainViewModel", "Check error", e)
            }
        }
    }

    fun cancelCheck() {
        currentJob?.cancel()
        currentJob = null
    }

    fun saveToFile(context: Context) {
        viewModelScope.launch {
            try {
                if (lastResults.isEmpty()) {
                    _errorFlow.emit("没有可保存的结果")
                    return@launch
                }

                emitLog("正在生成TXT文件...")

                val txtContent = pipeline.generateTxt(lastResults)

                val config = AppConfig.load(context)
                val fileName = if (config.fileName.isNotBlank()) {
                    if (config.fileName.endsWith(".txt", ignoreCase = true)) config.fileName else "${config.fileName}.txt"
                } else {
                    "subs-check-${getTimeStamp()}.txt"
                }

                val result = withContext(Dispatchers.IO) {
                    OutputWriter.saveToFile(context, txtContent, fileName)
                }

                if (result != null) {
                    emitLog("文件已保存: $result")
                    _saveResultFlow.emit(result)
                } else {
                    _errorFlow.emit("保存文件失败")
                }
            } catch (e: Exception) {
                _errorFlow.emit("保存出错: ${e.message}")
                Log.e("MainViewModel", "Save error", e)
            }
        }
    }

    private val mirrorPrefixes = listOf(
        "https://raw.githubusercontent.com",
        "https://gh-proxy.com/raw.githubusercontent.com",
        "https://gitproxy.click/raw.githubusercontent.com",
        "https://ghp.ci/raw.githubusercontent.com",
    )

    private suspend fun fetchAllSubscriptions(context: Context, urls: List<String>): List<Proxy> {
        val allProxies = mutableListOf<Proxy>()
        val app = context.applicationContext as SubsCheckApp
        val client = app.okHttpClient
        var successCount = 0
        var failCount = 0

        for ((index, url) in urls.withIndex()) {
            val shortUrl = if (url.length > 80) url.substring(0, 77) + "..." else url
            emitLog("[${index + 1}/${urls.size}] 获取: $shortUrl")
            var success = false

            // Build mirror URLs list
            val mirrorUrls = buildMirrorUrls(url)

            for ((mirrorIndex, mirrorUrl) in mirrorUrls.withIndex()) {
                if (success) break
                val mirrorLabel = if (mirrorIndex == 0) "原始" else "镜像${mirrorIndex}"
                emitLog("  [$mirrorLabel] 尝试连接...")

                try {
                    val request = Request.Builder()
                        .url(mirrorUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept", "*/*")
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        Log.w(TAG, "HTTP ${response.code} from $mirrorLabel")
                        continue
                    }

                    val body = response.body?.string()
                    if (body.isNullOrEmpty()) {
                        Log.w(TAG, "Empty body from $mirrorLabel")
                        continue
                    }

                    Log.i(TAG, "Fetched ${body.length} chars from $mirrorLabel")
                    val proxies = withContext(Dispatchers.Default) {
                        SubscriptionParser.parse(body)
                    }
                    if (proxies.isNotEmpty()) {
                        emitLog("  ✓ [$mirrorLabel] 成功: ${proxies.size} 个节点")
                        allProxies.addAll(proxies)
                        successCount++
                        success = true
                    } else {
                        Log.w(TAG, "0 proxies parsed from $mirrorLabel")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fetch error from $mirrorLabel: ${e.message}")
                }
            }

            if (!success) {
                emitLog("  ✗ 所有源均失败")
                failCount++
            }
        }

        emitLog("订阅源统计: 成功 $successCount, 失败 $failCount, 总节点 ${allProxies.size}")
        return allProxies
    }

    private fun buildMirrorUrls(originalUrl: String): List<String> {
        val urls = mutableListOf<String>()
        urls.add(originalUrl)

        // Try mirrors only for raw.githubusercontent.com URLs
        if (originalUrl.contains("raw.githubusercontent.com")) {
            val path = originalUrl.substringAfter("raw.githubusercontent.com")
            for (prefix in mirrorPrefixes.drop(1)) {
                urls.add("$prefix$path")
            }
        }

        return urls
    }

    private fun emitLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logFlow.tryEmit("[$timestamp] $message")
    }

    private fun getTimeStamp(): String {
        return SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        cancelCheck()
    }
}
