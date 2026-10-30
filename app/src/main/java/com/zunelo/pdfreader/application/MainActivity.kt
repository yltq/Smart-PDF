package com.zunelo.pdfreader.application

import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.application.*
import com.zunelo.pdfreader.common.*
import com.zunelo.pdfreader.cleanup.LargeFileResultFragment
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
import com.zunelo.pdfreader.ads.FullScreenResumeStore
import com.zunelo.pdfreader.ads.FullScreenResumeTask
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
        private const val STATE_AFTER_NEW_USER_DESTINATION = "state_after_new_user_destination"
        private const val PREFERENCES_ENTRY = "entry_flow"
        private const val KEY_UNINSTALL_BACKGROUND = "uninstall_background"
        const val ACTION_SHORTCUT_UNINSTALL = "com.zunelo.pdfreader.action.UNINSTALL"
        const val ACTION_SHORTCUT_VIEW = "com.zunelo.pdfreader.action.VIEW"
        const val ACTION_SHORTCUT_SCAN = "com.zunelo.pdfreader.action.SCAN"
    }

    private var launchNewUserFlow = false
    private var afterNewUserDestination = SplashFragment.DESTINATION_HOME
    private var hotStartRouting = false
    private var startupRouteVersion = 0
    private var backHomeNavigationPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        launchNewUserFlow = savedInstanceState?.getBoolean(STATE_NEW_USER_FLOW)
            ?: NewUserFlow.consumeForLaunch(this)
        afterNewUserDestination = savedInstanceState?.getString(STATE_AFTER_NEW_USER_DESTINATION)
            ?: SplashFragment.DESTINATION_HOME
        val entryPreferences = getSharedPreferences(PREFERENCES_ENTRY, 0)
        val uninstallWasBackgrounded = entryPreferences.getBoolean(KEY_UNINSTALL_BACKGROUND, false)
        if (uninstallWasBackgrounded) {
            launchNewUserFlow = false
            entryPreferences.edit().remove(KEY_UNINSTALL_BACKGROUND).apply()
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
        supportFragmentManager.setFragmentResultListener(FileDialogs.RESULT_CHANGED, this) { _, result ->
            supportFragmentManager.fragments
                .filterIsInstance<HomeFragment>()
                .lastOrNull()
                ?.applyFileChange(result)
        }
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
                if (!back()) {
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

    override fun onResume() {
        super.onResume()
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
        if (
            !SmartPdfAds.isFullScreenShowing() &&
            (current is UninstallStepOneFragment || current is UninstallStepTwoFragment)
        ) {
            launchNewUserFlow = false
            AppForegroundCoordinator.forceHotStartOnNextForeground()
            getSharedPreferences(PREFERENCES_ENTRY, 0).edit()
                .putBoolean(KEY_UNINSTALL_BACKGROUND, true)
                .apply()
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_NEW_USER_FLOW, launchNewUserFlow)
        outState.putString(STATE_AFTER_NEW_USER_DESTINATION, afterNewUserDestination)
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
        startupRouteVersion++
        val shortcutEntry = intent.action in setOf(
            ACTION_SHORTCUT_UNINSTALL,
            ACTION_SHORTCUT_VIEW,
            ACTION_SHORTCUT_SCAN
        )
        if (shortcutEntry) {
            AppForegroundCoordinator.prioritizeShortcutEntry()
            FullScreenResumeStore.clear(this)
        }
        if (intent.action == ACTION_SHORTCUT_UNINSTALL) {
            SmartPdfSignalHub.recordCustomSignal("shortcut_uninstall_click")
        }
        val destination = when (intent.action) {
            ACTION_SHORTCUT_UNINSTALL -> SplashFragment.DESTINATION_UNINSTALL
            ACTION_SHORTCUT_SCAN -> SplashFragment.DESTINATION_SCAN
            else -> SplashFragment.DESTINATION_HOME
        }
        val launchSource = if (shortcutEntry) {
            SplashFragment.SOURCE_SHORTCUT
        } else {
            intent.getStringExtra(SplashFragment.EXTRA_AD_SOURCE) ?: SplashFragment.SOURCE_OPEN
        }
        val showNewUserFlow = launchNewUserFlow
        if (showNewUserFlow) {
            afterNewUserDestination = if (destination == SplashFragment.DESTINATION_SCAN) {
                SplashFragment.DESTINATION_SCAN
            } else {
                SplashFragment.DESTINATION_HOME
            }
        }
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
        AppForegroundCoordinator.suppressNextForeground()
    }

    internal fun onShortForegroundReturn() {
        scheduleVisiblePageAdRefresh()
    }

    internal fun restartFromForeground(mode: AppForegroundCoordinator.RestartMode) {
        if (hotStartRouting) return
        hotStartRouting = true
        val expectedRouteVersion = startupRouteVersion
        window.decorView.post {
            if (expectedRouteVersion != startupRouteVersion) {
                hotStartRouting = false
                return@post
            }
            if (!isFinishing && !isDestroyed) {
                val current = supportFragmentManager.findFragmentById(R.id.main)
                val splash = if (mode == AppForegroundCoordinator.RestartMode.RESTART_CURRENT_SPLASH) {
                    (current as? SplashFragment)?.freshInstance()
                        ?: SplashFragment.newInstance(false, SplashFragment.DESTINATION_HOME)
                } else {
                    launchNewUserFlow = false
                    SplashFragment.newInstance(false, SplashFragment.DESTINATION_HOME)
                }
                showRoot(splash)
            }
            hotStartRouting = false
        }
    }

    fun consumePendingFullScreenResumeTask() {
        val task = FullScreenResumeStore.take(this) ?: return
        val payload = task.payloadJson()
        when (task.action) {
            FullScreenResumeTask.Action.OPEN_DOCUMENT -> {
                FullScreenResumeTask.document(payload)?.let { navigate(ViewerFragment.newInstance(it)) }
            }
            FullScreenResumeTask.Action.RETURN_HOME -> {
                backHomeNavigationPending = false
                showRoot(HomeFragment.newInstance(0))
            }
            FullScreenResumeTask.Action.SHOW_RESULT -> {
                val item = FullScreenResumeTask.document(payload) ?: return
                navigate(ResultFragment.newInstance(item, payload.optString("result_action", "processed")))
            }
            FullScreenResumeTask.Action.SHOW_CLEAN_RESULT -> {
                refreshHomeDocuments()
                navigate(
                    LargeFileResultFragment.newInstance(
                        freedBytes = payload.optLong("freed_bytes"),
                        deletedCount = payload.optInt("deleted_count"),
                        failedCount = payload.optInt("failed_count")
                    )
                )
            }
            FullScreenResumeTask.Action.APPLY_NEW_USER_LANGUAGE -> {
                val languageTag = payload.optString("language_tag")
                getSharedPreferences("onboarding", 0).edit()
                    .putString("language", languageTag)
                    .putBoolean("language_done", true)
                    .apply()
                applyNewUserLanguage(languageTag)
            }
            FullScreenResumeTask.Action.COMPLETE_NEW_USER -> {
                completeNewUserFlowAndOpenDestination()
            }
            FullScreenResumeTask.Action.OPEN_UNINSTALL_STEP_TWO -> showRoot(UninstallStepTwoFragment())
        }
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

    fun completeNewUserFlowAndOpenDestination() {
        completeNewUserFlow()
        val startScan = afterNewUserDestination == SplashFragment.DESTINATION_SCAN
        afterNewUserDestination = SplashFragment.DESTINATION_HOME
        showRoot(HomeFragment.newInstance(startScan = startScan))
    }

    fun leaveUninstallFlow() {
        if (launchNewUserFlow) showRoot(LanguageFragment.newInstance(true)) else returnHomeWithAd()
    }

    fun openDocumentWithAd(item: DocumentItem) {
        SmartPdfAds.show(this, AdScenes.FILE_ENTRY, resumeTask = FullScreenResumeTask.openDocument(item)) {
            if (!isFinishing && !isDestroyed) navigate(ViewerFragment.newInstance(item))
        }
    }

    fun returnHomeWithAd() {
        showBackHomeAd {
            if (!isFinishing && !isDestroyed) showRoot(HomeFragment.newInstance(0))
        }
    }

    fun beginCreationAd(scene: AdScene): Long {
        SmartPdfAds.markDisplayChance(scene)
        SmartPdfAds.load(scene)
        return SystemClock.elapsedRealtime()
    }

    fun showCreationResult(item: DocumentItem, action: String, scene: AdScene, startedAtMillis: Long) {
        continueAfterCreationAd(
            scene = scene,
            startedAtMillis = startedAtMillis,
            loadingMessage = getString(R.string.creating_pdf),
            resumeTask = FullScreenResumeTask.showResult(item, action)
        ) {
            if (!isFinishing && !isDestroyed) navigate(ResultFragment.newInstance(item, action))
        }
    }

    fun continueAfterCreationAd(
        scene: AdScene,
        startedAtMillis: Long,
        loadingMessage: String,
        resumeTask: FullScreenResumeTask? = null,
        onComplete: () -> Unit
    ) {
        val tag = "creation_ad_wait"
        ProcessingDialogFragment.newInstance(loadingMessage).show(supportFragmentManager, tag)
        if (SmartPdfAds.isSceneEnabled(scene)) SmartPdfAds.load(scene)
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        lateinit var checkAd: Runnable
        fun finish(showAd: Boolean) {
            if (finished) return
            finished = true
            handler.removeCallbacks(checkAd)
            (supportFragmentManager.findFragmentByTag(tag) as? ProcessingDialogFragment)?.dismissAllowingStateLoss()
            if (showAd) {
                SmartPdfAds.show(
                    this,
                    scene,
                    chanceAlreadyReported = true,
                    resumeTask = resumeTask,
                    onComplete = onComplete
                )
            } else {
                onComplete()
            }
        }
        checkAd = Runnable {
            val elapsed = SystemClock.elapsedRealtime() - startedAtMillis
            val sceneEnabled = SmartPdfAds.isSceneEnabled(scene)
            when {
                !sceneEnabled && elapsed >= 3_000L -> finish(false)
                elapsed >= 3_000L && SmartPdfAds.isReady(scene) -> finish(true)
                elapsed >= 8_000L -> finish(false)
                else -> {
                    if (sceneEnabled) SmartPdfAds.load(scene)
                    handler.postDelayed(checkAd, 200L)
                }
            }
        }
        handler.post(checkAd)
    }

    fun back(): Boolean {
        if (supportFragmentManager.backStackEntryCount <= 0) return false
        if (nextBackDestinationIsHome()) {
            showBackHomeAd {
                if (isFinishing || isDestroyed) return@showBackHomeAd
                if (nextBackDestinationIsHome()) {
                    if (supportFragmentManager.popBackStackImmediate()) refreshVisiblePageAds()
                } else {
                    showRoot(HomeFragment.newInstance(0))
                }
            }
            return true
        }
        val popped = supportFragmentManager.popBackStackImmediate()
        if (popped) refreshVisiblePageAds()
        return popped
    }

    private fun showBackHomeAd(onComplete: () -> Unit) {
        if (backHomeNavigationPending) return
        backHomeNavigationPending = true
        SmartPdfAds.show(this, AdScenes.BACK_HOME, resumeTask = FullScreenResumeTask.returnHome()) {
            backHomeNavigationPending = false
            onComplete()
        }
    }

    private fun nextBackDestinationIsHome(): Boolean {
        if (supportFragmentManager.backStackEntryCount != 1) return false
        val current = supportFragmentManager.findFragmentById(R.id.main)
        return supportFragmentManager.fragments.any { fragment ->
            fragment is HomeFragment && fragment.isAdded && fragment !== current
        }
    }
}
