package com.subscheck.apk.checker

import android.util.Log
import com.subscheck.apk.model.CheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Measures proxy download speed.
 * Similar to the Go project's speed test logic.
 */
class SpeedChecker(
    private val speedTestUrl: String = "http://cachefly.cachefly.net/10mb.test",
    private val timeoutMs: Long = 15000,
    private val maxConcurrent: Int = 5,
    private val downloadMB: Int = 10
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val downloadBytes = downloadMB * 1024 * 1024L

    /**
     * Check speed for filtered proxies.
     * Returns speed in KB/s.
     */
    suspend fun checkSpeed(results: List<CheckResult>): List<CheckResult> = coroutineScope {
        results.chunked(maxConcurrent).map { chunk ->
            chunk.map { result ->
                async(Dispatchers.IO) { checkSingleSpeed(result) }
            }.awaitAll()
        }.flatten()
    }

    private suspend fun checkSingleSpeed(result: CheckResult): CheckResult {
        return withTimeoutOrNull(timeoutMs) {
            try {
                val proxy = Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress(result.proxy.server, result.proxy.port)
                )
                val proxyClient = client.newBuilder()
                    .proxy(proxy)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build()

                val startTime = System.currentTimeMillis()
                val request = Request.Builder().url(speedTestUrl).get().build()
                val response = proxyClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    response.close()
                    return@withTimeoutOrNull result.copy(speedKBps = 0)
                }

                val body = response.body ?: run {
                    response.close()
                    return@withTimeoutOrNull result.copy(speedKBps = 0)
                }

                var totalBytes = 0L
                val buffer = ByteArray(8192)
                val inputStream = body.byteStream()
                var bytesRead: Int

                try {
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        totalBytes += bytesRead
                        if (totalBytes >= downloadBytes) break
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed >= timeoutMs) break
                    }
                } finally {
                    inputStream.close()
                    response.close()
                }

                val elapsed = System.currentTimeMillis() - startTime
                val speedKBps = if (elapsed > 0) {
                    (totalBytes * 1000L / elapsed / 1024).toInt()
                } else 0

                result.copy(speedKBps = speedKBps)
            } catch (e: Exception) {
                Log.w("SpeedChecker", "Speed check failed: ${e.message}")
                result.copy(speedKBps = 0)
            }
        } ?: result.copy(speedKBps = 0) // timeout
    }
}
