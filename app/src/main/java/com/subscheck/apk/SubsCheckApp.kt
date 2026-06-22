package com.subscheck.apk

import android.app.Application
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy as JavaNetProxy
import java.util.concurrent.TimeUnit

/**
 * Global application class.
 * Provides shared OkHttpClient and event bus.
 */
class SubsCheckApp : Application() {

    companion object {
        const val TAG = "SubsCheckApp"
        var proxyConfig: ProxyConfig? = null
    }

    data class ProxyConfig(val host: String, val port: Int)

    val okHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        val config = proxyConfig
        if (config != null) {
            val javaProxy = JavaNetProxy(JavaNetProxy.Type.SOCKS, InetSocketAddress(config.host, config.port))
            builder.proxy(javaProxy)
        }

        builder.build()
    }

    // Log events flow
    private val _logFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val logFlow: SharedFlow<String> = _logFlow

    fun emitLog(message: String) {
        Log.i(TAG, message)
        _logFlow.tryEmit(message)
    }
}
