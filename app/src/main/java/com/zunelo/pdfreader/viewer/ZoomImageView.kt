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
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

class ZoomImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AppCompatImageView(context, attrs) {
    private val imageMatrixValue = Matrix()
    private var scale = 1f
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true
        override fun onScroll(first: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scale <= 1f || scaleDetector.isInProgress || current.pointerCount != 1) return false
            parent?.requestDisallowInterceptTouchEvent(true)
            imageMatrixValue.postTranslate(-distanceX, -distanceY)
            constrainMatrix()
            imageMatrix = imageMatrixValue
            return true
        }
        override fun onDoubleTap(event: MotionEvent): Boolean {
            if (scale > 1.05f) resetImage() else zoomBy(2.25f, event.x, event.y)
            return true
        }
    })
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = (scale * detector.scaleFactor).coerceIn(1f, 4f)
            val factor = next / scale
            scale = next
            imageMatrixValue.postScale(factor, factor, detector.focusX, detector.focusY)
            constrainMatrix()
            imageMatrix = imageMatrixValue
            parent?.requestDisallowInterceptTouchEvent(scale > 1f)
            return true
        }
    })

    init { scaleType = ScaleType.MATRIX }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post(::resetImage)
    }

    private fun resetImage() {
        val current = drawable ?: return
        if (width == 0 || height == 0 || current.intrinsicWidth <= 0 || current.intrinsicHeight <= 0) return
        val fit = min(width.toFloat() / current.intrinsicWidth, height.toFloat() / current.intrinsicHeight)
        val contentWidth = current.intrinsicWidth * fit
        val contentHeight = current.intrinsicHeight * fit
        imageMatrixValue.reset()
        imageMatrixValue.setScale(fit, fit)
        imageMatrixValue.postTranslate((width - contentWidth) / 2f, (height - contentHeight) / 2f)
        imageMatrix = imageMatrixValue
        scale = 1f
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (scale > 1f) parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_POINTER_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_MOVE -> if (scale > 1f) parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun zoomBy(targetScale: Float, focusX: Float, focusY: Float) {
        val next = targetScale.coerceIn(1f, 4f)
        val factor = next / scale
        scale = next
        imageMatrixValue.postScale(factor, factor, focusX, focusY)
        constrainMatrix()
        imageMatrix = imageMatrixValue
    }

    private fun constrainMatrix() {
        val current = drawable ?: return
        val bounds = RectF(0f, 0f, current.intrinsicWidth.toFloat(), current.intrinsicHeight.toFloat())
        imageMatrixValue.mapRect(bounds)
        val moveX = when {
            bounds.width() <= width -> width / 2f - bounds.centerX()
            bounds.left > 0f -> -bounds.left
            bounds.right < width -> width - bounds.right
            else -> 0f
        }
        val moveY = when {
            bounds.height() <= height -> height / 2f - bounds.centerY()
            bounds.top > 0f -> -bounds.top
            bounds.bottom < height -> height - bounds.bottom
            else -> 0f
        }
        imageMatrixValue.postTranslate(moveX, moveY)
    }
}
