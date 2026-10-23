package com.zunelo.pdfreader.telemetry

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.firebase.analytics.FirebaseAnalytics
import com.zunelo.pdfreader.BuildConfig
import com.zunelo.pdfreader.telemetry.network.OkHttpManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object SmartPdfSignalHub {
    private const val TEST_ENDPOINT = "https://test-oxide.filereadxsmartrkgiv.com/emeritus/mizar"
    private const val RELEASE_ENDPOINT = "https://oxide.filereadxsmartrkgiv.com/asbestos/likewise/prelude"
    private const val PREFERENCES = "tba_signal"
    private const val KEY_STABLE_ID = "stable_id"
    private const val KEY_INSTALL_REPORTED = "install_reported"
    private const val KEY_REFERRER_READY = "referrer_ready"
    private const val KEY_REFERRER = "install_referrer"
    private const val KEY_INSTALL_VERSION = "install_version"
    private const val KEY_CLICK_CLIENT = "click_client"
    private const val KEY_INSTALL_CLIENT = "install_client"
    private const val KEY_CLICK_SERVER = "click_server"
    private const val KEY_INSTALL_SERVER = "install_server"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val booted = AtomicBoolean(false)
    private val installWorkInFlight = AtomicBoolean(false)
    private lateinit var application: Application

    private val preferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }

    val stableId: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        preferences.getString(KEY_STABLE_ID, null)
            ?: UUID.randomUUID().toString().also {
                preferences.edit().putString(KEY_STABLE_ID, it).commit()
            }
    }

    fun boot(application: Application) {
        if (!booted.compareAndSet(false, true)) return
        this.application = application
        reportInstallIfNeeded()
    }

    fun reportSession() {
        reportInstallIfNeeded()
        upload(commonPayload().put("etc", JSONObject()))
    }

    fun recordCustomSignal(signalName: String, attributes: Map<String, Any?> = emptyMap()) {
        if (signalName.isBlank()) return
        runCatching {
            FirebaseAnalytics.getInstance(application).logEvent(signalName, attributes.toBundle())
        }
        val payload = commonPayload().put("cliche", signalName)
        attributes.forEach { (key, value) ->
            if (key.isNotBlank()) payload.put("petulant&$key", JSONObject.wrap(value))
        }
        upload(payload)
    }

    fun reportAdRevenue(
        valueMicros: Long,
        currencyCode: String,
        adNetwork: String,
        adUnitId: String,
        slotKey: String,
        adPosId: String,
        adFormat: String,
        precisionType: String
    ) {
        upload(
            commonPayload()
                .put("cliche", "leachate")
                .put("womb", valueMicros)
                .put("impulse", currencyCode.take(3))
                .put("twine", adNetwork)
                .put("diatribe", "admob")
                .put("doom", adUnitId)
                .put("emigree", slotKey)
                .put("fireman", adPosId)
                .put("softwood", adFormat)
                .put("tipple", precisionType)
        )
    }

    internal fun commonPayload(): JSONObject {
        val locale = Locale.getDefault()
        val localeTag = listOf(locale.language, locale.country)
            .filter(String::isNotBlank)
            .joinToString("_")
        return JSONObject()
            .put("film", application.packageName)
            .put("monster", "owens")
            .put("monkey", BuildConfig.VERSION_NAME)
            .put("echoes", stableId)
            .put("flaxseed", UUID.randomUUID().toString())
            .put("elgin", System.currentTimeMillis())
            .put("secede", Build.MANUFACTURER.orEmpty().lowercase(Locale.US))
            .put("racial", Build.BRAND.orEmpty())
            .put("wildlife", Build.MODEL.orEmpty())
            .put("froze", Build.VERSION.RELEASE.orEmpty())
            .put("cheap", "mcceew")
            .put("champion", localeTag)
            .put("i", stableId)
    }

    internal fun cloakPayload(): JSONObject = JSONObject()
        .put("film", application.packageName)
        .put("monster", "owens")
        .put("monkey", BuildConfig.VERSION_NAME)
        .put("echoes", stableId)
        .put("elgin", System.currentTimeMillis())

    private fun reportInstallIfNeeded() {
        if (preferences.getBoolean(KEY_INSTALL_REPORTED, false)) return
        if (!installWorkInFlight.compareAndSet(false, true)) return
        if (preferences.getBoolean(KEY_REFERRER_READY, false)) {
            uploadInstall()
            return
        }
        val client = runCatching { InstallReferrerClient.newBuilder(application).build() }
            .getOrElse {
                installWorkInFlight.set(false)
                return
            }
        val listener = object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                if (responseCode != InstallReferrerClient.InstallReferrerResponse.OK) {
                    runCatching { client.endConnection() }
                    installWorkInFlight.set(false)
                    return
                }
                val details = runCatching { client.installReferrer }.getOrNull()
                runCatching { client.endConnection() }
                if (details == null) {
                    installWorkInFlight.set(false)
                    return
                }
                preferences.edit()
                    .putBoolean(KEY_REFERRER_READY, true)
                    .putString(KEY_REFERRER, details.installReferrer.orEmpty())
                    .putString(KEY_INSTALL_VERSION, details.installVersion.orEmpty())
                    .putLong(KEY_CLICK_CLIENT, details.referrerClickTimestampSeconds)
                    .putLong(KEY_INSTALL_CLIENT, details.installBeginTimestampSeconds)
                    .putLong(KEY_CLICK_SERVER, details.referrerClickTimestampServerSeconds)
                    .putLong(KEY_INSTALL_SERVER, details.installBeginTimestampServerSeconds)
                    .commit()
                uploadInstall()
            }

            override fun onInstallReferrerServiceDisconnected() {
                installWorkInFlight.set(false)
            }
        }
        runCatching { client.startConnection(listener) }
            .onFailure {
                runCatching { client.endConnection() }
                installWorkInFlight.set(false)
            }
    }

    private fun uploadInstall() {
        val packageInfo = runCatching {
            application.packageManager.getPackageInfo(application.packageName, 0)
        }.getOrNull()
        val payload = commonPayload()
            .put("aleutian", "build/${Build.ID.orEmpty()}")
            .put("barnacle", preferences.getString(KEY_REFERRER, "").orEmpty())
            .put("landmass", preferences.getString(KEY_INSTALL_VERSION, "").orEmpty())
            .put("elevate", "wvuuu")
            .put("palermo", "chile")
            .put("generous", preferences.getLong(KEY_CLICK_CLIENT, 0L))
            .put("rhode", preferences.getLong(KEY_INSTALL_CLIENT, 0L))
            .put("twit", preferences.getLong(KEY_CLICK_SERVER, 0L))
            .put("stodgy", preferences.getLong(KEY_INSTALL_SERVER, 0L))
            .put("suction", (packageInfo?.firstInstallTime ?: 0L) / 1_000L)
            .put("khartoum", (packageInfo?.lastUpdateTime ?: 0L) / 1_000L)
        scope.launch {
            if (OkHttpManager.postJson(endpoint()) { payload.bodyForAttempt() } != null) {
                preferences.edit().putBoolean(KEY_INSTALL_REPORTED, true).apply()
            }
            installWorkInFlight.set(false)
        }
    }

    private fun upload(payload: JSONObject) {
        scope.launch { OkHttpManager.postJson(endpoint()) { payload.bodyForAttempt() } }
    }

    private fun JSONObject.bodyForAttempt(): String =
        put("elgin", System.currentTimeMillis()).toString()

    private fun Map<String, Any?>.toBundle() = Bundle().also { bundle ->
        forEach { (key, value) ->
            if (key.isBlank()) return@forEach
            when (value) {
                is Double -> bundle.putDouble(key, value)
                is Float -> bundle.putDouble(key, value.toDouble())
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Short -> bundle.putLong(key, value.toLong())
                is Byte -> bundle.putLong(key, value.toLong())
                is Boolean -> bundle.putLong(key, if (value) 1L else 0L)
                null -> Unit
                else -> bundle.putString(key, value.toString())
            }
        }
    }

    private fun endpoint(): String = if (BuildConfig.DEBUG) TEST_ENDPOINT else RELEASE_ENDPOINT
}
