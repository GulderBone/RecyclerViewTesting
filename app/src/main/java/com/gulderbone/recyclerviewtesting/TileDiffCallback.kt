package com.gulderbone.recyclerviewtesting

import androidx.recyclerview.widget.DiffUtil

object TileDiffCallback : DiffUtil.ItemCallback<Tile>() {
    override fun areItemsTheSame(oldTile: Tile, newTile: Tile): Boolean = oldTile.name == newTile.name

    override fun areContentsTheSame(oldTile: Tile, newTile: Tile): Boolean = oldTile == newTile
}