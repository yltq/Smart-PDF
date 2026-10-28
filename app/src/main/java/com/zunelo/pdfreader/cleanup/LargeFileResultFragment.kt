package com.zunelo.pdfreader.cleanup

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.ads.AdScenes
import com.zunelo.pdfreader.ads.PageAdRefreshable
import com.zunelo.pdfreader.ads.SmartPdfAds
import com.zunelo.pdfreader.application.MainActivity
import com.zunelo.pdfreader.data.DocumentStore

class LargeFileResultFragment : Fragment(R.layout.fragment_large_file_result), PageAdRefreshable {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val freedBytes = requireArguments().getLong(ARG_FREED_BYTES)
        val deletedCount = requireArguments().getInt(ARG_DELETED_COUNT)
        val failedCount = requireArguments().getInt(ARG_FAILED_COUNT)
        view.findViewById<TextView>(R.id.toolbarTitle).setText(R.string.large_files_result_toolbar)
        view.findViewById<View>(R.id.toolbarAction).visibility = View.INVISIBLE
        view.findViewById<View>(R.id.backButton).setOnClickListener { closeResult() }
        view.findViewById<TextView>(R.id.largeFileResultTitle).setText(
            if (failedCount > 0) R.string.large_files_result_partial_title
            else R.string.large_files_result_title
        )
        view.findViewById<TextView>(R.id.largeFileResultMessage).setText(
            if (failedCount > 0) R.string.large_files_result_partial_message
            else R.string.large_files_result_message
        )
        view.findViewById<TextView>(R.id.largeFileResultFreed).text = DocumentStore.formatSize(freedBytes)
        view.findViewById<TextView>(R.id.largeFileResultDeleted).text = deletedCount.toString()
        view.findViewById<View>(R.id.largeFileResultFailedRow).apply {
            visibility = if (failedCount > 0) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.largeFileResultFailed).text = failedCount.toString()
        }
        view.findViewById<View>(R.id.largeFileResultDone).setOnClickListener { closeResult() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeResult()
        })
        if (savedInstanceState == null) {
            SmartPdfAds.bindNative(requireActivity(), AdScenes.RESULT_NATIVE, view.findViewById(R.id.resultNativeAd))
        }
        animateContent(view)
    }

    private fun closeResult() {
        (requireActivity() as MainActivity).returnHomeWithAd()
    }

    override fun refreshPageAds() {
        val container = view?.findViewById<ViewGroup>(R.id.resultNativeAd) ?: return
        SmartPdfAds.bindNative(requireActivity(), AdScenes.RESULT_NATIVE, container)
    }

    private fun animateContent(view: View) {
        val interpolator = DecelerateInterpolator()
        view.findViewById<View>(R.id.largeFileResultHero).apply {
            alpha = 0f
            scaleX = .78f
            scaleY = .78f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320L).setInterpolator(interpolator).start()
        }
        view.findViewById<View>(R.id.largeFileResultContent).apply {
            alpha = 0f
            translationY = 16f * resources.displayMetrics.density
            animate().alpha(1f).translationY(0f).setStartDelay(45L).setDuration(320L).setInterpolator(interpolator).start()
        }
        view.findViewById<View>(R.id.largeFileResultDone).apply {
            alpha = 0f
            translationY = 10f * resources.displayMetrics.density
            animate().alpha(1f).translationY(0f).setStartDelay(110L).setDuration(240L).setInterpolator(interpolator).start()
        }
    }

    override fun onDestroyView() {
        view?.findViewById<ViewGroup>(R.id.resultNativeAd)?.let(SmartPdfAds::release)
        super.onDestroyView()
    }

    companion object {
        private const val ARG_FREED_BYTES = "freed_bytes"
        private const val ARG_DELETED_COUNT = "deleted_count"
        private const val ARG_FAILED_COUNT = "failed_count"

        fun newInstance(freedBytes: Long, deletedCount: Int, failedCount: Int) = LargeFileResultFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_FREED_BYTES, freedBytes)
                putInt(ARG_DELETED_COUNT, deletedCount)
                putInt(ARG_FAILED_COUNT, failedCount)
            }
        }
    }
}
