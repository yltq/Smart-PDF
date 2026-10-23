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

import android.graphics.PointF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class PdfMarkupFragment : Fragment(R.layout.fragment_pdf_markup) {
    companion object { fun newInstance(item: DocumentItem) = PdfMarkupFragment().apply { arguments = Bundle().putDocument(item) } }
    private lateinit var item: DocumentItem
    private lateinit var markup: PdfMarkupView
    private var loader: PdfPageLoader? = null
    private var pageCount = 0
    private var currentPage = 0
    private var pendingTextPoint: PointF? = null
    private val textRequest = "markup_text"
    private val saveRequest = "markup_save"

    override fun onCreate(state: Bundle?) {
        super.onCreate(state); item = requireArguments().document() ?: error("Missing PDF")
        parentFragmentManager.setFragmentResultListener(saveRequest, this) { _, data -> data.getString(OutputNameDialogFragment.RESULT_NAME)?.let(::save) }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        markup = view.findViewById(R.id.markupLayer)
        val toolbar = view.findViewById<View>(R.id.toolbar)
        toolbar.findViewById<TextView>(R.id.toolbarTitle).setText(R.string.annotate_pdf)
        toolbar.findViewById<View>(R.id.backButton).setOnClickListener { (requireActivity() as MainActivity).back() }
        toolbar.findViewById<TextView>(R.id.toolbarAction).apply { text = getString(R.string.save); textSize = 14f; setTextColor(requireContext().getColor(R.color.brand_red)); setOnClickListener { requestSave() } }
        parentFragmentManager.setFragmentResultListener(textRequest, viewLifecycleOwner) { _, data ->
            val point = pendingTextPoint
            val text = data.getString(AnnotationTextDialogFragment.RESULT_TEXT)
            if (point != null && !text.isNullOrBlank()) markup.addText(point, text)
            pendingTextPoint = null
        }
        markup.onTextRequested = { point ->
            pendingTextPoint = point
            if (parentFragmentManager.findFragmentByTag("annotation_text") == null) {
                AnnotationTextDialogFragment.newInstance(textRequest).show(parentFragmentManager, "annotation_text")
            }
        }
        view.findViewById<View>(R.id.previousPage).setOnClickListener { showPage((currentPage - 1).coerceAtLeast(0)) }
        view.findViewById<View>(R.id.nextPage).setOnClickListener { showPage((currentPage + 1).coerceAtMost(pageCount - 1)) }
        addTools(view.findViewById(R.id.markupTools))
        val width = (resources.displayMetrics.widthPixels - requireContext().dp(16)).coerceAtMost(1440)
        loader = PdfPageLoader(requireContext().applicationContext, item, width, { count -> pageCount = count; showPage(0) }, { Toast.makeText(requireContext(), R.string.pdf_open_failed, Toast.LENGTH_SHORT).show(); (requireActivity() as MainActivity).back() }).also(PdfPageLoader::open)
    }

    private fun addTools(container: LinearLayout) {
        val tools = listOf(
            Triple(null, R.string.move_page, R.drawable.ic_markup_pan),
            Triple(MarkupType.PEN, R.string.pen, R.drawable.ic_markup_pen),
            Triple(MarkupType.HIGHLIGHT, R.string.highlight, R.drawable.ic_markup_highlight),
            Triple(MarkupType.UNDERLINE, R.string.underline, R.drawable.ic_markup_underline),
            Triple(MarkupType.STRIKE, R.string.strikeout, R.drawable.ic_markup_strike),
            Triple(MarkupType.TEXT, R.string.add_text, R.drawable.ic_markup_text),
            Triple(MarkupType.SIGNATURE, R.string.signature, R.drawable.ic_markup_signature)
        )
        tools.forEachIndexed { index, (mode, label, icon) ->
            val tool = LayoutInflater.from(requireContext()).inflate(R.layout.item_markup_tool, container, false) as ImageView
            tool.setImageResource(icon)
            tool.contentDescription = getString(label)
            tool.isSelected = index == 0
            tool.setOnClickListener {
                markup.panEnabled = mode == null
                if (mode != null) markup.mode = mode
                tools.indices.forEach { toolIndex -> container.getChildAt(toolIndex).isSelected = toolIndex == index }
                showToolHint(if (mode == MarkupType.TEXT) R.string.tap_to_place_text else label)
            }
            container.addView(tool)
        }
        addActionTool(container, R.drawable.ic_markup_undo, R.string.undo) { markup.undo() }
        addActionTool(container, R.drawable.ic_markup_redo, R.string.redo) { markup.redo() }
    }

    private fun addActionTool(container: LinearLayout, icon: Int, label: Int, action: () -> Unit) {
        val tool = LayoutInflater.from(requireContext()).inflate(R.layout.item_markup_tool, container, false) as ImageView
        tool.setImageResource(icon)
        tool.contentDescription = getString(label)
        tool.setOnClickListener { action(); showToolHint(label) }
        container.addView(tool)
    }

    private fun showToolHint(label: Int): Boolean {
        Toast.makeText(requireContext(), label, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun showPage(index: Int) {
        if (index !in 0 until pageCount) return
        currentPage = index; markup.pageIndex = index; markup.invalidate()
        view?.findViewById<TextView>(R.id.markupPageNumber)?.text = getString(R.string.pdf_page_progress, index + 1, pageCount)
        view?.findViewById<View>(R.id.markupLoading)?.visibility = View.VISIBLE
        loader?.load(index) { bitmap -> if (currentPage == index && bitmap != null) { markup.setPageBitmap(bitmap); view?.findViewById<View>(R.id.markupLoading)?.visibility = View.GONE } }
    }

    private fun requestSave() {
        if (markup.markups.isEmpty()) { Toast.makeText(requireContext(), R.string.no_annotations, Toast.LENGTH_SHORT).show(); return }
        OutputNameDialogFragment.newInstance(saveRequest, PdfOutputNames.annotated(requireContext()), getString(R.string.save_annotated_pdf)).show(parentFragmentManager, "markup_name")
    }

    private fun save(name: String) {
        val adStartedAt = (requireActivity() as MainActivity).beginCreationAd(com.zunelo.pdfreader.ads.AdScenes.CREATE_PDF)
        val edits = markup.markups.toList(); ProcessingDialogFragment.newInstance(getString(R.string.saving_pdf)).show(parentFragmentManager, "markup_saving"); val context = requireContext().applicationContext
        Thread { val result = runCatching { PdfTools.applyMarkups(context, item, edits, name) }; activity?.runOnUiThread { (parentFragmentManager.findFragmentByTag("markup_saving") as? ProcessingDialogFragment)?.dismissAllowingStateLoss(); result.onSuccess { (requireActivity() as MainActivity).showCreationResult(it, "annotated", com.zunelo.pdfreader.ads.AdScenes.CREATE_PDF, adStartedAt) }.onFailure { Toast.makeText(requireContext(), R.string.save_pdf_failed, Toast.LENGTH_SHORT).show() } } }.start()
    }

    override fun onDestroyView() { loader?.close(); loader = null; super.onDestroyView() }
}
