package com.zunelo.pdfreader.onboarding

import android.content.Context

object NewUserFlow {
    private const val PREFERENCES = "onboarding"
    private const val KEY_CONSUMED = "new_user_flow_consumed"
    private const val LEGACY_LANGUAGE_DONE = "language_done"

    @Synchronized
    fun consumeForLaunch(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_CONSUMED, false)) return false
        val existingUser = preferences.getBoolean(LEGACY_LANGUAGE_DONE, false)
        preferences.edit().putBoolean(KEY_CONSUMED, true).commit()
        return !existingUser
    }
}
