package com.zunelo.pdfreader.telemetry

import android.app.Application
import android.content.Context
import com.facebook.appevents.AppEventsLogger
import com.google.firebase.analytics.FirebaseAnalytics
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

object AdValueDispatcher {
    private const val PREFERENCES = "ad_value_totals"
    private const val TOTAL_USD_MICROS_KEY = "pending_usd_micros"
    private const val MICROS_PER_UNIT = 1_000_000.0
    private const val REPORT_STEP_MICROS = 10_000L
    private const val IMPRESSION_EVENT = "ad_impression_revenue"
    private const val TOTAL_EVENT = "Total_Ads_Revenue_001"
    private const val FALLBACK_CURRENCY = "USD"

    internal data class Payload(
        val valueMicros: Long,
        val currencyCode: String,
        val adNetwork: String,
        val mediationAdapter: String,
        val adUnitId: String,
        val slotKey: String,
        val adPosId: String,
        val adFormat: String,
        val precisionType: String
    )

    private val totalLock = Any()
    private lateinit var application: Application
    @Volatile
    private var facebookLogger: AppEventsLogger? = null

    fun initialize(application: Application) {
        this.application = application
        AppsFlyerTracker.initialize(application)
    }

    fun initializeFacebook() {
        if (!::application.isInitialized || facebookLogger != null) return
        facebookLogger = runCatching { AppEventsLogger.newLogger(application) }.getOrNull()
    }

    fun dispatch(
        valueMicros: Long,
        currencyCode: String,
        adNetwork: String,
        mediationAdapter: String,
        adUnitId: String,
        slotKey: String,
        adPosId: String,
        adFormat: String,
        precisionType: String
    ) {
        if (!::application.isInitialized || valueMicros < 0L) return
        val payload = Payload(
            valueMicros = valueMicros,
            currencyCode = currencyCode.normalizedCurrency(),
            adNetwork = adNetwork.trim(),
            mediationAdapter = mediationAdapter.trim(),
            adUnitId = adUnitId.trim(),
            slotKey = slotKey.trim(),
            adPosId = adPosId.trim(),
            adFormat = adFormat.trim(),
            precisionType = precisionType.trim()
        )

        runCatching { reportTbaAd(payload) }
        runCatching { reportImpressionValue(payload) }
        if (payload.valueMicros <= 0L) return
        runCatching { reportFacebookValue(payload) }
        runCatching { AppsFlyerTracker.send(payload) }
        runCatching { reportAccumulatedValue(payload) }
    }

    private fun reportTbaAd(payload: Payload) {
        SmartPdfSignalHub.reportAdRevenue(
            valueMicros = payload.valueMicros,
            currencyCode = payload.currencyCode,
            adNetwork = payload.adNetwork,
            adUnitId = payload.adUnitId,
            slotKey = payload.slotKey,
            adPosId = payload.adPosId,
            adFormat = payload.adFormat,
            precisionType = payload.precisionType
        )
    }

    private fun reportFacebookValue(payload: Payload) {
        facebookLogger?.logPurchase(
            BigDecimal.valueOf(payload.valueMicros, 6),
            Currency.getInstance(payload.currencyCode)
        )
    }

    private fun reportImpressionValue(payload: Payload) {
        publishSharedEvent(
            IMPRESSION_EVENT,
            mapOf(
                FirebaseAnalytics.Param.VALUE to payload.valueMicros / MICROS_PER_UNIT,
                FirebaseAnalytics.Param.CURRENCY to payload.currencyCode,
                "precision_type" to payload.precisionType,
                FirebaseAnalytics.Param.AD_SOURCE to payload.mediationAdapter.ifBlank { payload.adNetwork },
                FirebaseAnalytics.Param.AD_UNIT_NAME to payload.adUnitId,
                "ad_slot_key" to payload.slotKey,
                "ad_pos_id" to payload.adPosId,
                FirebaseAnalytics.Param.AD_FORMAT to payload.adFormat,
                FirebaseAnalytics.Param.AD_PLATFORM to "admob"
            )
        )
    }

    private fun reportAccumulatedValue(payload: Payload) {
        val reportableMicros = synchronized(totalLock) {
            val preferences = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val previous = preferences.getLong(TOTAL_USD_MICROS_KEY, 0L).coerceAtLeast(0L)
            val total = if (Long.MAX_VALUE - previous < payload.valueMicros) {
                Long.MAX_VALUE
            } else {
                previous + payload.valueMicros
            }
            if (total >= REPORT_STEP_MICROS) {
                preferences.edit().putLong(TOTAL_USD_MICROS_KEY, 0L).apply()
                total
            } else {
                preferences.edit().putLong(TOTAL_USD_MICROS_KEY, total).apply()
                0L
            }
        }
        if (reportableMicros <= 0L) return
        publishSharedEvent(
            TOTAL_EVENT,
            mapOf(
                FirebaseAnalytics.Param.VALUE to reportableMicros / MICROS_PER_UNIT,
                FirebaseAnalytics.Param.CURRENCY to FALLBACK_CURRENCY
            )
        )
    }

    private fun publishSharedEvent(name: String, values: Map<String, Any?>) {
        runCatching { SmartPdfSignalHub.recordCustomSignal(name, values) }
    }

    private fun String.normalizedCurrency(): String {
        val normalized = trim().uppercase(Locale.US)
        return runCatching { Currency.getInstance(normalized).currencyCode }
            .getOrDefault(FALLBACK_CURRENCY)
    }
}
