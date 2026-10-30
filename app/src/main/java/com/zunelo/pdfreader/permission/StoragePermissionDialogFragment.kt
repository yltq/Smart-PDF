package com.zunelo.pdfreader.permission

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.zunelo.pdfreader.R

class StoragePermissionDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "storage_permission_settings"
        const val RESULT_REQUEST = "storage_permission_settings_result"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_storage_permission, null)
        val dialog = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = 0.34f }
                setWindowAnimations(R.style.Animation_ZuneloPDF_Dialog)
            }
        }
        content.findViewById<View>(R.id.cancelPermissionSettings).setOnClickListener {
            dismiss()
        }
        content.findViewById<View>(R.id.openPermissionSettings).setOnClickListener {
            parentFragmentManager.setFragmentResult(RESULT_REQUEST, bundleOf())
            dismiss()
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val horizontalMargin = (32 * resources.displayMetrics.density).toInt()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels - horizontalMargin).coerceAtLeast(1),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
