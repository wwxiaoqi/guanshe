package com.wwxiaoqi.guanshe.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

import com.wwxiaoqi.guanshe.Skin;

/**
 * 按下反馈：**保留控件原有背景**，只在上面叠一层被形状裁住的波纹。
 *
 * 这一版在波纹之外补了"按下去会动"的反馈：
 * - 卡片 / chip 按下缩到 96.5%，松手回弹；
 * - 悬浮的卡片按下会再抬高 3dp，像被手指压起来；
 * - 列表行不缩放（整行缩放会让整张卡在抖），只保留波纹和轻震。
 */
public final class Tap {

    private Tap() {
    }

    /** 矩形控件（列表行、tab 项）。 */
    public static void feedback(View v) {
        ripple(v, 0f);
    }

    /** @param radiusDp > 0 时按圆角矩形裁波纹（胶囊/圆形传 999） */
    public static void feedback(View v, float radiusDp) {
        ripple(v, radiusDp);
    }

    /** 可点卡片：波纹 + 缩放 + 悬浮抬起。 */
    public static void card(View v) {
        card(v, Surface.CARD);
    }

    public static void card(View v, float restElevDp) {
        ripple(v, 0f);
        Motion.interact(v, true, false, restElevDp);
    }

    /** 可点小色块 / chip / 图标按钮：波纹 + 缩放 + 轻震。 */
    public static void chip(View v) {
        ripple(v, 999f);
        Motion.interact(v, true, true, 0f);
    }

    /** 列表行：波纹 + 轻震，不缩放。 */
    public static void row(View v) {
        ripple(v, 0f);
        Motion.interact(v, false, true, 0f);
    }

    /** 带圆角背景的行。 */
    public static void row(View v, float radiusDp) {
        ripple(v, radiusDp);
        Motion.interact(v, false, true, 0f);
    }

    /** 只要波纹，不要缩放 / 震动（例如点了只是切页的大色带）。 */
    public static void plain(View v) {
        ripple(v, 0f);
    }

    /**
     * 保留原背景、只叠波纹。
     * mask 必须传圆角 Drawable：传 null 时波纹不受边界约束，会画成一坨大圆。
     */
    static void ripple(View v, float radiusDp) {
        Context c = v.getContext();
        Drawable content = v.getBackground();

        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setColor(0xFFFFFFFF);                       // mask 只用 alpha
        if (radiusDp > 0) mask.setCornerRadius(Skin.dp(c, radiusDp));

        int ripple = Skin.isNight(c) ? 0x33FFFFFF : 0x22000000;
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), content, mask));
    }
}
