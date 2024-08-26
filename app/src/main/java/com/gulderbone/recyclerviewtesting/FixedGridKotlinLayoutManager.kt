package com.gulderbone.recyclerviewtesting

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import kotlin.math.ceil

class FixedGridLayoutManager(private val columns: Int, private val rows: Int) : RecyclerView.LayoutManager() {

    companion object {
        private val TAG = FixedGridLayoutManager::class.java.simpleName

        /* View Removal Constants */
        private const val REMOVE_VISIBLE = 0
        private const val REMOVE_INVISIBLE = 1

        /* Fill Direction Constants */
        private const val DIRECTION_NONE = -1
        private const val DIRECTION_START = 0
        private const val DIRECTION_END = 1
    }

    /* First (top-left) position visible at any point */
    private var mFirstVisiblePosition = 0

    /* Consistent size applied to all child views */
    private val mDecoratedChildWidth: Int get() = width / columns
    private val mDecoratedChildHeight: Int get() = height / rows

    /* Metrics for the visible window of our data */
    private val mVisibleColumnCount = columns
    private val mVisibleRowCount = rows

    /* Used for tracking off-screen change events */
    private var mFirstChangedPosition = 0
    private var mChangedPositionCount = 0

    /**
     * Number of columns the layout manager uses.
     * Setting it will trigger layout update.
     */
    private var totalColumnCount: Int = columns

    override fun isAutoMeasureEnabled(): Boolean {
        return false
    }

