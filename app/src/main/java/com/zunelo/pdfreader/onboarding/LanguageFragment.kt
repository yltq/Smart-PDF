package com.zunelo.pdfreader.onboarding

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
import android.widget.TextView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import android.view.ViewGroup
import com.zunelo.pdfreader.ads.AdScenes
import com.zunelo.pdfreader.ads.SmartPdfAds
import com.zunelo.pdfreader.ads.PageAdRefreshable
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub

class LanguageFragment : Fragment(R.layout.fragment_language), PageAdRefreshable {
    companion object {
        private const val ARG_ONBOARDING = "onboarding"
        fun newInstance(onboarding: Boolean) = LanguageFragment().apply { arguments = Bundle().apply { putBoolean(ARG_ONBOARDING, onboarding) } }
    }
    private var selected = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val onboarding = requireArguments().getBoolean(ARG_ONBOARDING)
        if (onboarding) SmartPdfSignalHub.recordCustomSignal("page_language_show")
        if (onboarding) requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {}
        val preferences = requireContext().getSharedPreferences("onboarding", 0)
        val systemLocale = android.content.res.Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
        val supportedLanguages = listOf(
            "en" to "English", "zh-CN" to "简体中文", "zh-TW" to "繁體中文",
            "ja" to "日本語", "ko" to "한국어", "it" to "Italiano", "de" to "Deutsch",
            "fr" to "Français", "pt" to "Português", "es" to "Español", "th" to "ภาษาไทย",
            "id" to "Bahasa Indonesia", "hi" to "हिन्दी", "tr" to "Türkçe", "ar" to "العربية"
        )
        val systemTag = when (systemLocale.language) {
            "zh" -> if (systemLocale.country in setOf("TW", "HK", "MO") || systemLocale.script == "Hant") "zh-TW" else "zh-CN"
            else -> systemLocale.language
        }
        val supportedSystemLanguage = supportedLanguages.firstOrNull { it.first == systemTag }
        val languages = if (supportedSystemLanguage != null) {
            listOf("" to supportedSystemLanguage.second) + supportedLanguages.filterNot { it.first == systemTag }
        } else {
            supportedLanguages
        }
        selected = preferences.getString("language", null)
            ?: if (supportedSystemLanguage != null) "" else "en"
        if (supportedSystemLanguage != null && selected == systemTag) selected = ""
        view.findViewById<TextView>(R.id.toolbarTitle).setText(if (onboarding) R.string.choose_language else R.string.language)
        view.findViewById<View>(R.id.backButton).apply { visibility = if (onboarding) View.INVISIBLE else View.VISIBLE; setOnClickListener { (requireActivity() as MainActivity).back() } }
        view.findViewById<RecyclerView>(R.id.languageList).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = LanguageAdapter(languages, selected) { selected = it }
        }
        view.findViewById<TextView>(R.id.applyLanguage).apply {
            setText(if (onboarding) R.string.continue_action else R.string.apply)
            setOnClickListener {
                isEnabled = false
                if (onboarding) SmartPdfSignalHub.recordCustomSignal("page_language_click")
                val continueFlow = {
                    preferences.edit().putString("language", selected).putBoolean("language_done", true).apply()
                    if (onboarding) {
                        (requireActivity() as MainActivity).applyNewUserLanguage(selected)
                    } else {
                        (requireActivity() as MainActivity).applyLanguage(selected, 3)
                    }
                }
                if (onboarding) SmartPdfAds.show(requireActivity(), AdScenes.NEW_LANGUAGE_INTERSTITIAL, onComplete = continueFlow)
                else continueFlow()
            }
        }
        if (onboarding && savedInstanceState == null) {
            SmartPdfAds.bindNative(requireActivity(), AdScenes.NEW_LANGUAGE_NATIVE, view.findViewById(R.id.languageNativeAd))
        }
    }

    override fun onDestroyView() {
        view?.findViewById<ViewGroup>(R.id.languageNativeAd)?.let(SmartPdfAds::release)
        super.onDestroyView()
    }

    override fun refreshPageAds() {
        if (arguments?.getBoolean(ARG_ONBOARDING) != true) return
        val container = view?.findViewById<ViewGroup>(R.id.languageNativeAd) ?: return
        SmartPdfAds.bindNative(requireActivity(), AdScenes.NEW_LANGUAGE_NATIVE, container)
    }
}
