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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PdfWatermarkDialogFragment : BottomSheetDialogFragment() {
    companion object { fun newInstance(item: DocumentItem) = PdfWatermarkDialogFragment().apply { arguments = Bundle().putDocument(item) } }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) = inflater.inflate(R.layout.dialog_rename, container, false)
    override fun onViewCreated(view: View, state: Bundle?) {
        val item = requireArguments().document() ?: return; val input = view.findViewById<EditText>(R.id.renameInput)
        view.findViewById<TextView>(R.id.renameTitle).setText(R.string.add_watermark); view.findViewById<TextView>(R.id.renameMessage).setText(R.string.watermark_hint); input.hint = getString(R.string.watermark_text)
        view.findViewById<TextView>(R.id.cancelRename).apply { setText(R.string.cancel); setOnClickListener { dismiss() } }
        view.findViewById<TextView>(R.id.confirmRename).apply { setText(R.string.add); setOnClickListener { val text = input.text.toString().trim(); if (text.isNotEmpty()) process(item, text) } }
    }
    private fun process(item: DocumentItem, text: String) {
        val host = requireActivity() as MainActivity
        val manager = parentFragmentManager
        val context = requireContext().applicationContext
        val adStartedAt = host.beginCreationAd(com.zunelo.pdfreader.ads.AdScenes.CREATE_PDF)
        val loading = ProcessingDialogFragment.newInstance(getString(R.string.adding_watermark))
        dismiss()
        loading.show(manager, "watermark_saving")
        Thread {
            val result = runCatching { PdfTools.addWatermark(context, item, text) }
            host.runOnUiThread {
                (manager.findFragmentByTag("watermark_saving") as? ProcessingDialogFragment)?.dismissAllowingStateLoss()
                result.onSuccess { host.showCreationResult(it, "watermarked", com.zunelo.pdfreader.ads.AdScenes.CREATE_PDF, adStartedAt) }
                    .onFailure { Toast.makeText(host, R.string.save_pdf_failed, Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }
}
