package com.zunelo.pdfreader.tools

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
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class ScanPageAdapter(paths: List<String>, private val onChanged: () -> Unit) : RecyclerView.Adapter<ScanPageAdapter.Holder>() {
    private val pages = paths.map { ScanPage(it) }.toMutableList()
    private val thumbnails = mutableMapOf<String, Bitmap>()

    override fun getItemCount() = pages.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_scan_page, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val page = pages[position]
        holder.image.setImageBitmap(thumbnails.getOrPut(page.path) { decodeThumbnail(page.path) })
        holder.number.text = "${position + 1}"
        holder.selected.setImageResource(if (page.selected) R.drawable.ic_selection_selected else R.drawable.ic_selection_unselected)
        holder.itemView.alpha = if (page.selected) 1f else .48f
        holder.itemView.setOnClickListener {
            page.selected = !page.selected
            notifyItemChanged(holder.bindingAdapterPosition)
            onChanged()
        }
    }

    fun move(from: Int, to: Int): Boolean {
        if (from !in pages.indices || to !in pages.indices) return false
        Collections.swap(pages, from, to)
        notifyItemMoved(from, to)
        return true
    }

    fun selectedPaths() = pages.filter { it.selected }.map { it.path }
    fun release() { thumbnails.values.forEach { if (!it.isRecycled) it.recycle() }; thumbnails.clear() }

    private fun decodeThumbnail(path: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 960) sample *= 2
        return requireNotNull(BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }))
    }

    data class ScanPage(val path: String, var selected: Boolean = true)
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.scanPageImage)
        val number: TextView = view.findViewById(R.id.scanPageNumber)
        val selected: ImageView = view.findViewById(R.id.scanPageSelected)
    }
}
