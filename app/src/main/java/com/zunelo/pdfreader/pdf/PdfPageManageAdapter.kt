package com.zunelo.pdfreader.pdf

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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

data class PdfPagePlan(val sourceIndex: Int, var rotation: Int = 0, var selected: Boolean = false)

class PdfPageManageAdapter(private val loader: PdfPageLoader, pageCount: Int, private val onChanged: () -> Unit) : RecyclerView.Adapter<PdfPageManageAdapter.Holder>() {
    val pages = MutableList(pageCount) { PdfPagePlan(it) }
    override fun getItemCount() = pages.size
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_manage_page, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val page = pages[position]; holder.boundSource = page.sourceIndex; holder.image.setImageDrawable(null)
        holder.number.text = "${position + 1}"; holder.rotation.text = if (page.rotation == 0) "" else "↻ ${page.rotation}°"
        holder.selected.setImageResource(if (page.selected) R.drawable.ic_selection_selected else R.drawable.ic_selection_unselected)
        holder.image.rotation = page.rotation.toFloat()
        loader.load(page.sourceIndex) { bitmap -> if (holder.boundSource == page.sourceIndex && bitmap != null) holder.image.setImageBitmap(bitmap) }
        holder.itemView.setOnClickListener { page.selected = !page.selected; notifyItemChanged(holder.bindingAdapterPosition); onChanged() }
    }
    override fun onViewRecycled(holder: Holder) { holder.boundSource = -1; holder.image.setImageDrawable(null) }
    fun move(from: Int, to: Int): Boolean { if (from !in pages.indices || to !in pages.indices) return false; Collections.swap(pages, from, to); notifyItemMoved(from, to); return true }
    fun rotateSelected() { pages.filter { it.selected }.forEach { it.rotation = (it.rotation + 90) % 360 }; notifyDataSetChanged(); onChanged() }
    fun deleteSelected(): Boolean { val selected = pages.count { it.selected }; if (selected == 0 || selected == pages.size) return false; pages.removeAll { it.selected }; notifyDataSetChanged(); onChanged(); return true }
    fun selectedCount() = pages.count { it.selected }
    class Holder(view: View) : RecyclerView.ViewHolder(view) { val image: ImageView = view.findViewById(R.id.managePageImage); val number: TextView = view.findViewById(R.id.managePageNumber); val rotation: TextView = view.findViewById(R.id.managePageRotation); val selected: ImageView = view.findViewById(R.id.managePageSelected); var boundSource = -1 }
}
