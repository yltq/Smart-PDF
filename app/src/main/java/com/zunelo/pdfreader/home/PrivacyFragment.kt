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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment

class PrivacyFragment : Fragment(R.layout.fragment_privacy) {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<View>(R.id.toolbar); toolbar.findViewById<TextView>(R.id.toolbarTitle).setText(R.string.privacy_policy); toolbar.findViewById<View>(R.id.backButton).setOnClickListener { (requireActivity() as MainActivity).back() }
        val progress = view.findViewById<ProgressBar>(R.id.webProgress)
        view.findViewById<WebView>(R.id.privacyWeb).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true; webViewClient = WebViewClient(); webChromeClient = object : WebChromeClient() { override fun onProgressChanged(webView: WebView?, value: Int) { progress.progress = value; progress.visibility = if (value >= 100) View.GONE else View.VISIBLE } }; loadUrl(getString(R.string.privacy_url)) }
    }
}
