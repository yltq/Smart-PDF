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

import android.app.Activity
import android.content.res.ColorStateList
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

private fun BottomSheetDialogFragment.expandSheet() {
    (dialog as? BottomSheetDialog)?.behavior?.apply {
        skipCollapsed = true
        state = BottomSheetBehavior.STATE_EXPANDED
    }
}

object FileDialogs {
    const val RESULT_CHANGED = "file_changed"
    const val RESULT_VIEWER_CHANGED = "viewer_file_changed"
    const val RESULT_ACTION = "file_action"
    const val RESULT_URI = "file_uri"
    const val RESULT_OLD_URI = "old_file_uri"
    const val RESULT_FAVORITE = "favorite"
    const val ACTION_RENAMED = "renamed"
    const val ACTION_DELETED = "deleted"
    const val ACTION_FAVORITE = "favorite"
    fun share(activity: Activity, item: DocumentItem) {
        val shareUri: Uri = runCatching { DocumentStore.shareUri(activity, item) }.getOrElse { item.uri }
        (activity as? MainActivity)?.suppressNextPageAdRefresh()
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = item.mime.ifBlank { DocumentStore.inferredMime(item.name) }; putExtra(Intent.EXTRA_STREAM, shareUri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, activity.getString(R.string.share_document)))
    }
}

