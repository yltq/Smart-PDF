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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors

class PdfPageLoader(
    private val context: Context,
    private val item: DocumentItem,
    private val targetWidth: Int,
    private val onReady: (Int) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = LruCache<Int, Bitmap>(6)
    private var renderer: PdfRenderer? = null
    private var closed = false

    fun open() {
        executor.execute {
            runCatching {
                val descriptor = requireNotNull(DocumentStore.openFileDescriptor(context, item))
                PdfRenderer(descriptor).also { renderer = it }.pageCount
            }.onSuccess { count -> main.post { if (!closed) onReady(count) } }
                .onFailure { error -> main.post { if (!closed) onError(error) } }
        }
    }

    fun load(index: Int, callback: (Bitmap?) -> Unit) {
        cache.get(index)?.let { callback(it); return }
        executor.execute {
            val bitmap = runCatching {
                val activeRenderer = requireNotNull(renderer)
                activeRenderer.openPage(index).use { page ->
                    val height = (targetWidth * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(Color.WHITE)
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }.also { cache.put(index, it) }
            }.getOrNull()
            main.post { if (!closed) callback(bitmap) }
        }
    }

    fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
        executor.execute { runCatching { renderer?.close() }; renderer = null; cache.evictAll() }
        executor.shutdown()
    }
}
