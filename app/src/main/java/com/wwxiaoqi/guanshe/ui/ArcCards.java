package com.wwxiaoqi.guanshe.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.wwxiaoqi.guanshe.Skin;

import java.util.ArrayList;
import java.util.List;

/**
 * 弧形卡片轮播：卡片沿一段圆环排布，只露出中间约 1/3 圆环。
 *
 * - 中间卡正对用户、最大；两侧卡沿弧线低下去、倾斜一点、变小变淡，像悬在暗场里；
 * - 左右拖动时所有卡沿弧线滑动，松手用弹簧回正 —— 就是用户说的"滑块感"；
 * - 中间卡轻轻上下浮动 + 呼吸，是这一屏唯一的主动作；
 * - 点中间卡 = 分享；点旁边卡 = 把它转到中间；点空白 = 交给宿主关闭。
 */
public class ArcCards extends FrameLayout {

    public interface Listener {
        void onCardTap(int index);

        void onIndexChanged(int index);

        void onBackdropTap();

        /** 当前连续偏移（拖拽/回弹过程中的小数 index），给刻度盘用。 */
        void onOffsetChanged(float offset);

        /** 跨过一张卡片的边界（拖动中与回弹中都触发），用于刻度震动。 */
        void onTick(int index);
    }

    /** 相邻卡片之间的圆心角（弧度）。约 26°。 */
    private static final float STEP = 0.50f;
    private static final float ROT_PER_CARD = 14f;

    private final List<FrameLayout> pages = new ArrayList<FrameLayout>();
    private final int slop;
    private Listener listener;
    private float offset = 0f;
    private int current = 0;
    private boolean dragging;
    private float downX;
    private float downY;
    private float startOffset;
    private ValueAnimator settleAnim;
    private ValueAnimator bobAnim;
    private boolean laidOut;
    private float homeY;
    private int lastTick = Integer.MIN_VALUE;
    private Paint halo;

