package com.zunelo.pdfreader.application

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.zunelo.pdfreader.ads.SmartPdfAds
import com.zunelo.pdfreader.ads.config.AdConfigRepository
import com.zunelo.pdfreader.telemetry.CloakRepository
import com.zunelo.pdfreader.telemetry.AdValueDispatcher
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SmartPdfApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mobileAdsInitialization = CompletableDeferred<Unit>()

    override fun onCreate() {
        super.onCreate()
        SmartPdfSignalHub.boot(this)
        AdValueDispatcher.initialize(this)
        CloakRepository.initialize(this)
        SmartPdfAds.initialize(this)
        applicationScope.launch {
            runCatching {
                FirebaseApp.initializeApp(this@SmartPdfApplication)
                    ?.let(AdConfigRepository::initialize)
            }
            runCatching {
                MobileAds.initialize(this@SmartPdfApplication) {
                    mobileAdsInitialization.complete(Unit)
                }
            }.onFailure { mobileAdsInitialization.complete(Unit) }
            while (isActive) {
                SmartPdfSignalHub.recordCustomSignal("session_back")
                delay(5 * 60 * 1_000L)
            }
        }
    }

    suspend fun awaitMobileAdsInitialization() {
        mobileAdsInitialization.await()
    }
}
