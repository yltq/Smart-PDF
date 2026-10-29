package com.zunelo.pdfreader.ads.config

data class AdUnitConfig(
    val unitId: String,
    val platform: String,
    val format: String,
    val inventoryTtlSeconds: Long,
    val priority: Int
)

data class AdConfig(
    val slots: Map<String, List<AdUnitConfig>>
) {
    fun candidates(slot: String): List<AdUnitConfig> = slots[slot].orEmpty()
}

data class ReferrerRules(
    val open: Int,
    val allowList: List<String>,
    val backList: List<String>
)

data class UserRuntimeConfig(
    val adsSettings: UserAdsSettings,
    val featureSettings: UserFeatureSettings
)

data class UserAdsSettings(
    val loadMinWaitMillis: Long,
    val hotStartGapMillis: Long,
    val fullscreenGapMillis: Long,
    val homeBackAd: Boolean,
    val introFirstInterstitial: Boolean,
    val introFirstNative: Boolean,
    val homeNative: Boolean,
    val homeBanner: Boolean,
    val fileOpenInterstitial: Boolean,
    val pdfMergeInterstitial: Boolean,
    val pdfCleanInterstitial: Boolean,
    val finishNative: Boolean,
    val removeAppInterstitial: Boolean,
    val removeAppNative: Boolean,
    val nativeButtonTheme: Boolean
) {
    fun isSceneEnabled(adPosId: String): Boolean = when (adPosId) {
        "ad_open_launch" -> true
        "ad_create_int", "ad_merge_int", "ad_split_int" -> pdfMergeInterstitial
        "ad_scan_int", "ad_clean_int" -> pdfCleanInterstitial
        "ad_file_int" -> fileOpenInterstitial
        "ad_back_int" -> homeBackAd
        "new_langua_int", "new_intro_int" -> introFirstInterstitial
        "page_uninstall_int" -> removeAppInterstitial
        "ad_main_nat" -> homeNative
        "ad_merge_nat", "ad_result_nat" -> finishNative
        "new_langua_nat", "new_intro_nat" -> introFirstNative
        "page_uninstall_nat1", "page_uninstall_nat2" -> removeAppNative
        "ad_main_ban" -> homeBanner
        else -> false
    }
}

data class UserFeatureSettings(
    val screenWakeup: ScreenWakeupSettings,
    val foregroundEnabled: Boolean,
    val notificationPopupEnabled: Boolean
)

data class ScreenWakeupSettings(
    val enabled: Boolean,
    val dailyCap: Int
)

object AdSlots {
    const val LAUNCH = "ad_launch"
    const val SCAN_INTERSTITIAL = "ad_scan_int"
    const val BACK_INTERSTITIAL = "ad_back_int"
    const val MAIN_NATIVE = "ad_main_nat"
    const val SCAN_NATIVE = "ad_scan_nat"
    const val MAIN_BANNER = "ad_main_ban"

    val all = listOf(
        LAUNCH,
        SCAN_INTERSTITIAL,
        BACK_INTERSTITIAL,
        MAIN_NATIVE,
        SCAN_NATIVE,
        MAIN_BANNER
    )
}
