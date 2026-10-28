package com.zunelo.pdfreader.telemetry

import android.app.Application
import com.appsflyer.AFAdRevenueData
import com.appsflyer.AdRevenueScheme
import com.appsflyer.AppsFlyerLib
import com.appsflyer.MediationNetwork
import com.zunelo.pdfreader.BuildConfig
import com.zunelo.pdfreader.config.AppRuntimeConfig
import java.util.concurrent.atomic.AtomicBoolean

internal object AppsFlyerTracker {
    private const val MICROS_PER_UNIT = 1_000_000.0
    private val ready = AtomicBoolean(false)

    fun initialize(application: Application) {
        val devKey = AppRuntimeConfig.appsFlyerDevKey
        if (devKey.isEmpty() || !ready.compareAndSet(false, true)) return

        runCatching {
            AppsFlyerLib.getInstance().apply {
                setDebugLog(BuildConfig.DEBUG)
                init(devKey, null, application)
                setCustomerUserId(SmartPdfSignalHub.stableId)
                start(application)
                logSession(application)
            }
        }.onFailure {
            ready.set(false)
        }
    }

    fun send(payload: AdValueDispatcher.Payload) {
        if (!ready.get() || payload.valueMicros <= 0L) return
        runCatching {
            val revenue = AFAdRevenueData(
                payload.adNetwork.ifBlank { "admob" },
                MediationNetwork.GOOGLE_ADMOB,
                payload.currencyCode,
                payload.valueMicros / MICROS_PER_UNIT
            )
            AppsFlyerLib.getInstance().logAdRevenue(
                revenue,
                hashMapOf<String, Any>(
                    AdRevenueScheme.AD_UNIT to payload.adUnitId,
                    AdRevenueScheme.AD_TYPE to payload.adFormat,
                    AdRevenueScheme.PLACEMENT to payload.adPosId,
                    "ad_slot_key" to payload.slotKey
                )
            )
        }
    }
}
