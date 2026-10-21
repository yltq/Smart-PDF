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
import com.zunelo.pdfreader.viewer.*

import android.content.pm.ActivityInfo
import android.os.Bundle
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
    private var launchNewUserFlow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (savedInstanceState == null) launchNewUserFlow = NewUserFlow.consumeForLaunch(this)
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
                    supportFragmentManager.popBackStackImmediate()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        if (savedInstanceState == null) showRoot(SplashFragment.newInstance(launchNewUserFlow))
    }

    fun showRoot(fragment: Fragment) {
        supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.main, fragment).commit()
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

    fun back() = supportFragmentManager.popBackStackImmediate()
}
