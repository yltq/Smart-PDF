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
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.activity.OnBackPressedCallback

class ResultFragment : Fragment(R.layout.fragment_result) {
    companion object {
        private const val ARG_ACTION = "action"
        fun newInstance(item: DocumentItem, action: String) = ResultFragment().apply { arguments = Bundle().putDocument(item).apply { putString(ARG_ACTION, action) } }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val item = requireArguments().document() ?: return
        val action = requireArguments().getString(ARG_ACTION) ?: "processed"
        val toolbar = view.findViewById<View>(R.id.toolbar)
        val horizontalInset = requireContext().dp(4)
        toolbar.setPaddingRelative(horizontalInset, toolbar.paddingTop, horizontalInset, toolbar.paddingBottom)
        toolbar.findViewById<TextView>(R.id.toolbarTitle).setText(R.string.result)
        toolbar.findViewById<View>(R.id.backButton).setOnClickListener { returnHome() }
        toolbar.findViewById<TextView>(R.id.toolbarAction).apply {
            contentDescription = getString(R.string.share_file)
            setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_share, 0)
            setOnClickListener { FileDialogs.share(requireActivity(), item) }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = returnHome() })
        view.findViewById<TextView>(R.id.resultTitle).setText(
            when (action) {
                "merged" -> R.string.merge_complete
                "split" -> R.string.split_complete
                "created" -> R.string.create_complete
                "protected" -> R.string.protect_complete
                "unlocked" -> R.string.unlock_complete
                "annotated" -> R.string.annotation_complete
                "organized" -> R.string.organize_complete
                "watermarked" -> R.string.watermark_complete
                else -> R.string.file_ready
            }
        )
        bindDocumentView(view.findViewById(R.id.resultDocument), item, false)
        view.findViewById<View>(R.id.resultDocument).findViewById<View>(R.id.favoriteButton).visibility = View.GONE
        view.findViewById<View>(R.id.resultDocument).findViewById<View>(R.id.moreButton).visibility = View.GONE
        view.findViewById<View>(R.id.openResult).setOnClickListener { (requireActivity() as MainActivity).navigate(ViewerFragment.newInstance(item)) }
        view.findViewById<View>(R.id.returnHome).setOnClickListener { returnHome() }
        animateContent(view)
    }

    private fun returnHome() { (requireActivity() as MainActivity).showRoot(HomeFragment.newInstance(0)) }

    private fun animateContent(view: View) {
        val interpolator = DecelerateInterpolator()
        view.findViewById<View>(R.id.resultHero).apply {
            alpha = 0f
            scaleX = 0.82f
            scaleY = 0.82f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320L).setInterpolator(interpolator).start()
        }
        view.findViewById<View>(R.id.resultContent).apply {
            translationY = 18f * resources.displayMetrics.density
            animate().translationY(0f).setDuration(360L).setInterpolator(interpolator).start()
        }
        view.findViewById<View>(R.id.resultActions).apply {
            alpha = 0f
            translationY = 12f * resources.displayMetrics.density
            animate().alpha(1f).translationY(0f).setStartDelay(90L).setDuration(260L).setInterpolator(interpolator).start()
        }
    }
}
