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
