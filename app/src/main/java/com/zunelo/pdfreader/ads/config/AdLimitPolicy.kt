package com.zunelo.pdfreader.ads.config

import android.app.Application
import android.content.SharedPreferences
import android.text.TextUtils
import org.json.JSONObject
import java.time.LocalDate

data class AdLimitConfig(
    val showLimit: Int,
    val clickLimit: Int
)

object AdLimitPolicy {
    const val REMOTE_KEY = "app_ad_limit"
    private const val PREFERENCES = "daily_ad_limit"
    private const val KEY_DAY = "day"
    private const val KEY_SHOW_COUNT = "show_count"
    private const val KEY_CLICK_COUNT = "click_count"
    private val defaultConfig = AdLimitConfig(showLimit = 100, clickLimit = 100)
    private val lock = Any()

    @Volatile
    private var config = defaultConfig
    private var preferences: SharedPreferences? = null
    private var day = ""
    private var showCount = 0
    private var clickCount = 0
    private var onLimitReached: (() -> Unit)? = null

    fun initialize(application: Application, onLimitReached: () -> Unit) {
        synchronized(lock) {
            preferences = application.getSharedPreferences(PREFERENCES, 0)
            this.onLimitReached = onLimitReached
            readCountersLocked()
            resetForNewDayLocked()
        }
        if (!canServeAds()) onLimitReached()
    }

    fun defaultJson(): String = JSONObject().apply {
        put("ad_show_limit", defaultConfig.showLimit)
        put("ad_click_limit", defaultConfig.clickLimit)
    }.toString()

    fun applyRemote(rawConfig: String) {
        val updated = parse(rawConfig) ?: defaultConfig
        val becameBlocked = synchronized(lock) {
            resetForNewDayLocked()
            val wasBlocked = isBlockedLocked()
            config = updated
            !wasBlocked && isBlockedLocked()
        }
        if (becameBlocked) onLimitReached?.invoke()
    }

    fun canServeAds(): Boolean = synchronized(lock) {
        resetForNewDayLocked()
        !isBlockedLocked()
    }

    fun recordImpression() = record(KEY_SHOW_COUNT)

    fun recordClick() = record(KEY_CLICK_COUNT)

    private fun record(counterKey: String) {
        val becameBlocked = synchronized(lock) {
            resetForNewDayLocked()
            val wasBlocked = isBlockedLocked()
            when (counterKey) {
                KEY_SHOW_COUNT -> showCount++
                KEY_CLICK_COUNT -> clickCount++
            }
            persistCountersLocked()
            !wasBlocked && isBlockedLocked()
        }
        if (becameBlocked) onLimitReached?.invoke()
    }

    private fun parse(rawConfig: String): AdLimitConfig? {
        if (TextUtils.isEmpty(rawConfig)) return null
        return runCatching {
            val root = JSONObject(rawConfig)
            AdLimitConfig(
                showLimit = root.getInt("ad_show_limit").coerceAtLeast(0),
                clickLimit = root.getInt("ad_click_limit").coerceAtLeast(0)
            )
        }.getOrNull()
    }

    private fun readCountersLocked() {
        val store = preferences ?: return
        day = store.getString(KEY_DAY, "").orEmpty()
        showCount = store.getInt(KEY_SHOW_COUNT, 0).coerceAtLeast(0)
        clickCount = store.getInt(KEY_CLICK_COUNT, 0).coerceAtLeast(0)
    }

    private fun resetForNewDayLocked() {
        val today = LocalDate.now().toString()
        if (day == today) return
        day = today
        showCount = 0
        clickCount = 0
        persistCountersLocked()
    }

    private fun persistCountersLocked() {
        preferences?.edit()
            ?.putString(KEY_DAY, day)
            ?.putInt(KEY_SHOW_COUNT, showCount)
            ?.putInt(KEY_CLICK_COUNT, clickCount)
            ?.apply()
    }

    private fun isBlockedLocked(): Boolean =
        showCount >= config.showLimit || clickCount >= config.clickLimit
}
