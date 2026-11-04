package com.wwxiaoqi.guanshe.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.R;
import com.wwxiaoqi.guanshe.Skin;

/**
 * 胶囊筛选行。
 *
 * 旧版每次选中都 removeAllViews 重建整行 —— 看起来是"闪一下换了一套"。这一版：
 * 1. 行只建一次，选中状态用颜色过渡 + 轻微放大表达；
 * 2. 横滑的行会把选中的胶囊自动滚到中间；
 * 3. 选中仍然是"墨底反白"，不靠色相区分（无障碍要求）。
 */
public final class Chips {

    private static final ArgbEvaluator ARGB = new ArgbEvaluator();

    private Chips() {
    }

    public interface OnPick {
        void onPick(int index, String label);
    }

    /** 一行的句柄：拿 view 塞进布局，调 select 改选中。 */
    public static final class Bar {
        public final View view;
        private final Context c;
        private final TextView[] chips;
        private final String[] labels;
        private final OnPick onPick;
        private final HorizontalScrollView sc;
        private int selected;

        Bar(Context c, View view, TextView[] chips, String[] labels,
            OnPick onPick, HorizontalScrollView sc, int selected) {
            this.c = c;
            this.view = view;
            this.chips = chips;
            this.labels = labels;
            this.onPick = onPick;
            this.sc = sc;
            this.selected = selected;
        }

        public int selected() {
            return selected;
        }

        public void select(int index) {
            select(index, true);
        }

        public void select(int index, boolean animate) {
            selected = index;
            for (int i = 0; i < chips.length; i++) {
                paint(chips[i], labels[i], i == index, animate);
            }
            if (sc != null && index >= 0 && index < chips.length) center(chips[index]);
        }

        void pick(int index) {
            Haptics.select(chips[index]);
            if (index == selected) return;
            select(index);
            if (onPick != null) onPick.onPick(index, labels[index]);
        }

        private void center(final TextView chip) {
            sc.post(new Runnable() {
                public void run() {
                    View content = sc.getChildAt(0);
                    if (content == null) return;
                    int target = chip.getLeft() + chip.getWidth() / 2 - sc.getWidth() / 2;
                    int max = Math.max(0, content.getWidth() - sc.getWidth());
                    if (target < 0) target = 0;
                    if (target > max) target = max;
                    sc.smoothScrollTo(target, 0);
                }
            });
        }
    }

    /**
     * @param scrollX true = 单行横滑（色系 16 个）；false = 平铺换行
     */
    public static Bar bar(Context c, String[] labels, int selected, boolean scrollX, OnPick onPick) {
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(Skin.dp(c, Page.PAD), 0, Skin.dp(c, Page.PAD), 0);

        TextView[] chips = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView chip = chipView(c, labels[i]);
            chips[i] = chip;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = Skin.dp(c, 8);
            line.addView(chip, lp);
        }

        HorizontalScrollView hv = null;
        View view;
        if (scrollX) {
            hv = new HorizontalScrollView(c);
            hv.setHorizontalScrollBarEnabled(false);
            hv.setClipToPadding(false);
            hv.addView(line, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            view = hv;
        } else {
            view = line;
        }

        final Bar bar = new Bar(c, view, chips, labels, onPick, hv, selected);
        for (int i = 0; i < chips.length; i++) {
            final int idx = i;
            chips[i].setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    bar.pick(idx);
                }
            });
        }
        bar.select(selected, false);
        return bar;
    }

    /** 兼容旧调用：只要一个 View 的地方。 */
    public static View row(Context c, String[] labels, int selected, boolean scrollX, OnPick onPick) {
        return bar(c, labels, selected, scrollX, onPick).view;
    }

    /** 单颗胶囊（不属于筛选行时用）。 */
    public static TextView chipView(Context c, String label) {
        TextView t = Skin.medium(c, label, Skin.T_CHIP, Skin.text2(c));
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(Skin.dp(c, 38));
        t.setPadding(Skin.dp(c, 14), Skin.dp(c, 8), Skin.dp(c, 14), Skin.dp(c, 8));
        t.setLetterSpacing(0.01f);
        t.setTag(0f);                       // 动画进度：0=未选 1=选中
        Tap.chip(t);                        // 波纹 + 按压缩放 + 轻震
        return t;
    }

    // ---------------------------------------------------------------- 绘制
    private static void paint(final TextView chip, String label, boolean on, boolean animate) {
        Context c = chip.getContext();
        float target = on ? 1f : 0f;
        Object tag = chip.getTag();
        float from = tag instanceof Float ? (Float) tag : (on ? 0f : 1f);
        chip.setContentDescription(label + (on ? "，已选中" : ""));
        if (!animate || !Motion.enabled(c) || Math.abs(from - target) < 0.01f) {
            apply(chip, target);
            chip.setTag(target);
            restore(chip);
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(from, target);
        a.setDuration(190);
        a.setInterpolator(Motion.ease());
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator anim) {
                float f = (Float) anim.getAnimatedValue();
                apply(chip, f);
                chip.setTag(f);
            }
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                restore(chip);
            }
        });
        a.start();
    }

    private static void apply(TextView chip, float on) {
        Context c = chip.getContext();
        int accent = Skin.c(c, R.color.accentFill);
        int fill = (Integer) ARGB.evaluate(on, 0x00000000, accent);
        int stroke = (Integer) ARGB.evaluate(on, Skin.c(c, R.color.cardStroke), accent);
        int text = (Integer) ARGB.evaluate(on, Skin.text2(c), Skin.c(c, R.color.bg));

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(Skin.dp(c, 999));
        if (on < 0.98f) shape.setStroke(Math.max(1, Skin.dp(c, 1)), stroke);
        chip.setBackground(shape);
        chip.setTextColor(text);
    }

    /** 动画期间背景是普通 Shape，结束后补回波纹。 */
    private static void restore(TextView chip) {
        Tap.ripple(chip, 999f);
    }
}
