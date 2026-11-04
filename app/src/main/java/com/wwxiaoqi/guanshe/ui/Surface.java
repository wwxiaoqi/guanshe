package com.wwxiaoqi.guanshe.ui;

import android.graphics.Outline;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.wwxiaoqi.guanshe.Skin;

/**
 * 层次与阴影。
 *
 * 旧版刻意不用 elevation，层次只靠描边。这次反过来：
 * 纸面上仍然轻，只在"能按的东西"上给一层很浅的暖影 —— 影子是深度提示，不是装饰。
 * 暖影靠 API 28 的 outlineShadowColor 实现，低版本退回系统默认黑。
 */
public final class Surface {

    /** 卡片默认抬升：0 —— 普通卡片不再有影子，层次交给描边与底色差。
     *  影子只留给真正"浮在页面上"的东西（底栏 / FAB / sheet / 提示条）。 */
    public static final float CARD = 0f;
    /** 可点卡片按下时抬升到的高度。 */
    public static final float PRESS = 3f;
    /** 浮起来的底栏。 */
    public static final float FLOAT = 7f;
    /** 详情页 sheet 压在色带上的那层。 */
    public static final float SHEET = 6f;
    /** 提示条。 */
    public static final float SNACK = 9f;

    private Surface() {
    }

    /** 抬升。阴影颜色按明暗主题取暖墨色 / 纯黑。 */
    public static void elevate(View v, float dp) {
        if (v == null) return;
        v.setElevation(Skin.dp(v.getContext(), dp));
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                int shadow = Skin.isNight(v.getContext()) ? 0xFF000000 : 0xFF4A3A28;
                v.setOutlineAmbientShadowColor(shadow);
                v.setOutlineSpotShadowColor(shadow);
            } catch (Throwable ignored) {
                // 个别 ROM 的古怪实现：阴影只是锦上添花
            }
        }
    }

    /** 圆角轮廓。投影按这个轮廓算，子 View 也会被裁在圆角里。 */
    public static void round(final View v, final float radiusDp) {
        if (v == null) return;
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        Skin.dp(view.getContext(), radiusDp));
            }
        });
        v.setClipToOutline(true);
    }

    /** 一张卡：圆角轮廓 + 可选抬升。restDp <= 0 时不加影子。 */
    public static void card(View v, float radiusDp, float restDp) {
        round(v, radiusDp);
        elevate(v, restDp);
    }

    /** 只要轮廓、不要裁剪（大圆角容器里放可点内容时用）。 */
    public static void outline(final View v, final float radiusDp) {
        if (v == null) return;
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        Skin.dp(view.getContext(), radiusDp));
            }
        });
    }
}