class RenameDialogFragment : BottomSheetDialogFragment() {
    companion object {
        private const val ARG_NOTIFY_VIEWER = "notify_viewer"
        fun newInstance(item: DocumentItem, notifyViewer: Boolean = false) = RenameDialogFragment().apply {
            arguments = Bundle().putDocument(item).apply { putBoolean(ARG_NOTIFY_VIEWER, notifyViewer) }
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = inflater.inflate(R.layout.dialog_rename, container, false)
    override fun onStart() { super.onStart(); expandSheet() }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val item = requireArguments().document() ?: return
        val extension = item.name.substringAfterLast('.', ""); val input = view.findViewById<EditText>(R.id.renameInput)
        view.findViewById<TextView>(R.id.renameTitle).setText(R.string.rename)
        view.findViewById<TextView>(R.id.renameMessage).setText(R.string.rename_extension_hint)
        view.findViewById<TextView>(R.id.cancelRename).setText(R.string.cancel)
        view.findViewById<TextView>(R.id.confirmRename).setText(R.string.rename)
        input.setText(item.name.substringBeforeLast('.', item.name)); input.setSelectAllOnFocus(true)
        view.findViewById<View>(R.id.cancelRename).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.confirmRename).setOnClickListener {
            val base = input.text.toString().trim(); if (base.isEmpty()) return@setOnClickListener
            val name = if (extension.isBlank()) base else "$base.$extension"
            if (DocumentStore.nameExists(item, name)) {
                input.error = getString(R.string.file_name_exists)
                return@setOnClickListener
            }
            val renamed = DocumentStore.renameItem(requireContext(), item, name)
            if (renamed != null) {
                val result = Bundle().putDocument(renamed).apply {
                    putString(FileDialogs.RESULT_ACTION, FileDialogs.ACTION_RENAMED)
                    putString(FileDialogs.RESULT_OLD_URI, item.uri.toString())
                }
                parentFragmentManager.setFragmentResult(FileDialogs.RESULT_CHANGED, result)
                if (requireArguments().getBoolean(ARG_NOTIFY_VIEWER)) parentFragmentManager.setFragmentResult(FileDialogs.RESULT_VIEWER_CHANGED, result)
                dismiss()
            } else Toast.makeText(requireContext(), R.string.rename_failed, Toast.LENGTH_SHORT).show()
        }
        input.requestFocus(); input.postDelayed({ (requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }, 180)
    }
}

class OutputNameDialogFragment : BottomSheetDialogFragment() {
    companion object {
        const val RESULT_NAME = "output_name"
        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_DEFAULT_NAME = "default_name"
        private const val ARG_TITLE = "title"
        fun newInstance(requestKey: String, defaultName: String, title: String) = OutputNameDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_REQUEST_KEY, requestKey)
                putString(ARG_DEFAULT_NAME, defaultName)
                putString(ARG_TITLE, title)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = inflater.inflate(R.layout.dialog_rename, container, false)
    override fun onStart() { super.onStart(); expandSheet() }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val input = view.findViewById<EditText>(R.id.renameInput)
        view.findViewById<TextView>(R.id.renameTitle).text = requireArguments().getString(ARG_TITLE)
        view.findViewById<TextView>(R.id.renameMessage).setText(R.string.output_name_hint)
        view.findViewById<TextView>(R.id.cancelRename).apply { setText(R.string.cancel); setOnClickListener { dismiss() } }
        view.findViewById<TextView>(R.id.confirmRename).apply {
            setText(R.string.confirm)
            setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setOnClickListener
                if (PdfTools.outputExists(requireContext(), name)) {
                    input.error = getString(R.string.file_name_exists)
                    return@setOnClickListener
                }
                parentFragmentManager.setFragmentResult(
                    requireArguments().getString(ARG_REQUEST_KEY).orEmpty(),
                    Bundle().apply { putString(RESULT_NAME, name) }
                )
                dismiss()
            }
        }
        input.setText(requireArguments().getString(ARG_DEFAULT_NAME).orEmpty())
        input.setSelectAllOnFocus(true)
        input.requestFocus()
        input.postDelayed({
            (requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 180)
    }
}

class DeleteConfirmDialogFragment : BottomSheetDialogFragment() {
    companion object {
        private const val ARG_NOTIFY_VIEWER = "notify_viewer"
        fun newInstance(item: DocumentItem, notifyViewer: Boolean = false) = DeleteConfirmDialogFragment().apply {
            arguments = Bundle().putDocument(item).apply { putBoolean(ARG_NOTIFY_VIEWER, notifyViewer) }
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = inflater.inflate(R.layout.dialog_confirm_delete, container, false)
    override fun onStart() { super.onStart(); expandSheet() }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val item = requireArguments().document() ?: return
        view.findViewById<View>(R.id.cancelDelete).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.confirmDelete).setOnClickListener {
            if (DocumentStore.delete(requireContext(), item)) {
                val result = Bundle().apply {
                    putString(FileDialogs.RESULT_ACTION, FileDialogs.ACTION_DELETED)
                    putString(FileDialogs.RESULT_URI, item.uri.toString())
                }
                parentFragmentManager.setFragmentResult(FileDialogs.RESULT_CHANGED, result)
                if (requireArguments().getBoolean(ARG_NOTIFY_VIEWER)) parentFragmentManager.setFragmentResult(FileDialogs.RESULT_VIEWER_CHANGED, result)
                dismiss()
            } else Toast.makeText(requireContext(), R.string.delete_failed, Toast.LENGTH_SHORT).show()
        }
    }
}

class MoreDialogFragment : BottomSheetDialogFragment() {
    companion object {
        private const val ARG_FROM_VIEWER = "from_viewer"
        fun newInstance(item: DocumentItem, fromViewer: Boolean = false) = MoreDialogFragment().apply { arguments = Bundle().putDocument(item).apply { putBoolean(ARG_FROM_VIEWER, fromViewer) } }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = inflater.inflate(R.layout.dialog_more, container, false)
    override fun onStart() { super.onStart(); expandSheet() }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val item = requireArguments().document() ?: return
        val fromViewer = requireArguments().getBoolean(ARG_FROM_VIEWER)
        val header = view.findViewById<View>(R.id.documentHeader)
        bindDocumentView(header, item, DocumentStore.isFavorite(requireContext(), item))
        header.findViewById<View>(R.id.moreButton).visibility = View.GONE
        view.findViewById<TextView>(R.id.documentPath).apply {
            text = item.uri.path?.takeIf(String::isNotBlank) ?: item.uri.toString()
            setOnClickListener {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.file_path), text))
                Toast.makeText(requireContext(), R.string.file_path_copied, Toast.LENGTH_SHORT).show()
            }
        }
        val favoriteButton = header.findViewById<ImageView>(R.id.favoriteButton)
        favoriteButton.setOnClickListener {
            val favorite = DocumentStore.toggleFavorite(requireContext(), item)
            favoriteButton.setImageResource(if (favorite) R.drawable.bookmark_on else R.drawable.bookmark_off)
            parentFragmentManager.setFragmentResult(
                FileDialogs.RESULT_CHANGED,
                Bundle().apply {
                    putString(FileDialogs.RESULT_ACTION, FileDialogs.ACTION_FAVORITE)
                    putString(FileDialogs.RESULT_URI, item.uri.toString())
                    putBoolean(FileDialogs.RESULT_FAVORITE, favorite)
                }
            )
        }
        val showOpen = !fromViewer
        view.findViewById<View>(R.id.openDivider).visibility = if (showOpen) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.openAction).apply {
            visibility = if (showOpen) View.VISIBLE else View.GONE
            if (visibility == View.VISIBLE) action(this, R.drawable.ic_open, getString(R.string.open_file)) {
                (requireActivity() as MainActivity).openDocumentWithAd(item)
                dismiss()
            }
        }
        action(view.findViewById(R.id.renameAction), R.drawable.ic_rename, getString(R.string.rename)) { dismiss(); RenameDialogFragment.newInstance(item, fromViewer).show(parentFragmentManager, "rename") }
        action(view.findViewById(R.id.shareAction), R.drawable.ic_share, getString(R.string.share_file)) { FileDialogs.share(requireActivity(), item); dismiss() }
        val showPdfActions = item.type == DocumentType.PDF
        if (showPdfActions) view.findViewById<View>(R.id.actionsScroll).layoutParams = view.findViewById<View>(R.id.actionsScroll).layoutParams.apply { height = requireContext().dp(380) }
        view.findViewById<View>(R.id.pdfDivider).visibility = if (showPdfActions) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.pdfMergeDivider).visibility = if (showPdfActions) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.pdfMergeAction).apply {
            visibility = if (showPdfActions) View.VISIBLE else View.GONE
            if (showPdfActions) action(this, R.drawable.ic_merge, getString(R.string.merge_pdf)) { dismiss(); (requireActivity() as MainActivity).navigate(ToolListFragment.newInstance(ToolListFragment.MODE_MERGE, item)) }
        }
        view.findViewById<View>(R.id.pdfPrintDivider).visibility = if (showPdfActions) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.pdfAction).apply {
            visibility = if (showPdfActions) View.VISIBLE else View.GONE
            if (item.type == DocumentType.PDF) action(this, R.drawable.ic_split, getString(R.string.split_pdf)) { openSplit(item) }
        }
        view.findViewById<View>(R.id.pdfPrintAction).apply {
            visibility = if (showPdfActions) View.VISIBLE else View.GONE
            if (item.type == DocumentType.PDF) action(this, R.drawable.ic_print, getString(R.string.print_pdf)) { PdfPrintHelper.print(requireActivity(), item); dismiss() }
        }
        view.findViewById<View>(R.id.pdfMarkupDivider).visibility = if (showPdfActions) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.pdfMarkupAction).apply {
            visibility = if (showPdfActions) View.VISIBLE else View.GONE
            if (showPdfActions) action(this, R.drawable.ic_markup, getString(R.string.annotate_pdf)) { dismiss(); (requireActivity() as MainActivity).navigate(PdfMarkupFragment.newInstance(item)) }
        }
        view.findViewById<View>(R.id.pdfOrganizeDivider).visibility = if (showPdfActions) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.pdfOrganizeAction).apply { visibility = if (showPdfActions) View.VISIBLE else View.GONE; if (showPdfActions) action(this, R.drawable.ic_pages, getString(R.string.organize_pages)) { dismiss(); (requireActivity() as MainActivity).navigate(PdfPageManageFragment.newInstance(item)) } }
        view.findViewById<View>(R.id.pdfWatermarkDivider).visibility = if (showPdfActions) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.pdfWatermarkAction).apply { visibility = if (showPdfActions) View.VISIBLE else View.GONE; if (showPdfActions) action(this, R.drawable.ic_watermark, getString(R.string.add_watermark)) { dismiss(); PdfWatermarkDialogFragment.newInstance(item).show(parentFragmentManager, "watermark") } }
        action(view.findViewById(R.id.infoAction), R.drawable.ic_info, getString(R.string.file_information)) { Toast.makeText(requireContext(), "${item.name}\n${DocumentStore.subtitle(requireContext(), item)}\n${item.uri}", Toast.LENGTH_LONG).show() }
        val deleteAction = view.findViewById<View>(R.id.deleteAction)
        action(deleteAction, R.drawable.ic_delete, getString(R.string.delete)) { confirmDelete(item, fromViewer) }
        deleteAction.findViewById<ImageView>(R.id.actionIcon).imageTintList = ColorStateList.valueOf(requireContext().getColor(R.color.brand_red))
        deleteAction.findViewById<TextView>(R.id.actionLabel).setTextColor(requireContext().getColor(R.color.brand_red))
        configureScrollHint(view)
    }
    private fun configureScrollHint(root: View) {
        val scroll = root.findViewById<NestedScrollView>(R.id.actionsScroll)
        val hint = root.findViewById<ImageView>(R.id.scrollHint)
        fun updateHint() {
            val content = scroll.getChildAt(0) ?: return
            val hasMoreBelow = content.height - scroll.scrollY > scroll.height + requireContext().dp(8)
            if (hasMoreBelow && hint.visibility != View.VISIBLE) {
                hint.alpha = 0f
                hint.translationY = -requireContext().dp(4).toFloat()
                hint.visibility = View.VISIBLE
                hint.animate().alpha(1f).translationY(0f).setDuration(180L).start()
            } else if (!hasMoreBelow && hint.visibility == View.VISIBLE) {
                hint.animate().alpha(0f).setDuration(120L).withEndAction { hint.visibility = View.GONE }.start()
            }
        }
        scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> updateHint() })
        hint.setOnClickListener { scroll.smoothScrollBy(0, requireContext().dp(180)) }
        scroll.post { updateHint() }
    }
    private fun confirmDelete(item: DocumentItem, fromViewer: Boolean) { dismiss(); DeleteConfirmDialogFragment.newInstance(item, fromViewer).show(parentFragmentManager, "delete_confirm") }
    private fun openSplit(item: DocumentItem) {
        val host = requireActivity() as MainActivity
        val context = requireContext().applicationContext
        dismiss()
        Thread {
            val count = runCatching { PdfTools.pageCount(context, item) }.getOrDefault(0)
            host.runOnUiThread {
                if (count == 1) Toast.makeText(host, R.string.single_page_cannot_split, Toast.LENGTH_LONG).show()
                else if (count > 1) host.navigate(SplitDetailFragment.newInstance(item))
                else Toast.makeText(host, R.string.pdf_preview_failed, Toast.LENGTH_LONG).show()
            }
        }.start()
    }
    private fun action(root: View, icon: Int, label: String, click: () -> Unit) {
        root.findViewById<ImageView>(R.id.actionIcon).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(root.context.getColor(R.color.ink))
        }
        root.findViewById<TextView>(R.id.actionLabel).text = label
        root.setOnClickListener { click() }
    }
}
