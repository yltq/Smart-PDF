package com.zunelo.pdfreader.common

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

import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class DocumentAdapter(
    private val selectionMode: Boolean = false,
    private val draggable: Boolean = false,
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit = {},
    private val onOpen: (DocumentItem) -> Unit,
    private val onFavorite: (DocumentItem) -> Unit = {},
    private val onMore: (DocumentItem) -> Unit = {}
) : ListAdapter<DocumentItem, DocumentAdapter.Holder>(Diff) {
    var selectedUris: Set<String> = emptySet()
        set(value) { field = value; notifyDataSetChanged() }

    fun refreshFavorite(item: DocumentItem) {
        val position = currentList.indexOfFirst { it.uri == item.uri }
        if (position >= 0) notifyItemChanged(position)
    }

    fun move(from: Int, to: Int): Boolean {
        if (from !in currentList.indices || to !in currentList.indices || from == to) return false
        val reordered = currentList.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        submitList(reordered)
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_document, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        bindDocumentView(holder.itemView, item, DocumentStore.isFavorite(holder.itemView.context, item), selectionMode, selectedUris.contains(item.uri.toString()), draggable)
        holder.itemView.setOnClickListener { onOpen(item) }
        holder.itemView.findViewById<View>(R.id.favoriteButton).setOnClickListener { button ->
            onFavorite(item)
            button.animate().cancel()
            button.scaleX = .78f; button.scaleY = .78f
            button.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
        }
        holder.itemView.findViewById<View>(R.id.moreButton).setOnClickListener { onMore(item) }
        holder.itemView.findViewById<View>(R.id.dragHandle).setOnTouchListener { _, event ->
            if (draggable && event.actionMasked == MotionEvent.ACTION_DOWN) { onDragStart(holder); true } else false
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view)
    private object Diff : DiffUtil.ItemCallback<DocumentItem>() {
        override fun areItemsTheSame(old: DocumentItem, new: DocumentItem) = old.uri == new.uri
        override fun areContentsTheSame(old: DocumentItem, new: DocumentItem) = old == new
    }
}

fun bindDocumentView(root: View, item: DocumentItem, favorite: Boolean, selectionMode: Boolean = false, selected: Boolean = false, draggable: Boolean = false) {
    root.findViewById<ImageView>(R.id.documentIcon).setImageResource(documentIcon(item.type))
    root.findViewById<TextView>(R.id.documentName).text = item.name
    root.findViewById<TextView>(R.id.documentSubtitle).text = DocumentStore.subtitle(root.context, item)
    root.findViewById<ImageView>(R.id.favoriteButton).apply {
        visibility = if (selectionMode) View.GONE else View.VISIBLE
        setImageResource(if (favorite) R.drawable.bookmark_on else R.drawable.bookmark_off)
    }
    root.findViewById<View>(R.id.moreButton).visibility = if (selectionMode) View.GONE else View.VISIBLE
    root.findViewById<View>(R.id.dragHandle).visibility = if (draggable) View.VISIBLE else View.GONE
    root.findViewById<ImageView>(R.id.selectionIndicator).apply {
        visibility = if (selectionMode) View.VISIBLE else View.GONE
        setImageResource(if (selected) R.drawable.ic_selection_selected else R.drawable.ic_selection_unselected)
    }
}

class CategoryAdapter(private val onSelected: (DocumentType?) -> Unit) : RecyclerView.Adapter<CategoryAdapter.Holder>() {
    private val values = listOf<DocumentType?>(null, DocumentType.PDF, DocumentType.WORD, DocumentType.EXCEL, DocumentType.PPT)
    private var selected = 0
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false))
    override fun getItemCount() = values.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val type = values[position]
        holder.label.text = type?.label ?: holder.itemView.context.getString(R.string.all)
        holder.label.isSelected = position == selected
        holder.label.setTextColor(if (position == selected) Palette.WHITE else Palette.MUTED)
        holder.label.setTypeface(holder.label.typeface, if (position == selected) Typeface.BOLD else Typeface.NORMAL)
        holder.itemView.setOnClickListener {
            val previous = selected
            selected = holder.bindingAdapterPosition
            notifyItemChanged(previous)
            notifyItemChanged(selected)
            holder.itemView.animate().cancel()
            holder.itemView.scaleX = .96f; holder.itemView.scaleY = .96f
            holder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(170).start()
            onSelected(type)
        }
    }
    class Holder(view: View) : RecyclerView.ViewHolder(view) { val label: TextView = view.findViewById(R.id.categoryName) }
}

