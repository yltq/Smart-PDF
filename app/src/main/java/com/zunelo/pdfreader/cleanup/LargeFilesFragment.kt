package com.zunelo.pdfreader.cleanup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.application.MainActivity
import com.zunelo.pdfreader.data.DocumentStore
import com.zunelo.pdfreader.dialog.ProcessingDialogFragment
import com.zunelo.pdfreader.permission.StorageAccessFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SizeFilter(val minimumBytes: Long, @param:StringRes val label: Int) {
    TEN_MB(10L * 1024L * 1024L, R.string.large_file_size_10),
    FIFTY_MB(50L * 1024L * 1024L, R.string.large_file_size_50),
    HUNDRED_MB(100L * 1024L * 1024L, R.string.large_file_size_100),
    FIVE_HUNDRED_MB(500L * 1024L * 1024L, R.string.large_file_size_500)
}

private enum class TypeFilter(@param:StringRes val label: Int, val category: LargeFileCategory?) {
    ALL(R.string.large_file_type_all, null),
    DOCUMENTS(R.string.large_file_type_documents, LargeFileCategory.DOCUMENT),
    IMAGES(R.string.large_file_type_images, LargeFileCategory.IMAGE),
    VIDEOS(R.string.large_file_type_videos, LargeFileCategory.VIDEO),
    AUDIO(R.string.large_file_type_audio, LargeFileCategory.AUDIO),
    ARCHIVES(R.string.large_file_type_archives, LargeFileCategory.ARCHIVE),
    OTHER(R.string.large_file_type_other, LargeFileCategory.OTHER)
}

