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

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SplitDetailFragment : Fragment(R.layout.fragment_split_detail) {
    companion object { fun newInstance(item: DocumentItem) = SplitDetailFragment().apply { arguments = Bundle().putDocument(item) } }
    private lateinit var item: DocumentItem
    private lateinit var adapter: PageSelectionAdapter
    private lateinit var action: TextView
    private var selected = emptySet<Int>()
    private var pendingPages = emptySet<Int>()
    private var splitTask: FixedDurationTask<DocumentItem>? = null
    private val splitNameRequest = "split_output_name"

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); item = requireArguments().document() ?: error("Missing document") }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<View>(R.id.toolbar)
        toolbar.findViewById<TextView>(R.id.toolbarTitle).setText(R.string.select_pages)
        toolbar.findViewById<View>(R.id.backButton).setOnClickListener { (requireActivity() as MainActivity).back() }
        action = view.findViewById<TextView>(R.id.splitAction).apply { setOnClickListener { requestSplitName() } }
        adapter = PageSelectionAdapter { pages ->
            selected = pages
            action.isEnabled = pages.isNotEmpty()
            action.text = if (pages.isEmpty()) getString(R.string.split_selected_pages) else resources.getQuantityString(R.plurals.split_page_count, pages.size, pages.size)
        }
        parentFragmentManager.setFragmentResultListener(splitNameRequest, viewLifecycleOwner) { _, result ->
            result.getString(OutputNameDialogFragment.RESULT_NAME)?.let(::split)
        }
        view.findViewById<RecyclerView>(R.id.pageList).apply { layoutManager = GridLayoutManager(requireContext(), 3); adapter = this@SplitDetailFragment.adapter }
        renderPages()
    }

    private fun renderPages() {
        val resolver = requireContext().contentResolver
        Thread {
            runCatching {
                DocumentStore.openFileDescriptor(requireContext(), item)!!.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        for (index in 0 until renderer.pageCount) {
                            val page = renderer.openPage(index); val width = 320; val height = (width * page.height.toFloat() / page.width).toInt().coerceAtMost(460)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close()
                            activity?.runOnUiThread { if (isAdded) adapter.add(PagePreview(index, bitmap)) }
                        }
                    }
                }
            }.onFailure { activity?.runOnUiThread { if (isAdded) Toast.makeText(requireContext(), R.string.pdf_preview_failed, Toast.LENGTH_LONG).show() } }
        }.start()
    }

    private fun requestSplitName() {
        if (selected.isEmpty()) return
        pendingPages = selected.toSet()
        val defaultName = PdfOutputNames.split(requireContext())
        OutputNameDialogFragment.newInstance(splitNameRequest, defaultName, getString(R.string.split_output_name_title))
            .show(parentFragmentManager, "split_name")
    }

    private fun split(outputName: String) {
        if (pendingPages.isEmpty() || splitTask != null) return
        val adStartedAt = (requireActivity() as MainActivity).beginCreationAd(com.zunelo.pdfreader.ads.AdScenes.SPLIT_PDF)
        action.isEnabled = false
        ProcessingDialogFragment.newInstance(getString(R.string.splitting)).show(parentFragmentManager, "processing")
        val context = requireContext().applicationContext
        splitTask = FixedDurationTask(work = { PdfTools.split(context, item, pendingPages, outputName) }) { result ->
            splitTask = null
            (parentFragmentManager.findFragmentByTag("processing") as? ProcessingDialogFragment)?.dismissAllowingStateLoss()
            if (!isAdded) return@FixedDurationTask
            action.isEnabled = true
            result.onSuccess { (requireActivity() as MainActivity).showCreationResult(it, "split", com.zunelo.pdfreader.ads.AdScenes.SPLIT_PDF, adStartedAt) }
                .onFailure { Toast.makeText(requireContext(), R.string.split_failed, Toast.LENGTH_LONG).show() }
        }.also { it.start() }
    }

    override fun onDestroyView() { splitTask?.cancel(); splitTask = null; super.onDestroyView() }
}
