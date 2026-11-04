package com.wwxiaoqi.guanshe.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.Skin;

/**
 * 动效系统。全 App 的动画时长、缓动曲线只从这里取，保证"同一个动作到处长一个样"。
 *
 * 三条规矩：
 * 1. 进场的元素从下方 14dp 轻轻升起（纸张被放上桌面的感觉），不要弹跳。
 * 2. 回弹只用在"确认"类动作：收藏、选中 chip、按下松手。
 * 3. 系统关掉动画（开发者选项 / 无障碍减少动态效果）时全部瞬时完成。
 */
public final class Motion {

    public static final long FAST = 130L;
    public static final long BASE = 230L;
    public static final long SLOW = 360L;
    public static final long STEP = 45L;

    private static Interpolator ease;
    private static Interpolator out;
    private static Interpolator over;
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();

    private Motion() {
    }

    /** 标准缓动：快起慢停，适合位置 / 透明度。 */
    public static Interpolator ease() {
        if (ease == null) ease = new PathInterpolator(0.2f, 0f, 0f, 1f);
        return ease;
    }

    /** 纯减速，适合大段内容进场。 */
    public static Interpolator out() {
        if (out == null) out = new DecelerateInterpolator(1.7f);
        return out;
    }

    /** 轻微回弹，只偶尔用。 */
    public static Interpolator over() {
        if (over == null) over = new OvershootInterpolator(1.05f);
        return over;
    }

