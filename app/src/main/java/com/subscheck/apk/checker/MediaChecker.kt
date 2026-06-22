package com.subscheck.apk.checker

import android.util.Log
import com.subscheck.apk.model.CheckResult
import com.subscheck.apk.model.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Checks streaming media platform unlock status.
 * Mirrors the Go project's check/platform logic.
 */
class MediaChecker(
    private val timeoutMs: Long = 5000,
    private val maxConcurrent: Int = 10
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * Check media unlock status for alive proxies.
     */
    suspend fun checkMedia(results: List<CheckResult>): List<CheckResult> = coroutineScope {
        results.chunked(maxConcurrent).map { chunk ->
            chunk.map { result ->
                async(Dispatchers.IO) { checkSingleMedia(result) }
            }.awaitAll()
        }.flatten()
    }

    private suspend fun checkSingleMedia(result: CheckResult): CheckResult {
        val proxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress(result.proxy.server, result.proxy.port)
        )
        val proxyClient = client.newBuilder()
            .proxy(proxy)
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        var openai: String? = null
        var youtube: String? = null
        var netflix: String? = null
        var disney = false
        var google = false
        var cloudflare = false
        var gemini: String? = null
        var tiktok: String? = null
        var claude: String? = null
        var spotify: String? = null

        // Google check
        try {
            val resp = proxyClient.newCall(
                Request.Builder().url("http://www.google.com/generate_204").get().build()
            ).execute()
            google = resp.code == 204
            resp.close()
        } catch (_: Exception) { }

        // Cloudflare check
        try {
            val resp = proxyClient.newCall(
                Request.Builder().url("http://www.cloudflare.com/cdn-cgi/trace").get().build()
            ).execute()
            cloudflare = resp.isSuccessful
            resp.close()
        } catch (_: Exception) { }

        // Netflix check
        try {
            val resp = proxyClient.newCall(
                Request.Builder()
                    .url("https://www.netflix.com/title/81280792")
                    .header("User-Agent", "Mozilla/5.0")
                    .get().build()
            ).execute()
            netflix = when {
                !resp.isSuccessful -> "no"
                resp.code == 404 -> "yes" // has Netflix but title not found = accessible
                else -> {
                    val body = resp.body?.string() ?: ""
                    if (body.contains("Sorry, no titles matched")) "no"
                    else "yes"
                }
            }
            resp.close()
        } catch (_: Exception) {
            netflix = "no"
        }

        // YouTube check
        try {
            val resp = proxyClient.newCall(
                Request.Builder()
                    .url("https://www.youtube.com/premium")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept-Language", "en")
                    .get().build()
            ).execute()
            val body = resp.body?.string() ?: ""
            youtube = when {
                !resp.isSuccessful -> "no"
                body.contains("isn't available in your country") -> "no"
                else -> "yes"
            }
            resp.close()
        } catch (_: Exception) {
            youtube = "no"
        }

        // Disney+ check
        try {
            val resp = proxyClient.newCall(
                Request.Builder()
                    .url("https://disney.api.edge.bamgrid.com/devices")
                    .header("Authorization", "ZGlzbmV5JmJyb3dzZXImMS4wLjA.Cu56AgSfBTDag5NiRA81oLHkDZfu5L3CKadnefEAY84")
                    .header("Content-Type", "application/json")
                    .post("""{"applicationRuntime":"chrome","deviceFamily":"browser","deviceProfile":"windows"}""".toRequestBody("application/json".toMediaType())).build()
            ).execute()
            disney = resp.isSuccessful
            resp.close()
        } catch (_: Exception) { }

        // OpenAI check
        try {
            val resp = proxyClient.newCall(
                Request.Builder()
                    .url("https://chat.openai.com")
                    .header("User-Agent", "Mozilla/5.0")
                    .get().build()
            ).execute()
            val body = resp.body?.string() ?: ""
            openai = when {
                !resp.isSuccessful -> "no"
                body.contains("You are using OpenAI in an unsupported region") -> "no"
                body.contains("VPN") -> "no"
                else -> "yes"
            }
            resp.close()
        } catch (_: Exception) {
            openai = "no"
        }

        // Gemini check
        try {
            val resp = proxyClient.newCall(
                Request.Builder()
                    .url("https://gemini.google.com")
                    .header("User-Agent", "Mozilla/5.0")
                    .get().build()
            ).execute()
            val body = resp.body?.string() ?: ""
            gemini = when {
                !resp.isSuccessful -> "no"
                body.contains("not available") -> "no"
                else -> "yes"
            }
            resp.close()
        } catch (_: Exception) {
            gemini = "no"
        }

        // TikTok check
        try {
            val resp = proxyClient.newCall(
                Request.Builder()
                    .url("https://www.tiktok.com")
                    .header("User-Agent", "Mozilla/5.0")
                    .get().build()
            ).execute()
            val body = resp.body?.string() ?: ""
            tiktok = when {
                !resp.isSuccessful -> "no"
                body.contains("region lock") || body.contains("not available") -> "no"
                else -> "yes"
            }
            resp.close()
        } catch (_: Exception) {
            tiktok = "no"
        }

        // Claude check
        try {
            val resp = proxyClient.newCall(
                Request.Builder()
                    .url("https://claude.ai")
                    .header("User-Agent", "Mozilla/5.0")
                    .get().build()
            ).execute()
            val body = resp.body?.string() ?: ""
            claude = when {
                !resp.isSuccessful -> "no"
                body.contains("not available") -> "no"
                else -> "yes"
            }
            resp.close()
        } catch (_: Exception) {
            claude = "no"
        }

        // Spotify check
        try {
            val resp = proxyClient.newCall(
                Request.Builder()
                    .url("https://www.spotify.com")
                    .header("User-Agent", "Mozilla/5.0")
                    .get().build()
            ).execute()
            spotify = if (resp.isSuccessful) "yes" else "no"
            resp.close()
        } catch (_: Exception) {
            spotify = "no"
        }

        return result.copy(
            openai = openai,
            youtube = youtube,
            netflix = netflix,
            disney = disney,
            google = google,
            cloudflare = cloudflare,
            gemini = gemini,
            tiktok = tiktok,
            claude = claude,
            spotify = spotify
        )
    }
}
