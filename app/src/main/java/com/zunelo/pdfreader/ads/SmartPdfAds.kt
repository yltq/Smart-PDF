package com.zunelo.pdfreader.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.ads.mediation.admob.AdMobAdapter
import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.ads.config.AdConfigRepository
import com.zunelo.pdfreader.ads.config.AdLimitPolicy
import com.zunelo.pdfreader.ads.config.AdSlots
import com.zunelo.pdfreader.ads.config.AdUnitConfig
import com.zunelo.pdfreader.guard.TestAdState
import com.zunelo.pdfreader.telemetry.AdValueDispatcher
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub
import java.util.WeakHashMap

data class AdScene(val slotKey: String, val adPosId: String)

interface PageAdRefreshable {
    fun refreshPageAds()
}

object AdScenes {
    val LAUNCH = AdScene(AdSlots.LAUNCH, "ad_open_launch")
    val CREATE_PDF = AdScene(AdSlots.SCAN_INTERSTITIAL, "ad_create_int")
    val LARGE_FILE_SCAN = AdScene(AdSlots.SCAN_INTERSTITIAL, "ad_scan_int")
    val CLEAN_FILES = AdScene(AdSlots.SCAN_INTERSTITIAL, "ad_clean_int")
    val MERGE_PDF = AdScene(AdSlots.SCAN_INTERSTITIAL, "ad_merge_int")
    val SPLIT_PDF = AdScene(AdSlots.SCAN_INTERSTITIAL, "ad_split_int")
    val FILE_ENTRY = AdScene(AdSlots.SCAN_INTERSTITIAL, "ad_file_int")
    val BACK_HOME = AdScene(AdSlots.BACK_INTERSTITIAL, "ad_back_int")
    val NEW_LANGUAGE_INTERSTITIAL = AdScene(AdSlots.SCAN_INTERSTITIAL, "new_langua_int")
    val NEW_INTRO_INTERSTITIAL = AdScene(AdSlots.BACK_INTERSTITIAL, "new_intro_int")
    val UNINSTALL_INTERSTITIAL = AdScene(AdSlots.BACK_INTERSTITIAL, "page_uninstall_int")
    val HOME_NATIVE = AdScene(AdSlots.MAIN_NATIVE, "ad_main_nat")
    val MERGE_LIST_NATIVE = AdScene(AdSlots.SCAN_NATIVE, "ad_merge_nat")
    val RESULT_NATIVE = AdScene(AdSlots.SCAN_NATIVE, "ad_result_nat")
    val NEW_LANGUAGE_NATIVE = AdScene(AdSlots.MAIN_NATIVE, "new_langua_nat")
    val NEW_INTRO_NATIVE = AdScene(AdSlots.SCAN_NATIVE, "new_intro_nat")
    val UNINSTALL_NATIVE_ONE = AdScene(AdSlots.MAIN_NATIVE, "page_uninstall_nat1")
    val UNINSTALL_NATIVE_TWO = AdScene(AdSlots.SCAN_NATIVE, "page_uninstall_nat2")
    val HOME_BANNER = AdScene(AdSlots.MAIN_BANNER, "ad_main_ban")
    val preloadOrder = listOf(
        LAUNCH,
        CREATE_PDF,
        LARGE_FILE_SCAN,
        CLEAN_FILES,
        MERGE_PDF,
        SPLIT_PDF,
        FILE_ENTRY,
        NEW_LANGUAGE_INTERSTITIAL,
        BACK_HOME,
        NEW_INTRO_INTERSTITIAL,
        UNINSTALL_INTERSTITIAL,
        HOME_NATIVE,
        NEW_LANGUAGE_NATIVE,
        UNINSTALL_NATIVE_ONE,
        MERGE_LIST_NATIVE,
        RESULT_NATIVE,
        NEW_INTRO_NATIVE,
        UNINSTALL_NATIVE_TWO
    )
}

object SmartPdfAds {
    private const val PLATFORM_ADMOB = "admob"
    private const val FORMAT_APP_OPEN = "op"
    private const val FORMAT_INTERSTITIAL = "int"
    private const val FORMAT_NATIVE = "nat"
    private const val FORMAT_BANNER = "ban"
    private data class AdExposure(
        var scene: AdScene,
        var source: String = scene.adPosId,
        var impressionAtMillis: Long = 0L
    )

