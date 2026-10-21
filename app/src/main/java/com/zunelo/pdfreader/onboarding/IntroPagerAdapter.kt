package com.zunelo.pdfreader.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.zunelo.pdfreader.R

data class IntroPage(
    @DrawableRes val image: Int,
    @StringRes val title: Int,
    @StringRes val description: Int
)

class IntroPagerAdapter(private val pages: List<IntroPage>) : RecyclerView.Adapter<IntroPagerAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_intro_page, parent, false)
    )

    override fun getItemCount() = pages.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val page = pages[position]
        holder.itemView.tag = "intro_page_$position"
        holder.image.setImageResource(page.image)
        holder.image.contentDescription = holder.itemView.context.getString(page.title)
        holder.title.setText(page.title)
        holder.description.setText(page.description)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.introImage)
        val title: TextView = view.findViewById(R.id.introTitle)
        val description: TextView = view.findViewById(R.id.introDescription)
    }
}
