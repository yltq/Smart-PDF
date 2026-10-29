package com.zunelo.pdfreader.cleanup

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.ads.AdScenes
import com.zunelo.pdfreader.ads.FullScreenResumeTask
import com.zunelo.pdfreader.ads.SmartPdfAds
import com.zunelo.pdfreader.application.MainActivity
import com.zunelo.pdfreader.data.DocumentStore
import com.zunelo.pdfreader.dialog.ProcessingDialogFragment
import com.zunelo.pdfreader.permission.StorageAccessFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SizeFilter(val minimumBytes: Long, @param:StringRes val label: Int) {
    TEN_MB(10L * 1024L * 1024L, R.string.large_file_size_10),
    FIFTY_MB(50L * 1024L * 1024L, R.string.large_file_size_50),
    HUNDRED_MB(100L * 1024L * 1024L, R.string.large_file_size_100),
    FIVE_HUNDRED_MB(500L * 1024L * 1024L, R.string.large_file_size_500)
}

enum class TypeFilter(@param:StringRes val label: Int, val category: LargeFileCategory?) {
    ALL(R.string.large_file_type_all, null),
    DOCUMENTS(R.string.large_file_type_documents, LargeFileCategory.DOCUMENT),
    IMAGES(R.string.large_file_type_images, LargeFileCategory.IMAGE),
    VIDEOS(R.string.large_file_type_videos, LargeFileCategory.VIDEO),
    AUDIO(R.string.large_file_type_audio, LargeFileCategory.AUDIO),
    ARCHIVES(R.string.large_file_type_archives, LargeFileCategory.ARCHIVE),
    OTHER(R.string.large_file_type_other, LargeFileCategory.OTHER)
}

enum class DateFilter(@param:StringRes val label: Int, val ageMillis: Long) {
    ANY(R.string.large_file_date_any, 0L),
    MONTH(R.string.large_file_date_month, 30L * 24L * 60L * 60L * 1_000L),
    SIX_MONTHS(R.string.large_file_date_six_months, 183L * 24L * 60L * 60L * 1_000L),
    YEAR(R.string.large_file_date_year, 365L * 24L * 60L * 60L * 1_000L)
}