    private data class CachedAd(val value: Any, val unit: AdUnitConfig, val loadedAtMillis: Long)

    private data class FullScreenSession(
        val scene: AdScene,
        val resumeTask: FullScreenResumeTask?,
        val onComplete: () -> Unit,
        var suppressContinuation: Boolean = false,
        var completed: Boolean = false
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = mutableMapOf<String, CachedAd>()
    private val loadingSlots = mutableSetOf<String>()
    private val loadCallbacks = mutableMapOf<String, MutableList<(Boolean) -> Unit>>()
    private val activeNativeAds = WeakHashMap<ViewGroup, NativeAd>()
    private val activeBanners = WeakHashMap<ViewGroup, AdView>()
    private val nativeExposures = WeakHashMap<NativeAd, AdExposure>()
    private val nativeBindTokens = WeakHashMap<ViewGroup, Any>()
    private val bannerBindTokens = WeakHashMap<ViewGroup, Any>()
    private var application: Application? = null
    private var fullScreenShowing = false
    private var activeFullScreenSession: FullScreenSession? = null
    private var lastFullscreenClosedAt = 0L
    fun initialize(application: Application) {
        this.application = application
        AdLimitPolicy.initialize(application) { clear() }
        TestAdState.initialize(application)
    }

    fun preloadAll(launchSource: String = AdScenes.LAUNCH.adPosId) = onMain {
        AdScenes.preloadOrder.forEach { scene ->
            load(scene, if (scene === AdScenes.LAUNCH) launchSource else scene.adPosId)
        }
    }

    fun load(
        scene: AdScene,
        adSource: String = scene.adPosId,
        onLoaded: ((Boolean) -> Unit)? = null
    ) = onMain {
        if (!AdLimitPolicy.canServeAds() || !canRequest(scene)) {
            onLoaded?.invoke(false)
            return@onMain
        }
        loadCached(scene, cacheableFormats(scene), adSource, onLoaded)
    }

    fun isReady(scene: AdScene): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!AdLimitPolicy.canServeAds() || !canDisplay(scene) || !canShowFullScreenNow(scene)) return false
        return peekValidCache(scene.slotKey) != null
    }

    fun isLoading(scene: AdScene): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!AdLimitPolicy.canServeAds() || !canDisplay(scene)) return false
        return scene.slotKey in loadingSlots
    }

    fun isFullScreenShowing(): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        return fullScreenShowing
    }

    fun isLaunchFullScreenShowing(): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        return activeFullScreenSession?.scene?.adPosId == AdScenes.LAUNCH.adPosId
    }

    fun prepareForStartupRestart(cancelContinuation: Boolean) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val session = activeFullScreenSession ?: return
        session.suppressContinuation = true
        val app = application ?: return
        if (cancelContinuation) {
            FullScreenResumeStore.clear(app)
        } else {
            session.resumeTask?.let { FullScreenResumeStore.save(app, it) }
                ?: FullScreenResumeStore.clear(app)
        }
    }

    fun onProviderAdActivityDestroyed() = onMain {
        activeFullScreenSession?.let(::completeFullScreenSession)
    }

    fun resetFullScreenInterval() = onMain {
        lastFullscreenClosedAt = 0L
    }

    fun isSceneEnabled(scene: AdScene): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        return AdLimitPolicy.canServeAds() && canDisplay(scene)
    }

    fun show(
        activity: Activity,
        scene: AdScene,
        adSource: String = scene.adPosId,
        chanceAlreadyReported: Boolean = false,
        resumeTask: FullScreenResumeTask? = null,
        onComplete: () -> Unit
    ) = onMain {
        if (
            !AdLimitPolicy.canServeAds() ||
            !canDisplay(scene) ||
            fullScreenShowing ||
            activity.isFinishing ||
            activity.isDestroyed
        ) {
            onComplete()
            return@onMain
        }
        if (!canShowFullScreenNow(scene)) {
            load(scene, adSource)
            onComplete()
            return@onMain
        }
        if (!chanceAlreadyReported) reportChanceIfEligible(scene, adSource)
        val cached = takeValidCache(scene.slotKey, FORMAT_APP_OPEN, FORMAT_INTERSTITIAL)
        when (val ad = cached?.value) {
            is AppOpenAd, is InterstitialAd -> showFullScreen(
                activity,
                scene,
                adSource,
                cached.unit,
                ad,
                resumeTask,
                onComplete
            )
            else -> {
                load(scene, adSource)
                onComplete()
            }
        }
    }

    fun bindNative(
        activity: Activity,
        scene: AdScene,
        container: ViewGroup,
        onResult: (Boolean) -> Unit = {}
    ) = onMain {
        val token = Any()
        nativeBindTokens[container] = token
        if (!AdLimitPolicy.canServeAds() || activity.isFinishing || activity.isDestroyed) {
            release(container)
            onResult(false)
            return@onMain
        }
        if (!canDisplay(scene)) {
            release(container)
            if (canRequest(scene)) loadCached(scene, setOf(FORMAT_NATIVE))
            onResult(false)
            return@onMain
        }
        reportChanceIfEligible(scene, scene.adPosId)
        val ready = takeValidCache(scene.slotKey, FORMAT_NATIVE)
        val readyAd = ready?.value as? NativeAd
        if (readyAd != null) {
            if (nativeBindTokens[container] === token) renderNative(container, readyAd, scene, ready.unit)
            loadCached(scene, setOf(FORMAT_NATIVE))
            onResult(true)
            return@onMain
        }
        loadCached(scene, setOf(FORMAT_NATIVE)) { loaded ->
            if (
                !loaded ||
                activity.isFinishing ||
                activity.isDestroyed ||
                nativeBindTokens[container] !== token
            ) {
                onResult(false)
                return@loadCached
            }
            if (!canDisplay(scene)) {
                release(container)
                onResult(false)
                return@loadCached
            }
            val cached = takeValidCache(scene.slotKey, FORMAT_NATIVE)
            val ad = cached?.value as? NativeAd
            if (ad == null || cached == null) onResult(false) else {
                renderNative(container, ad, scene, cached.unit)
                loadCached(scene, setOf(FORMAT_NATIVE))
                onResult(true)
            }
        }
    }

    fun bindBanner(
        activity: Activity,
        scene: AdScene,
        container: ViewGroup,
        onResult: (Boolean) -> Unit = {}
    ) = onMain {
        val token = Any()
        bannerBindTokens[container] = token
        if (!AdLimitPolicy.canServeAds() || activity.isFinishing || activity.isDestroyed) {
            release(container)
            onResult(false)
            return@onMain
        }
        if (!canDisplay(scene)) {
            release(container)
            onResult(false)
            return@onMain
        }
        reportChanceIfEligible(scene, scene.adPosId)
        loadBannerCandidate(activity, scene, container, token, candidates(scene.slotKey, FORMAT_BANNER), 0, onResult)
    }

    fun markDisplayChance(scene: AdScene, adSource: String = scene.adPosId) = onMain {
        reportChanceIfEligible(scene, adSource)
    }

    fun release(container: ViewGroup) = onMain {
        nativeBindTokens.remove(container)
        bannerBindTokens.remove(container)
        activeNativeAds.remove(container)?.let { nativeExposures.remove(it); it.destroy() }
        activeBanners.remove(container)?.destroy()
        container.removeAllViews()
        container.minimumHeight = 0
    }

    fun clear(slotKey: String? = null) = onMain {
        val removed = if (slotKey == null) cache.values.toList().also { cache.clear() }
        else listOfNotNull(cache.remove(slotKey))
        removed.forEach(::destroyIfNeeded)
        if (slotKey == null) {
            activeNativeAds.entries.toList().forEach { (container, ad) ->
                container.removeAllViews()
                nativeExposures.remove(ad)
                ad.destroy()
            }
            activeBanners.entries.toList().forEach { (container, ad) ->
                container.removeAllViews()
                container.minimumHeight = 0
                ad.destroy()
            }
            activeNativeAds.clear()
            nativeExposures.clear()
            activeBanners.clear()
            nativeBindTokens.clear()
            bannerBindTokens.clear()
            loadingSlots.clear()
            loadCallbacks.values.flatten().also { loadCallbacks.clear() }.forEach { it(false) }
        }
    }

    private fun loadCached(
        scene: AdScene,
        formats: Set<String>,
        adSource: String = scene.adPosId,
        onLoaded: ((Boolean) -> Unit)? = null
    ) {
        if (!AdLimitPolicy.canServeAds() || !canRequest(scene)) {
            onLoaded?.invoke(false)
            return
        }
        if (peekValidCache(scene.slotKey)?.unit?.format in formats) {
            onLoaded?.invoke(true)
            return
        }
        onLoaded?.let { loadCallbacks.getOrPut(scene.slotKey, ::mutableListOf).add(it) }
        if (!loadingSlots.add(scene.slotKey)) return
        val app = application
        val units = candidates(scene.slotKey).filter { it.format in formats }
        if (app == null || units.isEmpty()) {
            finishLoad(scene.slotKey, false)
            return
        }
        loadCandidate(app, scene, adSource, units, 0)
    }

    private fun loadCandidate(
        application: Application,
        scene: AdScene,
        adSource: String,
        units: List<AdUnitConfig>,
        index: Int
    ) {
        if (!AdLimitPolicy.canServeAds() || !canRequest(scene)) {
            finishLoad(scene.slotKey, false)
            return
        }
        val unit = units.getOrNull(index)
        if (unit == null) {
            finishLoad(scene.slotKey, false)
            return
        }
        val request = AdRequest.Builder().build()
        when (unit.format) {
            FORMAT_APP_OPEN -> {
                reportRequest(scene, adSource)
                AppOpenAd.load(application, unit.unitId, request, object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) = cacheLoaded(scene, adSource, unit, ad)
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        reportLoadFailure(error)
                        loadCandidate(application, scene, adSource, units, index + 1)
                    }
                })
            }
            FORMAT_INTERSTITIAL -> {
                reportRequest(scene, adSource)
                InterstitialAd.load(application, unit.unitId, request, object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) = cacheLoaded(scene, adSource, unit, ad)
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        reportLoadFailure(error)
                        loadCandidate(application, scene, adSource, units, index + 1)
                    }
                })
            }
            FORMAT_NATIVE -> {
                val exposure = AdExposure(scene, adSource)
                val loader = AdLoader.Builder(application, unit.unitId)
                    .forNativeAd { ad ->
                        nativeExposures[ad] = exposure
                        TestAdState.inspectNativeHeadline(application, ad.headline)
                        cacheLoaded(scene, adSource, unit, ad)
                    }
                    .withNativeAdOptions(NativeAdOptions.Builder().build())
                    .withAdListener(object : com.google.android.gms.ads.AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            reportLoadFailure(error)
                            loadCandidate(application, scene, adSource, units, index + 1)
                        }
                        override fun onAdImpression() = recordImpression(exposure)
                        override fun onAdClicked() = recordClick(exposure)
                    }).build()
                reportRequest(scene, adSource)
                loader.loadAd(request)
            }
            else -> loadCandidate(application, scene, adSource, units, index + 1)
        }
    }

    private fun cacheLoaded(scene: AdScene, adSource: String, unit: AdUnitConfig, ad: Any) {
        reportFill(scene, adSource)
        if (!AdLimitPolicy.canServeAds()) {
            (ad as? NativeAd)?.let { nativeExposures.remove(it); it.destroy() }
            finishLoad(scene.slotKey, false)
            return
        }
        cache.put(scene.slotKey, CachedAd(ad, unit, System.currentTimeMillis()))?.let(::destroyIfNeeded)
        finishLoad(scene.slotKey, true)
    }

    private fun finishLoad(slotKey: String, loaded: Boolean) {
        loadingSlots.remove(slotKey)
        loadCallbacks.remove(slotKey).orEmpty().forEach { it(loaded) }
    }

    private fun showFullScreen(
        activity: Activity,
        scene: AdScene,
        adSource: String,
        unit: AdUnitConfig,
        ad: Any,
        resumeTask: FullScreenResumeTask?,
        onComplete: () -> Unit
    ) {
        if (!AdLimitPolicy.canServeAds()) {
            onComplete()
            return
        }
        fullScreenShowing = true
        bindPaidEvent(ad, scene, unit)
        load(scene, adSource)
        val exposure = AdExposure(scene, adSource)
        val session = FullScreenSession(scene, resumeTask, onComplete)
        activeFullScreenSession = session
        var adShown = false
        fun complete() {
            completeFullScreenSession(session)
        }
        val callback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                adShown = true
            }
            override fun onAdDismissedFullScreenContent() {
                if (adShown) lastFullscreenClosedAt = SystemClock.elapsedRealtime()
                complete()
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                reportShowFailure(error)
                complete()
            }
            override fun onAdImpression() {
                recordImpression(exposure)
            }
            override fun onAdClicked() = recordClick(exposure)
        }
        when (ad) {
            is AppOpenAd -> {
                ad.setImmersiveMode(true)
                ad.fullScreenContentCallback = callback
                runCatching { ad.show(activity) }.onFailure { complete() }
            }
            is InterstitialAd -> {
                ad.setImmersiveMode(true)
                ad.fullScreenContentCallback = callback
                runCatching { ad.show(activity) }.onFailure { complete() }
            }
            else -> complete()
        }
    }

    private fun completeFullScreenSession(session: FullScreenSession) {
        if (session.completed) return
        session.completed = true
        if (activeFullScreenSession === session) activeFullScreenSession = null
        fullScreenShowing = false
        if (!session.suppressContinuation) session.onComplete()
    }

    private fun loadBannerCandidate(
        activity: Activity,
        scene: AdScene,
        container: ViewGroup,
        token: Any,
        units: List<AdUnitConfig>,
        index: Int,
        onResult: (Boolean) -> Unit
    ) {
        val unit = units.getOrNull(index)
        if (
            unit == null ||
            !AdLimitPolicy.canServeAds() ||
            !canDisplay(scene) ||
            activity.isFinishing ||
            activity.isDestroyed ||
            bannerBindTokens[container] !== token
        ) {
            onResult(false)
            return
        }
        val pending = AdView(activity).apply {
            adUnitId = unit.unitId
            val bannerSize = adaptiveBannerSize(activity, container)
            setAdSize(bannerSize)
            val collapsedHeight = bannerSize.getHeightInPixels(activity)
            val exposure = AdExposure(scene)
            adListener = object : com.google.android.gms.ads.AdListener() {
                override fun onAdLoaded() {
                    reportFill(scene)
                    if (
                        !AdLimitPolicy.canServeAds() ||
                        !canDisplay(scene) ||
                        bannerBindTokens[container] !== token
                    ) {
                        destroy()
                        onResult(false)
                        return
                    }
                    activeBanners.remove(container)?.destroy()
                    activeBanners[container] = this@apply
                    bindPaidEvent(this@apply, scene, unit)
                    container.removeAllViews()
                    container.minimumHeight = collapsedHeight
                    container.addView(
                        this@apply,
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, collapsedHeight, Gravity.CENTER)
                    )
                    onResult(true)
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    reportLoadFailure(error)
                    destroy()
                    loadBannerCandidate(activity, scene, container, token, units, index + 1, onResult)
                }
                override fun onAdImpression() = recordImpression(exposure)
                override fun onAdClicked() = recordClick(exposure)
            }
        }
        val collapsibleExtras = Bundle().apply { putString("collapsible", "bottom") }
        reportRequest(scene)
        pending.loadAd(
            AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, collapsibleExtras)
                .build()
        )
    }

    private fun adaptiveBannerSize(activity: Activity, container: ViewGroup): AdSize {
        val density = activity.resources.displayMetrics.density
        val width = container.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, (width / density).toInt().coerceAtLeast(1))
    }

    private fun renderNative(container: ViewGroup, nativeAd: NativeAd, scene: AdScene, unit: AdUnitConfig) {
        if (!AdLimitPolicy.canServeAds()) {
            nativeExposures.remove(nativeAd)
            nativeAd.destroy()
            container.removeAllViews()
            return
        }
        bindPaidEvent(nativeAd, scene, unit)
        nativeExposures[nativeAd]?.apply {
            this.scene = scene
            source = scene.adPosId
            impressionAtMillis = 0L
        }
        activeNativeAds.remove(container)?.let { nativeExposures.remove(it); it.destroy() }
        val adView = LayoutInflater.from(container.context).inflate(R.layout.view_smart_pdf_native_ad, container, false) as NativeAdView
        val icon = adView.findViewById<ImageView>(R.id.native_ad_icon)
        val headline = adView.findViewById<TextView>(R.id.native_ad_headline)
        val body = adView.findViewById<TextView>(R.id.native_ad_body)
        val action = adView.findViewById<TextView>(R.id.native_ad_action)
        adView.iconView = icon
        adView.headlineView = headline
        adView.bodyView = body
        adView.callToActionView = action
        headline.text = nativeAd.headline
        body.text = nativeAd.body
        body.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
        action.text = nativeAd.callToAction
        action.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        nativeAd.icon?.drawable?.let { icon.setImageDrawable(it); icon.visibility = View.VISIBLE } ?: run { icon.visibility = View.GONE }
        adView.setNativeAd(nativeAd)
        container.removeAllViews()
        container.addView(adView)
        activeNativeAds[container] = nativeAd
    }

    private fun bindPaidEvent(ad: Any, scene: AdScene, unit: AdUnitConfig) {
        val listener = com.google.android.gms.ads.OnPaidEventListener { value ->
            AdValueDispatcher.dispatch(
                valueMicros = value.valueMicros,
                currencyCode = value.currencyCode,
                adNetwork = responseInfo(ad)?.loadedAdapterResponseInfo?.adSourceName
                    ?: responseInfo(ad)?.mediationAdapterClassName.orEmpty(),
                mediationAdapter = responseInfo(ad)?.mediationAdapterClassName.orEmpty(),
                adUnitId = unit.unitId,
                slotKey = scene.slotKey,
                adPosId = scene.adPosId,
                adFormat = when (unit.format) {
                    FORMAT_APP_OPEN -> "open"
                    FORMAT_INTERSTITIAL -> "interstitial"
                    FORMAT_NATIVE -> "native"
                    FORMAT_BANNER -> "banner"
                    else -> unit.format
                },
                precisionType = when (value.precisionType) {
                    AdValue.PrecisionType.ESTIMATED -> "ESTIMATED"
                    AdValue.PrecisionType.PUBLISHER_PROVIDED -> "PUBLISHER_PROVIDED"
                    AdValue.PrecisionType.PRECISE -> "PRECISE"
                    else -> "UNKNOWN"
                }
            )
        }
        when (ad) {
            is AppOpenAd -> ad.onPaidEventListener = listener
            is InterstitialAd -> ad.onPaidEventListener = listener
            is NativeAd -> ad.setOnPaidEventListener(listener)
            is AdView -> ad.onPaidEventListener = listener
        }
    }

    private fun responseInfo(ad: Any): ResponseInfo? = when (ad) {
        is AppOpenAd -> ad.responseInfo
        is InterstitialAd -> ad.responseInfo
        is NativeAd -> ad.responseInfo
        is AdView -> ad.responseInfo
        else -> null
    }

    private fun cacheableFormats(scene: AdScene): Set<String> = when (scene.slotKey) {
        AdSlots.LAUNCH -> setOf(FORMAT_APP_OPEN, FORMAT_INTERSTITIAL)
        AdSlots.SCAN_INTERSTITIAL, AdSlots.BACK_INTERSTITIAL -> setOf(FORMAT_INTERSTITIAL)
        AdSlots.MAIN_NATIVE, AdSlots.SCAN_NATIVE -> setOf(FORMAT_NATIVE)
        else -> emptySet()
    }

    private fun candidates(slotKey: String, format: String? = null): List<AdUnitConfig> =
        AdConfigRepository.candidates(slotKey).filter {
            it.unitId.isNotBlank() && it.platform == PLATFORM_ADMOB && (format == null || it.format == format)
        }

    private fun takeValidCache(slotKey: String, vararg formats: String): CachedAd? {
        val cached = peekValidCache(slotKey) ?: return null
        if (cached.unit.format !in formats) return null
        cache.remove(slotKey)
        return cached
    }

    private fun peekValidCache(slotKey: String): CachedAd? {
        val cached = cache[slotKey] ?: return null
        val configured = candidates(slotKey, cached.unit.format).any { it.unitId == cached.unit.unitId }
        val ttl = cached.unit.inventoryTtlSeconds * 1_000L
        if (configured && ttl > 0L && System.currentTimeMillis() - cached.loadedAtMillis < ttl) return cached
        cache.remove(slotKey)
        destroyIfNeeded(cached)
        return null
    }

    private fun destroyIfNeeded(cached: CachedAd) {
        (cached.value as? NativeAd)?.let { nativeExposures.remove(it); it.destroy() }
    }

    private fun reportRequest(scene: AdScene, source: String = scene.adPosId) = SmartPdfSignalHub.recordCustomSignal(
        "app_ad_request",
        sceneProperties(scene, source)
    )

    private fun reportFill(scene: AdScene, source: String = scene.adPosId) = SmartPdfSignalHub.recordCustomSignal(
        "app_ad_fill",
        sceneProperties(scene, source)
    )

    private fun reportChanceIfEligible(scene: AdScene, source: String) {
        if (!AdLimitPolicy.canServeAds() || !canDisplay(scene) || candidates(scene.slotKey).isEmpty()) return
        SmartPdfSignalHub.recordCustomSignal("app_ad_chance", sceneProperties(scene, source))
    }

    private fun recordImpression(exposure: AdExposure) {
        exposure.impressionAtMillis = SystemClock.elapsedRealtime()
        AdLimitPolicy.recordImpression()
        SmartPdfSignalHub.recordCustomSignal(
            "app_ad_impression",
            sceneProperties(exposure.scene, exposure.source)
        )
    }

    private fun recordClick(exposure: AdExposure) {
        val clickedAt = SystemClock.elapsedRealtime()
        val elapsed = if (exposure.impressionAtMillis > 0L) {
            (clickedAt - exposure.impressionAtMillis).coerceAtLeast(0L)
        } else {
            0L
        }
        AdLimitPolicy.recordClick()
        SmartPdfSignalHub.recordCustomSignal(
            "app_ad_impression_click",
            sceneProperties(exposure.scene, exposure.source) + ("show_to_click_s" to elapsed)
        )
    }

    private fun reportLoadFailure(error: LoadAdError) = SmartPdfSignalHub.recordCustomSignal(
        "ad_load_failed",
        mapOf("reason" to failureReason(error.code, error.message))
    )

    private fun reportShowFailure(error: com.google.android.gms.ads.AdError) = SmartPdfSignalHub.recordCustomSignal(
        "ad_show_failed",
        mapOf("reason" to failureReason(error.code, error.message))
    )

    private fun failureReason(code: Int, message: String): String = "$code:$message".take(100)

    private fun sceneProperties(scene: AdScene, source: String) = mapOf(
        "ad_pos_id" to scene.adPosId,
        "ad_source" to source.ifBlank { scene.adPosId }
    )

    private fun canRequest(scene: AdScene): Boolean {
        val app = application ?: return false
        if (!AdConfigRepository.isAdSceneBlocked(app, scene.adPosId)) return true
        return isNativeScene(scene) && TestAdState.decision(app) == null
    }

    private fun canDisplay(scene: AdScene): Boolean {
        val app = application ?: return false
        return !AdConfigRepository.isAdSceneBlocked(app, scene.adPosId)
    }

    private fun canShowFullScreenNow(scene: AdScene): Boolean {
        if (
            !isFullScreenScene(scene) ||
            scene.adPosId == AdScenes.LAUNCH.adPosId ||
            lastFullscreenClosedAt == 0L
        ) return true
        val app = application ?: return false
        val gapMillis = AdConfigRepository.userConfig(app)
            .adsSettings
            .fullscreenGapMillis
            .coerceAtLeast(0L)
        return SystemClock.elapsedRealtime() - lastFullscreenClosedAt >= gapMillis
    }

    private fun isFullScreenScene(scene: AdScene): Boolean = scene.slotKey == AdSlots.LAUNCH ||
        scene.slotKey == AdSlots.SCAN_INTERSTITIAL ||
        scene.slotKey == AdSlots.BACK_INTERSTITIAL

    private fun isNativeScene(scene: AdScene): Boolean =
        scene.slotKey == AdSlots.MAIN_NATIVE || scene.slotKey == AdSlots.SCAN_NATIVE

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }
}