private enum class DateFilter(@param:StringRes val label: Int, val ageMillis: Long) {
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
            itemAnimator = null
        }
        view.findViewById<View>(R.id.sizeFilter).setOnClickListener { showSizeMenu(it) }
        view.findViewById<View>(R.id.typeFilter).setOnClickListener { showTypeMenu(it) }
        view.findViewById<View>(R.id.dateFilter).setOnClickListener { showDateMenu(it) }
        view.findViewById<View>(R.id.selectAllAction).setOnClickListener { toggleSelectAll() }
        view.findViewById<View>(R.id.largeFileStateAction).setOnClickListener {
            if (DocumentStore.canModifyStorage(requireContext())) scanFiles() else accessFlow.request()
        }
        view.findViewById<View>(R.id.cleanLargeFiles).setOnClickListener { confirmDelete() }
        parentFragmentManager.setFragmentResultListener(DELETE_REQUEST, viewLifecycleOwner) { _, _ -> deleteSelected() }
        updateFilterLabels()
        if (DocumentStore.canModifyStorage(requireContext())) scanFiles() else renderAccessState()
    }

    private fun scanFiles() {
        if (scanning || !isAdded) return
        scanning = true
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) { LargeFileRepository.scan(requireContext().applicationContext) }
            if (!isAdded) return@launch
            allFiles = snapshot
            scanning = false
            applyFilters(clearSelection = true)
        }
    }

    private fun applyFilters(clearSelection: Boolean) {
        if (clearSelection) selectedPaths.clear()
        val oldestAllowed = if (dateFilter.ageMillis == 0L) Long.MAX_VALUE else System.currentTimeMillis() - dateFilter.ageMillis
        visibleFiles = allFiles.asSequence()
            .filter { it.size >= sizeFilter.minimumBytes }
            .filter { typeFilter.category == null || it.category == typeFilter.category }
            .filter { dateFilter.ageMillis == 0L || (it.modified in 1..oldestAllowed) }
            .sortedWith(compareByDescending<LargeFileItem> { it.size }.thenBy { it.path })
            .toList()
        adapter.submitList(visibleFiles)
        adapter.selectedPaths = selectedPaths.toSet()
        renderContent()
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
        root.findViewById<TextView>(R.id.largeFileStateAction).apply {
            setText(R.string.storage_access_action)
            visibility = View.VISIBLE
        }
        intArrayOf(R.id.sizeFilter, R.id.typeFilter, R.id.dateFilter).forEach { id ->
            root.findViewById<View>(id).isEnabled = false
        }
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
        root.findViewById<TextView>(R.id.selectedLargeFileSummary).text = if (selectedItems.isEmpty()) {
            getString(R.string.large_files_nothing_selected)
        } else {
            resources.getQuantityString(
                R.plurals.large_files_selected,
                selectedItems.size,
                selectedItems.size,
                DocumentStore.formatSize(selectedSize)
            )
        }
        root.findViewById<TextView>(R.id.cleanLargeFiles).apply {
            isEnabled = selectedItems.isNotEmpty()
            text = if (selectedItems.isEmpty()) getString(R.string.large_files_clean)
            else getString(R.string.large_files_clean_size, DocumentStore.formatSize(selectedSize))
        }
    }

    private fun showLoading(show: Boolean) {
        val root = view ?: return
        root.findViewById<View>(R.id.largeFileLoading).visibility = if (show) View.VISIBLE else View.GONE
        intArrayOf(R.id.sizeFilter, R.id.typeFilter, R.id.dateFilter).forEach { id ->
            root.findViewById<View>(id).isEnabled = !show
        }
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
        val dialogTag = "large_file_cleaning"
        ProcessingDialogFragment.newInstance(getString(R.string.large_files_cleaning))
            .show(parentFragmentManager, dialogTag)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                LargeFileRepository.delete(requireContext().applicationContext, selected)
            }
            (parentFragmentManager.findFragmentByTag(dialogTag) as? ProcessingDialogFragment)?.dismissAllowingStateLoss()
            allFiles = allFiles.filterNot { result.deletedPaths.contains(it.path) }
            selectedPaths.clear()
            applyFilters(clearSelection = false)
            if (result.deletedPaths.isNotEmpty()) {
                (requireActivity() as MainActivity).refreshHomeDocuments()
            }
            val message = when {
                result.deletedPaths.isEmpty() -> getString(R.string.large_files_delete_failed)
                result.failedCount > 0 -> resources.getQuantityString(
                    R.plurals.large_files_clean_partial,
                    result.failedCount,
                    DocumentStore.formatSize(result.freedBytes),
                    result.failedCount
                )
                else -> getString(R.string.large_files_clean_success, DocumentStore.formatSize(result.freedBytes))
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateFilterLabels() {
        view?.findViewById<TextView>(R.id.sizeFilter)?.setText(sizeFilter.label)
        view?.findViewById<TextView>(R.id.typeFilter)?.setText(typeFilter.label)
        view?.findViewById<TextView>(R.id.dateFilter)?.setText(dateFilter.label)
    }

    private fun showSizeMenu(anchor: View) = showMenu(anchor, SizeFilter.entries, sizeFilter) {
        sizeFilter = it
        updateFilterLabels()
        applyFilters(clearSelection = true)
    }

    private fun showTypeMenu(anchor: View) = showMenu(anchor, TypeFilter.entries, typeFilter) {
        typeFilter = it
        updateFilterLabels()
        applyFilters(clearSelection = true)
    }

    private fun showDateMenu(anchor: View) = showMenu(anchor, DateFilter.entries, dateFilter) {
        dateFilter = it
        updateFilterLabels()
        applyFilters(clearSelection = true)
    }

    private fun <T> showMenu(anchor: View, values: List<T>, selected: T, onSelected: (T) -> Unit) where T : Enum<T> {
        val popup = PopupMenu(requireContext(), anchor)
        values.forEachIndexed { index, value ->
            val label = when (value) {
                is SizeFilter -> value.label
                is TypeFilter -> value.label
                is DateFilter -> value.label
                else -> error("Unsupported filter")
            }
            popup.menu.add(0, index, index, label).isChecked = value == selected
        }
        popup.menu.setGroupCheckable(0, true, true)
        popup.setOnMenuItemClickListener { item ->
            values.getOrNull(item.itemId)?.let(onSelected)
            true
        }
        popup.show()
    }

    companion object {
        const val DELETE_REQUEST = "large_file_delete_confirmed"
    }
}

class LargeFileDeleteDialog : BottomSheetDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.dialog_large_file_delete, container, false)

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
        view.findViewById<TextView>(R.id.largeDeleteMessage).text =
            resources.getQuantityString(
                R.plurals.large_files_delete_message,
                count,
                count,
                DocumentStore.formatSize(size)
            )
        view.findViewById<View>(R.id.cancelLargeDelete).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.confirmLargeDelete).setOnClickListener {
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
