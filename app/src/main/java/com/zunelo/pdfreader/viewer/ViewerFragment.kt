package com.zunelo.pdfreader.viewer

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
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub

class ViewerFragment : Fragment(R.layout.fragment_viewer) {
    companion object { fun newInstance(item: DocumentItem) = ViewerFragment().apply { arguments = Bundle().putDocument(item) } }
    private lateinit var item: DocumentItem
    private var pageLoader: PdfPageLoader? = null

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); item = requireArguments().document() ?: error("Missing document") }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        SmartPdfSignalHub.recordCustomSignal("file_detail_show")
        parentFragmentManager.setFragmentResultListener(FileDialogs.RESULT_VIEWER_CHANGED, viewLifecycleOwner) { _, result ->
            when (result.getString(FileDialogs.RESULT_ACTION)) {
                FileDialogs.ACTION_DELETED -> (requireActivity() as MainActivity).back()
                FileDialogs.ACTION_RENAMED -> result.document()?.let { renamed ->
                    item = renamed
                    view.findViewById<TextView>(R.id.viewerTitle).text = renamed.name
                    view.findViewById<TextView>(R.id.viewerSubtitle).text = viewerSubtitle(renamed)
                }
            }
        }
        view.findViewById<TextView>(R.id.viewerTitle).text = item.name
        view.findViewById<TextView>(R.id.viewerSubtitle).text = viewerSubtitle(item)
        view.findViewById<View>(R.id.backButton).setOnClickListener { (requireActivity() as MainActivity).back() }
        view.findViewById<View>(R.id.moreButton).setOnClickListener { MoreDialogFragment.newInstance(item, fromViewer = true).show(parentFragmentManager, "more") }
        view.findViewById<RecyclerView>(R.id.viewerPages).layoutManager = LinearLayoutManager(requireContext())
        view.findViewById<View>(R.id.openExternal).setOnClickListener { openExternally() }
        DocumentStore.markRecent(requireContext(), item)
        when (item.type) { DocumentType.PDF -> renderPdf(); DocumentType.WORD, DocumentType.EXCEL, DocumentType.PPT -> renderOffice(); DocumentType.OTHER -> renderPlainOrFallback() }
    }

    private fun renderPdf() {
        val width = (resources.displayMetrics.widthPixels - requireContext().dp(16)).coerceAtMost(1440)
        pageLoader = PdfPageLoader(requireContext().applicationContext, item, width,
            onReady = { count ->
                view?.findViewById<RecyclerView>(R.id.viewerPages)?.adapter = ViewerPageAdapter(requireNotNull(pageLoader), count)
                view?.findViewById<View>(R.id.viewerLoading)?.visibility = View.GONE
                view?.findViewById<TextView>(R.id.viewerSubtitle)?.text = resources.getQuantityString(R.plurals.pdf_viewer_summary, count, count, DocumentStore.formatSize(item.size))
            },
            onError = { showFallback(getString(R.string.pdf_open_failed)) }
        ).also(PdfPageLoader::open)
    }
    private fun renderOffice() {
        val context = requireContext().applicationContext
        view?.findViewById<View>(R.id.viewerPages)?.visibility = View.GONE
        Thread {
            val result = runCatching { OfficePreview.html(context, item) }
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                view?.findViewById<View>(R.id.viewerLoading)?.visibility = View.GONE
                result.onSuccess { html ->
                    view?.findViewById<WebView>(R.id.officePreview)?.apply {
                        visibility = View.VISIBLE
                        setBackgroundColor(Color.rgb(253, 253, 253))
                        settings.apply {
                            javaScriptEnabled = false
                            allowFileAccess = false
                            allowContentAccess = false
                            builtInZoomControls = true
                            displayZoomControls = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            defaultFontSize = 16
                        }
                        loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    }
                }.onFailure { showFallback(getString(R.string.office_preview_unavailable)) }
            }
        }.start()
    }
    private fun renderPlainOrFallback() { if (item.name.substringAfterLast('.', "").lowercase() in setOf("txt", "csv", "rtf")) renderOffice() else showFallback(getString(R.string.open_with_compatible_app)) }
    private fun viewerSubtitle(document: DocumentItem) = "${document.type.label} · ${DocumentStore.formatSize(document.size)}"
    private fun showFallback(message: String) { val root = view ?: return; root.findViewById<View>(R.id.viewerLoading).visibility = View.GONE; root.findViewById<View>(R.id.viewerPages).visibility = View.GONE; root.findViewById<View>(R.id.officePreview).visibility = View.GONE; root.findViewById<View>(R.id.viewerFallback).visibility = View.VISIBLE; root.findViewById<ImageView>(R.id.fallbackIcon).setImageResource(documentIcon(item.type)); root.findViewById<TextView>(R.id.fallbackName).text = item.name; root.findViewById<TextView>(R.id.fallbackMessage).text = message }
    private fun openExternally() { runCatching { (requireActivity() as MainActivity).suppressNextPageAdRefresh(); startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(DocumentStore.shareUri(requireContext(), item), item.mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, getString(R.string.open_document))) }.onFailure { Toast.makeText(requireContext(), R.string.no_compatible_app, Toast.LENGTH_SHORT).show() } }

    override fun onDestroyView() {
        pageLoader?.close()
        pageLoader = null
        view?.findViewById<WebView>(R.id.officePreview)?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroyView()
    }
}
