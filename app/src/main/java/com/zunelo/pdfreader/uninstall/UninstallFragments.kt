package com.zunelo.pdfreader.uninstall

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.application.MainActivity
import android.view.ViewGroup
import com.zunelo.pdfreader.ads.AdScenes
import com.zunelo.pdfreader.ads.SmartPdfAds

class UninstallStepOneFragment : Fragment(R.layout.fragment_uninstall_step_one) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity() as MainActivity
        val leaveFlow = View.OnClickListener { activity.leaveUninstallFlow() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { activity.leaveUninstallFlow() }
        view.findViewById<View>(R.id.uninstallBack).setOnClickListener(leaveFlow)
        view.findViewById<View>(R.id.tryAgain).setOnClickListener(leaveFlow)
        view.findViewById<View>(R.id.discoverMore).setOnClickListener(leaveFlow)
        view.findViewById<View>(R.id.keepApp).setOnClickListener(leaveFlow)
        view.findViewById<View>(R.id.continueUninstall).setOnClickListener {
            SmartPdfAds.show(activity, AdScenes.UNINSTALL_INTERSTITIAL) {
                activity.showRoot(UninstallStepTwoFragment())
            }
        }
        if (savedInstanceState == null) {
            SmartPdfAds.bindNative(activity, AdScenes.UNINSTALL_NATIVE_ONE, view.findViewById(R.id.uninstallNativeAd))
        }
        animateContent(view)
    }

    override fun onDestroyView() {
        view?.findViewById<ViewGroup>(R.id.uninstallNativeAd)?.let(SmartPdfAds::release)
        super.onDestroyView()
    }
}

class UninstallStepTwoFragment : Fragment(R.layout.fragment_uninstall_step_two) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity() as MainActivity
        val leaveFlow = View.OnClickListener { activity.leaveUninstallFlow() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { activity.leaveUninstallFlow() }
        view.findViewById<View>(R.id.uninstallBack).setOnClickListener(leaveFlow)
        view.findViewById<View>(R.id.keepApp).setOnClickListener(leaveFlow)

        listOf(
            R.id.reasonHardToUse,
            R.id.reasonTooManyAds,
            R.id.reasonPoorViewing,
            R.id.reasonTooManyAlerts,
            R.id.reasonNotEnoughEditing,
            R.id.reasonPhoneTools
        ).forEach { id ->
            view.findViewById<TextView>(id).setOnClickListener { option -> option.isSelected = !option.isSelected }
        }

        view.findViewById<View>(R.id.uninstallAnyway).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
        }
        if (savedInstanceState == null) {
            SmartPdfAds.bindNative(activity, AdScenes.UNINSTALL_NATIVE_TWO, view.findViewById(R.id.uninstallNativeAd))
        }
        animateContent(view)
    }

    override fun onDestroyView() {
        view?.findViewById<ViewGroup>(R.id.uninstallNativeAd)?.let(SmartPdfAds::release)
        super.onDestroyView()
    }
}

private fun Fragment.animateContent(root: View) {
    root.alpha = 0f
    root.translationY = root.resources.displayMetrics.density * 10f
    root.animate().alpha(1f).translationY(0f).setDuration(280L).start()
}
