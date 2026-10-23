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
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class ScanReviewFragment : Fragment(R.layout.fragment_scan_review) {
    companion object {
        private const val ARG_PATHS = "paths"
        fun newInstance(paths: ArrayList<String>) = ScanReviewFragment().apply {
            arguments = Bundle().apply { putStringArrayList(ARG_PATHS, paths) }
        }
    }

    private val nameRequest = "scan_output_name"
    private lateinit var adapter: ScanPageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentManager.setFragmentResultListener(nameRequest, this) { _, result ->
            result.getString(OutputNameDialogFragment.RESULT_NAME)?.let(::createPdf)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val paths = requireArguments().getStringArrayList(ARG_PATHS).orEmpty()
        val toolbar = view.findViewById<View>(R.id.toolbar)
        toolbar.findViewById<TextView>(R.id.toolbarTitle).setText(R.string.scan_pages)
        toolbar.findViewById<View>(R.id.backButton).setOnClickListener { (requireActivity() as MainActivity).back() }
        adapter = ScanPageAdapter(paths) { updateAction(view) }
        view.findViewById<RecyclerView>(R.id.scanPages).apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@ScanReviewFragment.adapter
            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0) {
                override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean =
                    this@ScanReviewFragment.adapter.move(source.bindingAdapterPosition, target.bindingAdapterPosition)
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
                override fun isLongPressDragEnabled() = true
            }).attachToRecyclerView(this)
        }
        view.findViewById<View>(R.id.createScanPdf).setOnClickListener { requestName() }
        updateAction(view)
    }

    private fun updateAction(root: View = requireView()) {
        val count = adapter.selectedPaths().size
        root.findViewById<TextView>(R.id.scanHint).text = resources.getQuantityString(R.plurals.scan_selected_pages, count, count)
        root.findViewById<View>(R.id.createScanPdf).isEnabled = count > 0
    }

    private fun requestName() {
        val defaultName = PdfOutputNames.scan(requireContext())
        OutputNameDialogFragment.newInstance(nameRequest, defaultName, getString(R.string.scan_output_name_title))
            .show(parentFragmentManager, "scan_name")
    }

    private fun createPdf(name: String) {
        val paths = adapter.selectedPaths()
        if (paths.isEmpty()) return
        val adStartedAt = (requireActivity() as MainActivity).beginCreationAd(com.zunelo.pdfreader.ads.AdScenes.CREATE_PDF)
        ProcessingDialogFragment.newInstance(getString(R.string.creating_pdf)).show(parentFragmentManager, "scan_creating")
        val context = requireContext().applicationContext
        Thread {
            val result = runCatching { PdfTools.createFromImages(context, paths, name) }
            activity?.runOnUiThread {
                (parentFragmentManager.findFragmentByTag("scan_creating") as? ProcessingDialogFragment)?.dismissAllowingStateLoss()
                result.onSuccess { (requireActivity() as MainActivity).showCreationResult(it, "created", com.zunelo.pdfreader.ads.AdScenes.CREATE_PDF, adStartedAt) }
                    .onFailure { Toast.makeText(requireContext(), R.string.create_pdf_failed, Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    override fun onDestroyView() {
        if (::adapter.isInitialized) adapter.release()
        super.onDestroyView()
    }
}
