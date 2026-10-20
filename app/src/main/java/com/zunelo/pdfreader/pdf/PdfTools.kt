package com.zunelo.pdfreader.pdf

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
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.File

object PdfTools {
    fun outputExists(context: Context, outputName: String): Boolean {
        if (!DocumentStore.hasAccess(context)) return false
        return File(outputFolder(), pdfName(outputName)).exists()
    }

    fun pageCount(context: Context, item: DocumentItem): Int = PDDocument.load(DocumentStore.cacheFile(context, item)).use { it.numberOfPages }
    fun organizePages(context: Context, item: DocumentItem, plans: List<PdfPagePlan>, outputName: String): DocumentItem {
        require(plans.isNotEmpty())
        PDFBoxResourceLoader.init(context.applicationContext)
        val output = File(context.cacheDir, "organize_work_${System.nanoTime()}.pdf")
        PDDocument.load(DocumentStore.cacheFile(context, item)).use { source ->
            PDDocument().use { destination ->
                plans.forEach { plan ->
                    val imported = destination.importPage(source.getPage(plan.sourceIndex))
                    imported.rotation = (imported.rotation + plan.rotation) % 360
                }
                destination.save(output)
            }
        }
        return persist(context, output, pdfName(outputName))
    }

    fun addWatermark(context: Context, item: DocumentItem, text: String): DocumentItem {
        require(text.isNotBlank())
        PDFBoxResourceLoader.init(context.applicationContext)
        val output = File(context.cacheDir, "watermark_work_${System.nanoTime()}.pdf")
        PDDocument.load(DocumentStore.cacheFile(context, item)).use { document ->
            val bitmap = watermarkBitmap(text)
            val image = LosslessFactory.createFromImage(document, bitmap)
            for (index in 0 until document.numberOfPages) {
                val page = document.getPage(index); val box = page.cropBox; val width = box.width * .72f; val height = width * bitmap.height / bitmap.width
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { it.drawImage(image, (box.width - width) / 2f, (box.height - height) / 2f, width, height) }
            }
            bitmap.recycle(); document.save(output)
        }
        return persist(context, output, pdfName(PdfOutputNames.watermarked(context)))
    }

