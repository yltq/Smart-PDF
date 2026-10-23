package com.zunelo.pdfreader.viewer

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
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfPrintHelper {
    fun print(activity: Activity, item: DocumentItem) {
        if (item.type != DocumentType.PDF) return
        val file = runCatching { DocumentStore.cacheFile(activity, item) }.getOrElse {
            Toast.makeText(activity, R.string.print_prepare_failed, Toast.LENGTH_SHORT).show()
            return
        }
        (activity as? MainActivity)?.suppressNextPageAdRefresh()
        (activity.getSystemService(Activity.PRINT_SERVICE) as PrintManager).print(
            item.name,
            PdfAdapter(file),
            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.NA_LETTER).build()
        )
    }

    private class PdfAdapter(private val file: File) : PrintDocumentAdapter() {
        override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback?, extras: Bundle?) {
            callback?.onLayoutFinished(PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
        }
        override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor?, cancellationSignal: CancellationSignal?, callback: WriteResultCallback?) {
            runCatching { FileInputStream(file).use { input -> FileOutputStream(destination!!.fileDescriptor).use(input::copyTo) } }
                .onSuccess { callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                .onFailure { callback?.onWriteFailed(it.message) }
        }
    }
}
