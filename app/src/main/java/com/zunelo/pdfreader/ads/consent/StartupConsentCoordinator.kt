package com.zunelo.pdfreader.ads.consent

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object StartupConsentCoordinator {
    private const val PREFERENCES = "startup_consent"
    private const val KEY_ATTEMPTED = "first_launch_cmp_attempted"

    private val cmpCountries = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE",
        "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT",
        "RO", "SK", "SI", "ES", "SE", "NO", "IS", "LI", "CH", "GB"
    )

    fun prepare(
        activity: Activity,
        firstColdStart: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        if (!firstColdStart || !requiresCmp(Locale.getDefault().country)) {
            onComplete(true)
            return
        }
        val preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_ATTEMPTED, false)) {
            onComplete(true)
            return
        }
        preferences.edit().putBoolean(KEY_ATTEMPTED, true).commit()
        val completed = AtomicBoolean(false)
        fun finish(success: Boolean) {
            if (completed.compareAndSet(false, true)) onComplete(success)
        }
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val requestParameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            requestParameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    finish(formError == null)
                }
            },
            { finish(false) }
        )
    }

    private fun requiresCmp(countryCode: String): Boolean =
        countryCode.uppercase(Locale.US) in cmpCountries
}
