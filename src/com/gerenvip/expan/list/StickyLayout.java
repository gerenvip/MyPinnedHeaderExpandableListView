package com.gerenvip.expan.list;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import java.util.NoSuchElementException;

/**
 * 黏性的layout,可以实现收缩头部
 * Created by wangwei_cs on 2014/7/7.
 */
public class StickyLayout extends LinearLayout {

    private static final String TAG = "StickyLayout";
    private static final boolean DEBUG = true;
    private View mHeader;
    private View mContent;
    // header的高度  单位：px
    private int mOriginalHeaderHeight;
    private int mHeaderHeight;
    private int mTouchSlop;
    private OnGiveUpTouchEventListener mGiveUpTouchEventListener;
    // 分别记录上次滑动的坐标(onInterceptTouchEvent)
    private int mLastXIntercept = 0;
    private int mLastYIntercept = 0;

    // 分别记录上次滑动的坐标
    private int mLastX = 0;
    private int mLastY = 0;

    private int mStatus = STATUS_EXPANDED;
    //header展开状态
    public static final int STATUS_EXPANDED = 1;
    //header收缩状态
    public static final int STATUS_COLLAPSED = 2;

    //是否粘性,如果为false,头部就固定了，不会收缩
    private boolean mIsSticky = true;

    public interface OnGiveUpTouchEventListener {
        /**
         * 判断是否放弃touch事件的处理权
         *
         * @param event
         * @return true:touch事件交由父view处理，即StickyLayout处理<br/>
         * false:touch事件不中断，交给子view处理
         */
        public boolean giveUpTouchEvent(MotionEvent event);
    }

    public StickyLayout(Context context) {
        super(context);
    }