class LargeFilesFragment : Fragment(R.layout.fragment_large_files) {
    private lateinit var accessFlow: StorageAccessFlow
    private lateinit var adapter: LargeFileAdapter
    private var allFiles = emptyList<LargeFileItem>()
    private var visibleFiles = emptyList<LargeFileItem>()
    private val selectedPaths = linkedSetOf<String>()
    private var sizeFilter = SizeFilter.TEN_MB
    private var typeFilter = TypeFilter.ALL
    private var dateFilter = DateFilter.ANY
    private var scanning = false
    private var scanCompletionPending = false
    private var contentAnimated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accessFlow = StorageAccessFlow(
            fragment = this,
            onGranted = { scanFiles() },
            onDenied = { renderAccessState() },
            requireWriteAccess = true
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.toolbarTitle).setText(R.string.large_files)
        view.findViewById<View>(R.id.backButton).setOnClickListener { (requireActivity() as MainActivity).back() }
        view.findViewById<View>(R.id.toolbarAction).visibility = View.INVISIBLE
        adapter = LargeFileAdapter(::toggleSelection)
        view.findViewById<RecyclerView>(R.id.largeFileList).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LargeFilesFragment.adapter
            itemAnimator = DefaultItemAnimator().apply {
                addDuration = 180L
                removeDuration = 190L
                moveDuration = 180L
                changeDuration = 0L
                supportsChangeAnimations = false
            }
        }
        view.findViewById<View>(R.id.sizeFilter).setOnClickListener { showFilter(LargeFileFilterKind.SIZE) }
        view.findViewById<View>(R.id.typeFilter).setOnClickListener { showFilter(LargeFileFilterKind.TYPE) }
        view.findViewById<View>(R.id.dateFilter).setOnClickListener { showFilter(LargeFileFilterKind.DATE) }
        view.findViewById<View>(R.id.selectAllAction).setOnClickListener { toggleSelectAll() }
        view.findViewById<View>(R.id.largeFileStateAction).setOnClickListener {
            if (DocumentStore.canModifyStorage(requireContext())) scanFiles() else accessFlow.request()
        }
        view.findViewById<View>(R.id.cleanLargeFiles).setOnClickListener { confirmDelete() }
        parentFragmentManager.setFragmentResultListener(DELETE_REQUEST, viewLifecycleOwner) { _, _ -> deleteSelected() }
        parentFragmentManager.setFragmentResultListener(LargeFileFilterDialog.RESULT_REQUEST, viewLifecycleOwner) { _, result ->
            val kind = result.getString(LargeFileFilterDialog.RESULT_KIND)
                ?.let { runCatching { LargeFileFilterKind.valueOf(it) }.getOrNull() }
                ?: return@setFragmentResultListener
            val index = result.getInt(LargeFileFilterDialog.RESULT_INDEX, -1)
            val changed = when (kind) {
                LargeFileFilterKind.SIZE -> {
                    val next = SizeFilter.entries.getOrNull(index) ?: return@setFragmentResultListener
                    (next != sizeFilter).also { sizeFilter = next }
                }
                LargeFileFilterKind.TYPE -> {
                    val next = TypeFilter.entries.getOrNull(index) ?: return@setFragmentResultListener
                    (next != typeFilter).also { typeFilter = next }
                }
                LargeFileFilterKind.DATE -> {
                    val next = DateFilter.entries.getOrNull(index) ?: return@setFragmentResultListener
                    (next != dateFilter).also { dateFilter = next }
                }
            }
            if (!changed) return@setFragmentResultListener
            updateFilterLabels(animated = true)
            applyFilters(clearSelection = true, animated = true)
        }
        updateFilterLabels()
        animateFilterEntry(view)
        if (DocumentStore.canModifyStorage(requireContext())) scanFiles() else renderAccessState()
    }

    private fun scanFiles() {
        if (scanning || !isAdded) return
        scanning = true
        showLoading(true)
        val scanStartedAt = SystemClock.elapsedRealtime()
        SmartPdfAds.load(AdScenes.LARGE_FILE_SCAN)
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) { LargeFileRepository.scan(requireContext().applicationContext) }
            val remainingAnimation = SCAN_ANIMATION_MILLIS - (SystemClock.elapsedRealtime() - scanStartedAt)
            if (remainingAnimation > 0L) delay(remainingAnimation)
            if (!isAdded) return@launch
            allFiles = snapshot
            scanning = false
            scanCompletionPending = true
            presentCompletedScanIfPossible()
        }
    }

    private fun presentCompletedScanIfPossible() {
        if (
            !scanCompletionPending ||
            !isAdded ||
            view == null ||
            !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            return
        }
        scanCompletionPending = false
        SmartPdfAds.show(requireActivity(), AdScenes.LARGE_FILE_SCAN) {}
        applyFilters(clearSelection = true)
    }

    override fun onResume() {
        super.onResume()
        presentCompletedScanIfPossible()
    }

    private fun applyFilters(clearSelection: Boolean, animated: Boolean = false) {
        if (clearSelection) selectedPaths.clear()
        val oldestAllowed = if (dateFilter.ageMillis == 0L) Long.MAX_VALUE else System.currentTimeMillis() - dateFilter.ageMillis
        visibleFiles = allFiles.asSequence()
            .filter { it.size >= sizeFilter.minimumBytes }
            .filter { typeFilter.category == null || it.category == typeFilter.category }
            .filter { dateFilter.ageMillis == 0L || (it.modified in 1..oldestAllowed) }
            .sortedWith(compareByDescending<LargeFileItem> { it.size }.thenBy { it.path })
            .toList()
        val commit = {
            adapter.submitList(visibleFiles) {
                if (animated) view?.findViewById<RecyclerView>(R.id.largeFileList)?.scrollToPosition(0)
            }
            adapter.selectedPaths = selectedPaths.toSet()
            renderContent()
        }
        val content = view?.findViewById<View>(R.id.largeFileContent)
        if (animated && content != null) {
            content.animate().cancel()
            content.animate().alpha(.38f).translationY(4f * resources.displayMetrics.density).setDuration(80L)
                .withEndAction {
                    commit()
                    content.animate().alpha(1f).translationY(0f).setDuration(180L).start()
                    view?.findViewById<View>(R.id.largeFileSummary)?.run {
                        alpha = .35f
                        animate().alpha(1f).setDuration(180L).start()
                    }
                }.start()
        } else {
            commit()
        }
    }

    private fun toggleSelection(item: LargeFileItem) {
        if (!selectedPaths.remove(item.path)) selectedPaths.add(item.path)
        adapter.selectedPaths = selectedPaths.toSet()
        updateSelectionUi()
    }

    private fun toggleSelectAll() {
        if (visibleFiles.isEmpty()) return
        if (selectedPaths.size == visibleFiles.size) selectedPaths.clear()
        else {
            selectedPaths.clear()
            selectedPaths += visibleFiles.map { it.path }
        }
        adapter.selectedPaths = selectedPaths.toSet()
        updateSelectionUi()
    }

    private fun renderContent() {
        val root = view ?: return
        showLoading(false)
        val empty = visibleFiles.isEmpty()
        root.findViewById<View>(R.id.largeFileList).visibility = if (empty) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.largeFileEmptyState).visibility = if (empty) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.largeFileSummary).visibility = if (empty) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.largeFileBottomBar).visibility = if (empty) View.GONE else View.VISIBLE
        root.findViewById<TextView>(R.id.largeFileStateTitle).setText(R.string.large_files_empty_title)
        root.findViewById<TextView>(R.id.largeFileStateMessage).setText(R.string.large_files_empty_message)
        root.findViewById<ImageView>(R.id.largeFileStateImage).setImageResource(R.drawable.state_no_files)
        root.findViewById<TextView>(R.id.largeFileStateAction).apply {
            setText(R.string.large_files_scan_again)
            visibility = if (empty) View.VISIBLE else View.GONE
        }
        val totalSize = visibleFiles.sumOf { it.size }
        root.findViewById<TextView>(R.id.largeFileSummaryValue).text = DocumentStore.formatSize(totalSize)
        root.findViewById<TextView>(R.id.largeFileSummaryLabel).text = resources.getQuantityString(
            R.plurals.large_files_found,
            visibleFiles.size,
            visibleFiles.size
        )
        updateSelectionUi()
        animateContentOnce(root, empty)
    }

    private fun renderAccessState() {
        val root = view ?: return
        scanning = false
        showLoading(false)
        root.findViewById<View>(R.id.largeFileList).visibility = View.GONE
        root.findViewById<View>(R.id.largeFileSummary).visibility = View.GONE
        root.findViewById<View>(R.id.largeFileBottomBar).visibility = View.GONE
        root.findViewById<View>(R.id.largeFileEmptyState).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.largeFileStateTitle).setText(R.string.storage_access_title)
        root.findViewById<TextView>(R.id.largeFileStateMessage).setText(R.string.large_files_permission_message)
        root.findViewById<ImageView>(R.id.largeFileStateImage).setImageResource(R.drawable.state_no_permission)
        root.findViewById<TextView>(R.id.largeFileStateAction).apply {
            setText(R.string.storage_access_action)
            visibility = View.VISIBLE
        }
        intArrayOf(R.id.sizeFilter, R.id.typeFilter, R.id.dateFilter).forEach { id ->
            root.findViewById<View>(id).isEnabled = false
        }
        root.findViewById<View>(R.id.filterGroup).alpha = .58f
        selectedPaths.clear()
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        val root = view ?: return
        val selectedItems = allFiles.filter { selectedPaths.contains(it.path) }
        val selectedSize = selectedItems.sumOf { it.size }
        root.findViewById<TextView>(R.id.selectAllAction).setText(
            if (visibleFiles.isNotEmpty() && selectedPaths.size == visibleFiles.size) R.string.large_files_deselect_all
            else R.string.large_files_select_all
        )
        root.findViewById<TextView>(R.id.cleanLargeFiles).apply {
            val enabledNow = selectedItems.isNotEmpty()
            val changed = isEnabled != enabledNow
            isEnabled = enabledNow
            text = if (!enabledNow) getString(R.string.large_files_clean)
            else getString(R.string.large_files_clean_size, DocumentStore.formatSize(selectedSize))
            if (changed || enabledNow) {
                animate().cancel()
                alpha = .72f
                scaleX = if (enabledNow) .985f else 1f
                scaleY = if (enabledNow) .985f else 1f
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170L).start()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        val root = view ?: return
        root.findViewById<View>(R.id.largeFileLoading).visibility = if (show) View.VISIBLE else View.GONE
        intArrayOf(R.id.sizeFilter, R.id.typeFilter, R.id.dateFilter).forEach { id ->
            root.findViewById<View>(id).isEnabled = !show
        }
        root.findViewById<View>(R.id.filterGroup).animate().alpha(if (show) .58f else 1f).setDuration(160L).start()
        if (show) {
            root.findViewById<View>(R.id.largeFileList).visibility = View.GONE
            root.findViewById<View>(R.id.largeFileEmptyState).visibility = View.GONE
            root.findViewById<View>(R.id.largeFileSummary).visibility = View.GONE
            root.findViewById<View>(R.id.largeFileBottomBar).visibility = View.GONE
        }
    }

    private fun confirmDelete() {
        val selected = allFiles.filter { selectedPaths.contains(it.path) }
        if (selected.isEmpty()) return
        LargeFileDeleteDialog.newInstance(selected.size, selected.sumOf { it.size })
            .show(parentFragmentManager, "large_file_delete")
    }

    private fun deleteSelected() {
        val selected = allFiles.filter { selectedPaths.contains(it.path) }
        if (selected.isEmpty()) return
        val host = requireActivity() as MainActivity
        val adStartedAt = host.beginCreationAd(AdScenes.CLEAN_FILES)
        val dialogTag = "large_file_cleaning"
        ProcessingDialogFragment.newInstance(getString(R.string.large_files_cleaning))
            .show(parentFragmentManager, dialogTag)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                LargeFileRepository.delete(requireContext().applicationContext, selected)
            }
            (parentFragmentManager.findFragmentByTag(dialogTag) as? ProcessingDialogFragment)?.dismissAllowingStateLoss()
            if (!isAdded) return@launch

            if (result.deletedPaths.isEmpty()) {
                Toast.makeText(requireContext(), R.string.large_files_delete_failed, Toast.LENGTH_LONG).show()
                return@launch
            }

            val openResult = {
                if (isAdded && view != null) {
                    allFiles = allFiles.filterNot { result.deletedPaths.contains(it.path) }
                    selectedPaths.clear()
                    applyFilters(clearSelection = false)
                    host.refreshHomeDocuments()
                    host.navigate(
                        LargeFileResultFragment.newInstance(
                            freedBytes = result.freedBytes,
                            deletedCount = result.deletedPaths.size,
                            failedCount = result.failedCount
                        )
                    )
                }
            }
            host.continueAfterCreationAd(
                scene = AdScenes.CLEAN_FILES,
                startedAtMillis = adStartedAt,
                loadingMessage = getString(R.string.large_files_cleaning),
                resumeTask = FullScreenResumeTask.showCleanResult(
                    freedBytes = result.freedBytes,
                    deletedCount = result.deletedPaths.size,
                    failedCount = result.failedCount
                ),
                onComplete = openResult
            )
        }
    }

    private fun updateFilterLabels(animated: Boolean = false) {
        updateFilterLabel(R.id.sizeFilter, sizeFilter.label, R.drawable.ic_filter_size, animated)
        updateFilterLabel(R.id.typeFilter, typeFilter.label, LargeFileVisuals.icon(typeFilter.category), animated)
        updateFilterLabel(R.id.dateFilter, dateFilter.label, R.drawable.ic_filter_date, animated)
    }

    private fun updateFilterLabel(viewId: Int, label: Int, icon: Int, animated: Boolean) {
        val filter = view?.findViewById<TextView>(viewId) ?: return
        val update = {
            filter.setText(label)
            filter.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, R.drawable.ic_chevron_down, 0)
        }
        if (!animated) {
            update()
            return
        }
        filter.animate().cancel()
        filter.animate().alpha(.25f).setDuration(70L).withEndAction {
            update()
            filter.animate().alpha(1f).setDuration(140L).start()
        }.start()
    }

    private fun showFilter(kind: LargeFileFilterKind) {
        if (parentFragmentManager.findFragmentByTag(FILTER_TAG) != null) return
        val selectedIndex = when (kind) {
            LargeFileFilterKind.SIZE -> sizeFilter.ordinal
            LargeFileFilterKind.TYPE -> typeFilter.ordinal
            LargeFileFilterKind.DATE -> dateFilter.ordinal
        }
        LargeFileFilterDialog.newInstance(kind, selectedIndex).show(parentFragmentManager, FILTER_TAG)
    }

    private fun animateFilterEntry(root: View) {
        root.findViewById<View>(R.id.filterGroup).apply {
            alpha = 0f
            translationY = -6f * resources.displayMetrics.density
            animate().alpha(1f).translationY(0f).setDuration(220L).setStartDelay(40L).start()
        }
    }

    private fun animateContentOnce(root: View, empty: Boolean) {
        if (contentAnimated) return
        contentAnimated = true
        val targets = buildList {
            if (!empty) add(root.findViewById<View>(R.id.largeFileSummary))
            add(root.findViewById<View>(R.id.largeFileContent))
            if (!empty) add(root.findViewById<View>(R.id.largeFileBottomBar))
        }
        targets.forEachIndexed { index, target ->
            target.alpha = 0f
            target.translationY = 8f * resources.displayMetrics.density
            target.animate().alpha(1f).translationY(0f).setStartDelay(index * 45L).setDuration(220L).start()
        }
    }

    companion object {
        const val DELETE_REQUEST = "large_file_delete_confirmed"
        private const val FILTER_TAG = "large_file_filter"
        private const val SCAN_ANIMATION_MILLIS = 2_000L
    }
}

class LargeFileDeleteDialog : BottomSheetDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.dialog_confirm_delete, container, false)

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val count = requireArguments().getInt(ARG_COUNT)
        val size = requireArguments().getLong(ARG_SIZE)
        view.findViewById<TextView>(R.id.deleteTitle).setText(R.string.large_files_delete_title)
        view.findViewById<TextView>(R.id.deleteMessage).text =
            resources.getQuantityString(
                R.plurals.large_files_delete_message,
                count,
                count,
                DocumentStore.formatSize(size)
            )
        view.findViewById<View>(R.id.cancelDelete).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.confirmDelete).setOnClickListener {
            parentFragmentManager.setFragmentResult(LargeFilesFragment.DELETE_REQUEST, Bundle())
            dismiss()
        }
    }

    companion object {
        private const val ARG_COUNT = "count"
        private const val ARG_SIZE = "size"

        fun newInstance(count: Int, size: Long) = LargeFileDeleteDialog().apply {
            arguments = Bundle().apply {
                putInt(ARG_COUNT, count)
                putLong(ARG_SIZE, size)
            }
        }
    }
}
