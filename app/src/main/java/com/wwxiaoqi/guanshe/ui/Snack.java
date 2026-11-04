package com.wwxiaoqi.guanshe.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.MainActivity;
import com.wwxiaoqi.guanshe.R;
import com.wwxiaoqi.guanshe.Skin;

/**
 * 页面内提示条（Snackbar）：从底部升起的小卡片，支持一个"撤销 / 查看"动作。
 *
 * 比 Toast 好的地方：不抢焦点、带动作、位置永远在底栏之上。
 * 旧版只有淡入淡出；这一版是升起 + 淡入 + 轻微放大，并且换掉了夜间不合适的深色底。
 */
public final class Snack {

    private static final long DURATION = 2600L;
    private static final long DURATION_ACTION = 4200L;

    private static View showing;
    private static Runnable dismissShowing;

    private Snack() {
    }

    public static void show(View anchor, String text) {
        show(anchor, text, null, null);
    }

    /**
     * @param actionLabel 动作文字（如"撤销"），null 表示纯提示
     * @param action     点击动作后执行，执行前提示条自动消失
     */
    public static void show(View anchor, String text, String actionLabel, final Runnable action) {
        Activity a = activityOf(anchor == null ? null : anchor.getContext());
        if (a == null) return;
        View content = a.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        final ViewGroup parent = (ViewGroup) content;

        dismissNow();

        boolean night = Skin.isNight(a);
        int bg = night ? 0xF2F2EFE9 : 0xF21F1D1A;
        int fg = night ? 0xFF17171A : 0xFFF6F5F2;
        int actionColor = night ? 0xFF8A5F12 : 0xFFE8B45A;

        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(Skin.round(a, bg, 16f));
        int ph = Skin.dp(a, 18), pv = Skin.dp(a, 12);
        bar.setPadding(ph, pv, ph, pv);
        Surface.elevate(bar, Surface.SNACK);
        Surface.round(bar, 16f);

        TextView t = Skin.text(a, text, Skin.T_BODY, fg);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (actionLabel != null && action != null) {
            TextView act = Skin.medium(a, actionLabel, Skin.T_LABEL, actionColor);
            act.setPadding(Skin.dp(a, 14), 0, Skin.dp(a, 2), 0);
            act.setContentDescription(actionLabel);
            act.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Haptics.tap(v);
                    dismissNow();
                    action.run();
                }
            });
            Tap.row(act, 12f);
            bar.addView(act);
        }

        int maxWidth = a.getResources().getDisplayMetrics().widthPixels - Skin.dp(a, 40);
        bar.setMinimumWidth(Skin.dp(a, 120));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = bottomOffset(a);
        lp.leftMargin = Skin.dp(a, 20);
        lp.rightMargin = Skin.dp(a, 20);
        if (bar.getMeasuredWidth() > maxWidth) {
            // 宽度由内容决定，交给 maxWidth 兜底
        }
        parent.addView(bar, lp);

        final View v = bar;
        if (Motion.enabled(a)) {
            v.setAlpha(0f);
            v.setTranslationY(Skin.dp(a, 22));
            v.setScaleX(0.96f);
            v.setScaleY(0.96f);
            v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(Motion.BASE).setInterpolator(Motion.over()).start();
        }

        showing = v;
        final long delay = (actionLabel == null || action == null) ? DURATION : DURATION_ACTION;
        dismissShowing = new Runnable() {
            public void run() {
                dismiss();
            }
        };
        v.postDelayed(dismissShowing, delay);
    }

    /** 复制到剪贴板并提示。 */
    public static void copy(View anchor, String text) {
        copy(anchor, text, null);
    }

    /** @param label 提示语；为空时用「已复制 <内容>」，内容太长就只说字数 */
    public static void copy(View anchor, String text, String label) {
        Context c = anchor.getContext();
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("观色", text));
        String tip;
        if (label != null && label.length() > 0) {
            tip = label;
        } else if (text != null && text.length() <= 40) {
            tip = "已复制 " + text;
        } else {
            tip = "已复制 " + (text == null ? 0 : text.length()) + " 个字符";
        }
        Haptics.success(anchor);
        show(anchor, tip);
    }

    // ---------------------------------------------------------------- 内部
    private static void dismissNow() {
        if (showing != null) {
            // 先把旧的自动消失定时器摘掉，否则它到点会把刚弹出来的新提示条关掉
            if (dismissShowing != null) showing.removeCallbacks(dismissShowing);
            ViewGroup p = (ViewGroup) showing.getParent();
            if (p != null) p.removeView(showing);
            showing = null;
            dismissShowing = null;
        }
    }

    private static void dismiss() {
        final View v = showing;
        if (v == null) return;
        showing = null;
        dismissShowing = null;
        final ViewGroup p = (ViewGroup) v.getParent();
        if (p == null) return;
        if (!Motion.enabled(v.getContext())) {
            p.removeView(v);
            return;
        }
        v.animate().alpha(0f).translationY(Skin.dp(v.getContext(), 10)).scaleX(0.98f).scaleY(0.98f)
                .setDuration(Motion.FAST).setInterpolator(Motion.ease())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        p.removeView(v);
                    }
                }).start();
    }

    /** 提示条离屏幕底部的距离：主界面的底栏之上，其他页面贴着导航条。 */
    private static int bottomOffset(Activity a) {
        if (a instanceof MainActivity) {
            int dock = ((MainActivity) a).snackBottomMargin();
            if (dock > 0) return dock;
        }
        return Bars.bottom(a) + Skin.dp(a, 24);
    }

    private static Activity activityOf(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
            if (c == null) return null;
        }
        return null;
    }
}
