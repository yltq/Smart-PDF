package com.zunelo.pdfreader.application

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.google.android.gms.ads.AdActivity
import com.zunelo.pdfreader.ads.SmartPdfAds
import com.zunelo.pdfreader.ads.config.AdConfigRepository
import java.lang.ref.WeakReference

object AppForegroundCoordinator : Application.ActivityLifecycleCallbacks {
    enum class RestartMode {
        RESTART_CURRENT_SPLASH,
        HOT_START
    }

    private var startedActivities = 0
    private var backgroundAtMillis = 0L
    private var suppressNextForeground = false
    private var forceNextHotStart = false
    private var shortcutEntryPending = false
    private var pendingRestart: RestartMode? = null
    private var mainActivity = WeakReference<MainActivity>(null)
    private var providerAdActivity = WeakReference<AdActivity>(null)

    fun initialize(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    fun suppressNextForeground() {
        suppressNextForeground = true
    }

    fun forceHotStartOnNextForeground() {
        forceNextHotStart = true
    }

    fun prioritizeShortcutEntry() {
        shortcutEntryPending = startedActivities == 0 && backgroundAtMillis > 0L
        suppressNextForeground = false
        forceNextHotStart = false
        pendingRestart = null
        if (SmartPdfAds.isFullScreenShowing()) {
            SmartPdfAds.prepareForStartupRestart(cancelContinuation = true)
            providerAdActivity.get()?.finish()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        track(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        track(activity)
        val returnedFromBackground = startedActivities == 0 && backgroundAtMillis > 0L
        startedActivities++
        if (!returnedFromBackground) return

        val backgroundDuration = SystemClock.elapsedRealtime() - backgroundAtMillis
        backgroundAtMillis = 0L
        handleForegroundReturn(activity, backgroundDuration)
    }

    override fun onActivityResumed(activity: Activity) {
        track(activity)
        if (activity is MainActivity && pendingRestart != null) {
            val restart = pendingRestart
            SmartPdfAds.prepareForStartupRestart(
                cancelContinuation = restart == RestartMode.RESTART_CURRENT_SPLASH
            )
            dispatchPendingRestart(activity)
        }
        SmartPdfAds.onHostActivityResumed(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        SmartPdfAds.onHostActivityPaused(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0 && !activity.isChangingConfigurations) {
            backgroundAtMillis = SystemClock.elapsedRealtime()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === mainActivity.get()) mainActivity.clear()
        if (activity === providerAdActivity.get()) {
            providerAdActivity.clear()
            SmartPdfAds.onProviderAdActivityDestroyed()
        }
    }

    private fun track(activity: Activity) {
        when (activity) {
            is MainActivity -> mainActivity = WeakReference(activity)
            is AdActivity -> providerAdActivity = WeakReference(activity)
        }
    }

    private fun handleForegroundReturn(firstActivity: Activity, backgroundDuration: Long) {
        val main = mainActivity.get()
        if (shortcutEntryPending) {
            shortcutEntryPending = false
            return
        }
        if (suppressNextForeground) {
            suppressNextForeground = false
            forceNextHotStart = false
            return
        }

        val launchAdShowing = SmartPdfAds.isLaunchFullScreenShowing()
        val fullScreenShowing = SmartPdfAds.isFullScreenShowing()
        val hotStartGap = main?.let {
            AdConfigRepository.userConfig(it).adsSettings.hotStartGapMillis.coerceAtLeast(0L)
        } ?: Long.MAX_VALUE
        val shouldHotStart = forceNextHotStart || backgroundDuration >= hotStartGap
        forceNextHotStart = false

        if (!launchAdShowing && !shouldHotStart) {
            if (!fullScreenShowing) main?.onShortForegroundReturn()
            return
        }

        pendingRestart = if (launchAdShowing) {
            RestartMode.RESTART_CURRENT_SPLASH
        } else {
            RestartMode.HOT_START
        }

        if (fullScreenShowing) {
            SmartPdfAds.prepareForStartupRestart(cancelContinuation = launchAdShowing)
            providerAdActivity.get()?.finish()
        }

        if (firstActivity is MainActivity) dispatchPendingRestart(firstActivity)
    }

    private fun dispatchPendingRestart(activity: MainActivity) {
        val restart = pendingRestart ?: return
        if (activity.isFinishing || activity.isDestroyed || activity.supportFragmentManager.isStateSaved) return
        pendingRestart = null
        activity.restartFromForeground(restart)
    }
}
