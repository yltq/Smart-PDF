package com.zunelo.pdfreader.startup

import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.ads.consent.StartupConsentCoordinator
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

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import com.zunelo.pdfreader.uninstall.UninstallStepOneFragment
import com.zunelo.pdfreader.ads.AdScenes
import com.zunelo.pdfreader.ads.SmartPdfAds
import com.zunelo.pdfreader.ads.config.AdConfigRepository
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub

class SplashFragment : Fragment(R.layout.fragment_splash) {
    companion object {
        private const val ARG_NEW_USER_FLOW = "new_user_flow"
        private const val ARG_DESTINATION = "destination"
        private const val ARG_AD_SOURCE = "ad_source"
        private const val MAXIMUM_AD_WAIT_DURATION = 15_000L
        private const val AD_POLL_INTERVAL = 200L
        const val DESTINATION_HOME = "home"
        const val DESTINATION_SCAN = "scan"
        const val DESTINATION_UNINSTALL = "uninstall"
        const val EXTRA_AD_SOURCE = "smart_pdf_launch_source"
        const val SOURCE_OPEN = "open_launch"
        const val SOURCE_MEDIA = "media_launch"
        const val SOURCE_NOTIFICATION = "noti_launch"
        const val SOURCE_SERVICE = "service_launch"
        const val SOURCE_SHORTCUT = "shortcut_launch"

        fun newInstance(
            showNewUserFlow: Boolean,
            destination: String = DESTINATION_HOME,
            adSource: String = SOURCE_OPEN
        ) = SplashFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_NEW_USER_FLOW, showNewUserFlow)
                putString(ARG_DESTINATION, destination)
                putString(ARG_AD_SOURCE, adSource)
            }
        }
    }

    private lateinit var scene: WonderlandSplashView
    private lateinit var logo: ImageView
    private lateinit var title: TextView
    private var startupReady = false
    private var exitStarted = false
    private var exitFinished = false
    private var routed = false
    private var startupAdGateReady = false
    private var startupAdvertisingComplete = false
    private var resumeSequence = 0
    private var handledResumeSequence = -1
    private var launchAdStartedAt = 0L
    private var launchMinWaitMillis = 0L
    private var launchAdShowing = false
    private var skipNextResumeAd = false
    private val launchAdPollAction = Runnable { requestLaunchAd() }
    private val launchAdTimeoutAction = Runnable { completeStartupAdWait() }
    private val startupReadyAction = Runnable {
        startupReady = true
        attemptExit()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {}
        SmartPdfAds.resetFullScreenInterval()
        SmartPdfSignalHub.reportSession()
        scene = view.findViewById(R.id.wonderlandScene)
        logo = view.findViewById(R.id.splashLogo)
        title = view.findViewById(R.id.splashTitle)
        logo.alpha = 0f
        logo.scaleX = .76f
        logo.scaleY = .76f
        logo.rotation = -3f
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f).setDuration(720L).start()
        title.alpha = 0f
        title.translationY = requireContext().dp(12).toFloat()
        title.animate().alpha(1f).translationY(0f).setStartDelay(280L).setDuration(520L).start()
        prepareStartupAdvertising()
    }

    private fun prepareStartupAdvertising() {
        val firstColdStart = arguments?.getBoolean(ARG_NEW_USER_FLOW) == true
        StartupConsentCoordinator.prepare(requireActivity(), firstColdStart) {
            if (!isAdded || view == null) return@prepare
            beginStartupAdTiming()
            startupAdGateReady = true
            dispatchStartupAdIfReady()
        }
    }

    private fun dispatchStartupAdIfReady() {
        if (!startupAdGateReady || !isResumed || handledResumeSequence == resumeSequence) return
        handledResumeSequence = resumeSequence
        startupAdvertisingComplete = false
        if (launchAdStartedAt == 0L) beginStartupAdTiming()
        launchMinWaitMillis = AdConfigRepository.userConfig(requireContext())
            .adsSettings
            .loadMinWaitMillis
            .coerceAtLeast(0L)
        val elapsed = SystemClock.elapsedRealtime() - launchAdStartedAt
        startupReady = elapsed >= launchMinWaitMillis
        view?.removeCallbacks(startupReadyAction)
        if (!startupReady) {
            view?.postDelayed(startupReadyAction, launchMinWaitMillis - elapsed)
        }
        scheduleLaunchAdTimeout(elapsed)
        SmartPdfAds.markDisplayChance(AdScenes.LAUNCH, launchSource())
        SmartPdfAds.preloadAll(launchSource())
        requestLaunchAd()
    }

    private fun beginStartupAdTiming() {
        if (launchAdStartedAt != 0L || !isAdded) return
        launchAdStartedAt = SystemClock.elapsedRealtime()
        startupReady = false
    }

    private fun requestLaunchAd() {
        if (!isAdded || !isResumed || startupAdvertisingComplete) return
        val now = SystemClock.elapsedRealtime()
        val adWaitElapsed = now - launchAdStartedAt
        if (adWaitElapsed >= MAXIMUM_AD_WAIT_DURATION) {
            completeStartupAdWait()
            return
        }
        SmartPdfAds.load(AdScenes.LAUNCH, launchSource())
        when {
            adWaitElapsed >= launchMinWaitMillis && SmartPdfAds.isReady(AdScenes.LAUNCH) -> {
                startupReady = true
                view?.removeCallbacks(startupReadyAction)
                view?.removeCallbacks(launchAdTimeoutAction)
                launchAdShowing = true
                SmartPdfAds.show(
                    requireActivity(),
                    AdScenes.LAUNCH,
                    adSource = launchSource(),
                    chanceAlreadyReported = true
                ) {
                    launchAdShowing = false
                    skipNextResumeAd = !isResumed
                    startupAdvertisingComplete = true
                    attemptExit()
                }
            }
            else -> view?.postDelayed(launchAdPollAction, AD_POLL_INTERVAL)
        }
    }

    private fun scheduleLaunchAdTimeout(elapsed: Long) {
        view?.removeCallbacks(launchAdTimeoutAction)
        view?.postDelayed(
            launchAdTimeoutAction,
            (MAXIMUM_AD_WAIT_DURATION - elapsed).coerceAtLeast(0L)
        )
    }

    private fun completeStartupAdWait() {
        if (!isAdded || !isResumed || startupAdvertisingComplete || launchAdShowing) return
        view?.removeCallbacks(launchAdPollAction)
        view?.removeCallbacks(startupReadyAction)
        view?.removeCallbacks(launchAdTimeoutAction)
        startupReady = true
        startupAdvertisingComplete = true
        attemptExit()
    }

    private fun attemptExit() {
        if (!startupReady || !startupAdvertisingComplete || !isResumed || exitStarted || !::scene.isInitialized) return
        exitStarted = true
        logo.animate().alpha(0f).scaleX(.94f).scaleY(.94f).setDuration(280L).start()
        title.animate().alpha(0f).translationY(-requireContext().dp(5).toFloat()).setDuration(240L).start()
        scene.finish {
            exitFinished = true
            routeIfPossible()
        }
    }

    private fun routeIfPossible() {
        if (!exitFinished || !isResumed || routed || !isAdded) return
        SmartPdfAds.preloadAll(launchSource())
        routed = true
        val showNewUserFlow = arguments?.getBoolean(ARG_NEW_USER_FLOW) == true
        val destination = arguments?.getString(ARG_DESTINATION) ?: DESTINATION_HOME
        val target = when {
            destination == DESTINATION_UNINSTALL -> UninstallStepOneFragment()
            showNewUserFlow -> LanguageFragment.newInstance(true)
            destination == DESTINATION_SCAN -> HomeFragment.newInstance(startScan = true)
            else -> HomeFragment()
        }
        (requireActivity() as MainActivity).apply {
            showRoot(target)
            supportFragmentManager.executePendingTransactions()
            if (launchSource() != SOURCE_SHORTCUT) consumePendingFullScreenResumeTask()
        }
    }

    fun freshInstance(): SplashFragment = newInstance(
        showNewUserFlow = arguments?.getBoolean(ARG_NEW_USER_FLOW) == true,
        destination = arguments?.getString(ARG_DESTINATION) ?: DESTINATION_HOME,
        adSource = launchSource()
    )

    override fun onResume() {
        super.onResume()
        resumeSequence++
        if (::scene.isInitialized && !exitStarted) scene.start()
        if (skipNextResumeAd) {
            skipNextResumeAd = false
            handledResumeSequence = resumeSequence
        } else if (!launchAdShowing) {
            SmartPdfSignalHub.recordCustomSignal("page_loading_show")
            dispatchStartupAdIfReady()
        }
        if (exitFinished) routeIfPossible() else attemptExit()
    }

    override fun onStop() {
        view?.removeCallbacks(launchAdPollAction)
        view?.removeCallbacks(launchAdTimeoutAction)
        if (::scene.isInitialized && !exitStarted) scene.pauseAmbient()
        super.onStop()
    }

    override fun onDestroyView() {
        view?.removeCallbacks(startupReadyAction)
        view?.removeCallbacks(launchAdPollAction)
        view?.removeCallbacks(launchAdTimeoutAction)
        logo.animate().cancel()
        title.animate().cancel()
        scene.dispose()
        super.onDestroyView()
    }

    private fun launchSource(): String = arguments?.getString(ARG_AD_SOURCE).orEmpty()
        .ifBlank { SOURCE_OPEN }
}
