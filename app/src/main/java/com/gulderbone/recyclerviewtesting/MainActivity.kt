package com.gulderbone.recyclerviewtesting

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val itemCount = 100
    private val rows = 2
    private val columns = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val recyclerView = findViewById<RecyclerView>(R.id.gridRecyclerView)
        val gridLayoutManager1 = FixedGridLayoutManager(this, 4, 4, recyclerView)
        val tiles = List(itemCount) { Tile(it, "${it + 1}") }
        val tileAdapter1 = TileAdapter()

        val pagedItems: Flow<PagingData<Tile>> = Pager(
            config = PagingConfig(
                initialLoadSize = rows * columns,
                pageSize = rows * columns,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { FixedItemCountPagingSource(tiles) }
        ).flow.cachedIn(lifecycleScope)

        lifecycleScope.launch {
            pagedItems.collectLatest { pagingData ->
                tileAdapter1.submitData(pagingData)
            }
        }

        recyclerView.apply {
            layoutManager = gridLayoutManager1
            adapter = tileAdapter1
        }

        findViewById<Button>(R.id.nextPageButton).setOnClickListener {
            recyclerView.smoothScrollToPosition(rows * columns + 1)
        }

        findViewById<Button>(R.id.previousPageButton).setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
        }
    }
}