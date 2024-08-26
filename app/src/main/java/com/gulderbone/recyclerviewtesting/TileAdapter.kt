package com.gulderbone.recyclerviewtesting

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class TileAdapter(private val items: MutableList<Tile>) : RecyclerView.Adapter<TileViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.grid_item, parent, false)
        return TileViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val tile = items[position]
        holder.bind(tile)
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        val fromItem = items.removeAt(fromPosition)
        items.add(toPosition, fromItem)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun removeItem(position: Int): Tile {
        val item = items.removeAt(position)
        notifyItemRemoved(position)
        return item
    }

    fun addItem(item: Tile) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }
}