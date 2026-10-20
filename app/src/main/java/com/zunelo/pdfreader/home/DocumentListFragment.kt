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

class DocumentListFragment : Fragment(R.layout.fragment_document_list) {
    companion object {
        const val MODE_HOME = "home"; const val MODE_RECENT = "recent"; const val MODE_COLLECTION = "collection"
        private const val ARG_MODE = "mode"
        fun newInstance(mode: String) = DocumentListFragment().apply { arguments = Bundle().apply { putString(ARG_MODE, mode) } }
    }
    private lateinit var adapter: DocumentAdapter
    private var documents = emptyList<DocumentItem>()
    private var category: DocumentType? = null
    private var loadingDocuments = false
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
        setupCollapsingHeader(view, tools)
        setupTool(view.findViewById(R.id.mergeAction), R.drawable.action_merge, getString(R.string.merge_pdf)) { (requireActivity() as MainActivity).navigate(ToolListFragment.newInstance(ToolListFragment.MODE_MERGE)) }
        setupTool(view.findViewById(R.id.splitAction), R.drawable.action_split, getString(R.string.split_pdf)) { (requireActivity() as MainActivity).navigate(ToolListFragment.newInstance(ToolListFragment.MODE_SPLIT)) }
        setupTool(view.findViewById(R.id.printAction), R.drawable.action_print, getString(R.string.print)) { (requireActivity() as MainActivity).navigate(ToolListFragment.newInstance(ToolListFragment.MODE_PRINT)) }
        view.findViewById<RecyclerView>(R.id.categoryList).apply { layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false); adapter = CategoryAdapter { category = it; render() } }
        adapter = DocumentAdapter(
            onOpen = { open(it) },
            onFavorite = {
                DocumentStore.toggleFavorite(requireContext(), it)
                if (mode == MODE_COLLECTION) render() else adapter.refreshFavorite(it)
            },
            onMore = { MoreDialogFragment.newInstance(it).show(parentFragmentManager, "more") }
        )
        view.findViewById<RecyclerView>(R.id.documentList).apply { layoutManager = LinearLayoutManager(requireContext()); adapter = this@DocumentListFragment.adapter; itemAnimator = null }
        parentFragmentManager.setFragmentResultListener(FileDialogs.RESULT_CHANGED, viewLifecycleOwner) { _, _ -> loadDocuments() }
        view.findViewById<View>(R.id.stateAction).setOnClickListener { accessFlow.request() }
        title.alpha = 0f; title.translationY = requireContext().dp(6).toFloat()
        title.animate().alpha(1f).translationY(0f).setDuration(240).start()
        if (tools.visibility == View.VISIBLE) {
            tools.alpha = 0f; tools.translationY = requireContext().dp(8).toFloat()
            tools.animate().alpha(1f).translationY(0f).setStartDelay(50).setDuration(260).start()
        }
        loadDocuments()
    }

    override fun onResume() { super.onResume(); accessFlow.onResume(); if (view != null && DocumentStore.hasAccess(requireContext())) loadDocuments() }

    private fun setupTool(root: View, icon: Int, label: String, click: () -> Unit) {
        root.findViewById<ImageView>(R.id.actionIcon).setImageResource(icon); root.findViewById<TextView>(R.id.actionLabel).text = label; root.setOnClickListener { click() }
    }

    private fun setupCollapsingHeader(root: View, tools: View) {
        val appBar = root.findViewById<AppBarLayout>(R.id.documentAppBar)
        if (mode != MODE_HOME) {
            appBar.setExpanded(false, false)
            return
        }
        appBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { layout, offset ->
            val range = layout.totalScrollRange.takeIf { it > 0 } ?: return@OnOffsetChangedListener
            val progress = (abs(offset).toFloat() / range).coerceIn(0f, 1f)
            tools.alpha = 1f - progress
            tools.scaleX = 1f - progress * 0.025f
            tools.scaleY = 1f - progress * 0.025f
        })
    }

    private fun loadDocuments() {
        if (loadingDocuments) return
        val currentView = view ?: return
        loadingDocuments = true
        currentView.findViewById<View>(R.id.loading).visibility = View.VISIBLE
        currentView.findViewById<View>(R.id.emptyState).visibility = View.GONE
        Thread {
            val result = DocumentStore.scan(requireContext())
            activity?.runOnUiThread {
                loadingDocuments = false
                if (!isAdded || view == null) return@runOnUiThread
                documents = result
                render()
            }
        }.start()
    }

    private fun render() {
        val root = view ?: return
        root.findViewById<View>(R.id.loading).visibility = View.GONE
        val access = DocumentStore.hasAccess(requireContext())
        val source = when (mode) {
            MODE_RECENT -> { val map = documents.associateBy { it.uri.toString() }; DocumentStore.recentUris(requireContext()).mapNotNull(map::get) }
            MODE_COLLECTION -> documents.filter { DocumentStore.isFavorite(requireContext(), it) }
            else -> documents
        }
        val filtered = category?.let { type -> source.filter { it.type == type } } ?: source
        adapter.submitList(filtered)
        val state = root.findViewById<View>(R.id.emptyState)
        if (filtered.isNotEmpty()) { state.visibility = View.GONE; return }
        state.visibility = View.VISIBLE
        val image = state.findViewById<ImageView>(R.id.stateImage)
        val title = state.findViewById<TextView>(R.id.stateTitle)
        val message = state.findViewById<TextView>(R.id.stateMessage)
        val action = state.findViewById<TextView>(R.id.stateAction)
        if (!access && mode == MODE_HOME) {
            image.setImageResource(R.drawable.state_no_permission); title.setText(R.string.storage_access_title); message.setText(R.string.storage_access_message); action.setText(R.string.storage_access_action); action.visibility = View.VISIBLE
        } else {
            image.setImageResource(R.drawable.state_no_files)
            title.text = if (category == null) getString(R.string.nothing_here) else getString(R.string.no_type_files, category?.label)
            message.setText(if (!access) R.string.permission_home_hint else if (mode == MODE_COLLECTION) R.string.collection_hint else R.string.documents_hint)
            action.visibility = if (mode == MODE_HOME) View.VISIBLE else View.GONE; action.setText(R.string.storage_access_action)
        }
    }

    private fun open(item: DocumentItem) { DocumentStore.markRecent(requireContext(), item); (requireActivity() as MainActivity).navigate(ViewerFragment.newInstance(item)) }
}
