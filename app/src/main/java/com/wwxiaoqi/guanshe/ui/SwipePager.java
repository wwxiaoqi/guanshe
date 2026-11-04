package com.wwxiaoqi.guanshe.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import java.util.ArrayList;

/**
 * 轻量横向分页容器（不依赖 androidx ViewPager）。
 *
 * 这一版重点是"连续快速切页不出叠影"：
 * - 任何新转场开始前先 finishNow()，把上一段动画立刻收尾（只留一页可见）；
 * - 拖拽 / 点 tab / 转场动画共用同一个"当前页"状态；
 * - 手指落在页内横向滚动控件上时不抢手势。
 */
public class SwipePager extends FrameLayout {

    public interface Listener {
        void onPageSelected(int index);
    }

    private final ArrayList<View> pages = new ArrayList<View>();
    private final int slop;

    private Listener listener;
    private int current = -1;
    private int target = -1;          // 拖动中的目标页；-1 = 没有相邻页可去
    private boolean dragging;
    private boolean intercepting;
    private boolean animating;
    private ValueAnimator running;
    private int animTo = -1;
    private float downX;
    private float downY;
    private VelocityTracker velocity;

    public SwipePager(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClipChildren(true);
    }

    public void addPage(View v) {
        pages.add(v);
        addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        v.setVisibility(GONE);
    }

    public int count() {
        return pages.size();
    }

    public int current() {
        return current;
    }

