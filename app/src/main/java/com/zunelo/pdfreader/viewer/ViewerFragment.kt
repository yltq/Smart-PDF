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
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile

class ViewerFragment : Fragment(R.layout.fragment_viewer) {
    companion object { fun newInstance(item: DocumentItem) = ViewerFragment().apply { arguments = Bundle().putDocument(item) } }
    private lateinit var item: DocumentItem
    private var pageLoader: PdfPageLoader? = null
    private var mediaPlayer: ExoPlayer? = null

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
        when (item.type) {
            DocumentType.PDF -> renderPdf()
            DocumentType.WORD, DocumentType.EXCEL, DocumentType.PPT -> renderOffice()
            DocumentType.OTHER -> renderOther()
        }
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
    private fun renderOther() {
        val extension = item.name.substringAfterLast('.', "").lowercase()
        when {
            item.mime.startsWith("image/") -> renderImage()
            item.mime.startsWith("video/") || item.mime.startsWith("audio/") -> renderMedia()
            extension in setOf("txt", "csv", "rtf") -> renderOffice()
            extension == "zip" -> renderZip()
            extension == "apk" -> renderApk()
            else -> showFallback(getString(R.string.open_with_compatible_app))
        }
    }

    private fun renderImage() {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val file = DocumentStore.cacheFile(context, item)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                }.getOrNull()
            }
            if (!isAdded) return@launch
            view?.findViewById<View>(R.id.viewerLoading)?.visibility = View.GONE
            if (bitmap == null) {
                showFallback(getString(R.string.open_with_compatible_app))
            } else {
                view?.findViewById<View>(R.id.viewerPages)?.visibility = View.GONE
                view?.findViewById<ImageView>(R.id.imagePreview)?.apply {
                    visibility = View.VISIBLE
                    setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun renderMedia() {
        view?.findViewById<View>(R.id.viewerPages)?.visibility = View.GONE
        view?.findViewById<View>(R.id.viewerLoading)?.visibility = View.GONE
        val player = ExoPlayer.Builder(requireContext()).build().also { mediaPlayer = it }
        view?.findViewById<PlayerView>(R.id.mediaPreview)?.apply {
            visibility = View.VISIBLE
            this.player = player
        }
        player.setMediaItem(MediaItem.fromUri(item.uri))
        player.prepare()
    }

    private fun renderZip() {
        val context = requireContext().applicationContext
        val contentsLabel = getString(R.string.archive_contents)
        renderDetailsHtml {
            val file = DocumentStore.cacheFile(context, item)
            val entries = mutableListOf<String>()
            var totalEntries = 0
            ZipFile(file).use { zip ->
                val iterator = zip.entries()
                while (iterator.hasMoreElements()) {
                    val entry = iterator.nextElement()
                    totalEntries++
                    if (entries.size < 200) entries += entry.name
                }
            }
            buildString {
                append("<h2>").append(escaped(item.name)).append("</h2>")
                append("<p>").append(escaped(contentsLabel)).append(": ").append(totalEntries)
                    .append(" · ").append(DocumentStore.formatSize(item.size)).append("</p><hr>")
                entries.forEach { append("<div class='entry'>").append(escaped(it)).append("</div>") }
                if (totalEntries > entries.size) append("<p>… +").append(totalEntries - entries.size).append("</p>")
            }
        }
    }

    private fun renderApk() {
        val context = requireContext().applicationContext
        val versionLabel = getString(R.string.file_version)
        val sizeLabel = getString(R.string.file_size)
        val pathLabel = getString(R.string.file_path)
        renderDetailsHtml {
            val file = DocumentStore.cacheFile(context, item)
            val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            val appInfo = info?.applicationInfo?.apply {
                sourceDir = file.absolutePath
                publicSourceDir = file.absolutePath
            }
            val label = appInfo?.loadLabel(context.packageManager)?.toString().orEmpty().ifBlank { item.name }
            buildString {
                append("<h2>").append(escaped(label)).append("</h2>")
                append("<p>").append(escaped(info?.packageName.orEmpty())).append("</p>")
                append("<div class='card'><b>").append(escaped(item.name)).append("</b><br>")
                append(escaped(versionLabel)).append(": ").append(escaped(info?.versionName.orEmpty().ifBlank { "—" })).append("<br>")
                append(escaped(sizeLabel)).append(": ").append(DocumentStore.formatSize(item.size)).append("<br>")
                append(escaped(pathLabel)).append(": ").append(escaped(item.uri.path ?: item.uri.toString())).append("</div>")
            }
        }
    }

    private fun renderDetailsHtml(content: () -> String) {
        view?.findViewById<View>(R.id.viewerPages)?.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { content() } }
            if (!isAdded) return@launch
            result.onSuccess(::showDetailsHtml)
                .onFailure { showFallback(getString(R.string.open_with_compatible_app)) }
        }
    }

    private fun showDetailsHtml(body: String) {
        view?.findViewById<View>(R.id.viewerLoading)?.visibility = View.GONE
        view?.findViewById<WebView>(R.id.officePreview)?.apply {
            visibility = View.VISIBLE
            setBackgroundColor(Color.rgb(253, 253, 253))
            settings.javaScriptEnabled = false
            loadDataWithBaseURL(
                null,
                "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font-family:sans-serif;color:#202124;padding:18px}h2{word-break:break-word}.card,.entry{padding:12px;margin:8px 0;background:#f4f6f8;border-radius:10px;word-break:break-word}.entry{padding:9px 12px}</style></head><body>$body</body></html>",
                "text/html",
                "utf-8",
                null
            )
        }
    }

    private fun escaped(value: String): String = TextUtils.htmlEncode(value)
    private fun viewerSubtitle(document: DocumentItem) = "${document.type.label} · ${DocumentStore.formatSize(document.size)}"
    private fun showFallback(message: String) { val root = view ?: return; root.findViewById<View>(R.id.viewerLoading).visibility = View.GONE; root.findViewById<View>(R.id.viewerPages).visibility = View.GONE; root.findViewById<View>(R.id.officePreview).visibility = View.GONE; root.findViewById<View>(R.id.imagePreview).visibility = View.GONE; root.findViewById<View>(R.id.mediaPreview).visibility = View.GONE; root.findViewById<View>(R.id.viewerFallback).visibility = View.VISIBLE; root.findViewById<ImageView>(R.id.fallbackIcon).setImageResource(documentIcon(item.type)); root.findViewById<TextView>(R.id.fallbackName).text = item.name; root.findViewById<TextView>(R.id.fallbackMessage).text = "$message\n${item.uri.path ?: item.uri}" }
    private fun openExternally() { runCatching { (requireActivity() as MainActivity).suppressNextPageAdRefresh(); startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(DocumentStore.shareUri(requireContext(), item), item.mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, getString(R.string.open_document))) }.onFailure { Toast.makeText(requireContext(), R.string.no_compatible_app, Toast.LENGTH_SHORT).show() } }

    override fun onDestroyView() {
        pageLoader?.close()
        pageLoader = null
        mediaPlayer?.release()
        mediaPlayer = null
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
