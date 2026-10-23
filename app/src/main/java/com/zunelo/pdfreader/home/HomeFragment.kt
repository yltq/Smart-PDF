package com.zunelo.pdfreader.home

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

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlin.math.abs
import com.zunelo.pdfreader.ads.AdScenes
import com.zunelo.pdfreader.ads.SmartPdfAds
import com.zunelo.pdfreader.ads.PageAdRefreshable
import com.zunelo.pdfreader.telemetry.SmartPdfSignalHub

class HomeFragment : Fragment(R.layout.fragment_home), PageAdRefreshable {
    companion object {
        private const val ARG_INITIAL_TAB = "initial_tab"
        private const val ARG_START_SCAN = "start_scan"
        fun newInstance(initialTab: Int = 0, startScan: Boolean = false) = HomeFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_INITIAL_TAB, initialTab)
                putBoolean(ARG_START_SCAN, startScan)
            }
        }
    }
    private var activeTab = 0
    private var suppressInitialAdRefresh = false
    private lateinit var navViews: List<View>
    private lateinit var scanAccessFlow: StorageAccessFlow
    private val icons = listOf(R.drawable.nav_selector_home, R.drawable.nav_selector_recent, R.drawable.nav_selector_collection, R.drawable.nav_selector_settings)
    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data) ?: return@registerForActivityResult
        prepareScan(scanResult.pages.orEmpty().map { it.imageUri })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanAccessFlow = StorageAccessFlow(
            fragment = this,
            onGranted = { if (isAdded) launchScanner() },
            onDenied = {}
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        SmartPdfSignalHub.recordCustomSignal("page_home_show")
        suppressInitialAdRefresh = savedInstanceState != null
        activeTab = savedInstanceState?.getInt("tab") ?: arguments?.getInt(ARG_INITIAL_TAB) ?: 0
        val labels = listOf(R.string.tab_home, R.string.tab_recent, R.string.tab_collection, R.string.tab_settings)
        navViews = listOf(view.findViewById(R.id.navHome), view.findViewById(R.id.navRecent), view.findViewById(R.id.navCollection), view.findViewById(R.id.navSettings))
        navViews.forEachIndexed { index, nav ->
            nav.findViewById<ImageView>(R.id.navIcon).setImageResource(icons[index])
            nav.findViewById<TextView>(R.id.navLabel).setText(labels[index])
            nav.setOnClickListener { switchTab(index) }
        }
        view.findViewById<View>(R.id.scanFab).apply {
            setOnClickListener {
                SmartPdfSignalHub.recordCustomSignal("home_create_click")
                requestScan()
            }
            makeScanFabDraggable(this)
        }
        switchTab(activeTab)
        suppressInitialAdRefresh = false
        if (savedInstanceState == null && arguments?.getBoolean(ARG_START_SCAN) == true) {
            view.post { if (isAdded) requestScan() }
        }
    }

    private fun makeScanFabDraggable(fab: View) {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        val edgeInset = requireContext().dp(12).toFloat()
        val preferences = requireContext().getSharedPreferences("home_fab_position", 0)
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false

        fun bounds(): Pair<Float, Float> {
            val parent = fab.parent as View
            return (parent.width - fab.width - edgeInset).coerceAtLeast(edgeInset) to
                (parent.height - fab.height - edgeInset).coerceAtLeast(edgeInset)
        }

        fun savePosition() {
            val (_, maxY) = bounds()
            val verticalRange = (maxY - edgeInset).coerceAtLeast(1f)
            preferences.edit()
                .putBoolean("end_side", fab.x > (fab.parent as View).width / 2f)
                .putFloat("vertical_ratio", ((fab.y - edgeInset) / verticalRange).coerceIn(0f, 1f))
                .apply()
        }

        fun snapToEdge() {
            val parent = fab.parent as View
            val (maxX, maxY) = bounds()
            val targetX = if (fab.x + fab.width / 2f < parent.width / 2f) edgeInset else maxX
            fab.y = fab.y.coerceIn(edgeInset, maxY)
            fab.animate().x(targetX).setDuration(180L).withEndAction { savePosition() }.start()
        }

        fab.post {
            val (maxX, maxY) = bounds()
            val verticalRange = (maxY - edgeInset).coerceAtLeast(1f)
            fab.x = if (preferences.getBoolean("end_side", true)) maxX else edgeInset
            fab.y = edgeInset + preferences.getFloat("vertical_ratio", 1f).coerceIn(0f, 1f) * verticalRange
        }

        fab.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fab.animate().cancel()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = fab.x
                    startY = fab.y
                    dragging = false
                    fab.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) dragging = true
                    if (dragging) {
                        val (maxX, maxY) = bounds()
                        fab.x = (startX + deltaX).coerceIn(edgeInset, maxX)
                        fab.y = (startY + deltaY).coerceIn(edgeInset, maxY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    fab.isPressed = false
                    if (dragging) snapToEdge() else fab.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    fab.isPressed = false
                    if (dragging) snapToEdge()
                    true
                }
                else -> false
            }
        }
    }

    private fun switchTab(index: Int) {
        if (index == activeTab && childFragmentManager.findFragmentById(R.id.tabContainer) != null) return
        activeTab = index
        view?.findViewById<View>(R.id.scanFab)?.apply {
            visibility = if (index == 0) View.VISIBLE else View.GONE
            animate().cancel()
            if (visibility == View.VISIBLE) {
                alpha = 0f; scaleX = .8f; scaleY = .8f
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220L).start()
            }
        }
        navViews.forEachIndexed { position, nav ->
            val selected = position == index
            nav.isSelected = selected
            nav.findViewById<ImageView>(R.id.navIcon).apply {
                isSelected = selected
                animate().cancel()
                animate().translationY(if (selected) -requireContext().dp(1).toFloat() else 0f)
                    .scaleX(if (selected) 1.08f else 1f).scaleY(if (selected) 1.08f else 1f)
                    .setDuration(180).start()
            }
            nav.findViewById<TextView>(R.id.navLabel).apply {
                setTextColor(if (selected) Palette.RED else Palette.MUTED)
                animate().alpha(if (selected) 1f else .82f).setDuration(160).start()
            }
        }
        val fragment = when (index) {
            0 -> DocumentListFragment.newInstance(DocumentListFragment.MODE_HOME)
            1 -> DocumentListFragment.newInstance(DocumentListFragment.MODE_RECENT)
            2 -> DocumentListFragment.newInstance(DocumentListFragment.MODE_COLLECTION)
            else -> SettingsFragment()
        }
        val shouldRefreshAds = !suppressInitialAdRefresh
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.tabContainer, fragment)
            .runOnCommit {
                if (shouldRefreshAds && isAdded) refreshCurrentNativeAd()
            }
            .commit()
        if (suppressInitialAdRefresh) suppressInitialAdRefresh = false else refreshBanner()
    }

    private fun refreshCurrentNativeAd() {
        (childFragmentManager.findFragmentById(R.id.tabContainer) as? PageAdRefreshable)?.refreshPageAds()
    }

    fun refreshAds() {
        refreshCurrentNativeAd()
        refreshBanner()
    }

    fun refreshDocuments() {
        (childFragmentManager.findFragmentById(R.id.tabContainer) as? DocumentListFragment)?.refreshDocuments()
    }

    private fun refreshBanner() {
        val root = view ?: return
        root.findViewById<ViewGroup>(R.id.homeBannerAd).post {
            if (isAdded && view != null) SmartPdfAds.bindBanner(requireActivity(), AdScenes.HOME_BANNER, root.findViewById(R.id.homeBannerAd))
        }
    }

    override fun refreshPageAds() = refreshAds()

    private fun launchScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(30)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(requireActivity())
            .addOnSuccessListener {
                (requireActivity() as MainActivity).suppressNextPageAdRefresh()
                scanLauncher.launch(IntentSenderRequest.Builder(it).build())
            }
            .addOnFailureListener { Toast.makeText(requireContext(), R.string.scan_unavailable, Toast.LENGTH_SHORT).show() }
    }

    private fun requestScan() {
        scanAccessFlow.request()
    }

    private fun prepareScan(uris: List<Uri>) {
        if (uris.isEmpty()) return
        ProcessingDialogFragment.newInstance(getString(R.string.preparing_scan)).show(parentFragmentManager, "scan_preparing")
        val context = requireContext().applicationContext
        Thread {
            val result = runCatching { ScanSession.copyPages(context, uris) }
            activity?.runOnUiThread {
                (parentFragmentManager.findFragmentByTag("scan_preparing") as? ProcessingDialogFragment)?.dismissAllowingStateLoss()
                result.onSuccess { paths -> (requireActivity() as MainActivity).navigate(ScanReviewFragment.newInstance(paths)) }
                    .onFailure { Toast.makeText(requireContext(), R.string.scan_prepare_failed, Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("tab", activeTab); super.onSaveInstanceState(outState) }

    override fun onDestroyView() {
        view?.findViewById<ViewGroup>(R.id.homeBannerAd)?.let(SmartPdfAds::release)
        super.onDestroyView()
    }
}
