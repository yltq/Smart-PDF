package com.zunelo.pdfreader.ads.config

import android.text.TextUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object AdConfigRepository {
    private const val REMOTE_KEY = "ad_config"

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

    fun current(): AdConfig = mutableConfig.value

    fun candidates(slot: String): List<AdUnitConfig> = current().candidates(slot)

    fun initialize(firebaseApp: FirebaseApp) {
        val remoteConfig = FirebaseRemoteConfig.getInstance(firebaseApp)
        remoteConfig.setDefaultsAsync(
            mapOf(
                REMOTE_KEY to localConfig.toJson(),
                AdLimitPolicy.REMOTE_KEY to AdLimitPolicy.defaultJson()
            )
        )
            .continueWithTask { remoteConfig.fetchAndActivate() }
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) return@addOnCompleteListener
                parse(remoteConfig.getString(REMOTE_KEY))?.let { mutableConfig.value = it }
                AdLimitPolicy.applyRemote(remoteConfig.getString(AdLimitPolicy.REMOTE_KEY))
            }
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

    private fun unit(unitId: String, format: String, ttlSeconds: Long, priority: Int = 3) = AdUnitConfig(
        unitId = unitId,
        platform = "admob",
        format = format,
        inventoryTtlSeconds = ttlSeconds,
        priority = priority
    )
}
