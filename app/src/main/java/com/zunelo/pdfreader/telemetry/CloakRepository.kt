package com.zunelo.pdfreader.telemetry

import android.app.Application
import android.content.Context
import com.zunelo.pdfreader.config.AppRuntimeConfig
import com.zunelo.pdfreader.telemetry.network.OkHttpManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object CloakRepository {
    const val BLACKLISTED = "prefab"
    const val NORMAL = "galatea"
    private const val PREFERENCES = "tba_cloak"
    private const val KEY_RESULT = "cloak_result"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requesting = AtomicBoolean(false)
    private lateinit var application: Application

    fun initialize(application: Application) {
        this.application = application
        fetchIfNeeded()
    }

    fun cachedResult(): String? = if (::application.isInitialized) {
        application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_RESULT, null)
    } else null

    fun isNormalMode(): Boolean = cachedResult() == NORMAL

    fun isBlacklisted(): Boolean = cachedResult() == BLACKLISTED

    fun fetchIfNeeded() {
        val endpoint = AppRuntimeConfig.cloakEndpoint
        if (endpoint.isBlank() || !::application.isInitialized || cachedResult() != null || !requesting.compareAndSet(false, true)) return
        scope.launch {
            val payload = SmartPdfSignalHub.cloakPayload()
            val result = OkHttpManager.postJson(endpoint, returnBodyInRelease = true) {
                payload.put("elgin", System.currentTimeMillis()).toString()
            }?.let(::parseResult)
            if (result != null) {
                application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_RESULT, result)
                    .apply()
            }
            requesting.set(false)
        }
    }

    private fun parseResult(rawResult: String): String? =
        when (rawResult.trim().removeSurrounding("\"").lowercase(Locale.US)) {
            BLACKLISTED -> BLACKLISTED
            NORMAL -> NORMAL
            else -> null
        }
}
