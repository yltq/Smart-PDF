package com.zunelo.pdfreader.guard

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.gms.ads.AdActivity
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub
import java.util.concurrent.atomic.AtomicBoolean

object TestAdState {
    private const val PREFERENCES = "smart_pdf_ad_probe"
    private const val KEY_DECISION = "native_test_result"

    private val lifecycleRegistered = AtomicBoolean(false)
    private val eventReported = AtomicBoolean(false)

    fun initialize(application: Application) {
        if (!lifecycleRegistered.compareAndSet(false, true)) return
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    inspectFullScreenActivity(activity)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    fun isTestAd(context: Context): Boolean = decision(context) == true

    fun decision(context: Context): Boolean? {
        val preferences = preferences(context)
        if (!preferences.contains(KEY_DECISION)) return null
        return preferences.getBoolean(KEY_DECISION, false)
    }

    fun inspectNativeHeadline(context: Context, headline: String?): Boolean {
        decision(context)?.let { saved ->
            if (saved) reportTestAdOnce()
            return saved
        }

        val title = headline?.trim().orEmpty()
        val localizedPrefix = localizedTestLabel(context)
        val detected = sequenceOf("Test Ad", localizedPrefix)
            .filter(String::isNotBlank)
            .any { prefix -> title.startsWith(prefix, ignoreCase = true) }
        save(context, detected)
        if (detected) reportTestAdOnce()
        return detected
    }

    private fun inspectFullScreenActivity(activity: Activity) {
        if (activity !is AdActivity || decision(activity) != null) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val inspect = Runnable {
            if (decision(activity) == null && content.hasTextViewDescendant()) {
                save(activity, true)
                reportTestAdOnce()
            }
        }
        inspect.run()
        content.post(inspect)
    }

    private fun localizedTestLabel(context: Context): String {
        val identifier = context.resources.getIdentifier(
            "s7",
            "string",
            "com.google.android.gms.ads.impl"
        )
        if (identifier == 0) return ""
        return runCatching { context.getString(identifier).trim() }.getOrDefault("")
    }

    private fun save(context: Context, isTestAd: Boolean) {
        preferences(context).edit().putBoolean(KEY_DECISION, isTestAd).apply()
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE
    )

    private fun reportTestAdOnce() {
        if (eventReported.compareAndSet(false, true)) {
            SmartPdfSignalHub.recordCustomSignal("ad_test")
        }
    }

    private fun ViewGroup.hasTextViewDescendant(): Boolean {
        for (index in 0 until childCount) {
            when (val child = getChildAt(index)) {
                is TextView -> return true
                is ViewGroup -> if (child.hasTextViewDescendant()) return true
            }
        }
        return false
    }
}
