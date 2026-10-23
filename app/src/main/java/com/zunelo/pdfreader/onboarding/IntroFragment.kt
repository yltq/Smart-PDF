package com.zunelo.pdfreader.onboarding

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.application.MainActivity
import com.zunelo.pdfreader.common.dp
import com.zunelo.pdfreader.home.HomeFragment
import kotlin.math.abs
import android.view.ViewGroup
import com.zunelo.pdfreader.ads.AdScenes
import com.zunelo.pdfreader.ads.SmartPdfAds
import com.zunelo.pdfreader.ads.PageAdRefreshable
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub

class IntroFragment : Fragment(R.layout.fragment_intro), PageAdRefreshable {
    private var leaving = false
    private var pageAdRefreshEnabled = true
    private val pages = listOf(
        IntroPage(R.drawable.intro_documents, R.string.intro_documents_title, R.string.intro_documents_description),
        IntroPage(R.drawable.intro_management, R.string.intro_management_title, R.string.intro_management_description),
        IntroPage(R.drawable.intro_tools, R.string.intro_tools_title, R.string.intro_tools_description)
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        SmartPdfSignalHub.recordCustomSignal("page_introduce_show")
        pageAdRefreshEnabled = savedInstanceState == null
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {}
        val pager = view.findViewById<ViewPager2>(R.id.introPager)
        val action = view.findViewById<TextView>(R.id.introAction)
        val dots = view.findViewById<LinearLayout>(R.id.introDots)
        pager.adapter = IntroPagerAdapter(pages)
        pager.offscreenPageLimit = pages.size
        pager.setPageTransformer { page, position ->
            val distance = abs(position).coerceAtMost(1f)
            page.alpha = .62f + (1f - distance) * .38f
            page.scaleX = .94f + (1f - distance) * .06f
            page.scaleY = .94f + (1f - distance) * .06f
            page.findViewById<ImageView>(R.id.introImage)?.translationX = -position * page.width * .12f
        }
        repeat(pages.size) {
            dots.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(requireContext().dp(7), requireContext().dp(7)).apply {
                    marginStart = requireContext().dp(3)
                    marginEnd = requireContext().dp(3)
                }
                background = requireContext().getDrawable(R.drawable.bg_intro_dot_inactive)
            })
        }
        fun renderPosition(position: Int) {
            repeat(dots.childCount) { index ->
                dots.getChildAt(index).apply {
                    layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                        width = requireContext().dp(if (index == position) 18 else 7)
                    }
                    background = requireContext().getDrawable(if (index == position) R.drawable.bg_intro_dot_active else R.drawable.bg_intro_dot_inactive)
                }
            }
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                renderPosition(position)
                if (pageAdRefreshEnabled) {
                    SmartPdfAds.bindNative(requireActivity(), AdScenes.NEW_INTRO_NATIVE, view.findViewById(R.id.introNativeAd))
                }
                pager.post {
                    pager.findViewWithTag<View>("intro_page_$position")?.findViewById<ImageView>(R.id.introImage)?.apply {
                        animate().cancel()
                        alpha = .5f
                        scaleX = .92f
                        scaleY = .92f
                        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320L).start()
                    }
                }
            }
        })
        action.setText(R.string.next)
        action.setOnClickListener { openHome() }
        view.findViewById<View>(R.id.skipIntro).setOnClickListener { openHome() }
        renderPosition(0)
        view.post { pageAdRefreshEnabled = true }
    }

    private fun openHome() {
        if (leaving) return
        leaving = true
        SmartPdfSignalHub.recordCustomSignal("page_introduce_click")
        SmartPdfAds.show(requireActivity(), AdScenes.NEW_INTRO_INTERSTITIAL) {
            (requireActivity() as MainActivity).apply {
                completeNewUserFlow()
                showRoot(HomeFragment())
            }
        }
    }

    override fun onDestroyView() {
        view?.findViewById<ViewGroup>(R.id.introNativeAd)?.let(SmartPdfAds::release)
        super.onDestroyView()
    }

    override fun refreshPageAds() {
        val container = view?.findViewById<ViewGroup>(R.id.introNativeAd) ?: return
        SmartPdfAds.bindNative(requireActivity(), AdScenes.NEW_INTRO_NATIVE, container)
    }
}
