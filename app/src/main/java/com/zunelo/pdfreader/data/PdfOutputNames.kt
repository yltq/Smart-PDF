package com.zunelo.pdfreader.data

import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.application.*
import com.zunelo.pdfreader.common.*
import com.zunelo.pdfreader.data.*
import com.zunelo.pdfreader.dialog.*
import com.zunelo.pdfreader.home.*
import com.zunelo.pdfreader.onboarding.*
import com.zunelo.pdfreader.pdf.*
import com.zunelo.pdfreader.permission.*
import com.zunelo.pdfreader.startup.*
import com.zunelo.pdfreader.tools.*
import com.zunelo.pdfreader.viewer.*

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfOutputNames {
    fun merge(context: Context) = create(context, "Merge")
    fun split(context: Context) = create(context, "Split")
    fun scan(context: Context) = create(context, "Scan")
    fun annotated(context: Context) = create(context, "Annotated")
    fun organized(context: Context) = create(context, "Organized")
    fun watermarked(context: Context) = create(context, "Watermarked")
    fun locked(context: Context) = create(context, "Locked")
    fun unlocked(context: Context) = create(context, "Unlocked")

    private fun create(context: Context, operation: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${context.getString(R.string.app_name)}_${operation}_$timestamp"
    }
}
