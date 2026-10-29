package com.zunelo.pdfreader.common

import android.util.Log
import com.zunelo.pdfreader.BuildConfig

object AppLog {
    private const val TAG = "SmartPDF"

    fun debug(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }
}