    public ArcCards(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClipChildren(false);
        setClickable(true);
        setWillNotDraw(false);
        halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void addCard(FrameLayout page) {
        pages.add(page);
        page.setClickable(false);
        page.setFocusable(false);
        addView(page);
    }

    public int count() {
        return pages.size();
    }

    public int current() {
        return current;
    }

    public float offset() {
        return offset;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** 停到某一页（无动画）。 */
    public void show(int index) {
        if (pages.size() == 0) return;
        cancelSettle();
        cancelBob();
        current = mod(index);
        offset = current;
        lastTick = current;
        requestLayout();
        post(new Runnable() {
            public void run() {
                layoutCards();
                if (listener != null) listener.onOffsetChanged(offset);
            }
        });
    }

    public void startBreathing() {
        startBob();
    }

    public void stopBreathing() {
        cancelBob();
    }

    // ---------------------------------------------------------------- 布局

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
        }
        laidOut = true;
        layoutCards();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w > 0 && h > 0 && halo != null) {
            halo.setShader(new RadialGradient(w / 2f, h * 0.44f, w * 0.92f,
                    new int[]{0x1FFFFFFF, 0x0DFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.55f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        }
        layoutCards();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (halo != null && halo.getShader() != null) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), halo);
        }
    }

    private void layoutCards() {
        int n = getChildCount();
        if (n == 0 || getWidth() <= 0) return;
        float w = getWidth();
        float h = getHeight();
        float cw = getChildAt(0).getWidth();
        float ch = getChildAt(0).getHeight();
        if (cw <= 0 || ch <= 0) return;

        float radius = Math.min(w * 1.25f, cw * 3.2f);
        float baseY = h * 0.44f;

        for (int i = 0; i < n; i++) {
            View card = getChildAt(i);
            float rel = i - offset;
            if (n > 2) rel -= n * Math.round(rel / n);   // 环形：永远取最近的自己
            if (Math.abs(rel) > 1.8f) {
                card.setVisibility(INVISIBLE);
                continue;
            }
            card.setVisibility(VISIBLE);
            float angle = rel * STEP;
            float x = (w - cw) / 2f + (float) (radius * Math.sin(angle));
            float y = baseY - ch / 2f + (float) (radius * (1f - Math.cos(angle)));
            card.setTranslationX(x);
            card.setTranslationY(y);
            if (i == current) homeY = y;
            float scale = 1f - Math.min(0.14f, Math.abs(rel) * 0.08f);
            card.setScaleX(scale);
            card.setScaleY(scale);
            card.setRotation(rel * ROT_PER_CARD);
            card.setAlpha(1f - Math.min(0.58f, Math.abs(rel) * 0.34f));
            card.setZ(10f - Math.abs(rel));
        }
    }

    // ---------------------------------------------------------------- 手势

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (pages.size() == 0) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                startOffset = offset;
                lastTick = mod(Math.round(offset));
                dragging = false;
                cancelSettle();
                cancelBob();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    dragging = true;
                }
                if (dragging) {
                    offset = startOffset - dx / (getWidth() * 0.44f);
                    layoutCards();
                    if (listener != null) listener.onOffsetChanged(offset);
                    tickIfCrossed();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    dragging = false;
                    settleTo(Math.round(offset));
                } else {
                    handleTap(e.getX(), e.getY());
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    settleTo(Math.round(offset));
                }
                return true;
            default:
                return true;
        }
    }

    private void handleTap(float x, float y) {
        int n = getChildCount();
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        float bestRel = 0f;
        for (int i = 0; i < n; i++) {
            View card = getChildAt(i);
            if (card.getVisibility() != VISIBLE) continue;
            float cx = card.getTranslationX() + card.getWidth() * card.getScaleX() / 2f;
            float cy = card.getTranslationY() + card.getHeight() * card.getScaleY() / 2f;
            float halfW = card.getWidth() * card.getScaleX() * 0.55f;
            float halfH = card.getHeight() * card.getScaleY() * 0.55f;
            if (Math.abs(x - cx) < halfW && Math.abs(y - cy) < halfH) {
                float rel = i - offset;
                if (n > 2) rel -= n * Math.round(rel / n);
                float d = Math.abs(rel);
                if (d < bestDist) {
                    bestDist = d;
                    best = i;
                    bestRel = rel;
                }
            }
        }
        if (best < 0) {
            if (listener != null) listener.onBackdropTap();
            return;
        }
        if (bestDist < 0.2f) {
            if (listener != null) listener.onCardTap(mod(Math.round(offset)));
        } else {
            settleTo(offset + bestRel);
        }
    }

    private int mod(int v) {
        int n = pages.size();
        if (n <= 1) return 0;
        int r = v % n;
        return r < 0 ? r + n : r;
    }

    public void settleTo(float targetOffset) {
        if (pages.size() == 0) return;
        cancelSettle();
        final float from = offset;
        final float to = targetOffset;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(340);
        a.setInterpolator(new OvershootInterpolator(1.05f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator anim) {
                float p = (Float) anim.getAnimatedValue();
                offset = from + (to - from) * p;
                layoutCards();
                if (listener != null) listener.onOffsetChanged(offset);
                tickIfCrossed();
            }
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (canceled) return;
                settleAnim = null;
                offset = to;
                current = mod(Math.round(to));
                layoutCards();
                tickIfCrossed();
                if (listener != null) listener.onIndexChanged(current);
                startBob();
            }
        });
        settleAnim = a;
        a.start();
    }

    /** 连续拖动 / 回弹时，每跨过一张卡片的边界就 tick 一次。 */
    private void tickIfCrossed() {
        int idx = mod(Math.round(offset));
        if (idx == lastTick) return;
        lastTick = idx;
        if (listener != null) listener.onTick(idx);
    }

    private void cancelSettle() {
        if (settleAnim != null) {
            ValueAnimator a = settleAnim;
            settleAnim = null;
            a.cancel();
        }
    }

    // ---------------------------------------------------------------- 中间卡的呼吸 / 浮动

    private void startBob() {
        cancelBob();
        if (pages.size() == 0) return;
        final FrameLayout page = pages.get(current);
        if (page == null) return;
        if (!Motion.enabled(getContext())) return;
        final float lift = Skin.dp(getContext(), 8f);
        final float baseY = homeY;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(2800);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator anim) {
                float p = (Float) anim.getAnimatedValue();
                float s = 1f + 0.02f * p;
                // 整张卡片一起呼吸 / 上下浮动，不是内部图片动
                page.setScaleX(s);
                page.setScaleY(s);
                page.setTranslationY(baseY - lift * p);
            }
        });
        a.start();
        bobAnim = a;
    }

    private void cancelBob() {
        if (bobAnim != null) {
            ValueAnimator a = bobAnim;
            bobAnim = null;
            a.cancel();
        }
        layoutCards();   // 把卡片的缩放 / 位移复位到基础布局
    }

    public boolean isLaidOut() {
        return laidOut;
    }
}
