package com.zunelo.pdfreader.tools

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
import android.net.Uri
import java.io.File

object ScanSession {
    fun copyPages(context: Context, uris: List<Uri>): ArrayList<String> {
        val folder = File(context.cacheDir, "scan_${System.currentTimeMillis()}").apply { mkdirs() }
        return ArrayList(uris.mapIndexed { index, uri ->
            val target = File(folder, "page_${index + 1}.jpg")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to read scanned page" }
                target.outputStream().use(input::copyTo)
            }
            target.absolutePath
        })
    }
}
