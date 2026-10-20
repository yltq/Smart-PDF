package com.zunelo.pdfreader.common

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

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.Window
import androidx.core.view.WindowCompat

object Palette {
    const val RED = 0xFFE91922.toInt()
    const val INK = 0xFF171A22.toInt()
    const val MUTED = 0xFF858C9B.toInt()
    const val LINE = 0xFFE9EBEF.toInt()
    const val SOFT = 0xFFF6F7F9.toInt()
    const val WHITE = Color.WHITE
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Activity.prepareWindow(window: Window = this.window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Palette.WHITE
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
}

fun documentIcon(type: DocumentType): Int = when (type) {
    DocumentType.PDF -> R.drawable.file_pdf
    DocumentType.WORD -> R.drawable.file_word
    DocumentType.EXCEL -> R.drawable.file_excel
    DocumentType.PPT -> R.drawable.file_ppt
    DocumentType.OTHER -> R.drawable.file_unknown
}
