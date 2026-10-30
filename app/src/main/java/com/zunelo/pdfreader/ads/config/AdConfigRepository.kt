package com.zunelo.pdfreader.ads.config

import android.content.Context
import android.text.TextUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.zunelo.pdfreader.application.FacebookRemoteInitializer
import com.zunelo.pdfreader.common.AppLog
import com.zunelo.pdfreader.guard.AcquisitionGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object AdConfigRepository {
    private const val REMOTE_KEY = "ad_config"
    private const val REFERRER_REMOTE_KEY = "ad_gbs_ref"
    private const val PAID_USER_REMOTE_KEY = "yfv_user"
    private const val ORGANIC_USER_REMOTE_KEY = "vfa_user"

    private const val TEST_APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_NATIVE_ID = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/2014213617"

    private val localConfig = AdConfig(
        slots = mapOf(
            AdSlots.LAUNCH to listOf(
                unit(TEST_APP_OPEN_ID, "op", 13_800L, priority = 3),
                unit(TEST_INTERSTITIAL_ID, "int", 3_000L, priority = 2)
            ),
            AdSlots.SCAN_INTERSTITIAL to listOf(unit(TEST_INTERSTITIAL_ID, "int", 3_000L)),
            AdSlots.BACK_INTERSTITIAL to listOf(unit(TEST_INTERSTITIAL_ID, "int", 3_000L)),
            AdSlots.MAIN_NATIVE to listOf(unit(TEST_NATIVE_ID, "nat", 3_000L)),
            AdSlots.SCAN_NATIVE to listOf(unit(TEST_NATIVE_ID, "nat", 3_000L)),
            AdSlots.MAIN_BANNER to listOf(unit(TEST_BANNER_ID, "ban", 3_000L))
        )
    )

    private val mutableConfig = MutableStateFlow(localConfig)
    val config: StateFlow<AdConfig> = mutableConfig.asStateFlow()

    private val localPaidUserConfig = UserRuntimeConfig(
        adsSettings = UserAdsSettings(
            loadMinWaitMillis = 1_000L,
            hotStartGapMillis = 1_000L,
            fullscreenGapMillis = 3_000L,
            homeBackAd = true,
            introFirstInterstitial = true,
            introFirstNative = true,
            homeNative = true,
            homeBanner = true,
            fileOpenInterstitial = true,
            pdfMergeInterstitial = true,
            pdfCleanInterstitial = true,
            finishNative = true,
            removeAppInterstitial = true,
            removeAppNative = true,
            nativeButtonTheme = true
        ),
        featureSettings = UserFeatureSettings(
            screenWakeup = ScreenWakeupSettings(enabled = true, dailyCap = 30),
            foregroundEnabled = true,
            notificationPopupEnabled = true
        )
    )

    private val localOrganicUserConfig = UserRuntimeConfig(
        adsSettings = UserAdsSettings(
            loadMinWaitMillis = 3_000L,
            hotStartGapMillis = 3_000L,
            fullscreenGapMillis = 5_000L,
            homeBackAd = false,
            introFirstInterstitial = false,
            introFirstNative = false,
            homeNative = false,
            homeBanner = false,
            fileOpenInterstitial = false,
            pdfMergeInterstitial = false,
            pdfCleanInterstitial = false,
            finishNative = false,
            removeAppInterstitial = false,
            removeAppNative = false,
            nativeButtonTheme = false
        ),
        featureSettings = UserFeatureSettings(
            screenWakeup = ScreenWakeupSettings(enabled = false, dailyCap = 30),
            foregroundEnabled = false,
            notificationPopupEnabled = false
        )
    )

    @Volatile
    private var currentPaidUserConfig = localPaidUserConfig

    @Volatile
    private var currentOrganicUserConfig = localOrganicUserConfig

    @Volatile
    private var currentReferrerRules = ReferrerRules(
        open = 1,
        allowList = listOf("gad_source", "wcA", "wcB", "BwE", ""),
        backList = listOf("gclid=123456789")
    )

    fun current(): AdConfig = mutableConfig.value

    fun candidates(slot: String): List<AdUnitConfig> = current().candidates(slot)

    fun referrerRules(): ReferrerRules = currentReferrerRules

    fun userConfig(context: Context): UserRuntimeConfig =
        if (AcquisitionGuard.isPaidAcquisitionUser(context.applicationContext)) {
            currentPaidUserConfig
        } else {
            currentOrganicUserConfig
        }

    fun isAdSceneBlocked(context: Context, adPosId: String): Boolean {
        if (adPosId == "ad_open_launch") return false
        return !userConfig(context).adsSettings.isSceneEnabled(adPosId)
    }

    fun initialize(firebaseApp: FirebaseApp) {
        val remoteConfig = FirebaseRemoteConfig.getInstance(firebaseApp)
        remoteConfig.setDefaultsAsync(
            mapOf(
                REMOTE_KEY to localConfig.toJson(),
                REFERRER_REMOTE_KEY to currentReferrerRules.toJson(),
                PAID_USER_REMOTE_KEY to localPaidUserConfig.toJson(),
                ORGANIC_USER_REMOTE_KEY to localOrganicUserConfig.toJson(),
                AdLimitPolicy.REMOTE_KEY to AdLimitPolicy.defaultJson()
            )
        )
            .continueWithTask { remoteConfig.fetchAndActivate() }
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    AppLog.debug {
                        "Firebase Remote Config fetch failed: ${task.exception?.message.orEmpty()}"
                    }
                    currentPaidUserConfig = localPaidUserConfig
                    currentOrganicUserConfig = localOrganicUserConfig
                    return@addOnCompleteListener
                }
                val adConfig = remoteConfig.loggedValue(REMOTE_KEY)
                val referrerConfig = remoteConfig.loggedValue(REFERRER_REMOTE_KEY)
                val paidUserConfig = remoteConfig.loggedValue(PAID_USER_REMOTE_KEY)
                val organicUserConfig = remoteConfig.loggedValue(ORGANIC_USER_REMOTE_KEY)
                val adLimitConfig = remoteConfig.loggedValue(AdLimitPolicy.REMOTE_KEY)
                val facebookConfig = remoteConfig.loggedValue(FacebookRemoteInitializer.REMOTE_KEY)

                parse(adConfig)?.let { mutableConfig.value = it }
                parseReferrerRules(referrerConfig)?.let {
                    currentReferrerRules = it
                }
                currentPaidUserConfig = parseUserConfig(
                    paidUserConfig,
                    localPaidUserConfig
                ) ?: localPaidUserConfig
                currentOrganicUserConfig = parseUserConfig(
                    organicUserConfig,
                    localOrganicUserConfig
                ) ?: localOrganicUserConfig
                AdLimitPolicy.applyRemote(adLimitConfig)
                FacebookRemoteInitializer.initialize(firebaseApp.applicationContext, facebookConfig)
            }
    }

    private fun FirebaseRemoteConfig.loggedValue(key: String): String = getString(key).also { value ->
        AppLog.debug { "Firebase Remote Config[$key]: $value" }
    }

    private fun parse(rawConfig: String): AdConfig? {
        if (TextUtils.isEmpty(rawConfig)) return null
        return runCatching {
            val root = JSONObject(rawConfig)
            val slots = AdSlots.all.associateWith { slot ->
                val candidates = root.getJSONArray(slot)
                buildList {
                    for (index in 0 until candidates.length()) {
                        val item = candidates.getJSONObject(index)
                        add(
                            AdUnitConfig(
                                unitId = item.getString("kmp").trim(),
                                platform = item.getString("rxt").trim().lowercase(),
                                format = item.getString("vqn").trim().lowercase(),
                                inventoryTtlSeconds = item.getLong("zlp").coerceAtLeast(0L),
                                priority = item.getInt("bwj")
                            )
                        )
                    }
                    sortByDescending(AdUnitConfig::priority)
                }
            }
            AdConfig(slots)
        }.getOrNull()
    }

    private fun AdConfig.toJson(): String = JSONObject().apply {
        slots.forEach { (slot, candidates) ->
            put(slot, candidates.map { candidate ->
                mapOf(
                    "kmp" to candidate.unitId,
                    "rxt" to candidate.platform,
                    "vqn" to candidate.format,
                    "zlp" to candidate.inventoryTtlSeconds,
                    "bwj" to candidate.priority
                )
            })
        }
    }.toString()

    private fun parseReferrerRules(rawConfig: String): ReferrerRules? {
        if (TextUtils.isEmpty(rawConfig)) return null
        return runCatching {
            val root = JSONObject(rawConfig)
            val rule = root.optJSONObject("user_re") ?: JSONObject()
            ReferrerRules(
                open = if (rule.optInt("open", 1) == 0) 0 else 1,
                allowList = rule.optJSONArray("list").strings(),
                backList = root.optJSONArray("back").strings()
            )
        }.getOrNull()
    }

    private fun ReferrerRules.toJson(): String = JSONObject().apply {
        put(
            "user_re",
            JSONObject().apply {
                put("open", open)
                put("list", JSONArray(allowList))
            }
        )
        put("back", JSONArray(backList))
    }.toString()

    private fun parseUserConfig(rawConfig: String, fallback: UserRuntimeConfig): UserRuntimeConfig? {
        if (TextUtils.isEmpty(rawConfig)) return null
        return runCatching {
            val root = JSONObject(rawConfig)
            val ads = root.optJSONObject("ads_settings")
            val features = root.optJSONObject("feature_settings")
            val wakeup = features?.optJSONObject("screen_wakeup")
            val fallbackAds = fallback.adsSettings
            val fallbackFeatures = fallback.featureSettings
            UserRuntimeConfig(
                adsSettings = UserAdsSettings(
                    loadMinWaitMillis = ads.longValue("load_min_wait", fallbackAds.loadMinWaitMillis),
                    hotStartGapMillis = ads.longValue("hot_start_gap", fallbackAds.hotStartGapMillis),
                    fullscreenGapMillis = ads.longValue("fullscreen_gap", fallbackAds.fullscreenGapMillis),
                    homeBackAd = ads.switchValue("home_back_ad", fallbackAds.homeBackAd),
                    introFirstInterstitial = ads.switchValue(
                        "intro_first_int",
                        fallbackAds.introFirstInterstitial
                    ),
                    introFirstNative = ads.switchValue("intro_first_nat", fallbackAds.introFirstNative),
                    homeNative = ads.switchValue("home_native", fallbackAds.homeNative),
                    homeBanner = ads.switchValue("home_banner", fallbackAds.homeBanner),
                    fileOpenInterstitial = ads.switchValue(
                        "file_open_int",
                        fallbackAds.fileOpenInterstitial
                    ),
                    pdfMergeInterstitial = ads.switchValue(
                        "pdf_merge_int",
                        fallbackAds.pdfMergeInterstitial
                    ),
                    pdfCleanInterstitial = ads.switchValue(
                        "pdf_clean_int",
                        fallbackAds.pdfCleanInterstitial
                    ),
                    finishNative = ads.switchValue("finish_native", fallbackAds.finishNative),
                    removeAppInterstitial = ads.switchValue(
                        "remove_app_int",
                        fallbackAds.removeAppInterstitial
                    ),
                    removeAppNative = ads.switchValue("remove_app_nat", fallbackAds.removeAppNative),
                    nativeButtonTheme = ads.switchValue(
                        "native_btn_theme",
                        fallbackAds.nativeButtonTheme
                    )
                ),
                featureSettings = UserFeatureSettings(
                    screenWakeup = ScreenWakeupSettings(
                        enabled = wakeup.switchValue(
                            "wake_enable",
                            fallbackFeatures.screenWakeup.enabled
                        ),
                        dailyCap = wakeup.intValue(
                            "wake_day_cap",
                            fallbackFeatures.screenWakeup.dailyCap
                        )
                    ),
                    foregroundEnabled = features.switchValue(
                        "foreground_enable",
                        fallbackFeatures.foregroundEnabled
                    ),
                    notificationPopupEnabled = features.switchValue(
                        "notify_popup",
                        fallbackFeatures.notificationPopupEnabled
                    )
                )
            )
        }.getOrNull()
    }

    private fun UserRuntimeConfig.toJson(): String = JSONObject().apply {
        put("ads_settings", JSONObject().apply {
            with(adsSettings) {
                put("load_min_wait", loadMinWaitMillis)
                put("hot_start_gap", hotStartGapMillis)
                put("fullscreen_gap", fullscreenGapMillis)
                put("home_back_ad", homeBackAd.asInt())
                put("intro_first_int", introFirstInterstitial.asInt())
                put("intro_first_nat", introFirstNative.asInt())
                put("home_native", homeNative.asInt())
                put("home_banner", homeBanner.asInt())
                put("file_open_int", fileOpenInterstitial.asInt())
                put("pdf_merge_int", pdfMergeInterstitial.asInt())
                put("pdf_clean_int", pdfCleanInterstitial.asInt())
                put("finish_native", finishNative.asInt())
                put("remove_app_int", removeAppInterstitial.asInt())
                put("remove_app_nat", removeAppNative.asInt())
                put("native_btn_theme", nativeButtonTheme.asInt())
            }
        })
        put("feature_settings", JSONObject().apply {
            put("screen_wakeup", JSONObject().apply {
                put("wake_enable", featureSettings.screenWakeup.enabled.asInt())
                put("wake_day_cap", featureSettings.screenWakeup.dailyCap)
            })
            put("foreground_enable", featureSettings.foregroundEnabled.asInt())
            put("notify_popup", featureSettings.notificationPopupEnabled.asInt())
        })
    }.toString()

    private fun JSONObject?.switchValue(key: String, fallback: Boolean): Boolean {
        if (this == null || !has(key) || isNull(key)) return fallback
        return when (optInt(key, -1)) {
            0 -> false
            1 -> true
            else -> fallback
        }
    }

    private fun JSONObject?.longValue(key: String, fallback: Long): Long {
        if (this == null || !has(key) || isNull(key)) return fallback
        return optLong(key, fallback).coerceAtLeast(0L)
    }

    private fun JSONObject?.intValue(key: String, fallback: Int): Int {
        if (this == null || !has(key) || isNull(key)) return fallback
        return optInt(key, fallback).coerceAtLeast(0)
    }

    private fun Boolean.asInt(): Int = if (this) 1 else 0

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
    }

    private fun unit(unitId: String, format: String, ttlSeconds: Long, priority: Int = 3) = AdUnitConfig(
        unitId = unitId,
        platform = "admob",
        format = format,
        inventoryTtlSeconds = ttlSeconds,
        priority = priority
    )
}
