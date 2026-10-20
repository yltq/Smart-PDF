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

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException

class FixedDurationTask<T>(
    private val durationMillis: Long = 2_000L,
    private val work: () -> T,
    private val onFinished: (Result<T>) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var future: Future<T>? = null
    private var cancelled = false

    fun start() {
        future = executor.submit<T> { work() }
        handler.postDelayed(::finish, durationMillis)
    }

    fun cancel() {
        cancelled = true
        handler.removeCallbacksAndMessages(null)
        future?.cancel(true)
        executor.shutdownNow()
    }

    private fun finish() {
        val runningTask = future ?: return
        val result = if (runningTask.isDone) {
            runCatching { runningTask.get() }.recoverCatching { error -> throw error.cause ?: error }
        } else {
            runningTask.cancel(true)
            Result.failure(TimeoutException("Operation exceeded $durationMillis ms"))
        }
        executor.shutdownNow()
        if (!cancelled) onFinished(result)
    }
}
