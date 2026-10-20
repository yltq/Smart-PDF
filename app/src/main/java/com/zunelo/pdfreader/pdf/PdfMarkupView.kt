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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class MarkupType { PEN, HIGHLIGHT, UNDERLINE, STRIKE, TEXT, SIGNATURE }
data class PdfMarkup(val page: Int, val type: MarkupType, val points: List<PointF>, val text: String = "")

class PdfMarkupView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var pageIndex = 0
    var mode = MarkupType.PEN
    var panEnabled = true
    var onChanged: (() -> Unit)? = null
    var onTextRequested: ((PointF) -> Unit)? = null
    val markups = mutableListOf<PdfMarkup>()
    private val redoStack = mutableListOf<PdfMarkup>()
    private val active = mutableListOf<PointF>()
    private var pageBitmap: Bitmap? = null
    private var pageRatio = 1f
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var multiTouch = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var lastPanX = 0f
    private var lastPanY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            multiTouch = true
            active.clear()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val nextScale = (scale * detector.scaleFactor).coerceIn(1f, 5f)
            val factor = nextScale / scale
            val centerX = width / 2f
            val centerY = height / 2f
            offsetX = (detector.focusX - centerX) * (1f - factor) + offsetX * factor
            offsetY = (detector.focusY - centerY) * (1f - factor) + offsetY * factor
            scale = nextScale
            clampOffsets()
            invalidate()
            return true
        }
    })

    fun setPageSize(width: Int, height: Int) { pageRatio = width.toFloat() / height.coerceAtLeast(1); invalidate() }
    fun setPageBitmap(bitmap: Bitmap) {
        pageBitmap = bitmap
        pageRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        resetViewport()
    }
    fun addText(point: PointF, text: String) { if (text.isNotBlank()) { redoStack.clear(); markups += PdfMarkup(pageIndex, MarkupType.TEXT, listOf(point), text); invalidate(); onChanged?.invoke() } }
    fun undo() { val index = markups.indexOfLast { it.page == pageIndex }; if (index >= 0) { redoStack += markups.removeAt(index); invalidate(); onChanged?.invoke() } }
    fun redo() { val index = redoStack.indexOfLast { it.page == pageIndex }; if (index >= 0) { markups += redoStack.removeAt(index); invalidate(); onChanged?.invoke() } }

    private fun pageRect(): RectF {
        val viewRatio = width.toFloat() / height.coerceAtLeast(1)
        return if (viewRatio > pageRatio) {
            val contentWidth = height * pageRatio; RectF((width - contentWidth) / 2f, 0f, (width + contentWidth) / 2f, height.toFloat())
        } else {
            val contentHeight = width / pageRatio; RectF(0f, (height - contentHeight) / 2f, width.toFloat(), (height + contentHeight) / 2f)
        }
    }

    private fun normalized(x: Float, y: Float): PointF? {
        val centerX = width / 2f
        val centerY = height / 2f
        val sourceX = centerX + (x - offsetX - centerX) / scale
        val sourceY = centerY + (y - offsetY - centerY) / scale
        val rect = pageRect()
        if (!rect.contains(sourceX, sourceY)) return null
        return PointF((sourceX - rect.left) / rect.width(), (sourceY - rect.top) / rect.height())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multiTouch = false
                active.clear()
                lastPanX = event.x
                lastPanY = event.y
                if (!panEnabled) normalized(event.x, event.y)?.let(active::add)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                active.clear()
                val focus = touchFocus(event)
                lastFocusX = focus.x
                lastFocusY = focus.y
            }
            MotionEvent.ACTION_MOVE -> if (event.pointerCount > 1) {
                val focus = touchFocus(event)
                offsetX += focus.x - lastFocusX
                offsetY += focus.y - lastFocusY
                clampOffsets()
                invalidate()
                lastFocusX = focus.x
                lastFocusY = focus.y
            } else if (!multiTouch && panEnabled && scale > 1f) {
                offsetX += event.x - lastPanX
                offsetY += event.y - lastPanY
                clampOffsets()
                invalidate()
                lastPanX = event.x
                lastPanY = event.y
            } else if (!multiTouch && !panEnabled) {
                normalized(event.x, event.y)?.let { point ->
                    when (mode) {
                        MarkupType.PEN, MarkupType.SIGNATURE -> active += point
                        MarkupType.HIGHLIGHT, MarkupType.UNDERLINE, MarkupType.STRIKE -> {
                            if (active.size < 2) active += point else active[1] = point
                        }
                        MarkupType.TEXT -> Unit
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!multiTouch && !panEnabled) {
                    val point = normalized(event.x, event.y) ?: active.lastOrNull()
                    if (mode == MarkupType.TEXT) point?.let { onTextRequested?.invoke(it) } else if (point != null) {
                        when (mode) {
                            MarkupType.PEN, MarkupType.SIGNATURE -> active += point
                            MarkupType.HIGHLIGHT, MarkupType.UNDERLINE, MarkupType.STRIKE -> {
                                if (active.size < 2) active += point else active[1] = point
                            }
                            MarkupType.TEXT -> Unit
                        }
                        if (active.size > 1) {
                            redoStack.clear()
                            markups += PdfMarkup(pageIndex, mode, active.toList())
                            onChanged?.invoke()
                        }
                    }
                }
                active.clear()
                multiTouch = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                active.clear()
                multiTouch = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = pageRect()
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale, width / 2f, height / 2f)
        pageBitmap?.let { canvas.drawBitmap(it, null, rect, bitmapPaint) }
        markups.filter { it.page == pageIndex }.forEach { drawMarkup(canvas, it) }
        if (active.size > 1) drawMarkup(canvas, PdfMarkup(pageIndex, mode, active))
        canvas.restore()
    }

    private fun touchFocus(event: MotionEvent): PointF {
        var x = 0f
        var y = 0f
        repeat(event.pointerCount) { index -> x += event.getX(index); y += event.getY(index) }
        return PointF(x / event.pointerCount, y / event.pointerCount)
    }

    private fun resetViewport() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        active.clear()
        invalidate()
    }

    private fun clampOffsets() {
        if (scale <= 1.001f) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            return
        }
        val rect = pageRect()
        val maxX = ((rect.width() * scale - width) / 2f).coerceAtLeast(0f)
        val maxY = ((rect.height() * scale - height) / 2f).coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
        if (abs(offsetX) < 0.5f) offsetX = 0f
        if (abs(offsetY) < 0.5f) offsetY = 0f
    }

    private fun drawMarkup(canvas: Canvas, markup: PdfMarkup) {
        val rect = pageRect(); val points = markup.points.map { PointF(rect.left + it.x * rect.width(), rect.top + it.y * rect.height()) }
        if (points.isEmpty()) return
        when (markup.type) {
            MarkupType.PEN, MarkupType.SIGNATURE -> {
                paint.style = Paint.Style.STROKE; paint.color = if (markup.type == MarkupType.PEN) Color.rgb(233, 25, 34) else Color.rgb(25, 27, 32); paint.strokeWidth = if (markup.type == MarkupType.PEN) 5f else 4f
                points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x, a.y, b.x, b.y, paint) }
            }
            MarkupType.HIGHLIGHT -> { val a = points.first(); val b = points.last(); paint.style = Paint.Style.FILL; paint.color = 0x66FFD633; canvas.drawRect(min(a.x,b.x), min(a.y,b.y), max(a.x,b.x), max(a.y,b.y), paint) }
            MarkupType.UNDERLINE, MarkupType.STRIKE -> { val a = points.first(); val b = points.last(); val y = if (markup.type == MarkupType.UNDERLINE) max(a.y,b.y) else (a.y+b.y)/2f; paint.style = Paint.Style.STROKE; paint.color = Color.rgb(233,25,34); paint.strokeWidth = 3f; canvas.drawLine(min(a.x,b.x), y, max(a.x,b.x), y, paint) }
            MarkupType.TEXT -> { paint.style = Paint.Style.FILL; paint.color = Color.rgb(23,26,34); paint.textSize = 16f * resources.displayMetrics.scaledDensity; canvas.drawText(markup.text, points.first().x, points.first().y, paint) }
        }
    }
}