class LanguageAdapter(
    private val values: List<Pair<String, String>>,
    selectedTag: String,
    private val onSelected: (String) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.Holder>() {
    private var selected = values.indexOfFirst { it.first == selectedTag }.coerceAtLeast(0)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_language, parent, false))
    override fun getItemCount() = values.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.name.text = values[position].second
        holder.radio.isChecked = position == selected
        holder.itemView.setBackgroundColor(if (position == selected) 0xFFFFF4F4.toInt() else Palette.WHITE)
        holder.itemView.setOnClickListener { val previous = selected; selected = holder.bindingAdapterPosition; notifyItemChanged(previous); notifyItemChanged(selected); onSelected(values[selected].first) }
    }
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.languageName)
        val radio: android.widget.RadioButton = view.findViewById(R.id.languageRadio)
    }
}

data class SettingRow(val icon: Int, val title: String, val subtitle: String, val action: () -> Unit)
class SettingsAdapter(private val rows: List<SettingRow>) : RecyclerView.Adapter<SettingsAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_setting, parent, false))
    override fun getItemCount() = rows.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.icon.setImageResource(row.icon); holder.title.text = row.title; holder.subtitle.text = row.subtitle
        holder.itemView.setOnClickListener { row.action() }
    }
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.settingIcon)
        val title: TextView = view.findViewById(R.id.settingTitle)
        val subtitle: TextView = view.findViewById(R.id.settingSubtitle)
    }
}

data class PagePreview(val index: Int, val bitmap: Bitmap)
class PageSelectionAdapter(private val onSelectionChanged: (Set<Int>) -> Unit) : RecyclerView.Adapter<PageSelectionAdapter.Holder>() {
    private val pages = mutableListOf<PagePreview>()
    private val selected = linkedSetOf<Int>()
    fun add(page: PagePreview) { pages += page; notifyItemInserted(pages.lastIndex) }
    override fun getItemCount() = pages.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val page = pages[position]
        holder.image.setImageBitmap(page.bitmap); holder.number.text = "${page.index + 1}"
        holder.root.setBackgroundResource(if (selected.contains(page.index)) R.drawable.bg_selected_page else R.drawable.bg_card)
        holder.itemView.setOnClickListener { if (!selected.remove(page.index)) selected.add(page.index); notifyItemChanged(holder.bindingAdapterPosition); onSelectionChanged(selected.toSet()) }
    }
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.pageRoot); val image: ImageView = view.findViewById(R.id.pageImage); val number: TextView = view.findViewById(R.id.pageNumber)
    }
}

class ViewerPageAdapter(private val loader: PdfPageLoader, private val pageCount: Int) : RecyclerView.Adapter<ViewerPageAdapter.Holder>() {
    override fun getItemCount() = pageCount
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_viewer_page, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.boundPage = position
        holder.image.setImageDrawable(null)
        holder.number.text = "${position + 1} / $pageCount"
        loader.load(position) { bitmap -> if (holder.boundPage == position && bitmap != null) holder.image.setImageBitmap(bitmap) }
    }
    override fun onViewRecycled(holder: Holder) { holder.boundPage = -1; holder.image.setImageDrawable(null); super.onViewRecycled(holder) }
    class Holder(view: View) : RecyclerView.ViewHolder(view) { val image: ImageView = view.findViewById(R.id.pageImage); val number: TextView = view.findViewById(R.id.pageNumber); var boundPage = -1 }
}
