package com.zunelo.pdfreader.application

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
import com.zunelo.pdfreader.uninstall.*
import com.zunelo.pdfreader.viewer.*
import com.zunelo.pdfreader.ads.AdScene
import com.zunelo.pdfreader.ads.AdScenes
import com.zunelo.pdfreader.ads.SmartPdfAds
import com.zunelo.pdfreader.ads.PageAdRefreshable
import com.zunelo.pdfreader.data.DocumentItem
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub

import android.content.pm.ActivityInfo
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

class MainActivity : AppCompatActivity() {
    companion object {
        private const val STATE_NEW_USER_FLOW = "state_new_user_flow"
        private const val PREFERENCES_ENTRY = "entry_flow"
        private const val KEY_UNINSTALL_BACKGROUND = "uninstall_background"
        private const val HOT_START_INTERVAL_MILLIS = 3_000L
        const val ACTION_SHORTCUT_UNINSTALL = "com.zunelo.pdfreader.action.UNINSTALL"
        const val ACTION_SHORTCUT_VIEW = "com.zunelo.pdfreader.action.VIEW"
        const val ACTION_SHORTCUT_SCAN = "com.zunelo.pdfreader.action.SCAN"
    }

    private var launchNewUserFlow = false
    private var restartAfterUninstallBackground = false
    private var backgroundAtMillis = 0L
    private var hotStartRouting = false
    private var suppressNextForegroundAdRefresh = false
    private var fullScreenAdTransition = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        launchNewUserFlow = savedInstanceState?.getBoolean(STATE_NEW_USER_FLOW)
            ?: NewUserFlow.consumeForLaunch(this)
        val entryPreferences = getSharedPreferences(PREFERENCES_ENTRY, 0)
        val uninstallWasBackgrounded = entryPreferences.getBoolean(KEY_UNINSTALL_BACKGROUND, false)
        if (uninstallWasBackgrounded) {
            launchNewUserFlow = false
            entryPreferences.edit().remove(KEY_UNINSTALL_BACKGROUND).apply()
            restartAfterUninstallBackground = savedInstanceState != null
        }
        val preferences = getSharedPreferences("onboarding", 0)
        if (preferences.contains("language")) {
            val savedLanguage = preferences.getString("language", "").orEmpty()
            if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != savedLanguage) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLanguage))
            }
        }
        super.onCreate(savedInstanceState)
        prepareWindow()
        setContentView(R.layout.activity_main)
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fragmentManager: FragmentManager, fragment: Fragment) {
                if (fragment is DialogFragment) return
                val orientation = if (fragment is ViewerFragment) {
                    ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                if (requestedOrientation != orientation) requestedOrientation = orientation
                updateNavigationBar(fragment)
            }
        }, false)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    if (supportFragmentManager.popBackStackImmediate()) refreshVisiblePageAds()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        if (savedInstanceState == null) {
            if (uninstallWasBackgrounded) {
                showRoot(SplashFragment.newInstance(false, SplashFragment.DESTINATION_HOME))
            } else {
                showStartup(intent)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val current = supportFragmentManager.findFragmentById(R.id.main)
        if (current is SplashFragment) {
            backgroundAtMillis = 0L
            return
        }

        val returnedFromBackground = backgroundAtMillis > 0L
        val forceRestart = restartAfterUninstallBackground
        val backgroundDuration = if (returnedFromBackground) {
            SystemClock.elapsedRealtime() - backgroundAtMillis
        } else {
            0L
        }
        backgroundAtMillis = 0L
        restartAfterUninstallBackground = false
        if (forceRestart) {
            getSharedPreferences(PREFERENCES_ENTRY, 0).edit().remove(KEY_UNINSTALL_BACKGROUND).apply()
        }
        if (fullScreenAdTransition || suppressNextForegroundAdRefresh) {
            fullScreenAdTransition = false
            suppressNextForegroundAdRefresh = false
            return
        }
        if (!forceRestart && backgroundDuration < HOT_START_INTERVAL_MILLIS) {
            if (returnedFromBackground) scheduleVisiblePageAdRefresh()
            return
        }

        launchNewUserFlow = false
        if (hotStartRouting) return
        hotStartRouting = true
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                showRoot(SplashFragment.newInstance(false, SplashFragment.DESTINATION_HOME))
            }
            hotStartRouting = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (suppressNextForegroundAdRefresh && backgroundAtMillis == 0L) {
            suppressNextForegroundAdRefresh = false
        }
        updateNavigationBar(supportFragmentManager.findFragmentById(R.id.main))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateNavigationBar(supportFragmentManager.findFragmentById(R.id.main))
    }

    override fun onStop() {
        if (isChangingConfigurations) {
            super.onStop()
            return
        }
        val current = supportFragmentManager.findFragmentById(R.id.main)
        if (current is SplashFragment) {
            backgroundAtMillis = 0L
            super.onStop()
            return
        }

        if (SmartPdfAds.isFullScreenShowing()) {
            fullScreenAdTransition = true
            backgroundAtMillis = 0L
            super.onStop()
            return
        }

        backgroundAtMillis = SystemClock.elapsedRealtime()
        if (current is UninstallStepOneFragment || current is UninstallStepTwoFragment) {
            launchNewUserFlow = false
            restartAfterUninstallBackground = true
            getSharedPreferences(PREFERENCES_ENTRY, 0).edit()
                .putBoolean(KEY_UNINSTALL_BACKGROUND, true)
                .apply()
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_NEW_USER_FLOW, launchNewUserFlow)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action in setOf(ACTION_SHORTCUT_UNINSTALL, ACTION_SHORTCUT_VIEW, ACTION_SHORTCUT_SCAN)) {
            showStartup(intent)
        }
    }

    private fun showStartup(intent: Intent) {
        if (intent.action == ACTION_SHORTCUT_UNINSTALL) {
            SmartPdfSignalHub.recordCustomSignal("shortcut_uninstall_click")
        }
        val destination = when (intent.action) {
            ACTION_SHORTCUT_UNINSTALL -> SplashFragment.DESTINATION_UNINSTALL
            ACTION_SHORTCUT_SCAN -> SplashFragment.DESTINATION_SCAN
            else -> SplashFragment.DESTINATION_HOME
        }
        val launchSource = if (intent.action in setOf(ACTION_SHORTCUT_UNINSTALL, ACTION_SHORTCUT_VIEW, ACTION_SHORTCUT_SCAN)) {
            SplashFragment.SOURCE_SHORTCUT
        } else {
            intent.getStringExtra(SplashFragment.EXTRA_AD_SOURCE) ?: SplashFragment.SOURCE_OPEN
        }
        val showNewUserFlow = launchNewUserFlow
        showRoot(SplashFragment.newInstance(showNewUserFlow, destination, launchSource))
    }

    fun showRoot(fragment: Fragment) {
        supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.main, fragment).commit()
    }

    fun suppressNextPageAdRefresh() {
        suppressNextForegroundAdRefresh = true
    }

    private fun refreshVisiblePageAds() {
        if (isFinishing || isDestroyed || hotStartRouting) return
        (supportFragmentManager.findFragmentById(R.id.main) as? PageAdRefreshable)?.refreshPageAds()
    }

    private fun updateNavigationBar(fragment: Fragment?) {
        val homeVisible = fragment is HomeFragment
        setNavigationBarVisible(homeVisible)
        if (homeVisible) {
            window.decorView.post {
                if (
                    hasWindowFocus() &&
                    supportFragmentManager.findFragmentById(R.id.main) is HomeFragment
                ) {
                    setNavigationBarVisible(true)
                }
            }
        }
    }

    private fun scheduleVisiblePageAdRefresh() {
        val expectedPage = supportFragmentManager.findFragmentById(R.id.main) as? PageAdRefreshable ?: return
        window.decorView.post {
            if (supportFragmentManager.findFragmentById(R.id.main) === expectedPage) {
                expectedPage.refreshPageAds()
            }
        }
    }

    fun navigate(fragment: Fragment) {
        val current = supportFragmentManager.findFragmentById(R.id.main)
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(R.anim.page_enter, R.anim.page_exit, 0, 0)
            .apply { if (current != null) hide(current) }
            .add(R.id.main, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun refreshHomeDocuments() {
        supportFragmentManager.fragments
            .filterIsInstance<HomeFragment>()
            .lastOrNull()
            ?.refreshDocuments()
    }

    fun applyLanguage(languageTag: String, destinationTab: Int) {
        showRoot(HomeFragment.newInstance(destinationTab))
        supportFragmentManager.executePendingTransactions()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    fun applyNewUserLanguage(languageTag: String) {
        showRoot(IntroFragment())
        supportFragmentManager.executePendingTransactions()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    fun completeNewUserFlow() {
        launchNewUserFlow = false
    }

    fun leaveUninstallFlow() {
        showRoot(if (launchNewUserFlow) LanguageFragment.newInstance(true) else HomeFragment())
    }

    fun openDocumentWithAd(item: DocumentItem) {
        SmartPdfAds.show(this, AdScenes.FILE_ENTRY) {
            if (!isFinishing && !isDestroyed) navigate(ViewerFragment.newInstance(item))
        }
    }

    fun returnHomeWithAd() {
        SmartPdfAds.show(this, AdScenes.BACK_HOME) {
            if (!isFinishing && !isDestroyed) showRoot(HomeFragment.newInstance(0))
        }
    }

    fun beginCreationAd(scene: AdScene): Long {
        SmartPdfAds.markDisplayChance(scene)
        SmartPdfAds.load(scene)
        return SystemClock.elapsedRealtime()
    }

    fun showCreationResult(item: DocumentItem, action: String, scene: AdScene, startedAtMillis: Long) {
        val tag = "creation_ad_wait"
        ProcessingDialogFragment.newInstance(getString(R.string.creating_pdf)).show(supportFragmentManager, tag)
        SmartPdfAds.load(scene)
        val handler = Handler(Looper.getMainLooper())
        lateinit var checkAd: Runnable
        fun finish(showAd: Boolean) {
            handler.removeCallbacks(checkAd)
            (supportFragmentManager.findFragmentByTag(tag) as? ProcessingDialogFragment)?.dismissAllowingStateLoss()
            val openResult = {
                if (!isFinishing && !isDestroyed) navigate(ResultFragment.newInstance(item, action))
            }
            if (showAd) SmartPdfAds.show(this, scene, chanceAlreadyReported = true, onComplete = openResult) else openResult()
        }
        checkAd = Runnable {
            val elapsed = SystemClock.elapsedRealtime() - startedAtMillis
            when {
                elapsed >= 3_000L && SmartPdfAds.isReady(scene) -> finish(true)
                elapsed >= 8_000L -> finish(false)
                else -> {
                    SmartPdfAds.load(scene)
                    handler.postDelayed(checkAd, 200L)
                }
            }
        }
        handler.post(checkAd)
    }

    fun back(): Boolean {
        val popped = supportFragmentManager.popBackStackImmediate()
        if (popped) refreshVisiblePageAds()
        return popped
    }
}
