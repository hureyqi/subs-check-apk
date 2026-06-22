package com.subscheck.apk.checker

import android.util.Log
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
 * Checks proxy availability and measures latency.
 * Similar to the Go project's alive check logic.
 */
class AliveChecker(
    private val testUrl: String = "http://gstatic.com/generate_204",
    private val timeoutMs: Long = 5000,
    private val maxConcurrent: Int = 20
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private var cancelFlag = false

    fun requestCancel() {
        cancelFlag = true
    }

    /**
     * Check multiple proxies concurrently.
     * Returns list of (proxy, isAlive, delayMs).
     */
    suspend fun checkAlive(proxies: List<com.subscheck.apk.model.Proxy>): List<AliveResult> {
        cancelFlag = false
        return coroutineScope {
            proxies.chunked(maxConcurrent).map { chunk ->
                chunk.map { proxy ->
                    async(Dispatchers.IO) {
                        if (cancelFlag) return@async AliveResult(proxy, false, 0L)
                        checkSingle(proxy)
                    }
                }.awaitAll()
            }.flatten()
        }
    }

    private suspend fun checkSingle(proxy: com.subscheck.apk.model.Proxy): AliveResult {
        return withTimeoutOrNull(timeoutMs) {
            try {
                val startTime = System.currentTimeMillis()
                val proxyClient = client.newBuilder()
                    .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxy.server, proxy.port)))
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build()

                val request = Request.Builder().url(testUrl).get().build()
                val response = proxyClient.newCall(request).execute()

                val delay = System.currentTimeMillis() - startTime
                // 204 or 200 means alive
                val isAlive = response.code in listOf(200, 204)
                response.close()
                AliveResult(proxy, isAlive, delay)
            } catch (e: Exception) {
                // Also try HTTP proxy type
                try {
                    val startTime = System.currentTimeMillis()
                    val proxyClient = client.newBuilder()
                        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.server, proxy.port)))
                        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .build()

                    val request = Request.Builder().url(testUrl).get().build()
                    val response = proxyClient.newCall(request).execute()

                    val delay = System.currentTimeMillis() - startTime
                    val isAlive = response.code in listOf(200, 204)
                    response.close()
                    AliveResult(proxy, isAlive, delay)
                } catch (e2: Exception) {
                    AliveResult(proxy, false, 0L)
                }
            }
        } ?: AliveResult(proxy, false, 0L) // timeout
    }

    data class AliveResult(
        val proxy: com.subscheck.apk.model.Proxy,
        val isAlive: Boolean,
        val delayMs: Long
    )
}
