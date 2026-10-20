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

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.fragment.app.DialogFragment

class ProcessingDialogFragment : DialogFragment() {
    companion object {
        private const val ARG_MESSAGE = "message"
        fun newInstance(message: String) = ProcessingDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_processing, null)
        content.findViewById<TextView>(R.id.processingMessage).text = requireArguments().getString(ARG_MESSAGE).orEmpty()
        return Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = 0.30f }
                decorView.elevation = 14f * resources.displayMetrics.density
                setWindowAnimations(R.style.Animation_ZuneloPDF_Dialog)
            }
        }
    }
}
