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

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment

class SplashFragment : Fragment(R.layout.fragment_splash) {
    companion object {
        private const val ARG_NEW_USER_FLOW = "new_user_flow"
        private const val MINIMUM_STARTUP_DURATION = 2680L

        fun newInstance(showNewUserFlow: Boolean) = SplashFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_NEW_USER_FLOW, showNewUserFlow) }
        }
    }

    private lateinit var scene: WonderlandSplashView
    private lateinit var logo: ImageView
    private lateinit var title: TextView
    private var startupReady = false
    private var exitStarted = false
    private var exitFinished = false
    private var routed = false
    private val startupReadyAction = Runnable {
        startupReady = true
        attemptExit()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
        beginStartupFlow(view)
    }

    private fun beginStartupFlow(root: View) {
        root.postDelayed(startupReadyAction, MINIMUM_STARTUP_DURATION)
    }

    private fun attemptExit() {
        if (!startupReady || !isResumed || exitStarted || !::scene.isInitialized) return
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
        routed = true
        val showNewUserFlow = arguments?.getBoolean(ARG_NEW_USER_FLOW) == true
        (requireActivity() as MainActivity).showRoot(if (showNewUserFlow) LanguageFragment.newInstance(true) else HomeFragment())
    }

    override fun onResume() {
        super.onResume()
        if (::scene.isInitialized && !exitStarted) scene.start()
        if (exitFinished) routeIfPossible() else attemptExit()
    }

    override fun onStop() {
        if (::scene.isInitialized && !exitStarted) scene.pauseAmbient()
        super.onStop()
    }

    override fun onDestroyView() {
        view?.removeCallbacks(startupReadyAction)
        logo.animate().cancel()
        title.animate().cancel()
        scene.dispose()
        super.onDestroyView()
    }
}
