package com.zunelo.pdfreader.home

import android.animation.ObjectAnimator
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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import kotlin.math.abs
import android.view.ViewGroup
import com.zunelo.pdfreader.ads.AdScenes
import com.zunelo.pdfreader.ads.PageAdRefreshable
import com.zunelo.pdfreader.ads.SmartPdfAds
import android.view.animation.AccelerateDecelerateInterpolator
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub
import com.zunelo.pdfreader.cleanup.LargeFilesFragment

class DocumentListFragment : Fragment(R.layout.fragment_document_list), PageAdRefreshable {
    companion object {
        const val MODE_HOME = "home"; const val MODE_RECENT = "recent"; const val MODE_COLLECTION = "collection"
        private const val ARG_MODE = "mode"
        fun newInstance(mode: String) = DocumentListFragment().apply { arguments = Bundle().apply { putString(ARG_MODE, mode) } }
    }
    private lateinit var adapter: DocumentAdapter
    private var documents = emptyList<DocumentItem>()
    private var category: DocumentType? = null
    private var loadingDocuments = false
    private var reloadAfterCurrentScan = false
    private var lastAccessState: Boolean? = null
    private var hintAnimator: ObjectAnimator? = null
    private lateinit var accessFlow: StorageAccessFlow
    private val mode get() = requireArguments().getString(ARG_MODE) ?: MODE_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accessFlow = StorageAccessFlow(this, onGranted = { loadDocuments() }, onDenied = { if (isAdded) render() })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val title = view.findViewById<TextView>(R.id.pageTitle)
        title.setText(when (mode) { MODE_RECENT -> R.string.recent_files; MODE_COLLECTION -> R.string.collection; else -> R.string.app_name })
        val tools = view.findViewById<View>(R.id.toolActions)
        tools.visibility = if (mode == MODE_HOME) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.printTopAction).apply {
            visibility = if (mode == MODE_HOME) View.VISIBLE else View.GONE
            setOnClickListener {
                (requireActivity() as MainActivity).navigate(ToolListFragment.newInstance(ToolListFragment.MODE_PRINT))
            }
        }
        setupCollapsingHeader(view, tools)
        setupTool(view.findViewById(R.id.mergeAction), R.drawable.action_merge, getString(R.string.merge_pdf)) {
            SmartPdfSignalHub.recordCustomSignal("merge_click")
            (requireActivity() as MainActivity).navigate(ToolListFragment.newInstance(ToolListFragment.MODE_MERGE))
        }
        setupTool(view.findViewById(R.id.splitAction), R.drawable.action_split, getString(R.string.split_pdf)) {
            SmartPdfSignalHub.recordCustomSignal("split_click")
            (requireActivity() as MainActivity).navigate(ToolListFragment.newInstance(ToolListFragment.MODE_SPLIT))
        }
        setupTool(view.findViewById(R.id.largeFileAction), R.drawable.action_large_files, getString(R.string.large_files)) {
            (requireActivity() as MainActivity).navigate(LargeFilesFragment())
        }
        view.findViewById<RecyclerView>(R.id.categoryList).apply { layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false); adapter = CategoryAdapter { category = it; render(); refreshPageAds() } }
        adapter = DocumentAdapter(
            onOpen = { open(it) },
            onFavorite = {
                DocumentStore.toggleFavorite(requireContext(), it)
                if (mode == MODE_COLLECTION) render() else adapter.refreshFavorite(it)
            },
            onMore = { MoreDialogFragment.newInstance(it).show(parentFragmentManager, "more") }
        )
        view.findViewById<RecyclerView>(R.id.documentList).apply { layoutManager = LinearLayoutManager(requireContext()); adapter = this@DocumentListFragment.adapter; itemAnimator = null }
        parentFragmentManager.setFragmentResultListener(FileDialogs.RESULT_CHANGED, viewLifecycleOwner) { _, result ->
            applyFileChange(result)
        }
        view.findViewById<View>(R.id.stateAction).setOnClickListener { accessFlow.request() }
        title.alpha = 0f; title.translationY = requireContext().dp(6).toFloat()
        title.animate().alpha(1f).translationY(0f).setDuration(240).start()
        if (tools.visibility == View.VISIBLE) {
            tools.alpha = 0f; tools.translationY = requireContext().dp(8).toFloat()
            tools.animate().alpha(1f).translationY(0f).setStartDelay(50).setDuration(260).start()
        }
        loadDocuments()
    }

    override fun onResume() {
        super.onResume()
        accessFlow.onResume()
        if (view == null) return
        if (DocumentStore.hasAccess(requireContext())) {
            loadDocuments(showLoading = documents.isEmpty())
        } else {
            documents = emptyList()
            render()
        }
    }

    private fun setupTool(root: View, icon: Int, label: String, click: () -> Unit) {
        root.findViewById<ImageView>(R.id.actionIcon).setImageResource(icon); root.findViewById<TextView>(R.id.actionLabel).text = label; root.setOnClickListener { click() }
    }

    private fun setupCollapsingHeader(root: View, tools: View) {
        val appBar = root.findViewById<AppBarLayout>(R.id.documentAppBar)
        val hint = root.findViewById<TextView>(R.id.pullToolsHint)
        if (mode != MODE_HOME) {
            hint.visibility = View.GONE
            appBar.setExpanded(false, false)
            return
        }
        hint.setOnClickListener { appBar.setExpanded(true, true) }
        appBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { layout, offset ->
            val range = layout.totalScrollRange.takeIf { it > 0 } ?: return@OnOffsetChangedListener
            val progress = (abs(offset).toFloat() / range).coerceIn(0f, 1f)
            tools.alpha = 1f - progress
            tools.scaleX = 1f - progress * 0.025f
            tools.scaleY = 1f - progress * 0.025f
            updateToolsHint(hint, progress >= 0.88f)
        })
    }

    private fun updateToolsHint(hint: View, show: Boolean) {
        if (!show) {
            hintAnimator?.cancel()
            hint.visibility = View.GONE
            hint.translationY = 0f
            return
        }
        if (hint.visibility == View.VISIBLE) return
        hint.visibility = View.VISIBLE
        hint.alpha = 0f
        hint.animate().alpha(1f).setDuration(160).start()
        hintAnimator?.cancel()
        hintAnimator = ObjectAnimator.ofFloat(hint, View.TRANSLATION_Y, 0f, requireContext().dp(4).toFloat(), 0f).apply {
            duration = 760
            repeatCount = 2
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun loadDocuments(showLoading: Boolean = true) {
        if (loadingDocuments) {
            reloadAfterCurrentScan = true
            return
        }
        val currentView = view ?: return
        loadingDocuments = true
        if (showLoading) {
            currentView.findViewById<View>(R.id.contentOverlay).visibility = View.VISIBLE
            currentView.findViewById<View>(R.id.loading).visibility = View.VISIBLE
            currentView.findViewById<View>(R.id.emptyState).visibility = View.GONE
        }
        Thread {
            val result = DocumentStore.scan(requireContext())
            activity?.runOnUiThread {
                loadingDocuments = false
                if (!isAdded || view == null) return@runOnUiThread
                documents = result
                render()
                if (reloadAfterCurrentScan) {
                    reloadAfterCurrentScan = false
                    loadDocuments()
                }
            }
        }.start()
    }

    fun refreshDocuments() = loadDocuments()

    fun applyFileChange(result: Bundle) {
        when (result.getString(FileDialogs.RESULT_ACTION)) {
            FileDialogs.ACTION_RENAMED -> {
                val oldUri = result.getString(FileDialogs.RESULT_OLD_URI) ?: return
                val renamed = result.document() ?: return
                documents = documents.map { if (it.uri.toString() == oldUri) renamed else it }
                render()
            }
            FileDialogs.ACTION_DELETED -> {
                val deletedUri = result.getString(FileDialogs.RESULT_URI) ?: return
                documents = documents.filterNot { it.uri.toString() == deletedUri }
                render()
            }
            FileDialogs.ACTION_FAVORITE -> {
                val changedUri = result.getString(FileDialogs.RESULT_URI) ?: return
                if (mode == MODE_COLLECTION) {
                    render()
                } else {
                    documents.firstOrNull { it.uri.toString() == changedUri }?.let(adapter::refreshFavorite)
                }
            }
            else -> loadDocuments(showLoading = documents.isEmpty())
        }
    }

    private fun render() {
        val root = view ?: return
        root.findViewById<View>(R.id.loading).visibility = View.GONE
        val access = DocumentStore.hasAccess(requireContext())
        if (mode == MODE_HOME && lastAccessState != access) {
            root.findViewById<AppBarLayout>(R.id.documentAppBar).setExpanded(access, true)
            lastAccessState = access
        }
        val source = when (mode) {
            MODE_RECENT -> { val map = documents.associateBy { it.uri.toString() }; DocumentStore.recentUris(requireContext()).mapNotNull(map::get) }
            MODE_COLLECTION -> {
                val map = documents.associateBy { it.uri.toString() }
                DocumentStore.favoriteUris(requireContext()).mapNotNull(map::get)
            }
            else -> documents
        }
        val filtered = category?.let { type -> source.filter { it.type == type } } ?: source
        adapter.submitList(filtered)
        val overlay = root.findViewById<View>(R.id.contentOverlay)
        val state = root.findViewById<View>(R.id.emptyState)
        if (filtered.isNotEmpty()) {
            state.visibility = View.GONE
            overlay.visibility = View.GONE
            return
        }
        overlay.visibility = View.VISIBLE
        state.visibility = View.VISIBLE
        val image = state.findViewById<ImageView>(R.id.stateImage)
        val title = state.findViewById<TextView>(R.id.stateTitle)
        val message = state.findViewById<TextView>(R.id.stateMessage)
        val action = state.findViewById<TextView>(R.id.stateAction)
        if (!access) {
            image.setImageResource(R.drawable.state_no_permission); title.setText(R.string.storage_access_title); message.setText(R.string.storage_access_message); action.setText(R.string.storage_access_action); action.visibility = View.VISIBLE
        } else {
            image.setImageResource(R.drawable.state_no_files)
            title.text = if (category == null) getString(R.string.nothing_here) else getString(R.string.no_type_files, category?.label)
            message.setText(if (!access) R.string.permission_home_hint else if (mode == MODE_COLLECTION) R.string.collection_hint else R.string.documents_hint)
            action.visibility = if (mode == MODE_HOME) View.VISIBLE else View.GONE; action.setText(R.string.storage_access_action)
        }
    }

    private fun open(item: DocumentItem) {
        SmartPdfSignalHub.recordCustomSignal("home_all_docs_click")
        DocumentStore.markRecent(requireContext(), item)
        (requireActivity() as MainActivity).openDocumentWithAd(item)
    }

    override fun refreshPageAds() {
        val container = view?.findViewById<ViewGroup>(R.id.tabNativeAd) ?: return
        SmartPdfAds.bindNative(requireActivity(), AdScenes.HOME_NATIVE, container)
    }

    override fun onDestroyView() {
        hintAnimator?.cancel()
        hintAnimator = null
        view?.findViewById<ViewGroup>(R.id.tabNativeAd)?.let(SmartPdfAds::release)
        super.onDestroyView()
    }
}
