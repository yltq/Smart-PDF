package com.zunelo.pdfreader.pdf

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

class PdfPageManageFragment : Fragment(R.layout.fragment_pdf_page_manage) {
    companion object { fun newInstance(item: DocumentItem) = PdfPageManageFragment().apply { arguments = Bundle().putDocument(item) } }
    private lateinit var item: DocumentItem
    private lateinit var adapter: PdfPageManageAdapter
    private var loader: PdfPageLoader? = null
    private val saveRequest = "page_manage_save"

    override fun onCreate(state: Bundle?) { super.onCreate(state); item = requireArguments().document() ?: error("Missing PDF"); parentFragmentManager.setFragmentResultListener(saveRequest, this) { _, data -> data.getString(OutputNameDialogFragment.RESULT_NAME)?.let(::save) } }
    override fun onViewCreated(view: View, state: Bundle?) {
        val toolbar = view.findViewById<View>(R.id.toolbar); toolbar.findViewById<TextView>(R.id.toolbarTitle).setText(R.string.organize_pages); toolbar.findViewById<View>(R.id.backButton).setOnClickListener { (requireActivity() as MainActivity).back() }
        val width = (resources.displayMetrics.widthPixels / 2).coerceAtMost(720)
        loader = PdfPageLoader(requireContext().applicationContext, item, width, { count -> setupList(view, count) }, { Toast.makeText(requireContext(), R.string.pdf_open_failed, Toast.LENGTH_SHORT).show(); (requireActivity() as MainActivity).back() }).also(PdfPageLoader::open)
    }
    private fun setupList(view: View, count: Int) {
        adapter = PdfPageManageAdapter(requireNotNull(loader), count) { updateHint() }
        view.findViewById<RecyclerView>(R.id.managePages).apply { layoutManager = GridLayoutManager(requireContext(), 2); adapter = this@PdfPageManageFragment.adapter; ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0) { override fun onMove(rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = this@PdfPageManageFragment.adapter.move(source.bindingAdapterPosition, target.bindingAdapterPosition); override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit }).attachToRecyclerView(this) }
        view.findViewById<View>(R.id.rotatePages).setOnClickListener { adapter.rotateSelected() }
        view.findViewById<View>(R.id.deletePages).setOnClickListener { if (!adapter.deleteSelected()) Toast.makeText(requireContext(), R.string.select_pages_to_delete, Toast.LENGTH_SHORT).show() }
        view.findViewById<View>(R.id.savePages).setOnClickListener { OutputNameDialogFragment.newInstance(saveRequest, PdfOutputNames.organized(requireContext()), getString(R.string.save_organized_pdf)).show(parentFragmentManager, "organize_name") }
        updateHint()
    }
    private fun updateHint() { if (::adapter.isInitialized) view?.findViewById<TextView>(R.id.pageManageHint)?.text = getString(R.string.page_manage_selection, adapter.selectedCount()) }
    private fun save(name: String) {
        val adStartedAt = (requireActivity() as MainActivity).beginCreationAd(com.zunelo.pdfreader.ads.AdScenes.CREATE_PDF)
        val plans = adapter.pages.map { it.copy() }; ProcessingDialogFragment.newInstance(getString(R.string.saving_pdf)).show(parentFragmentManager, "organize_saving"); val context = requireContext().applicationContext
        Thread { val result = runCatching { PdfTools.organizePages(context, item, plans, name) }; activity?.runOnUiThread { (parentFragmentManager.findFragmentByTag("organize_saving") as? ProcessingDialogFragment)?.dismissAllowingStateLoss(); result.onSuccess { (requireActivity() as MainActivity).showCreationResult(it, "organized", com.zunelo.pdfreader.ads.AdScenes.CREATE_PDF, adStartedAt) }.onFailure { Toast.makeText(requireContext(), R.string.save_pdf_failed, Toast.LENGTH_SHORT).show() } } }.start()
    }
    override fun onDestroyView() { loader?.close(); loader = null; super.onDestroyView() }
}
