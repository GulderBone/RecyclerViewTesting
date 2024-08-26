package com.gulderbone.recyclerviewtesting

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager.VERTICAL
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val gridLayoutManager1 = FixedGridLayoutManager(4, 4)
        val tileAdapter1 = TileAdapter(MutableList(100) { Tile("$it") })

       findViewById<RecyclerView>(R.id.gridRecyclerView).apply {
            layoutManager = gridLayoutManager1
            adapter = tileAdapter1
        }

        val gridLayoutManager2 = CustomLayoutManager(this, 2, 5)
        val tileAdapter2 = TileAdapter(MutableList(100) { Tile("$it") })

//        val recyclerView2 = findViewById<RecyclerView>(R.id.gridRecyclerView2).apply {
//            layoutManager = gridLayoutManager2
//            adapter = tileAdapter2
//        }

//        val itemTouchHelper1 = ItemTouchHelper(DragDropItemTouchHelper(tileAdapter1, tileAdapter2))
//        itemTouchHelper1.attachToRecyclerView(recyclerView1)

//        val itemTouchHelper2 = ItemTouchHelper(DragDropItemTouchHelper(tileAdapter2, tileAdapter1))
//        itemTouchHelper2.attachToRecyclerView(recyclerView2)
    }
}