package com.subscheck.apk

import android.app.Application
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Global application class.
 * Provides shared OkHttpClient and event bus.
 */
class SubsCheckApp : Application() {

    companion object {
        const val TAG = "SubsCheckApp"
    }

    val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // Log events flow
    private val _logFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val logFlow: SharedFlow<String> = _logFlow

    fun emitLog(message: String) {
        Log.i(TAG, message)
        _logFlow.tryEmit(message)
    }
}