    public StickyLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * 当窗口焦点变化的时候调用
     *
     * @param hasWindowFocus
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (DEBUG) {
            Log.e(TAG, "StickyLayout onWindowFocusChanged");
        }
        if (hasWindowFocus && (mHeader == null || mContent == null)) {
            initData();
        }
    }

    private void initData() {
        //获取main.xml中定义的id为header和content的资源id，如果没有返回0
        int headerId = getResources().getIdentifier("header", "id", getContext().getPackageName());
        int contentId = getResources().getIdentifier("content", "id", getContext().getPackageName());
        if (headerId != 0 && contentId != 0) {
            mHeader = findViewById(headerId);
            mContent = findViewById(contentId);
            mOriginalHeaderHeight = mHeader.getMeasuredHeight();
            mHeaderHeight = mOriginalHeaderHeight;
            //获得能够进行手势滑动的距离
            mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            Log.e(TAG, "mTouchSlop =" + mTouchSlop);
        } else {
            throw new NoSuchElementException("Did your view with id \"header\" or \"content\" exists?");
        }
    }

    public void setOnGiveUpTouchEventListener(OnGiveUpTouchEventListener listener) {
        mGiveUpTouchEventListener = listener;
    }

    /**
     * 设置是否头部可收缩（粘性）
     *
     * @param isSticky
     */
    public void setSticky(boolean isSticky) {
        mIsSticky = isSticky;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        //标记是否需要中断，0不处理，1处理
        int intercepted = 0;
        int x = (int) ev.getX();
        int y = (int) ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastXIntercept = x;
                mLastYIntercept = y;
                mLastX = x;
                mLastY = y;
                intercepted = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                int deltaX = x - mLastXIntercept;
                int deltaY = y - mLastYIntercept;
                //header处于展开状态，并且向上滑动距离超过了能够进行手势滑动的距离，这时需要拦截touch事件，将header收缩起来
                if (mStatus == STATUS_EXPANDED && deltaY <= -mTouchSlop) {
                    intercepted = 1;
                } else if (mGiveUpTouchEventListener != null) {
                    //header 未展开状态并且向下滑动距离超过了能够进行手势滑动的距离，拦截touch事件，将header展示出来
                    if (mGiveUpTouchEventListener.giveUpTouchEvent(ev) && deltaY >= mTouchSlop) {
                        intercepted = 1;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                //释放触摸事件的处理权
                intercepted = 0;
                //恢复标记变量
                mLastXIntercept = mLastYIntercept = 0;
                break;
            default:
                break;
        }
        Log.e(TAG, "intercepted=" + intercepted);
        return intercepted != 0 && mIsSticky;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mIsSticky) {
            return true;
        }
        int x = (int) event.getX();
        int y = (int) event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_MOVE:
                int deltaX = x - mLastX;
                int deltaY = y - mLastY;
                if (DEBUG) {
                    Log.e(TAG, "mHeaderHeight=" + mHeaderHeight + ";deltaY=" + deltaY + "; mLastY=" + mLastY);
                    //原headerview的height跟随滑动距离做增减
                    mHeaderHeight += deltaY;
                    setHeaderHeight(mHeaderHeight);
                }
                break;
            case MotionEvent.ACTION_UP:
                // 这里做了下判断，当松开手的时候，会自动向两边滑动，具体向哪边滑，要看当前所处的位置
                int destHeight = 0;
                if (mHeaderHeight < mOriginalHeaderHeight * 0.5) {
                    destHeight = 0;
                    //设置标记为收缩状态
                    mStatus = STATUS_COLLAPSED;
                } else {
                    destHeight = mOriginalHeaderHeight;
                    //设置标记为展开状态
                    mStatus = STATUS_EXPANDED;
                }
                //慢慢滑向终点
                smoothSetHeaderHeight(mHeaderHeight, destHeight, 500);
                break;
        }
        mLastX = x;
        mLastY = y;
        return true;
    }

    public void smoothSetHeaderHeight(int from, int to, long duration) {
        smoothSetHeaderHeight(from, to, duration, false);
    }

    /**
     * 平滑的改变header的高度
     *
     * @param from
     * @param to
     * @param duration
     * @param modifyOriginalHeaderHeight
     */
    public void smoothSetHeaderHeight(final int from, final int to, long duration, final boolean modifyOriginalHeaderHeight) {
        //帧数
        final int frameCount = (int) (duration / 1000f * 30) + 1;
        //每一帧变化距离
        final float partation = (to - from) / (float) frameCount;
        new Thread("Thread#smoothSetHeaderHeight") {
            @Override
            public void run() {
                for (int i = 0; i < frameCount; i++) {
                    final int height;
                    if (i == frameCount - 1) {
                        height = to;
                    } else {
                        height = (int) (from + partation * i);
                    }
                    post(new Runnable() {
                        @Override
                        public void run() {
                            setHeaderHeight(height);
                        }
                    });
                    try {
                        sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (modifyOriginalHeaderHeight) {
                    setOriginalHeaderHeight(to);
                }
            }
        }.start();
    }

    /**
     * 修改原header的高度
     *
     * @param originalHeaderHeight
     */
    private void setOriginalHeaderHeight(int originalHeaderHeight) {
        mOriginalHeaderHeight = originalHeaderHeight;
    }

    public void setHeaderHeight(int height, boolean modifyOriginalHeaderHeight) {
        if (modifyOriginalHeaderHeight) {
            setOriginalHeaderHeight(height);
        }
        setHeaderHeight(height);
    }

    public void setHeaderHeight(int height) {
        if (DEBUG) {
            Log.e(TAG, "setHeaderHeight=" + height);
        }
        if (height < 0) {
            height = 0;
        } else if (height > mOriginalHeaderHeight) {
            height = mOriginalHeaderHeight;
        }

        if (mHeader != null && mHeader.getLayoutParams() != null) {
            mHeader.getLayoutParams().height = height;
            //请求重新布局
            mHeader.requestLayout();
            mHeaderHeight = height;
        } else {
            if (DEBUG) {
                Log.e(TAG, "null LayoutParams when setHeaderHeight");
            }
        }
    }
}
