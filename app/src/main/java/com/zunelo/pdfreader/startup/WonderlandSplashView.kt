package com.zunelo.pdfreader.startup

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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.util.Random

class WonderlandSplashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private data class Particle(
        val centerX: Float,
        val centerY: Float,
        val radiusX: Float,
        val radiusY: Float,
        val size: Float,
        val phase: Float,
        val warm: Boolean
    )

    private data class FloatingPage(
        val centerX: Float,
        val centerY: Float,
        val scale: Float,
        val phase: Float,
        val rotation: Float,
        val warm: Boolean
    )

    private val density = resources.displayMetrics.density
    private val particles = createParticles()
    private val pages = listOf(
        FloatingPage(.14f, .29f, .78f, .06f, -9f, false),
        FloatingPage(.84f, .27f, .68f, .31f, 10f, true),
        FloatingPage(.10f, .61f, .58f, .57f, 7f, true),
        FloatingPage(.88f, .62f, .82f, .76f, -8f, false),
        FloatingPage(.24f, .79f, .54f, .90f, -5f, false)
    )
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leftFogPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rightFogPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.25f * density
    }
    private val lightTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 5f * density
    }
    private val bounds = RectF()
    private val foldPath = Path()
    private var loopAnimator: ValueAnimator? = null
    private var entranceAnimator: ValueAnimator? = null
    private var exitAnimator: ValueAnimator? = null
    private var loopProgress = 0f
    private var entranceProgress = 0f
    private var exitProgress = 0f
    private var hasEntered = false
    private var exiting = false

    fun start() {
        if (exiting || !ValueAnimator.areAnimatorsEnabled()) {
            entranceProgress = 1f
            invalidate()
            return
        }
        if (!hasEntered && entranceAnimator?.isRunning != true) {
            entranceAnimator = ValueAnimator.ofFloat(entranceProgress, 1f).apply {
                duration = ((1f - entranceProgress) * 720L).toLong().coerceAtLeast(1L)
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    entranceProgress = it.animatedValue as Float
                    invalidate()
                }
                doOnComplete { hasEntered = true }
                start()
            }
        }
        if (loopAnimator?.isRunning != true) {
            val resumeProgress = loopProgress
            loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = LOOP_DURATION
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    loopProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
                currentPlayTime = (resumeProgress * LOOP_DURATION).toLong()
            }
        }
    }

    fun finish(onFinished: () -> Unit) {
        if (exiting) return
        exiting = true
        loopAnimator?.cancel()
        entranceAnimator?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            exitProgress = 1f
            invalidate()
            onFinished()
            return
        }
        exitAnimator = ValueAnimator.ofFloat(exitProgress, 1f).apply {
            duration = 320L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                exitProgress = it.animatedValue as Float
                invalidate()
            }
            doOnComplete(onFinished)
            start()
        }
    }

    fun pauseAmbient() {
        loopAnimator?.cancel()
        entranceAnimator?.cancel()
    }

    fun dispose() {
        loopAnimator?.cancel()
        entranceAnimator?.cancel()
        exitAnimator?.cancel()
        loopAnimator = null
        entranceAnimator = null
        exitAnimator = null
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val centerX = width / 2f
        val centerY = height * .44f
        glowPaint.shader = RadialGradient(
            centerX,
            centerY,
            width * .48f,
            intArrayOf(0x24E91922, 0x10FFB45C, 0x00FDFDFD),
            floatArrayOf(0f, .48f, 1f),
            Shader.TileMode.CLAMP
        )
        leftFogPaint.shader = RadialGradient(
            width * .18f,
            height * .83f,
            width * .72f,
            intArrayOf(0x16E91922, 0x08FFB45C, 0x00FDFDFD),
            null,
            Shader.TileMode.CLAMP
        )
        rightFogPaint.shader = RadialGradient(
            width * .82f,
            height * .76f,
            width * .68f,
            intArrayOf(0x12FFB45C, 0x08E91922, 0x00FDFDFD),
            null,
            Shader.TileMode.CLAMP
        )
        lightTrailPaint.shader = LinearGradient(
            centerX - width * .25f,
            0f,
            centerX + width * .25f,
            0f,
            intArrayOf(Color.TRANSPARENT, 0x88E91922.toInt(), 0x55FFB45C, Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(253, 253, 253))
        if (width == 0 || height == 0) return
        val entrance = easeOut(entranceProgress)
        val visible = (entrance * (1f - exitProgress)).coerceIn(0f, 1f)
        if (visible <= 0f) return
        val angle = loopProgress * TWO_PI
        drawGlow(canvas, angle, visible)
        drawFog(canvas, angle, visible)
        drawPages(canvas, angle, visible)
        drawParticles(canvas, angle, visible)
        drawLightTrail(canvas, angle, visible)
    }

    private fun drawGlow(canvas: Canvas, angle: Float, visible: Float) {
        val centerX = width / 2f
        val centerY = height * .44f
        val pulse = 1f + sin(angle) * .035f
        glowPaint.alpha = (255f * visible).toInt()
        canvas.save()
        canvas.scale(pulse, pulse, centerX, centerY)
        canvas.drawCircle(centerX, centerY, width * .48f, glowPaint)
        canvas.restore()
    }

    private fun drawFog(canvas: Canvas, angle: Float, visible: Float) {
        leftFogPaint.alpha = (180f * visible).toInt()
        rightFogPaint.alpha = (150f * visible).toInt()
        canvas.save()
        canvas.translate(sin(angle) * 18f * density, cos(angle) * 5f * density)
        bounds.set(-width * .34f, height * .58f, width * .76f, height * 1.07f)
        canvas.drawOval(bounds, leftFogPaint)
        canvas.restore()
        canvas.save()
        canvas.translate(-sin(angle) * 15f * density, sin(angle) * 4f * density)
        bounds.set(width * .31f, height * .53f, width * 1.28f, height * 1.03f)
        canvas.drawOval(bounds, rightFogPaint)
        canvas.restore()
    }

    private fun drawPages(canvas: Canvas, angle: Float, visible: Float) {
        pages.forEach { page ->
            val pageAngle = angle + page.phase * TWO_PI
            val centerX = width * page.centerX + sin(pageAngle) * 8f * density
            val centerY = height * page.centerY + cos(pageAngle) * 11f * density
            val pageWidth = 35f * density * page.scale
            val pageHeight = 46f * density * page.scale
            val pulseAlpha = .68f + .32f * ((sin(pageAngle) + 1f) / 2f)
            pagePaint.color = if (page.warm) 0xFFFFA64D.toInt() else 0xFFE91922.toInt()
            pagePaint.alpha = (70f * visible * pulseAlpha).toInt()
            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.rotate(page.rotation + sin(pageAngle) * 2.2f)
            bounds.set(-pageWidth / 2f, -pageHeight / 2f, pageWidth / 2f, pageHeight / 2f)
            canvas.drawRoundRect(bounds, 5f * density, 5f * density, pagePaint)
            foldPath.reset()
            foldPath.moveTo(pageWidth * .12f, -pageHeight / 2f)
            foldPath.lineTo(pageWidth / 2f, -pageHeight * .14f)
            foldPath.moveTo(-pageWidth * .27f, pageHeight * .08f)
            foldPath.lineTo(pageWidth * .24f, pageHeight * .08f)
            foldPath.moveTo(-pageWidth * .27f, pageHeight * .24f)
            foldPath.lineTo(pageWidth * .12f, pageHeight * .24f)
            canvas.drawPath(foldPath, pagePaint)
            canvas.restore()
        }
    }

    private fun drawParticles(canvas: Canvas, angle: Float, visible: Float) {
        particles.forEachIndexed { index, particle ->
            val particleAngle = angle + particle.phase * TWO_PI
            val positionX = width * particle.centerX + sin(particleAngle) * width * particle.radiusX
            val positionY = height * particle.centerY + cos(particleAngle) * height * particle.radiusY
            val shimmer = .35f + .65f * ((sin(particleAngle * 1.7f) + 1f) / 2f)
            particlePaint.color = if (particle.warm) 0xFFFFAC4D.toInt() else 0xFFE91922.toInt()
            particlePaint.alpha = (125f * visible * shimmer).toInt()
            val radius = particle.size * density * (.82f + shimmer * .24f)
            canvas.drawCircle(positionX, positionY, radius, particlePaint)
            if (index % 7 == 0) {
                particlePaint.strokeWidth = .7f * density
                canvas.drawLine(positionX - radius * 2.2f, positionY, positionX + radius * 2.2f, positionY, particlePaint)
                canvas.drawLine(positionX, positionY - radius * 2.2f, positionX, positionY + radius * 2.2f, particlePaint)
            }
        }
    }

    private fun drawLightTrail(canvas: Canvas, angle: Float, visible: Float) {
        val pulse = .55f + .45f * ((sin(angle) + 1f) / 2f)
        val halfWidth = width * (.12f + pulse * .09f)
        lightTrailPaint.alpha = (180f * visible * pulse).toInt()
        val positionY = height * .63f
        canvas.drawLine(width / 2f - halfWidth, positionY, width / 2f + halfWidth, positionY, lightTrailPaint)
    }

    private fun createParticles(): List<Particle> {
        val random = Random(2607L)
        return List(28) {
            Particle(
                centerX = .08f + random.nextFloat() * .84f,
                centerY = .18f + random.nextFloat() * .66f,
                radiusX = .012f + random.nextFloat() * .035f,
                radiusY = .008f + random.nextFloat() * .024f,
                size = .8f + random.nextFloat() * 1.45f,
                phase = random.nextFloat(),
                warm = random.nextBoolean()
            )
        }
    }

    private fun easeOut(value: Float): Float = 1f - (1f - value) * (1f - value) * (1f - value)

    private fun ValueAnimator.doOnComplete(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: android.animation.Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (!cancelled) action()
            }
        })
    }

    private companion object {
        const val LOOP_DURATION = 4600L
        const val TWO_PI = (PI * 2.0).toFloat()
    }
}
