package com.gulderbone.recyclerviewtesting

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter

class TileAdapter : PagingDataAdapter<Tile, TileViewHolder>(TileDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.grid_item, parent, false)
        return TileViewHolder(view)
    }

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val tile = getItem(position)
        if (tile != null) {
            holder.bind(tile)
        }
    }
}