    /*
     * You must return true from this method if you want your
     * LayoutManager to support anything beyond "simple" item
     * animations. Enabling this causes onLayoutChildren() to
     * be called twice on each animated change; once for a
     * pre-layout, and again for the real layout.
     */
    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }

    /*
     * Called by RecyclerView when a view removal is triggered. This is called
     * before onLayoutChildren() in pre-layout if the views removed are not visible. We
     * use it in this case to inform pre-layout that a removal took place.
     *
     * This method is still called if the views removed were visible, but it will
     * happen AFTER pre-layout.
     */
    override fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        mFirstChangedPosition = positionStart
        mChangedPositionCount = itemCount
    }

    /*
     * This method is your initial call from the framework. You will receive it when you
     * need to start laying out the initial set of views. This method will not be called
     * repeatedly, so don't rely on it to continually process changes during user
     * interaction.
     *
     * This method will be called when the data set in the adapter changes, so it can be
     * used to update a layout based on a new item count.
     *
     * If predictive animations are enabled, you will see this called twice. First, with
     * state.isPreLayout() returning true to lay out children in their initial conditions.
     * Then again to lay out children in their final locations.
     */
    override fun onLayoutChildren(recycler: Recycler, state: RecyclerView.State) {
        // We have nothing to show for an empty data set but clear any existing views
        if (itemCount == 0) {
            detachAndScrapAttachedViews(recycler)
            return
        }
        if (childCount == 0 && state.isPreLayout) {
            // Nothing to do during prelayout when empty
            return
        }

        totalColumnCount = ceil(itemCount / rows.toDouble()).toInt()

        // Clear change tracking state when a real layout occurs
        if (!state.isPreLayout) {
            mFirstChangedPosition = 0
            mChangedPositionCount = 0
        }

        if (childCount == 0) { // First or empty layout
            // Scrap measure one child
            val scrap = recycler.getViewForPosition(0)
            addView(scrap)
            measureChildWithMargins(scrap, 0, 0)

            /*
             * We make some assumptions in this code based on every child
             * view being the same size (i.e. a uniform grid). This allows
             * us to compute the following values up front because they
             * won't change.
             */
            detachAndScrapView(scrap, recycler)
        }

        var removedCache: SparseIntArray? = null
        /*
         * During pre-layout, we need to take note of any views that are
         * being removed in order to handle predictive animations
         */
        if (state.isPreLayout) {
            removedCache = SparseIntArray(childCount)
            for (i in 0 until childCount) {
                val view = getChildAt(i)
                if (view != null) {
                    val lp = view.layoutParams as LayoutParams
                    if (lp.isItemRemoved) {
                        // Track these view removals as visible
                        removedCache.put(lp.viewLayoutPosition, REMOVE_VISIBLE)
                    }
                }
            }

            // Track view removals that happened out of bounds (i.e. off-screen)
            if (removedCache.size() == 0 && mChangedPositionCount > 0) {
                for (i in mFirstChangedPosition until mFirstChangedPosition + mChangedPositionCount) {
                    removedCache.put(i, REMOVE_INVISIBLE)
                }
            }
        }

        var childLeft: Int
        var childTop: Int
        if (childCount == 0) { // First or empty layout
            // Reset the visible and scroll positions
            mFirstVisiblePosition = 0
            childLeft = paddingLeft
            childTop = paddingTop
        } else if (!state.isPreLayout && visibleChildCount >= state.itemCount) {
            // Data set is too small to scroll fully, just reset position
            mFirstVisiblePosition = 0
            childLeft = paddingLeft
            childTop = paddingTop
        } else { // Adapter data set changes
            /*
             * Keep the existing initial position, and save off
             * the current scrolled offset.
             */
            val topChild = getChildAt(0)
            childLeft = if (topChild == null) 0 else getDecoratedLeft(topChild)
            childTop = if (topChild == null) 0 else getDecoratedTop(topChild)

            /*
             * When data set is too small to scroll vertically, adjust vertical offset
             * and shift position to the first row, preserving current column
             */
            if (!state.isPreLayout && height > (totalRowCount * mDecoratedChildHeight)) {
                mFirstVisiblePosition %= totalColumnCount
                childTop = paddingTop

                // If the shift overscrolls the column max, back it off
                if (mFirstVisiblePosition + mVisibleColumnCount > state.itemCount) {
                    mFirstVisiblePosition = (state.itemCount - mVisibleColumnCount).coerceAtLeast(0)
                    childLeft = paddingLeft
                }
            }

            /*
             * Adjust the visible position if out of bounds in the
             * new layout. This occurs when the new item count in an adapter
             * is much smaller than it was before, and you are scrolled to
             * a location where no items would exist.
             */
            val maxFirstRow = totalRowCount - (mVisibleRowCount - 1)
            val maxFirstCol = totalColumnCount - (mVisibleColumnCount - 1)
            val isOutOfRowBounds = firstVisibleRow > maxFirstRow
            val isOutOfColBounds = firstVisibleColumn > maxFirstCol
            if (isOutOfRowBounds || isOutOfColBounds) {
                val firstRow: Int = if (isOutOfRowBounds) maxFirstRow else firstVisibleRow
                val firstCol: Int = if (isOutOfColBounds) maxFirstCol else firstVisibleColumn
                mFirstVisiblePosition = firstRow * totalColumnCount + firstCol

                childLeft = width - (mDecoratedChildWidth * mVisibleColumnCount)
                childTop = height - (mDecoratedChildHeight * mVisibleRowCount)

                // Correct cases where shifting to the bottom-right overscrolls the top-left
                //  This happens on data sets too small to scroll in a direction.
                if (firstVisibleRow == 0) {
                    childTop = childTop.coerceAtMost(paddingTop)
                }
                if (firstVisibleColumn == 0) {
                    childLeft = childLeft.coerceAtMost(paddingLeft)
                }
            }
        }

        // Clear all attached views into the recycle bin
        detachAndScrapAttachedViews(recycler)

        // Fill the grid for the initial layout of views
        fillGrid(DIRECTION_NONE, recycler, state, childLeft, childTop, removedCache)

        // Evaluate any disappearing views that may exist
        if (!state.isPreLayout && recycler.scrapList.isNotEmpty()) {
            val scrapList = recycler.scrapList
            val disappearingViews = HashSet<View>(scrapList.size)

            for (holder in scrapList) {
                val child = holder.itemView
                val lp = child.layoutParams as LayoutParams
                if (!lp.isItemRemoved) {
                    disappearingViews.add(child)
                }
            }

            for (child in disappearingViews) {
                layoutDisappearingView(child)
            }
        }
    }

    override fun onAdapterChanged(oldAdapter: RecyclerView.Adapter<*>?, newAdapter: RecyclerView.Adapter<*>?) {
        // Completely scrap the existing layout
        removeAllViews()
    }

    private fun fillGrid(
        direction: Int,
        recycler: Recycler,
        state: RecyclerView.State,
        emptyLeft: Int = 0,
        emptyTop: Int = 0,
        removedPositions: SparseIntArray? = null,
    ) {
        if (mFirstVisiblePosition < 0) mFirstVisiblePosition = 0
        if (mFirstVisiblePosition >= itemCount) mFirstVisiblePosition = (itemCount - 1)

        /*
         * First, we will detach all existing views from the layout.
         * detachView() is a lightweight operation that we can use to
         * quickly reorder views without a full add/remove.
         */
        val viewCache = SparseArray<View>(childCount)
        var startLeftOffset = emptyLeft
        var startTopOffset = emptyTop
        if (childCount != 0) {
            val topView = getChildAt(0)
            startLeftOffset = if (topView == null) 0 else getDecoratedLeft(topView)
            startTopOffset = if (topView == null) 0 else getDecoratedTop(topView)
            when (direction) {
                DIRECTION_START -> startLeftOffset -= mDecoratedChildWidth
                DIRECTION_END -> startLeftOffset += mDecoratedChildWidth
            }

            // Cache all views by their existing position, before updating counts
            for (i in 0 until childCount) {
                val position = positionOfIndex(i)
                val child = getChildAt(i)
                viewCache.put(position, child)
            }

            // Temporarily detach all views.
            //  Views we still need will be added back at the proper index.
            for (i in 0 until viewCache.size()) {
                detachView(viewCache.valueAt(i)!!)
            }
        }

        /*
         * Next, we advance the visible position based on the fill direction.
         * DIRECTION_NONE doesn't advance the position in any direction.
         */
        when (direction) {
            DIRECTION_START -> mFirstVisiblePosition--
            DIRECTION_END -> mFirstVisiblePosition++
        }

        /*
         * Next, we supply the grid of items that are deemed visible.
         * If these items were previously there, they will simply be
         * re-attached. New views that must be created are obtained
         * from the Recycler and added.
         */
        var leftOffset = startLeftOffset
        var topOffset = startTopOffset
        for (i in 0 until visibleChildCount) {
            var nextPosition = mFirstVisiblePosition + i

            /*
             * When a removal happens out of bounds, the pre-layout positions of items
             * after the removal are shifted to their final positions ahead of schedule.
             * We have to track off-screen removals and shift those positions back
             * so we can properly lay out all current (and appearing) views in their
             * initial locations.
             */
            var offsetPositionDelta = 0
            if (state.isPreLayout) {
                var offsetPosition = nextPosition

                if (removedPositions != null) {
                    for (offset in 0 until removedPositions.size()) {
                        // Look for off-screen removals that are less-than this
                        if (removedPositions.valueAt(offset) == REMOVE_INVISIBLE &&
                            removedPositions.keyAt(offset) < nextPosition
                        ) {
                            // Offset position to match
                            offsetPosition--
                        }
                    }
                }
                offsetPositionDelta = nextPosition - offsetPosition
                nextPosition = offsetPosition
            }

            if (nextPosition < 0 || nextPosition >= state.itemCount) {
                // Item space beyond the data set, don't attempt to add a view
                continue
            }

            // Layout this position
            var view = viewCache[nextPosition]
            if (view == null) {
                /*
                 * The Recycler will give us either a newly constructed view,
                 * or a recycled view it has on-hand. In either case, the
                 * view will already be fully bound to the data by the
                 * adapter for us.
                 */
                view = recycler.getViewForPosition(nextPosition)
                addView(view)

                /*
                 * Update the new view's metadata, but only when this is a real
                 * layout pass.
                 */
                if (!state.isPreLayout) {
                    val lp = view.layoutParams as LayoutParams
                    lp.row = getGlobalRowOfPosition(nextPosition)
                    lp.column = getGlobalColumnOfPosition(nextPosition)
                }

                /*
                 * It is prudent to measure/layout each new view we
                 * receive from the Recycler. We don't have to do
                 * this for views we are just re-arranging.
                 */
                measureChildWithMargins(view, 0, 0)
                layoutDecorated(
                    view, leftOffset, topOffset,
                    leftOffset + mDecoratedChildWidth,
                    topOffset + mDecoratedChildHeight
                )
            } else {
                // Re-attach the cached view at its new index
                attachView(view)
                viewCache.remove(nextPosition)
            }

            if (i % mVisibleColumnCount == (mVisibleColumnCount - 1)) {
                leftOffset = startLeftOffset
                topOffset += mDecoratedChildHeight

                // During pre-layout, on each column end, apply any additional appearing views
                if (state.isPreLayout) {
                    val extraCount = removedPositions?.size() ?: 0
                    layoutAppearingViews(recycler, view, nextPosition, extraCount, offsetPositionDelta)
                }
            } else {
                leftOffset += mDecoratedChildWidth
            }
        }

        /*
         * Finally, we ask the Recycler to scrap and store any views
         * that we did not re-attach. These are views that are not currently
         * necessary because they are no longer visible.
         */
        for (i in 0 until viewCache.size()) {
            val removingView = viewCache.valueAt(i)
            recycler.recycleView(removingView!!)
        }
    }

    /*
     * You must override this method if you would like to support external calls
     * to shift the view to a given adapter position. In our implementation, this
     * is the same as doing a fresh layout with the given position as the top-left
     * (or first visible), so we simply set that value and trigger onLayoutChildren()
     */
    override fun scrollToPosition(position: Int) {
        if (position >= itemCount) {
            Log.e(TAG, "Cannot scroll to $position, item count is $itemCount")
            return
        }

        // Set requested position as first visible
        mFirstVisiblePosition = position
        // Toss all existing views away
        removeAllViews()
        // Trigger a new view layout
        requestLayout()
    }

    /*
     * You must override this method if you would like to support external calls
     * to animate a change to a new adapter position. The framework provides a
     * helper scroller implementation (LinearSmoothScroller), which we leverage
     * to do the animation calculations.
     */
    override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
        if (position >= itemCount) {
            Log.e(TAG, "Cannot scroll to $position, item count is $itemCount")
            return
        }

        /*
         * LinearSmoothScroller's default behavior is to scroll the contents until
         * the child is fully visible. It will snap to the top-left or bottom-right
         * of the parent depending on whether the direction of travel was positive
         * or negative.
         */
        val scroller: LinearSmoothScroller = object : LinearSmoothScroller(recyclerView.context) {
            /*
             * LinearSmoothScroller, at a minimum, just need to know the vector
             * (x/y distance) to travel in order to get from the current positioning
             * to the target.
             */
            override fun computeScrollVectorForPosition(targetPosition: Int): PointF {
                val rowOffset = getGlobalRowOfPosition(targetPosition) -
                        getGlobalRowOfPosition(mFirstVisiblePosition)
                val columnOffset = getGlobalColumnOfPosition(targetPosition) -
                        getGlobalColumnOfPosition(mFirstVisiblePosition)
                return PointF(
                    columnOffset * mDecoratedChildWidth.toFloat(),
                    rowOffset * mDecoratedChildHeight.toFloat()
                )
            }
        }
        scroller.targetPosition = position
        startSmoothScroll(scroller)
    }

    /*
     * Use this method to tell the RecyclerView if scrolling is even possible
     * in the horizontal direction.
     */
    override fun canScrollHorizontally(): Boolean {
        // We do allow scrolling
        return true
    }

    /*
     * This method describes how far RecyclerView thinks the contents should scroll horizontally.
     * You are responsible for verifying edge boundaries, and determining if this scroll
     * event somehow requires that new views be added or old views get recycled.
     */
    override fun scrollHorizontallyBy(dx: Int, recycler: Recycler, state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }

        // Take leftmost measurements from the top-left child
        val topView = getChildAt(0)
        // Take rightmost measurements from the top-right child
        val bottomView = getChildAt(mVisibleColumnCount - 1)

        // Optimize the case where the entire data set is too small to scroll
        val viewSpan = getDecoratedRight(bottomView!!) - getDecoratedLeft(topView!!)
        if (viewSpan < width) {
            // We cannot scroll in either direction
            return 0
        }

        val leftBoundReached = firstVisibleColumn == 0
        val rightBoundReached = lastVisibleColumn >= totalColumnCount
        val delta: Int =
            if (dx > 0) { //  Contents are scrolling left
                // Check right bound
                if (rightBoundReached) {
                    // If we've reached the last column, enforce limits
                    val rightOffset = width - getDecoratedRight(bottomView) + paddingRight
                    (-dx).coerceAtLeast(rightOffset)
                } else {
                    // No limits while the last column isn't visible
                    -dx
                }
            } else { //  Contents are scrolling right
                // Check left bound
                if (leftBoundReached) {
                    val leftOffset = -getDecoratedLeft(topView) + paddingLeft
                    (-dx).coerceAtMost(leftOffset)
                } else {
                    -dx
                }
            }

        offsetChildrenHorizontal(delta)

        if (dx > 0) {
            if (getDecoratedRight(topView) < 0 && !rightBoundReached) {
                fillGrid(DIRECTION_END, recycler, state)
            } else if (!rightBoundReached) {
                fillGrid(DIRECTION_NONE, recycler, state)
            }
        } else {
            if (getDecoratedLeft(topView) > 0 && !leftBoundReached) {
                fillGrid(DIRECTION_START, recycler, state)
            } else if (!leftBoundReached) {
                fillGrid(DIRECTION_NONE, recycler, state)
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

    override fun canScrollVertically(): Boolean = false

    /*
     * This is a helper method used by RecyclerView to determine
     * if a specific child view can be returned.
     */
    override fun findViewByPosition(position: Int): View? {
        for (i in 0 until childCount) {
            if (positionOfIndex(i) == position) {
                return getChildAt(i)
            }
        }
        return null
    }

    /** Boilerplate to extend LayoutParams for tracking row/column of attached views  */

    /*
     * Even without extending LayoutParams, we must override this method
     * to provide the default layout parameters that each child view
     * will receive when added.
     */
    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun generateLayoutParams(c: Context, attrs: AttributeSet): RecyclerView.LayoutParams {
        val layoutParams = super.generateLayoutParams(c, attrs)
        layoutParams.height = (height / rows)
        layoutParams.width = (width / columns)
        return LayoutParams(c, attrs)
    }

    override fun generateLayoutParams(lp: ViewGroup.LayoutParams): RecyclerView.LayoutParams {
        return if (lp is MarginLayoutParams) {
            LayoutParams(lp)
        } else {
            LayoutParams(lp)
        }
    }

    override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
        return lp is LayoutParams
    }

    class LayoutParams : RecyclerView.LayoutParams {

        // Current row in the grid
        var row = 0

        // Current column in the grid
        var column = 0

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: MarginLayoutParams?) : super(source)
        constructor(source: ViewGroup.LayoutParams?) : super(source)
        constructor(source: RecyclerView.LayoutParams?) : super(source)
    }

    /** Animation Layout Helpers  */

    /* Helper to obtain and place extra appearing views */
    private fun layoutAppearingViews(
        recycler: Recycler,
        referenceView: View,
        referencePosition: Int,
        extraCount: Int,
        offset: Int,
    ) {
        // Nothing to do...
        if (extraCount < 1) return

        // FIXME: This code currently causes double layout of views that are still visible…
        for (extra in 1..extraCount) {
            // Grab the next position after the reference
            val extraPosition = referencePosition + extra
            if (extraPosition < 0 || extraPosition >= itemCount) {
                // Can't do anything with this
                continue
            }

            /*
             * Obtain additional position views that we expect to appear
             * as part of the animation.
             */
            val appearing = recycler.getViewForPosition(extraPosition)
            addView(appearing)

            // Find layout delta from reference position
            val newRow = getGlobalRowOfPosition(extraPosition + offset)
            val rowDelta = newRow - getGlobalRowOfPosition(referencePosition + offset)
            val newCol = getGlobalColumnOfPosition(extraPosition + offset)
            val colDelta = newCol - getGlobalColumnOfPosition(referencePosition + offset)

            layoutTempChildView(appearing, rowDelta, colDelta, referenceView)
        }
    }

    /* Helper to place a disappearing view */
    private fun layoutDisappearingView(disappearingChild: View) {
        /*
         * LayoutManager has a special method for attaching views that
         * will only be around long enough to animate.
         */
        addDisappearingView(disappearingChild)

        // Adjust each disappearing view to its proper place
        val lp = disappearingChild.layoutParams as LayoutParams

        val newRow = getGlobalRowOfPosition(lp.viewAdapterPosition)
        val rowDelta = newRow - lp.row
        val newCol = getGlobalColumnOfPosition(lp.viewAdapterPosition)
        val colDelta = newCol - lp.column

        layoutTempChildView(disappearingChild, rowDelta, colDelta, disappearingChild)
    }

    /* Helper to lay out appearing/disappearing children */
    private fun layoutTempChildView(child: View, rowDelta: Int, colDelta: Int, referenceView: View) {
        // Set the layout position to the global row/column difference from the reference view
        val layoutTop = getDecoratedTop(referenceView) + rowDelta * mDecoratedChildHeight
        val layoutLeft = getDecoratedLeft(referenceView) + colDelta * mDecoratedChildWidth

        measureChildWithMargins(child, 0, 0)
        layoutDecorated(
            child, layoutLeft, layoutTop,
            layoutLeft + mDecoratedChildWidth,
            layoutTop + mDecoratedChildHeight
        )
    }

    /** Private Helpers and Metrics Accessors  */

    /* Return the overall column index of this position in the global layout */
    private fun getGlobalColumnOfPosition(position: Int): Int {
        return position % columns
    }

    /* Return the overall row index of this position in the global layout */
    private fun getGlobalRowOfPosition(position: Int): Int {
        return position / columns
    }

    /*
     * Mapping between child view indices and adapter data
     * positions helps fill the proper views during scrolling.
     */
    private fun positionOfIndex(childIndex: Int): Int {
        val row = childIndex / mVisibleColumnCount
        val column = childIndex % mVisibleColumnCount
        return mFirstVisiblePosition + (row * totalColumnCount) + column
    }

    private val firstVisibleColumn: Int get() = mFirstVisiblePosition % totalColumnCount
    private val lastVisibleColumn: Int get() = firstVisibleColumn + mVisibleColumnCount
    private val firstVisibleRow: Int get() = mFirstVisiblePosition / totalColumnCount
    private val visibleChildCount: Int get() = mVisibleColumnCount * mVisibleRowCount

    private val totalRowCount: Int = rows
}