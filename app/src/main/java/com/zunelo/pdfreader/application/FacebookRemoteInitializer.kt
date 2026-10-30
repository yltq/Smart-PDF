package com.zunelo.pdfreader.application

import android.content.Context
import com.facebook.FacebookSdk
import com.zunelo.pdfreader.telemetry.AdValueDispatcher
import org.json.JSONObject

object FacebookRemoteInitializer {
    const val REMOTE_KEY = "smart_fb"

    private var initializationStarted = false

    @Synchronized
    fun initialize(context: Context, rawConfig: String) {
        if (initializationStarted) return
        val credentials = parse(rawConfig) ?: return
        initializationStarted = true
        runCatching {
            FacebookSdk.setApplicationId(credentials.id)
            FacebookSdk.setClientToken(credentials.clientToken)
            FacebookSdk.sdkInitialize(context.applicationContext) {
                FacebookSdk.fullyInitialize()
                AdValueDispatcher.initializeFacebook()
            }
        }.onFailure {
            initializationStarted = false
        }
    }

    private fun parse(rawConfig: String): Credentials? = runCatching {
        val root = JSONObject(rawConfig)
        val id = root.optString("id").trim()
        val clientToken = root.optString("ct").trim()
        if (id.isEmpty() || clientToken.isEmpty()) null else Credentials(id, clientToken)
    }.getOrNull()

    private data class Credentials(
        val id: String,
        val clientToken: String
    )
}
