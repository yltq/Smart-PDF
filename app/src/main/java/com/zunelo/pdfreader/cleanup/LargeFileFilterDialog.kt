package com.zunelo.pdfreader.cleanup

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zunelo.pdfreader.R

enum class LargeFileFilterKind { SIZE, TYPE, DATE }

class LargeFileFilterDialog : BottomSheetDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.dialog_large_file_filter, container, false)

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val kind = LargeFileFilterKind.valueOf(requireArguments().getString(ARG_KIND).orEmpty())
        val selectedIndex = requireArguments().getInt(ARG_SELECTED)
        val options = options(kind)
        view.findViewById<TextView>(R.id.filterSheetTitle).setText(
            when (kind) {
                LargeFileFilterKind.SIZE -> R.string.large_file_filter_size_title
                LargeFileFilterKind.TYPE -> R.string.large_file_filter_type_title
                LargeFileFilterKind.DATE -> R.string.large_file_filter_date_title
            }
        )
        val optionContainer = view.findViewById<LinearLayout>(R.id.filterOptions)
        val rows = mutableListOf<Pair<View, ImageView>>()
        var selectionCommitted = false
        options.forEachIndexed { index, option ->
            val row = layoutInflater.inflate(R.layout.item_large_file_filter, optionContainer, false)
            val icon = row.findViewById<ImageView>(R.id.filterOptionIcon)
            val marker = row.findViewById<ImageView>(R.id.filterOptionSelection)
            icon.setImageResource(option.icon)
            icon.imageTintList = ColorStateList.valueOf(option.color)
            row.findViewById<TextView>(R.id.filterOptionLabel).setText(option.label)
            marker.setImageResource(if (index == selectedIndex) R.drawable.ic_selection_selected else R.drawable.ic_selection_unselected)
            row.isSelected = index == selectedIndex
            rows += row to marker
            row.setOnClickListener {
                if (selectionCommitted) return@setOnClickListener
                selectionCommitted = true
                rows.forEachIndexed { rowIndex, (optionRow, selection) ->
                    val selected = rowIndex == index
                    optionRow.isSelected = selected
                    selection.setImageResource(if (selected) R.drawable.ic_selection_selected else R.drawable.ic_selection_unselected)
                }
                marker.alpha = 0f
                marker.scaleX = .72f
                marker.scaleY = .72f
                marker.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150L).start()
                row.postDelayed({
                    parentFragmentManager.setFragmentResult(
                        RESULT_REQUEST,
                        Bundle().apply {
                            putString(RESULT_KIND, kind.name)
                            putInt(RESULT_INDEX, index)
                        }
                    )
                    dismiss()
                }, 110L)
            }
            optionContainer.addView(row)
            if (index != options.lastIndex) {
                layoutInflater.inflate(R.layout.item_dialog_divider, optionContainer, false).also(optionContainer::addView)
            }
        }
    }

    private fun options(kind: LargeFileFilterKind): List<Option> = when (kind) {
        LargeFileFilterKind.SIZE -> SizeFilter.entries.map {
            Option(it.label, R.drawable.ic_filter_size, requireContext().getColor(R.color.ink_secondary))
        }
        LargeFileFilterKind.TYPE -> TypeFilter.entries.map {
            Option(it.label, LargeFileVisuals.icon(it.category), LargeFileVisuals.color(it.category))
        }
        LargeFileFilterKind.DATE -> DateFilter.entries.map {
            Option(it.label, R.drawable.ic_filter_date, requireContext().getColor(R.color.ink_secondary))
        }
    }

    private data class Option(val label: Int, val icon: Int, val color: Int)

    companion object {
        const val RESULT_REQUEST = "large_file_filter_selected"
        const val RESULT_KIND = "filter_kind"
        const val RESULT_INDEX = "filter_index"
        private const val ARG_KIND = "kind"
        private const val ARG_SELECTED = "selected"

        fun newInstance(kind: LargeFileFilterKind, selectedIndex: Int) = LargeFileFilterDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_KIND, kind.name)
                putInt(ARG_SELECTED, selectedIndex)
            }
        }
    }
}
