package com.wwxiaoqi.guanshe.ui;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.Skin;

/**
 * 详情页的"浮动白纸"：白色信息区可以继续往下拖，拖到一定距离就滑出屏幕隐藏；
 * 隐藏后宿主会弹出暗场分享卡，卡片关闭时白纸再滑回来。
 */
public final class PullToShare {

    public interface Listener {
        void onOpen();
    }

    private PullToShare() {
    }

    public static void attach(final Activity a, final PullScrollView scroller,
                              final TextView hint, final Listener listener) {
        if (a == null || scroller == null) return;
        final float trigger = Skin.dp(a, 130f);
        final float openAt = Skin.dp(a, 110f);
        scroller.setPullListener(new PullScrollView.PullListener() {
            public void onPull(float dy, float maxDy) {
                float follow = Math.max(0f, dy);
                float f = Math.min(1f, follow / trigger);
                scroller.setTranslationY(follow);
                if (hint != null) {
                    hint.setAlpha(f);
                    hint.setTranslationY(Skin.dp(a, 10f) * (1f - f));
                }
            }

            public void onRelease(float maxDy, boolean canceled) {
                boolean open = !canceled && maxDy > openAt;
                if (open) {
                    if (hint != null) {
                        hint.animate().alpha(0f).setDuration(Motion.FAST).start();
                    }
                    Haptics.shareOpen(scroller);
                    hideThenShow(a, scroller, listener);
                } else {
                    restore(scroller, hint);
                }
            }
        });
    }

    /** 白纸滑出屏幕；结束后执行 after。白纸保持隐藏，等分享卡关闭再由调用方 restore。 */
    public static void hideThenShow(Activity a, final ScrollView scroller, final Listener after) {
        if (scroller == null) {
            if (after != null) after.onOpen();
            return;
        }
        final View root = a == null ? null : a.findViewById(android.R.id.content);
        final int distance = root != null && root.getHeight() > 0
                ? root.getHeight()
                : (int) (a.getResources().getDisplayMetrics().heightPixels * 0.9f);
        scroller.animate().cancel();
        scroller.animate().translationY(distance)
                .setDuration(270)
                .setInterpolator(new AccelerateInterpolator(1.7f))
                .withEndAction(new Runnable() {
                    public void run() {
                        if (after != null) after.onOpen();
                    }
                }).start();
    }

    /** 分享卡关闭后，白纸滑回原位。 */
    public static void restore(final ScrollView scroller) {
        if (scroller == null) return;
        scroller.animate().cancel();
        scroller.animate().translationY(0f)
                .setDuration(320)
                .setInterpolator(new DecelerateInterpolator(1.2f))
                .start();
    }

    private static void restore(View scroller, View hint) {
        scroller.animate().translationY(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        if (hint != null) {
            hint.animate().alpha(0f).translationY(0f)
                    .setDuration(Motion.FAST).start();
        }
    }
}
