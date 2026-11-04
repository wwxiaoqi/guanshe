package com.wwxiaoqi.guanshe.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/**
 * 详情页专用的 ScrollView：在 dispatch 阶段截获"向下拖"。
 *
 * 普通 ScrollView 挂 OnTouchListener 不行 —— 子 View 先拿到手势，父级监听收不到，
 * 所以之前只有从顶部空白处才能下拉。这里在事件分发给子 View 之前判断：
 * 手指向下、超过 slop、且以纵向为主，就给孩子发 CANCEL，把这一串手势收归"下拉白纸"。
 * 向上拖 / 横向拖仍然交给原来的滚动与点击。
 */
public class PullScrollView extends ScrollView {

    public interface PullListener {
        void onPull(float dy, float maxDy);

        void onRelease(float maxDy, boolean canceled);
    }

    private final int slop;
    private PullListener listener;
    private float downY;
    private float downX;
    private float maxDy;
    private boolean pulling;

    public PullScrollView(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setFillViewport(false);
        setClipToPadding(false);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
    }

    public void setPullListener(PullListener l) {
        listener = l;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (listener == null) return super.dispatchTouchEvent(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pulling = false;
                maxDy = 0f;
                downY = ev.getY();
                downX = ev.getX();
                super.dispatchTouchEvent(ev);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dy = ev.getY() - downY;
                float dx = ev.getX() - downX;
                if (!pulling) {
                    if (dy > slop && dy > Math.abs(dx) * 1.15f) {
                        pulling = true;
                        // 把已经发给子 View 的手势取消，避免它一边滚动/长按一边被拖走
                        MotionEvent cancel = MotionEvent.obtain(ev);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                    } else {
                        return super.dispatchTouchEvent(ev);
                    }
                }
                if (pulling) {
                    if (dy > maxDy) maxDy = dy;
                    listener.onPull(dy, maxDy);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (pulling) {
                    pulling = false;
                    listener.onRelease(maxDy,
                            ev.getActionMasked() == MotionEvent.ACTION_CANCEL);
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            default:
                return super.dispatchTouchEvent(ev);
        }
    }
}
