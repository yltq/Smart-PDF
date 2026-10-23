package com.zunelo.pdfreader.home

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

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import com.zunelo.pdfreader.ads.AdScenes
import com.zunelo.pdfreader.ads.PageAdRefreshable
import com.zunelo.pdfreader.ads.SmartPdfAds

class SettingsFragment : Fragment(R.layout.fragment_settings), PageAdRefreshable {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { render() }
    private fun render() {
        val rows = mutableListOf(
            SettingRow(R.drawable.ic_setting_language, getString(R.string.language), getString(R.string.language_setting_subtitle)) { (requireActivity() as MainActivity).navigate(LanguageFragment.newInstance(false)) },
            SettingRow(R.drawable.ic_setting_privacy, getString(R.string.privacy_policy), getString(R.string.privacy_setting_subtitle)) { (requireActivity() as MainActivity).navigate(PrivacyFragment()) },
            SettingRow(R.drawable.ic_setting_share, getString(R.string.share_app), getString(R.string.share_setting_subtitle)) { shareAppStoreLink() }
        )
        view?.findViewById<RecyclerView>(R.id.settingsList)?.apply { layoutManager = LinearLayoutManager(requireContext()); adapter = SettingsAdapter(rows) }
    }

    private fun shareAppStoreLink() {
        val storeLink = "https://play.google.com/store/apps/details?id=${requireContext().packageName}"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, storeLink)
        }
        (requireActivity() as MainActivity).suppressNextPageAdRefresh()
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)))
    }

    override fun refreshPageAds() {
        val container = view?.findViewById<ViewGroup>(R.id.tabNativeAd) ?: return
        SmartPdfAds.bindNative(requireActivity(), AdScenes.HOME_NATIVE, container)
    }

    override fun onDestroyView() {
        view?.findViewById<ViewGroup>(R.id.tabNativeAd)?.let(SmartPdfAds::release)
        super.onDestroyView()
    }
}
