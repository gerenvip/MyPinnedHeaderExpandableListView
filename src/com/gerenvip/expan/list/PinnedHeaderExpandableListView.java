package com.gerenvip.expan.list;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ExpandableListView;

/**
 * 分组view可以悬停的ExpandableListView
 * Created by wangwei_cs on 2014/7/7.
 */
public class PinnedHeaderExpandableListView extends ExpandableListView implements AbsListView.OnScrollListener {


    private static final String TAG = "PinnedHeaderExpandableListView";
    private boolean mActionDownHappened = false;

    public interface OnHeaderUpdateListener {
        /**
         * 返回一个view对象即可
         * 注意：view必须要有LayoutParams
         */
        public View getPinnedHeader();

        public void updatePinnedHeader(View headerView, int firstVisibileGroupPos);
    }

    private OnScrollListener mScrollListener;
    private OnHeaderUpdateListener mHeaderUpdateListener;
    //headerView,悬停的headerview
    private View mHeaderView;
    //headerView width
    private int mHeaderWidth;
    //headerView height
    private int mHeaderHeight;
    private View mTouchTarget;

    public PinnedHeaderExpandableListView(Context context) {
        super(context);
        initViews();
    }

    public PinnedHeaderExpandableListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initViews();
    }

    public PinnedHeaderExpandableListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initViews();
    }

    private void initViews() {
        setFadingEdgeLength(0);
        setOnScrollListener(this);
    }

    /**
     * 为外部设置onscrolllistener提供便利
     *
     * @param l
     */
    @Override
    public void setOnScrollListener(OnScrollListener l) {
        if (l != this) {
            mScrollListener = l;
        }
        super.setOnScrollListener(l);
    }

    public void setOnHeaderUpdateListener(OnHeaderUpdateListener listener) {
        mHeaderUpdateListener = listener;
        //如果listener为null，初始化headerview
        if (listener == null) {
            mHeaderView = null;
            mHeaderWidth = mHeaderHeight = 0;
            return;
        }

        mHeaderView = listener.getPinnedHeader();
        int firstVisiblePos = getFirstVisiblePosition();
        //通过fisrVisiblePos找到组的位置
        int firstVisibleGroupPos = getPackedPositionGroup(getExpandableListPosition(firstVisiblePos));
        listener.updatePinnedHeader(mHeaderView, firstVisibleGroupPos);
        //请求重新布局layou，会触发measure()过程 和 layout()
        requestLayout();
        //请求重新绘制view，如果视图大小没有发生变化，就不会调用layou方法，并且只会绘制需要绘制的部分
        postInvalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mHeaderView == null) {
            return;//如果没有headerview，就不需要重新测量，所以取消
        }
        measureChild(mHeaderView, widthMeasureSpec, heightMeasureSpec);
        mHeaderWidth = mHeaderView.getMeasuredWidth();
        mHeaderHeight = mHeaderView.getMeasuredHeight();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mHeaderView == null) {
            return;
        }
        int top = mHeaderView.getTop();
        mHeaderView.layout(0, top, mHeaderWidth, mHeaderHeight);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        //负责重新绘制所有的子view，但不包括headerview
        super.dispatchDraw(canvas);
        //绘制后添加的headerview
        if (mHeaderView != null) {
            drawChild(canvas, mHeaderView, getDrawingTime());
        }
    }

    /**
     * 分发触摸事件，由于添加的headerview并无法获取到点击事件，所以需要处理，否则点击headerview的时候，实际上
     * 是点击的headerview下的item
     *
     * @param ev
     * @return
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int x = (int) ev.getX();
        int y = (int) ev.getY();
        //两个坐标点变成一个位置
        int pos = pointToPosition(x, y);
        //当触摸位置是在headerview的位置时
        if (mHeaderView != null && y >= mHeaderView.getTop() && y <= mHeaderView.getBottom()) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                //找到headerview中touch事件的处理者(子view)
                mTouchTarget = getTouchTarget(mHeaderView, x, y);
                //手指放下
                mActionDownHappened = true;
            } else if (ev.getAction() == MotionEvent.ACTION_UP) {//离开屏幕的touch事件
                View touchTarget = getTouchTarget(mHeaderView, x, y);
                //处理headerview内部view的点击事件
                if (touchTarget == mTouchTarget && mTouchTarget.isClickable()) {
                    //回调onclicklistener，通知用户，点击了
                    mTouchTarget.performClick();
                    //比较需要draw的区域，必须在ui线程调用，该方法最终会调用onDraw(Canvas)
                    invalidate(new Rect(0, 0, mHeaderWidth, mHeaderHeight));
                } else {//抬手时处理touch事件的view不是down的时候记录的view
                    int groupPosition = getPackedPositionGroup(getExpandableListPosition(pos));
                    if (groupPosition != INVALID_POSITION && mActionDownHappened) {
                        if (isGroupExpanded(groupPosition)) {
                            //收起group
                            collapseGroup(groupPosition);
                        } else {
                            //展开group
                            expandGroup(groupPosition);
                        }
                    }
                }
                mActionDownHappened = false;
            }
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 获取具有touch事件处理权的view
     *
     * @param view
     * @param x
     * @param y
     * @return
     */
    private View getTouchTarget(View view, int x, int y) {
        if (!(view instanceof ViewGroup)) {
            //如果view不是viewGroup,那么触摸事件处理者就是它
            return view;
        }

        ViewGroup parent = (ViewGroup) view;
        int childrenCount = parent.getChildCount();
        //是否按照定义的顺序进行drawing
        final boolean customOrder = isChildrenDrawingOrderEnabled();
        View target = null;
        for (int i = childrenCount - 1; i >= 0; i--) {
            //得到处理事件的孩子view索引
            int childIndex = customOrder ? getChildDrawingOrder(childrenCount, i) : i;
            View child = parent.getChildAt(childIndex);
            //找到接收触摸事件的子view
            if (isTouchPointInView(child, x, y)) {
                target = child;
                break;
            }

            //如果没有子view符合处理touch事件的要求，将touch事件的处理权交给父view
            if (target == null) {
                target = parent;
            }
        }
        return target;
    }

    /**
     * 触摸位置是否在view上
     *
     * @param view
     * @param x
     * @param y
     * @return
     */
    private boolean isTouchPointInView(View view, int x, int y) {
        //触摸位置在view上
        if (view.isClickable() && y >= view.getTop() && y <= view.getBottom() && x >= view.getLeft() && x <= view.getRight()) {

            return true;
        }
        return false;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (mHeaderView != null && scrollState == SCROLL_STATE_IDLE) {
            int firstVisiblePos = getFirstVisiblePosition();
            if (firstVisiblePos == 0) {
                mHeaderView.layout(0, 0, mHeaderWidth, mHeaderHeight);
            }
        }
        if (mScrollListener != null) {
            mScrollListener.onScrollStateChanged(view, scrollState);
        }

    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        Log.e(TAG, "expandaleListview totalItemCount=" + totalItemCount);

        if (totalItemCount > 0) {
            refreshHeader();
        }
        if (mScrollListener != null) {
            mScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
        }
    }

    /**
     * 刷新悬停的header
     */
    private void refreshHeader() {
        if (mHeaderView == null) {
            return;
        }

        int firstVisiblePos = getFirstVisiblePosition();
        int pos = firstVisiblePos + 1;
        int firstVisibleGroupPos = getPackedPositionGroup(getExpandableListPosition(firstVisiblePos));
        int group = getPackedPositionGroup(getExpandableListPosition(pos));
        //Log.w(TAG, "refreshHeader firstVisibleGroupPos=" + firstVisibleGroupPos + "; group="+group);
        //Log.e(TAG, "child size=" + getChildCount());
        //这个时候该分组第一个可显示的条目是最后一个,即两个分组相遇啦
        if (group == firstVisibleGroupPos + 1) {
            // TODO: why getChileAt(1)?? 这是因为getCount返回的所包含的item总个数,而getChildCount返回的是当前可见的item个数
            // 所以getChileAt得到的是可见的item， 第0个真好被headerview盖住了，应该选取第1个条目距离父view的距离
            View view = getChildAt(1);
            if (view == null) {
                Log.w(TAG, "Warning:refreshHeader getChildAt(1)=null");
            }
            //Log.e(TAG, "view.getTop=" + view.getTop());
            if (view.getTop() <= mHeaderHeight) {//如果到顶部的距离小于等于header的高度，说明header应该被挤上去
                //说明悬浮的header正在推上去
                int delta = mHeaderHeight - view.getTop();
                //设置header相对父view的位置
                mHeaderView.layout(0, -delta, mHeaderWidth, mHeaderHeight - delta);
            } else {
                Log.e(TAG, "view.getTop > mHeaderHeight");
                //TODO : note it, when cause bug, remove it
                mHeaderView.layout(0, 0, mHeaderWidth, mHeaderHeight);
            }
        } else {
            //这时候两个分组没有相遇的情况
            mHeaderView.layout(0, 0, mHeaderWidth, mHeaderHeight);
        }

        if (mHeaderUpdateListener != null) {
            mHeaderUpdateListener.updatePinnedHeader(mHeaderView, firstVisibleGroupPos);
        }

    }
}