    /** 系统动画是否开启（关闭时所有动效直接跳到终点）。 */
    public static boolean enabled(Context c) {
        try {
            return Settings.Global.getFloat(c.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0.01f;
        } catch (Throwable t) {
            return true;
        }
    }

    public static long dur(Context c, long ms) {
        return enabled(c) ? ms : 0L;
    }

    private static int dp(View v, float dp) {
        return Skin.dp(v.getContext(), dp);
    }

    // ---------------------------------------------------------------- 进场

    /** 从下方升起 + 淡入。 */
    public static void riseIn(View v, long delay) {
        if (v == null) return;
        v.animate().cancel();
        if (!enabled(v.getContext())) {
            v.setAlpha(1f);
            v.setTranslationY(0f);
            return;
        }
        v.setAlpha(0f);
        v.setTranslationY(dp(v, 14));
        v.animate().alpha(1f).translationY(0f)
                .setDuration(SLOW).setStartDelay(Math.max(0, delay)).setInterpolator(out()).start();
    }

    /** 从下方升起 + 淡入（无延迟）。 */
    public static void riseIn(View v) {
        riseIn(v, 0);
    }

    public static void fadeIn(View v, long delay) {
        if (v == null) return;
        v.animate().cancel();
        if (!enabled(v.getContext())) {
            v.setAlpha(1f);
            return;
        }
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(BASE)
                .setStartDelay(Math.max(0, delay)).setInterpolator(out()).start();
    }

    public static void fadeIn(View v) {
        fadeIn(v, 0);
    }

    /** 从略小放大到 100%，带一点回弹 —— 卡片被"放"下来的感觉。 */
    public static void scaleIn(View v, long delay) {
        if (v == null) return;
        v.animate().cancel();
        if (!enabled(v.getContext())) {
            v.setAlpha(1f);
            v.setScaleX(1f);
            v.setScaleY(1f);
            return;
        }
        v.setAlpha(0f);
        v.setScaleX(0.94f);
        v.setScaleY(0.94f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(SLOW).setStartDelay(Math.max(0, delay)).setInterpolator(over()).start();
    }

    /**
     * 让容器里的子 View 依次进场（最多 animMax 个，后面的直接显示，避免长列表拖慢）。
     * 用于：页面首次展示、筛选后重建列表、分组结果出现。
     */
    public static void staggerIn(ViewGroup group) {
        staggerIn(group, 0, 10);
    }

    public static void staggerIn(final ViewGroup group, int from, int animMax) {
        if (group == null) return;
        Context c = group.getContext();
        int n = 0;
        for (int i = Math.max(0, from); i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            child.animate().cancel();
            if (!enabled(c) || n >= animMax) {
                child.setAlpha(1f);
                child.setTranslationY(0f);
                continue;
            }
            child.setAlpha(0f);
            child.setTranslationY(Skin.dp(c, 16));
            child.animate().alpha(1f).translationY(0f)
                    .setDuration(SLOW).setStartDelay(n * STEP).setInterpolator(out()).start();
            n++;
        }
    }

    // ---------------------------------------------------------------- 强调

    /** 收藏 / 选中：放大一下再弹回。 */
    public static void pop(final View v) {
        if (v == null) return;
        v.animate().cancel();
        if (!enabled(v.getContext())) {
            v.setScaleX(1f);
            v.setScaleY(1f);
            return;
        }
        v.setPivotX(v.getWidth() / 2f);
        v.setPivotY(v.getHeight() / 2f);
        v.animate().scaleX(1.16f).scaleY(1.16f)
                .setDuration(FAST).setInterpolator(ease())
                .withEndAction(new Runnable() {
                    public void run() {
                        v.animate().scaleX(1f).scaleY(1f)
                                .setDuration(BASE).setInterpolator(over()).start();
                    }
                }).start();
    }

    /** 文字颜色平滑过渡（切 tab、选中状态）。 */
    public static void textColor(final TextView t, final int color) {
        if (t == null) return;
        if (!enabled(t.getContext())) {
            t.setTextColor(color);
            return;
        }
        int from = t.getCurrentTextColor();
        if (from == color) return;
        ValueAnimator a = ValueAnimator.ofObject(ARGB, from, color);
        a.setDuration(180);
        a.setInterpolator(ease());
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator anim) {
                t.setTextColor((Integer) anim.getAnimatedValue());
            }
        });
        a.start();
    }

    // ---------------------------------------------------------------- 触摸反馈

    /**
     * 按下反馈：可选缩放、可选抬升、可选轻震。不消费事件，点击逻辑照常走。
     *
     * @param scale      按下时缩到 0.965（卡片 / chip / tab 用；整行列表不要缩）
     * @param haptic     按下是否轻震
     * @param restElevDp 静止时的抬升高度；>0 时按下会再抬高 3dp
     */
    public static void interact(final View v, final boolean scale, final boolean haptic,
                                final float restElevDp) {
        if (v == null) return;
        if (restElevDp > 0f) Surface.elevate(v, restElevDp);
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                int action = e.getActionMasked();
                boolean on = enabled(view.getContext());
                if (action == MotionEvent.ACTION_DOWN) {
                    if (haptic) Haptics.tap(view);
                    if (!on) return false;
                    view.animate().cancel();
                    ViewPropertyAnimator a = view.animate()
                            .setDuration(FAST).setInterpolator(ease());
                    if (scale) a.scaleX(0.965f).scaleY(0.965f);
                    if (restElevDp > 0f) a.translationZ(Skin.dp(view.getContext(), 3));
                    a.start();
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (!on) return false;
                    view.animate().cancel();
                    ViewPropertyAnimator a = view.animate().setDuration(BASE).setInterpolator(
                            action == MotionEvent.ACTION_CANCEL ? out() : over());
                    if (scale) a.scaleX(1f).scaleY(1f);
                    if (restElevDp > 0f) a.translationZ(0f);
                    a.start();
                }
                return false;
            }
        });
    }

    /** 只有按压缩放，不抬升、不震动。 */
    public static void interact(View v, boolean scale) {
        interact(v, scale, false, 0f);
    }

    // ---------------------------------------------------------------- 页面切换

    /** 新页面从一侧滑入并淡入。 */
    public static void pageIn(View v, float fromDp) {
        if (!enabled(v.getContext())) {
            v.setAlpha(1f);
            v.setTranslationX(0f);
            return;
        }
        v.setAlpha(0f);
        v.setTranslationX(dp(v, fromDp));
        v.animate().alpha(1f).translationX(0f)
                .setDuration(BASE).setInterpolator(ease()).start();
    }

    /** 旧页面淡出并向另一侧让开一点。 */
    public static void pageOut(View v, float toDp, Runnable end) {
        if (!enabled(v.getContext())) {
            if (end != null) end.run();
            return;
        }
        v.animate().alpha(0f).translationX(dp(v, toDp))
                .setDuration(BASE).setInterpolator(ease())
                .withEndAction(end).start();
    }
}
