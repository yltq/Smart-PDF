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
        private val location = view.findViewById<TextView>(R.id.largeFileLocation)
        private val type = view.findViewById<TextView>(R.id.largeFileType)
        private val selection = view.findViewById<ImageView>(R.id.largeFileSelection)

        fun bind(item: LargeFileItem, selectionOnly: Boolean) {
            val selected = selectedPaths.contains(item.path)
            selection.setImageResource(if (selected) R.drawable.ic_selection_selected else R.drawable.ic_selection_unselected)
            itemView.isSelected = selected
            if (selectionOnly) return
            val context = itemView.context
            icon.imageTintList = ColorStateList.valueOf(categoryColor(item.category))
            name.text = item.name
            val date = if (item.modified > 0L) {
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.modified))
            } else context.getString(R.string.unknown_date)
            details.text = context.getString(R.string.large_file_details, DocumentStore.formatSize(item.size), date)
            location.text = item.path.substringBeforeLast('/', context.getString(R.string.large_file_storage))
            type.setText(item.category.labelRes)
            itemView.setOnClickListener {
                onSelectionChanged(item)
                itemView.animate().cancel()
                itemView.scaleX = .985f
                itemView.scaleY = .985f
                itemView.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
            }
        }

        private fun categoryColor(category: LargeFileCategory): Int = when (category) {
            LargeFileCategory.DOCUMENT -> 0xFFE91922.toInt()
            LargeFileCategory.IMAGE -> 0xFF7657D6.toInt()
            LargeFileCategory.VIDEO -> 0xFFE05F83.toInt()
            LargeFileCategory.AUDIO -> 0xFF258E70.toInt()
            LargeFileCategory.ARCHIVE -> 0xFFC77B20.toInt()
            LargeFileCategory.OTHER -> 0xFF697080.toInt()
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
