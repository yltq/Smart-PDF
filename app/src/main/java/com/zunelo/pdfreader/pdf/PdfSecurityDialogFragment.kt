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

class PdfSecurityDialogFragment : BottomSheetDialogFragment() {
    companion object { fun newInstance(item: DocumentItem) = PdfSecurityDialogFragment().apply { arguments = Bundle().putDocument(item) } }
    private var encrypted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) = inflater.inflate(R.layout.dialog_pdf_security, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        val item = requireArguments().document() ?: return
        val password = view.findViewById<EditText>(R.id.pdfPassword)
        val confirm = view.findViewById<EditText>(R.id.pdfPasswordConfirm)
        view.findViewById<View>(R.id.cancelSecurity).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.confirmSecurity).setOnClickListener {
            val value = password.text.toString()
            if (value.length < 4) { password.error = getString(R.string.password_too_short); return@setOnClickListener }
            if (!encrypted && value != confirm.text.toString()) { confirm.error = getString(R.string.passwords_do_not_match); return@setOnClickListener }
            process(item, value)
        }
        setEnabled(view, false)
        Thread {
            val result = runCatching { PdfTools.isEncrypted(requireContext().applicationContext, item) }
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                encrypted = result.getOrDefault(false)
                view.findViewById<TextView>(R.id.securityTitle).setText(if (encrypted) R.string.unlock_pdf else R.string.protect_pdf)
                view.findViewById<TextView>(R.id.securityMessage).setText(if (encrypted) R.string.unlock_pdf_hint else R.string.protect_pdf_hint)
                confirm.visibility = if (encrypted) View.GONE else View.VISIBLE
                view.findViewById<TextView>(R.id.confirmSecurity).setText(if (encrypted) R.string.unlock_pdf else R.string.protect_pdf)
                setEnabled(view, true)
            }
        }.start()
    }

    private fun setEnabled(view: View, enabled: Boolean) { view.findViewById<View>(R.id.confirmSecurity).isEnabled = enabled }

    private fun process(item: DocumentItem, password: String) {
        val host = requireActivity() as MainActivity
        val manager = parentFragmentManager
        val context = requireContext().applicationContext
        val unlocking = encrypted
        val loading = ProcessingDialogFragment.newInstance(getString(if (unlocking) R.string.unlocking_pdf else R.string.protecting_pdf))
        dismiss()
        loading.show(manager, "pdf_security")
        Thread {
            val result = runCatching { if (unlocking) PdfTools.unlock(context, item, password) else PdfTools.protect(context, item, password) }
            host.runOnUiThread {
                (manager.findFragmentByTag("pdf_security") as? ProcessingDialogFragment)?.dismissAllowingStateLoss()
                result.onSuccess { host.navigate(ResultFragment.newInstance(it, if (unlocking) "unlocked" else "protected")) }
                    .onFailure { Toast.makeText(host, if (unlocking) R.string.wrong_pdf_password else R.string.pdf_security_failed, Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }
}
