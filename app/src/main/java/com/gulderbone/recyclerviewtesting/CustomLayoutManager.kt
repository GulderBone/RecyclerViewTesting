package com.gulderbone.recyclerviewtesting

import android.content.Context
import android.util.AttributeSet
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.util.forEach
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

class CustomLayoutManager(
    private val context: Context,
    private val columns: Int,
    private val rows: Int,
) : RecyclerView.LayoutManager() {

    private var _firstVisiblePosition = 0

    private var _decoratedChildWidth: Int = 0
    private var _decoratedChildHeight: Int = 0

    private var _firstChangedPosition = 0
    private var _changedPositionCount = 0

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
    }

    override fun generateLayoutParams(c: Context?, attrs: AttributeSet?): RecyclerView.LayoutParams =
        LayoutParams(c, attrs)

    override fun generateLayoutParams(lp: ViewGroup.LayoutParams?): RecyclerView.LayoutParams =
        LayoutParams(lp as MarginLayoutParams)

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (itemCount == 0) {
            detachAndScrapAttachedViews(recycler)
            return
        }

        if (itemCount == 0 && state.isPreLayout) {
            return
        }

        if (!state.isPreLayout) {
            _firstChangedPosition = 0
            _changedPositionCount = 0
        }

        if (childCount == 0) {
            val scrap = recycler.getViewForPosition(0)
            addView(scrap)
            measureChildWithMargins(scrap, 0, 0)

            _decoratedChildWidth = getDecoratedMeasuredWidth(scrap)
            _decoratedChildHeight = getDecoratedMeasuredHeight(scrap)

            detachAndScrapView(scrap, recycler)
        }

        var removedCache: SparseIntArray? = null

        if (state.isPreLayout) {
            removedCache = SparseIntArray(childCount)
            for (i in 0 until childCount) {
                val view = getChildAt(i) ?: continue
                val lp = view.layoutParams as LayoutParams

                if (lp.isItemRemoved) {
                    removedCache.put(lp.viewLayoutPosition, REMOVE_VISIBLE)
                }
            }

            if (removedCache.size() == 0 && _changedPositionCount > 0) {
                for (i in _firstChangedPosition until (_firstChangedPosition + _changedPositionCount)) {
                    removedCache.put(i, REMOVE_INVISIBLE)
                }
            }
        }

        var childLeft: Int
        var childTop: Int

        if (childCount == 0 || ((!state.isPreLayout && getVisibleChildCount() >= state.itemCount))) {
            _firstVisiblePosition = 0
            childLeft = paddingLeft
            childTop = paddingTop
        } else {
            val topChild = getChildAt(0) ?: return
            childLeft = getDecoratedLeft(topChild)
            childTop = getDecoratedTop(topChild)

            if (!state.isPreLayout && getVerticalSpace() > (rows * _decoratedChildHeight)) {
                _firstVisiblePosition %= this.columns
                childTop = paddingTop

                if ((_firstVisiblePosition + this.columns) > state.itemCount) {
                    _firstVisiblePosition = max(itemCount - this.columns, 0)
                    childLeft = paddingLeft
                }
            }
        }

        detachAndScrapAttachedViews(recycler)

        fillGrid(Direction.NONE, childLeft, childTop, recycler, state, removedCache)

        if (!state.isPreLayout && recycler.scrapList.isNotEmpty()) {
            val scrapList = recycler.scrapList
            val disappearingViews = mutableSetOf<View>()

            for (i in scrapList.indices) {
                val view = scrapList[i].itemView
                val lp = view.layoutParams as LayoutParams

                if (!lp.isItemRemoved) {
                    disappearingViews.add(view)
                }
            }

            for (child in disappearingViews) {
                layoutDisappearingView(child)
            }
        }
    }

    private fun layoutDisappearingView(disappearingChild: View) {
        addDisappearingView(disappearingChild)

        val lp: LayoutParams = disappearingChild.layoutParams as LayoutParams

        val newRow = getGlobalRowOfPosition(lp.viewAdapterPosition)
        val rowDelta: Int = newRow - lp.row
        val newCol = getGlobalColumnOfPosition(lp.viewAdapterPosition)
        val colDelta: Int = newCol - lp.column

        layoutTempChildView(disappearingChild, rowDelta, colDelta, disappearingChild)
    }

    override fun canScrollHorizontally(): Boolean = true

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }

        //Take leftmost measurements from the top-left child
        val topView = getChildAt(0)
        //Take rightmost measurements from the top-right child
        val bottomView = getChildAt(this.columns - 1)

        //Optimize the case where the entire data set is too small to scroll
        val viewSpan = getDecoratedRight(bottomView!!) - getDecoratedLeft(topView!!)
        if (viewSpan < getHorizontalSpace()) {
            //We cannot scroll in either direction
            return 0
        }

        val delta: Int
        val leftBoundReached = getFirstVisibleColumn() == 0
        val rightBoundReached: Boolean = getLastVisibleColumn() >= getTotalColumnCount()
        if (dx > 0) { // Contents are scrolling left
            //Check right bound
            if (rightBoundReached) {
                //If we've reached the last column, enforce limits
                val rightOffset = getHorizontalSpace() - getDecoratedRight(bottomView) + paddingRight
                delta = max(-dx.toDouble(), rightOffset.toDouble()).toInt()
            } else {
                //No limits while the last column isn't visible
                delta = -dx
            }
        } else { // Contents are scrolling right
            //Check left bound
            if (leftBoundReached) {
                val leftOffset = -getDecoratedLeft(topView) + paddingLeft
                delta = min(-dx.toDouble(), leftOffset.toDouble()).toInt()
            } else {
                delta = -dx
            }
        }

        offsetChildrenHorizontal(delta)

        if (dx > 0) {
            if (getDecoratedRight(topView) < 0 && !rightBoundReached) {
                fillGrid(Direction.END, recycler, state)
            } else if (!rightBoundReached) {
                fillGrid(Direction.NONE, recycler, state)
            }
        } else {
            if (getDecoratedLeft(topView) > 0 && !leftBoundReached) {
                fillGrid(Direction.START, recycler, state)
            } else if (!leftBoundReached) {
                fillGrid(Direction.NONE, recycler, state)
            }
        }

        /*
         * Return value determines if a boundary has been reached
         * (for edge effects and flings). If returned value does not
         * match original delta (passed in), RecyclerView will draw
         * an edge effect.
         */
        return -delta
    }

    private fun getFirstVisibleColumn(): Int = (_firstVisiblePosition % getTotalColumnCount())

    private fun getLastVisibleColumn(): Int = getFirstVisibleColumn() + this.columns

    private fun fillGrid(direction: Direction, recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        fillGrid(direction, 0, 0, recycler, state, null)
    }

    private fun fillGrid(
        direction: Direction,
        emptySpaceLeft: Int,
        emptySpaceTop: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        removedPositions: SparseIntArray?,
    ) {
        if (_firstVisiblePosition < 0) {
            _firstVisiblePosition = 0
        } else if (_firstVisiblePosition >= state.itemCount) {
            _firstVisiblePosition = state.itemCount - 1
        }

        val viewCache = SparseArray<View>(childCount)
        var startLeftOffset = emptySpaceLeft
        var startTopOffset = emptySpaceTop

        if (childCount != 0) {
            val topView = getChildAt(0) ?: return
            startTopOffset = getDecoratedTop(topView)
            startLeftOffset = getDecoratedLeft(topView)
            when (direction) {
                Direction.NONE -> {}
                Direction.START -> startLeftOffset -= _decoratedChildWidth
                Direction.END -> startLeftOffset += _decoratedChildWidth
            }
        }

        //Cache all views by their existing position, before updating counts
        cacheViewsByExistingPositions(viewCache)

        //Temporarily detach all views
        viewCache.forEach { _, value -> detachView(value) }

        when (direction) {
            Direction.NONE -> {}
            Direction.START -> _firstVisiblePosition -= 1
            Direction.END -> _firstVisiblePosition += 1
        }

        var leftOffset = startLeftOffset
        var topOffset = startTopOffset

        // Calculate the new width and height for each item
        val availableWidth = getHorizontalSpace()
        val availableHeight = getVerticalSpace()
        _decoratedChildWidth = availableWidth / this.columns
        _decoratedChildHeight = availableHeight / rows

        for (i in 0 until getVisibleChildCount()) {
            var nextPosition = positionOfIndex(i)

            var offsetPositionDelta = 0
            if (state.isPreLayout) {
                var offsetPosition = nextPosition


                for (offset in 0 until removedPositions?.size()!!) {
                    if (removedPositions.valueAt(offset) == REMOVE_INVISIBLE && removedPositions.keyAt(offset) < nextPosition) {
                        offsetPosition--
                    }
                }
                offsetPositionDelta = nextPosition - offsetPosition
                nextPosition = offsetPosition
            }

            // If the next position is out of bounds, we break
            if (nextPosition < 0 || nextPosition >= state.itemCount) {
                break
            }

            var view = viewCache.get(nextPosition)
            if (view == null) {
                view = recycler.getViewForPosition(nextPosition)
                addView(view)

                if (!state.isPreLayout) {
                    val lp = view.layoutParams as LayoutParams
                    lp.row = getGlobalRowOfPosition(nextPosition)
                    lp.column = getGlobalColumnOfPosition(nextPosition)
                }

                measureChildWithMargins(view, 0, 0)
                layoutDecorated(view, leftOffset, topOffset, leftOffset + _decoratedChildWidth, topOffset + _decoratedChildHeight)
            } else {
                attachView(view)
                viewCache.remove(nextPosition)
            }

            if (i % this.columns == this.columns - 1) {
                leftOffset = startLeftOffset
                topOffset += _decoratedChildHeight

                if (state.isPreLayout) {
                    layoutAppearingViews(recycler, view, nextPosition, removedPositions?.size()!!, offsetPositionDelta)
                }

            } else {
                leftOffset += _decoratedChildWidth
            }
        }
    }

    private fun layoutAppearingViews(
        recycler: RecyclerView.Recycler,
        referenceView: View,
        referencePosition: Int,
        extraCount: Int,
        offset: Int,
    ) {
        if (extraCount < 1) return

        for (extra in 1..extraCount) {
            //Grab the next position after the reference
            val extraPosition = referencePosition + extra
            if (extraPosition < 0 || extraPosition >= itemCount) {
                //Can't do anything with this
                continue
            }

            /*
             * Obtain additional position views that we expect to appear
             * as part of the animation.
             */
            val appearing = recycler.getViewForPosition(extraPosition)
            addView(appearing)

            //Find layout delta from reference position
            val newRow = getGlobalRowOfPosition(extraPosition + offset)
            val rowDelta = newRow - getGlobalRowOfPosition(referencePosition + offset)
            val newCol: Int = getGlobalRowOfPosition(extraPosition + offset)
            val colDelta: Int = newCol - getGlobalColumnOfPosition(referencePosition + offset)

            layoutTempChildView(appearing, rowDelta, colDelta, referenceView)
        }
    }

    private fun layoutTempChildView(child: View, rowDelta: Int, colDelta: Int, referenceView: View) {
        //Set the layout position to the global row/column difference from the reference view
        val layoutTop: Int = getDecoratedTop(referenceView) + rowDelta * _decoratedChildHeight
        val layoutLeft: Int = getDecoratedLeft(referenceView) + colDelta * _decoratedChildWidth

        measureChildWithMargins(child, 0, 0)
        layoutDecorated(
            child, layoutLeft, layoutTop,
            layoutLeft + _decoratedChildHeight,
            layoutTop + _decoratedChildWidth
        )
    }

    private fun cacheViewsByExistingPositions(viewCache: SparseArray<View>) {
        for (i in 0 until childCount) {
            val position = positionOfIndex(i)
            val child = getChildAt(i) ?: continue
            viewCache.put(position, child)
        }
    }

    private fun getVisibleChildCount(): Int {
        return this.columns * rows
    }

    private fun positionOfIndex(childIndex: Int): Int {
        val row = childIndex / this.columns
        val column = childIndex % this.columns

        return _firstVisiblePosition + row * this.columns + column
    }

    private fun getHorizontalSpace(): Int = width - paddingRight - paddingLeft
    private fun getVerticalSpace(): Int = height - paddingBottom - paddingTop
    private fun getTotalColumnCount(): Int = max(1, itemCount / this.columns)

    private companion object {
        private const val REMOVE_VISIBLE: Int = 0
        private const val REMOVE_INVISIBLE: Int = 1
    }

    private fun getGlobalRowOfPosition(position: Int): Int {
        return position / this.columns
    }

    private fun getGlobalColumnOfPosition(position: Int): Int {
        return position % this.columns
    }


    class LayoutParams : RecyclerView.LayoutParams {
        //Current row in the grid
        var row: Int = 0

        //Current column in the grid
        var column: Int = 0

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: MarginLayoutParams?) : super(source)
        constructor(source: ViewGroup.LayoutParams?) : super(source)
        constructor(source: RecyclerView.LayoutParams?) : super(source)
    }
}

