package com.gulderbone.recyclerviewtesting

import androidx.paging.PagingSource
import androidx.paging.PagingState

class FixedItemCountPagingSource(private val tiles: List<Tile>) : PagingSource<Int, Tile>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Tile> {
        val page = params.key ?: 0
        val pageSize = params.loadSize
        val start = page * pageSize

        return try {
            val data = tiles.subList(start, (start + pageSize).coerceAtMost(tiles.size))
            LoadResult.Page(
                data = data,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (data.isEmpty()) null else page + 1,
                itemsBefore = start,
                itemsAfter = tiles.size - (start + data.size)
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override val jumpingSupported: Boolean
        get() = true

    override fun getRefreshKey(state: PagingState<Int, Tile>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(state.config.pageSize)
        }
    }
}