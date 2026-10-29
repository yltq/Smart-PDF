package com.zunelo.pdfreader.tools

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
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.zunelo.pdfreader.ads.AdScenes
import com.zunelo.pdfreader.ads.PageAdRefreshable
import com.zunelo.pdfreader.ads.SmartPdfAds

class ToolListFragment : Fragment(R.layout.fragment_tool_list), PageAdRefreshable {
    companion object {
        const val MODE_MERGE = "merge"; const val MODE_SPLIT = "split"; const val MODE_PRINT = "print"; private const val ARG_MODE = "mode"
        fun newInstance(mode: String, preselected: DocumentItem? = null) = ToolListFragment().apply { arguments = Bundle().apply { putString(ARG_MODE, mode); preselected?.let(::putDocument) } }
    }
    private val mode get() = requireArguments().getString(ARG_MODE) ?: MODE_MERGE
    private lateinit var adapter: DocumentAdapter
    private lateinit var action: TextView
    private var documents = emptyList<DocumentItem>()
    private val selected = linkedSetOf<String>()
    private var pendingMerge = emptyList<DocumentItem>()
    private var mergeTask: FixedDurationTask<DocumentItem>? = null

    private val mergeNameRequest = "merge_output_name"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<View>(R.id.toolbar)
        toolbar.findViewById<TextView>(R.id.toolbarTitle).setText(when (mode) {
            MODE_MERGE -> R.string.merge_pdf
            MODE_PRINT -> R.string.print_pdf
            else -> R.string.split_pdf
        })
        toolbar.findViewById<View>(R.id.backButton).setOnClickListener { (requireActivity() as MainActivity).back() }
        lateinit var dragHelper: ItemTouchHelper
        adapter = DocumentAdapter(selectionMode = true, draggable = mode == MODE_MERGE, onDragStart = { dragHelper.startDrag(it) }, onOpen = { toggle(it) })
        view.findViewById<TextView>(R.id.toolHint).apply { visibility = if (mode == MODE_MERGE) View.VISIBLE else View.GONE; if (mode == MODE_MERGE) setText(R.string.merge_order_hint) }
        view.findViewById<RecyclerView>(R.id.pdfList).apply {
            layoutManager = LinearLayoutManager(requireContext()); adapter = this@ToolListFragment.adapter
            if (mode == MODE_MERGE) { dragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) =
                    this@ToolListFragment.adapter.move(source.bindingAdapterPosition, target.bindingAdapterPosition)
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
                override fun isLongPressDragEnabled() = false
            }); dragHelper.attachToRecyclerView(this) }
        }
        action = view.findViewById<TextView>(R.id.toolAction).apply {
            setText(when (mode) { MODE_MERGE -> R.string.continue_action; MODE_PRINT -> R.string.print; else -> R.string.next })
            setOnClickListener { proceed() }
        }
        parentFragmentManager.setFragmentResultListener(mergeNameRequest, viewLifecycleOwner) { _, result ->
            result.getString(OutputNameDialogFragment.RESULT_NAME)?.let(::merge)
        }
        bindListNative()
        load()
    }

    private fun bindListNative() {
        val container = view?.findViewById<ViewGroup>(R.id.toolNativeAd) ?: return
        if (mode == MODE_PRINT) {
            SmartPdfAds.release(container)
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        SmartPdfAds.bindNative(requireActivity(), AdScenes.MERGE_LIST_NATIVE, container)
    }

    private fun load() {
        val context = requireContext().applicationContext
        Thread { val result = DocumentStore.scan(context).filter { it.type == DocumentType.PDF }; activity?.runOnUiThread { if (!isAdded) return@runOnUiThread; documents = result; adapter.submitList(result); requireArguments().document()?.let { preset -> if (result.any { it.uri == preset.uri }) toggle(preset) } } }.start()
    }

    private fun toggle(item: DocumentItem) {
        if (mode != MODE_MERGE) selected.clear()
        if (!selected.remove(item.uri.toString())) {
            if (mode == MODE_MERGE && selected.size >= 9) { Toast.makeText(requireContext(), R.string.merge_selection_limit, Toast.LENGTH_SHORT).show(); return }
            selected.add(item.uri.toString())
        }
        adapter.selectedUris = selected.toSet()
        val enabled = if (mode == MODE_MERGE) selected.size >= 2 else selected.size == 1
        action.isEnabled = enabled
    }

    private fun proceed() {
        val chosen = adapter.currentList.filter { selected.contains(it.uri.toString()) }
        if (mode == MODE_PRINT) {
            chosen.firstOrNull()?.let { PdfPrintHelper.print(requireActivity(), it) }
            return
        }
        if (mode == MODE_SPLIT) {
            chosen.firstOrNull()?.let(::openSplit)
            return
        }
        if (chosen.size < 2) return
        pendingMerge = chosen
        val defaultName = PdfOutputNames.merge(requireContext())
        OutputNameDialogFragment.newInstance(mergeNameRequest, defaultName, getString(R.string.merge_output_name_title))
            .show(parentFragmentManager, "merge_name")
    }

    private fun openSplit(item: DocumentItem) {
        val context = requireContext().applicationContext
        Thread {
            val count = runCatching { PdfTools.pageCount(context, item) }.getOrDefault(0)
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                if (count == 1) Toast.makeText(requireContext(), R.string.single_page_cannot_split, Toast.LENGTH_LONG).show()
                else if (count > 1) (requireActivity() as MainActivity).navigate(SplitDetailFragment.newInstance(item))
                else Toast.makeText(requireContext(), R.string.pdf_preview_failed, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun merge(outputName: String) {
        if (pendingMerge.size < 2 || mergeTask != null) return
        val adStartedAt = (requireActivity() as MainActivity).beginCreationAd(com.zunelo.pdfreader.ads.AdScenes.MERGE_PDF)
        action.isEnabled = false
        ProcessingDialogFragment.newInstance(getString(R.string.merging)).show(parentFragmentManager, "processing")
        val context = requireContext().applicationContext
        mergeTask = FixedDurationTask(work = { PdfTools.merge(context, pendingMerge, outputName) }) { result ->
            mergeTask = null
            (parentFragmentManager.findFragmentByTag("processing") as? ProcessingDialogFragment)?.dismissAllowingStateLoss()
            if (!isAdded) return@FixedDurationTask
            action.isEnabled = true
            result.onSuccess { (requireActivity() as MainActivity).showCreationResult(it, "merged", com.zunelo.pdfreader.ads.AdScenes.MERGE_PDF, adStartedAt) }
                .onFailure { Toast.makeText(requireContext(), R.string.merge_failed, Toast.LENGTH_LONG).show() }
        }.also { it.start() }
    }

    override fun refreshPageAds() = bindListNative()

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        val container = view?.findViewById<ViewGroup>(R.id.toolNativeAd) ?: return
        if (hidden) SmartPdfAds.release(container) else bindListNative()
    }

    override fun onDestroyView() {
        mergeTask?.cancel()
        mergeTask = null
        view?.findViewById<ViewGroup>(R.id.toolNativeAd)?.let(SmartPdfAds::release)
        super.onDestroyView()
    }
}
