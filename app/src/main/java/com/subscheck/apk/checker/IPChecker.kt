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
import org.json.JSONObject

/**
 * Checks IP address and geographic information for proxies.
 * Uses ipinfo.io API.
 */
class IPChecker(
    private val timeoutMs: Long = 5000,
    private val maxConcurrent: Int = 10
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Check IP info for alive proxies.
     */
    suspend fun checkIP(results: List<CheckResult>): List<CheckResult> = coroutineScope {
        results.chunked(maxConcurrent).map { chunk ->
            chunk.map { result ->
                async(Dispatchers.IO) { checkSingleIP(result) }
            }.awaitAll()
        }.flatten()
    }

    private suspend fun checkSingleIP(result: CheckResult): CheckResult {
        return withTimeoutOrNull(timeoutMs) {
            try {
                val proxy = Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress(result.proxy.server, result.proxy.port)
                )
                val proxyClient = client.newBuilder()
                    .proxy(proxy)
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build()

                val request = Request.Builder()
                    .url("https://ipinfo.io/json")
                    .get().build()
                val response = proxyClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    response.close()
                    return@withTimeoutOrNull result
                }

                val body = response.body?.string()
                response.close()

                if (body != null) {
                    val json = JSONObject(body)
                    val ip = json.optString("ip", "")
                    val country = json.optString("country", "")
                    val org = json.optString("org", "")

                    // Simple risk assessment based on org
                    val ipRisk = when {
                        org.contains("datacenter", true) -> "high"
                        org.contains("hosting", true) -> "medium"
                        org.contains("residential", true) -> "low"
                        else -> "unknown"
                    }

                    result.copy(
                        ip = ip,
                        country = country,
                        ipRisk = ipRisk
                    )
                } else {
                    result
                }
            } catch (e: Exception) {
                Log.w("IPChecker", "IP check failed: ${e.message}")
                result
            }
        } ?: result
    }
}
