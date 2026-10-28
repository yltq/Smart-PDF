package com.zunelo.pdfreader.cleanup

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.data.DocumentStore
import java.text.DateFormat
import java.util.Date

class LargeFileAdapter(
    private val onSelectionChanged: (LargeFileItem) -> Unit
) : ListAdapter<LargeFileItem, LargeFileAdapter.Holder>(Diff) {
    var selectedPaths: Set<String> = emptySet()
        set(value) {
            val changed = field union value subtract (field intersect value)
            field = value
            changed.forEach { path ->
                val position = currentList.indexOfFirst { it.path == path }
                if (position >= 0) notifyItemChanged(position, SELECTION_PAYLOAD)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_large_file, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), false)

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        holder.bind(getItem(position), payloads.contains(SELECTION_PAYLOAD))
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon = view.findViewById<ImageView>(R.id.largeFileIcon)
        private val name = view.findViewById<TextView>(R.id.largeFileName)
        private val details = view.findViewById<TextView>(R.id.largeFileDetails)
        private val selection = view.findViewById<ImageView>(R.id.largeFileSelection)

        fun bind(item: LargeFileItem, selectionOnly: Boolean) {
            val selected = selectedPaths.contains(item.path)
            selection.animate().cancel()
            selection.setImageResource(if (selected) R.drawable.ic_selection_selected else R.drawable.ic_selection_unselected)
            itemView.isSelected = selected
            if (selectionOnly) {
                selection.alpha = 0f
                selection.scaleX = .76f
                selection.scaleY = .76f
                selection.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170L).start()
                return
            }
            selection.alpha = 1f
            selection.scaleX = 1f
            selection.scaleY = 1f
            val context = itemView.context
            icon.setImageResource(LargeFileVisuals.icon(item.category))
            icon.imageTintList = ColorStateList.valueOf(LargeFileVisuals.color(item.category))
            name.text = item.name
            val date = if (item.modified > 0L) {
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.modified))
            } else context.getString(R.string.unknown_date)
            details.text = context.getString(R.string.large_file_details, DocumentStore.formatSize(item.size), date)
            itemView.setOnClickListener { onSelectionChanged(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<LargeFileItem>() {
        override fun areItemsTheSame(oldItem: LargeFileItem, newItem: LargeFileItem) = oldItem.path == newItem.path
        override fun areContentsTheSame(oldItem: LargeFileItem, newItem: LargeFileItem) = oldItem == newItem
    }

    private companion object {
        const val SELECTION_PAYLOAD = "selection"
    }
}
