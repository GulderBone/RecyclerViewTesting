package com.gulderbone.recyclerviewtesting

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val nameTextView: TextView = view.findViewById(R.id.item_title)

    fun bind(tile: Tile) {
        nameTextView.text = tile.name
    }

    fun bindPlaceholder() {
        nameTextView.text = "Loading..."
    }
}