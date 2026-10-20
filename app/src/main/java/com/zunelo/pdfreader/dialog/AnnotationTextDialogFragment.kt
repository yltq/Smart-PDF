package com.zunelo.pdfreader.dialog

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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AnnotationTextDialogFragment : BottomSheetDialogFragment() {
    companion object {
        const val RESULT_TEXT = "annotation_text"
        fun newInstance(requestKey: String) = AnnotationTextDialogFragment().apply { arguments = Bundle().apply { putString("key", requestKey) } }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) = inflater.inflate(R.layout.dialog_rename, container, false)
    override fun onViewCreated(view: View, state: Bundle?) {
        val input = view.findViewById<EditText>(R.id.renameInput)
        view.findViewById<TextView>(R.id.renameTitle).setText(R.string.add_text)
        view.findViewById<TextView>(R.id.renameMessage).setText(R.string.add_text_hint)
        view.findViewById<TextView>(R.id.cancelRename).apply { setText(R.string.cancel); setOnClickListener { dismiss() } }
        val confirm = view.findViewById<TextView>(R.id.confirmRename).apply {
            setText(R.string.add)
            setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    parentFragmentManager.setFragmentResult(requireArguments().getString("key").orEmpty(), Bundle().apply { putString(RESULT_TEXT, text) })
                    dismiss()
                }
            }
        }
        input.hint = getString(R.string.annotation_text_hint)
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { confirm.performClick(); true } else false
        }
        input.requestFocus()
        input.postDelayed({ requireContext().getSystemService(InputMethodManager::class.java).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }, 180)
    }
}