    public View page(int i) {
        return i >= 0 && i < pages.size() ? pages.get(i) : null;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** 无动画直接停到某一页；同时把任何进行中的动画收尾。 */
    public void show(int index) {
        if (index < 0 || index >= pages.size()) return;
        cancelRunning();
        animating = false;
        dragging = false;
        target = -1;
        animTo = -1;
        for (int i = 0; i < pages.size(); i++) {
            View p = pages.get(i);
            p.animate().cancel();
            p.setTranslationX(0f);
            p.setAlpha(1f);
            p.setVisibility(i == index ? VISIBLE : GONE);
        }
        boolean changed = current != index;
        current = index;
        recycleVelocity();
        if (changed && listener != null) listener.onPageSelected(index);
    }

    /**
     * 把进行中的转场立刻收尾到目标页（不做动画）。
     * 连续点 tab 时先调它，避免两只页面同时停在屏幕上。
     */
    public void finishNow() {
        if (!animating) return;
        int to = animTo >= 0 ? animTo : current;
        cancelRunning();
        animating = false;
        dragging = false;
        target = -1;
        animTo = -1;
        for (int i = 0; i < pages.size(); i++) {
            View p = pages.get(i);
            p.animate().cancel();
            p.setTranslationX(0f);
            p.setAlpha(1f);
            p.setVisibility(i == to ? VISIBLE : GONE);
        }
        boolean changed = current != to;
        current = to;
        recycleVelocity();
        if (changed && listener != null) listener.onPageSelected(to);
    }

    /** 点 tab 切页：整页横向滑入滑出。 */
    public void switchTo(final int index, boolean animate) {
        if (index < 0 || index >= pages.size()) return;
        if (animating) finishNow();
        if (index == current) return;
        if (!animate || current < 0 || !Motion.enabled(getContext())) {
            show(index);
            return;
        }

        final int from = current;
        final View out = pages.get(from);
        final View in = pages.get(index);
        final boolean forward = index > from;
        final float dir = forward ? 1f : -1f;
        final float w = width();

        // 先把所有无关页面收掉，保证任何时刻最多两页参与转场
        for (int i = 0; i < pages.size(); i++) {
            if (i == from || i == index) continue;
            View p = pages.get(i);
            p.animate().cancel();
            p.setVisibility(GONE);
            p.setTranslationX(0f);
            p.setAlpha(1f);
        }

        animating = true;
        animTo = index;
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        in.setVisibility(VISIBLE);
        in.setTranslationX(dir * w);
        in.setAlpha(1f);
        in.bringToFront();

        final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(250);
        va.setInterpolator(Motion.ease());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                in.setTranslationX(dir * w * (1f - t));
                out.setTranslationX(-dir * w * t);
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator a) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (canceled) return;
                running = null;
                animTo = -1;
                out.setVisibility(GONE);
                out.setTranslationX(0f);
                out.setAlpha(1f);
                in.setTranslationX(0f);
                in.setAlpha(1f);
                current = index;
                target = -1;
                dragging = false;
                animating = false;
                if (listener != null) listener.onPageSelected(index);
            }
        });
        running = va;
        va.start();
    }

    // ---------------------------------------------------------------- 手势
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (pages.size() < 2) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (animating) finishNow();
                downX = ev.getX();
                downY = ev.getY();
                dragging = false;
                intercepting = false;
                track(ev);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!intercepting) {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.3f
                            && !childCanScroll(-dx)) {
                        intercepting = true;
                        startDrag(dx < 0 ? 1 : -1);
                        applyDrag(ev);
                    }
                }
                return intercepting;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                intercepting = false;
                return false;
            default:
                return intercepting;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (pages.size() < 2) return false;
        track(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (animating) finishNow();
                downX = ev.getX();
                downY = ev.getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.3f
                            && !childCanScroll(-dx)) {
                        startDrag(dx < 0 ? 1 : -1);
                    }
                }
                if (dragging) applyDrag(ev);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    settle(ev, ev.getActionMasked() == MotionEvent.ACTION_CANCEL);
                    return true;
                }
                break;
            default:
                break;
        }
        return true;
    }

    private void cancelRunning() {
        if (running != null) {
            ValueAnimator a = running;
            running = null;
            a.cancel();
        }
    }

    private void track(MotionEvent ev) {
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(ev);
    }

    private float width() {
        return getWidth() > 0 ? getWidth() : 1f;
    }

    /** dir = 1 表示向左滑去下一页。 */
    private void startDrag(int dir) {
        if (current < 0 || current >= pages.size()) return;
        dragging = true;
        target = current + dir;
        pages.get(current).setVisibility(VISIBLE);
        if (target >= 0 && target < pages.size()) {
            View t = pages.get(target);
            t.setVisibility(VISIBLE);
            t.setAlpha(1f);
            t.setTranslationX(dir > 0 ? width() : -width());
        } else {
            target = -1;
        }
    }

    private void applyDrag(MotionEvent ev) {
        if (!dragging || current < 0) return;
        float dx = ev.getX() - downX;
        View cur = pages.get(current);
        cur.setTranslationX(target >= 0 ? dx : dx * 0.25f);
        if (target >= 0) {
            View t = pages.get(target);
            t.setTranslationX(dx + (target > current ? width() : -width()));
        }
    }

    private void settle(MotionEvent ev, boolean cancel) {
        float vx = 0f;
        if (velocity != null) {
            velocity.computeCurrentVelocity(1000);
            vx = velocity.getXVelocity();
        }
        float dx = ev.getX() - downX;
        boolean valid = target >= 0 && target < pages.size();
        boolean forward = target > current;
        boolean commit = !cancel && valid
                && (Math.abs(dx) > width() * 0.22f
                || (forward ? -vx : vx) > 700f);
        if (commit) {
            animateCommit();
        } else {
            animateBack();
        }
    }

    private void animateCommit() {
        final View cur = pages.get(current);
        final View t = pages.get(target);
        final float curStart = cur.getTranslationX();
        final float tStart = t.getTranslationX();
        final float curEnd = target > current ? -width() : width();
        final int to = target;
        animating = true;
        animTo = to;
        dragging = false;
        final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(200);
        va.setInterpolator(Motion.ease());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float p = (Float) a.getAnimatedValue();
                cur.setTranslationX(curStart + (curEnd - curStart) * p);
                t.setTranslationX(tStart * (1f - p));
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator a) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (canceled) return;
                running = null;
                animTo = -1;
                cur.setVisibility(GONE);
                cur.setTranslationX(0f);
                cur.setAlpha(1f);
                t.setTranslationX(0f);
                t.setAlpha(1f);
                current = to;
                target = -1;
                animating = false;
                recycleVelocity();
                if (listener != null) listener.onPageSelected(current);
            }
        });
        running = va;
        va.start();
    }

    private void animateBack() {
        final View cur = pages.get(current);
        final View t = target >= 0 ? pages.get(target) : null;
        final float curStart = cur.getTranslationX();
        final float tStart = t == null ? 0f : t.getTranslationX();
        final float tEnd = t == null ? 0f : (target > current ? width() : -width());
        animating = true;
        dragging = false;
        final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(170);
        va.setInterpolator(Motion.ease());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float p = (Float) a.getAnimatedValue();
                cur.setTranslationX(curStart * (1f - p));
                if (t != null) t.setTranslationX(tStart + (tEnd - tStart) * p);
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator a) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (canceled) return;
                running = null;
                cur.setTranslationX(0f);
                if (t != null) {
                    t.setTranslationX(0f);
                    t.setVisibility(GONE);
                }
                target = -1;
                animating = false;
                recycleVelocity();
            }
        });
        running = va;
        va.start();
    }

    private void recycleVelocity() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    private boolean childCanScroll(float dx) {
        if (current < 0 || current >= pages.size()) return false;
        View hit = hitTest(pages.get(current), downX, downY, 0f, 0f);
        while (hit != null) {
            if (hit.canScrollHorizontally(dx < 0 ? 1 : -1)) return true;
            ViewParent p = hit.getParent();
            if (!(p instanceof View)) break;
            hit = (View) p;
            if (hit == this) break;
        }
        return false;
    }

    private View hitTest(View v, float x, float y, float ox, float oy) {
        if (v == null || v.getVisibility() != VISIBLE) return null;
        if (!(v instanceof ViewGroup)) return v;
        ViewGroup g = (ViewGroup) v;
        for (int i = g.getChildCount() - 1; i >= 0; i--) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != VISIBLE) continue;
            float cx = ox + c.getLeft() + c.getTranslationX();
            float cy = oy + c.getTop() + c.getTranslationY();
            if (x >= cx && x < cx + c.getWidth() && y >= cy && y < cy + c.getHeight()) {
                View deeper = hitTest(c, x, y, cx, cy);
                return deeper != null ? deeper : c;
            }
        }
        return v;
    }
}