    private fun watermarkBitmap(text: String): Bitmap = Bitmap.createBitmap(1200, 360, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this); val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x42E91922; textSize = 112f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        canvas.rotate(-18f, width / 2f, height / 2f); canvas.drawText(text, width / 2f, height / 2f + 38f, paint)
    }

    fun applyMarkups(context: Context, item: DocumentItem, markups: List<PdfMarkup>, outputName: String): DocumentItem {
        require(markups.isNotEmpty())
        PDFBoxResourceLoader.init(context.applicationContext)
        val output = File(context.cacheDir, "markup_work_${System.nanoTime()}.pdf")
        PDDocument.load(DocumentStore.cacheFile(context, item)).use { document ->
            markups.groupBy { it.page }.forEach { (pageIndex, pageMarkups) ->
                if (pageIndex !in 0 until document.numberOfPages) return@forEach
                val page = document.getPage(pageIndex)
                val box = page.cropBox
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    pageMarkups.forEach { markup ->
                        if (markup.points.isEmpty()) return@forEach
                        val points = markup.points.map { point -> point.x * box.width to box.height - point.y * box.height }
                        stream.saveGraphicsState()
                        when (markup.type) {
                            MarkupType.PEN, MarkupType.SIGNATURE -> {
                                if (markup.type == MarkupType.PEN) stream.setStrokingColor(233, 25, 34) else stream.setStrokingColor(23, 26, 34)
                                stream.setLineWidth(if (markup.type == MarkupType.PEN) 2.2f else 1.8f)
                                stream.setLineCapStyle(1); stream.setLineJoinStyle(1)
                                points.firstOrNull()?.let { stream.moveTo(it.first, it.second) }
                                points.drop(1).forEach { stream.lineTo(it.first, it.second) }
                                if (points.size > 1) stream.stroke()
                            }
                            MarkupType.HIGHLIGHT -> {
                                val a = points.first(); val b = points.last(); val x = minOf(a.first, b.first); val y = minOf(a.second, b.second)
                                stream.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = .32f })
                                stream.setNonStrokingColor(255, 214, 51)
                                stream.addRect(x, y, kotlin.math.abs(a.first - b.first).coerceAtLeast(2f), kotlin.math.abs(a.second - b.second).coerceAtLeast(2f)); stream.fill()
                            }
                            MarkupType.UNDERLINE, MarkupType.STRIKE -> {
                                val a = points.first(); val b = points.last(); val y = if (markup.type == MarkupType.UNDERLINE) minOf(a.second, b.second) else (a.second + b.second) / 2f
                                stream.setStrokingColor(233, 25, 34); stream.setLineWidth(1.5f); stream.moveTo(minOf(a.first,b.first), y); stream.lineTo(maxOf(a.first,b.first), y); stream.stroke()
                            }
                            MarkupType.TEXT -> {
                                val image = textBitmap(markup.text)
                                val pdfImage = LosslessFactory.createFromImage(document, image)
                                val point = points.first(); val height = 22f; val width = height * image.width / image.height.toFloat()
                                stream.drawImage(pdfImage, point.first, point.second - height, width, height)
                                image.recycle()
                            }
                        }
                        stream.restoreGraphicsState()
                    }
                }
            }
            document.save(output)
        }
        return persist(context, output, pdfName(outputName))
    }

    private fun textBitmap(text: String): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(23, 26, 34); textSize = 48f }
        val width = (paint.measureText(text) + 24f).toInt().coerceAtLeast(48)
        return Bitmap.createBitmap(width, 68, Bitmap.Config.ARGB_8888).apply { Canvas(this).drawText(text, 12f, 52f, paint) }
    }

    fun isEncrypted(context: Context, item: DocumentItem): Boolean {
        PDFBoxResourceLoader.init(context.applicationContext)
        return try {
            PDDocument.load(DocumentStore.cacheFile(context, item)).use { it.isEncrypted }
        } catch (_: InvalidPasswordException) {
            true
        }
    }

    fun protect(context: Context, item: DocumentItem, password: String): DocumentItem {
        require(password.isNotBlank())
        PDFBoxResourceLoader.init(context.applicationContext)
        val output = File(context.cacheDir, "protect_work_${System.nanoTime()}.pdf")
        PDDocument.load(DocumentStore.cacheFile(context, item)).use { document ->
            val permissions = AccessPermission()
            val ownerPassword = "${password}_${System.nanoTime()}"
            document.protect(StandardProtectionPolicy(ownerPassword, password, permissions).apply {
                encryptionKeyLength = 128
                isPreferAES = true
            })
            document.save(output)
        }
        return persist(context, output, pdfName(PdfOutputNames.locked(context)))
    }

    fun unlock(context: Context, item: DocumentItem, password: String): DocumentItem {
        PDFBoxResourceLoader.init(context.applicationContext)
        val output = File(context.cacheDir, "unlock_work_${System.nanoTime()}.pdf")
        PDDocument.load(DocumentStore.cacheFile(context, item), password).use { document ->
            document.isAllSecurityToBeRemoved = true
            document.save(output)
        }
        return persist(context, output, pdfName(PdfOutputNames.unlocked(context)))
    }

    fun createFromImages(context: Context, paths: List<String>, outputName: String): DocumentItem {
        require(paths.isNotEmpty())
        val output = File(context.cacheDir, "scan_work_${System.nanoTime()}.pdf")
        val document = PdfDocument()
        try {
            paths.forEachIndexed { index, path ->
                ensureNotInterrupted()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                val landscape = bounds.outWidth > bounds.outHeight
                val pageWidth = if (landscape) 842 else 595
                val pageHeight = if (landscape) 595 else 842
                var sample = 1
                while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
                val bitmap = requireNotNull(BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }))
                val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                val scale = minOf(pageWidth.toFloat() / bitmap.width, pageHeight.toFloat() / bitmap.height)
                val width = bitmap.width * scale
                val height = bitmap.height * scale
                page.canvas.drawBitmap(bitmap, null, RectF((pageWidth - width) / 2f, (pageHeight - height) / 2f, (pageWidth + width) / 2f, (pageHeight + height) / 2f), null)
                document.finishPage(page)
                bitmap.recycle()
            }
            output.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return persist(context, output, pdfName(outputName))
    }

    fun merge(context: Context, items: List<DocumentItem>, outputName: String): DocumentItem {
        PDFBoxResourceLoader.init(context.applicationContext)
        val sourceFiles = items.map { DocumentStore.cacheFile(context, it) }
        val finalName = pdfName(outputName)
        val output = File(context.cacheDir, "merge_work_${System.nanoTime()}.pdf")
        PDFMergerUtility().apply {
            sourceFiles.forEach(::addSource)
            destinationFileName = output.absolutePath
            mergeDocuments(null)
        }
        ensureNotInterrupted()
        return persist(context, output, finalName)
    }

    fun split(context: Context, item: DocumentItem, pages: Set<Int>, outputName: String): DocumentItem {
        PDFBoxResourceLoader.init(context.applicationContext)
        val sourceFile = DocumentStore.cacheFile(context, item)
        val finalName = pdfName(outputName)
        val output = File(context.cacheDir, "split_work_${System.nanoTime()}.pdf")
        PDDocument.load(sourceFile).use { source ->
            PDDocument().use { destination ->
                pages.sorted().forEach { pageIndex ->
                    ensureNotInterrupted()
                    if (pageIndex in 0 until source.numberOfPages) destination.importPage(source.getPage(pageIndex))
                }
                destination.save(output)
            }
        }
        ensureNotInterrupted()
        return persist(context, output, finalName)
    }

    private fun pdfName(value: String): String {
        val base = value.trim().removeSuffix(".pdf").replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Document" }
        return "$base.pdf"
    }

    private fun ensureNotInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Operation timed out")
    }

    private fun persist(context: Context, source: File, name: String): DocumentItem {
        if (DocumentStore.hasAccess(context)) {
            val folder = outputFolder().apply { mkdirs() }
            val target = File(folder, name)
            check(!target.exists()) { "Output file already exists: ${target.absolutePath}" }
            source.copyTo(target, overwrite = false)
            if (Thread.currentThread().isInterrupted) {
                target.delete()
                throw InterruptedException("Operation timed out")
            }
            return DocumentItem(Uri.fromFile(target), target.name, target.length(), target.lastModified(), "application/pdf")
        }
        ensureNotInterrupted()
        return DocumentItem(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", source), name, source.length(), System.currentTimeMillis(), "application/pdf")
    }

    private fun outputFolder() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Smart PDF")
}
