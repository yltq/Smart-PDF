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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class LanguageFragment : Fragment(R.layout.fragment_language) {
    companion object {
        private const val ARG_ONBOARDING = "onboarding"
        fun newInstance(onboarding: Boolean) = LanguageFragment().apply { arguments = Bundle().apply { putBoolean(ARG_ONBOARDING, onboarding) } }
    }
    private var selected = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val onboarding = requireArguments().getBoolean(ARG_ONBOARDING)
        val preferences = requireContext().getSharedPreferences("onboarding", 0)
        selected = preferences.getString("language", null) ?: ""
        val systemLocale = android.content.res.Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
        val supportedLanguages = listOf(
            "en" to "English", "es" to "Español", "pt" to "Português", "de" to "Deutsch",
            "fr" to "Français", "it" to "Italiano", "id" to "Bahasa Indonesia",
            "zh-CN" to "简体中文", "zh-TW" to "繁體中文", "ja" to "日本語", "ko" to "한국어"
        )
        val systemTag = when (systemLocale.language) {
            "zh" -> if (systemLocale.country in setOf("TW", "HK", "MO") || systemLocale.script == "Hant") "zh-TW" else "zh-CN"
            else -> systemLocale.language
        }
        val systemLanguageName = supportedLanguages.firstOrNull { it.first == systemTag }?.second
            ?: systemLocale.getDisplayLanguage(systemLocale).replaceFirstChar { it.uppercaseChar() }
        val languages = listOf("" to systemLanguageName) + supportedLanguages.filterNot { it.first == systemTag }
        if (selected == systemTag) selected = ""
        view.findViewById<TextView>(R.id.toolbarTitle).setText(if (onboarding) R.string.choose_language else R.string.language)
        view.findViewById<View>(R.id.backButton).apply { visibility = if (onboarding) View.INVISIBLE else View.VISIBLE; setOnClickListener { (requireActivity() as MainActivity).back() } }
        view.findViewById<RecyclerView>(R.id.languageList).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = LanguageAdapter(languages, selected) { selected = it }
        }
        view.findViewById<TextView>(R.id.applyLanguage).apply {
            setText(if (onboarding) R.string.continue_action else R.string.apply)
            setOnClickListener {
                preferences.edit().putString("language", selected).putBoolean("language_done", true).apply()
                (requireActivity() as MainActivity).applyLanguage(selected, if (onboarding) 0 else 3)
            }
        }
    }
}
