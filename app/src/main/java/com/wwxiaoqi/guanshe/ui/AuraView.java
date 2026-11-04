package com.wwxiaoqi.guanshe.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

import com.wwxiaoqi.guanshe.Skin;

/**
 * 顶部"色晕"：把一组颜色以极低透明度铺在页面顶端，向下淡出到底色。
 *
 * 用途（docs/ui-plan 立场 1 的延伸）：全 App 背景都是同一张纸，但发现页那组"今日配色"
 * 可以让纸面带一点自己的气息 —— 颜色仍然来自数据，不是品牌色。
 */
public final class AuraView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] colors;
    private final float topAlpha;

    private AuraView(Context c, int[] colors, float topAlpha) {
        super(c);
        this.colors = colors == null || colors.length == 0 ? new int[]{0xFFCCCCCC} : colors;
        this.topAlpha = topAlpha;
    }

    /** @param heightDp 渐变区高度；topAlpha 建议 0.10–0.18 */
    public static AuraView of(Context c, int[] colors, float heightDp, float topAlpha) {
        AuraView v = new AuraView(c, colors, topAlpha);
        v.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(c, heightDp)));
        return v;
    }

    @Override
    protected void onDraw(Canvas cv) {
        float seg = getWidth() / (float) colors.length;
        for (int i = 0; i < colors.length; i++) {
            int top = Skin.tint(colors[i], topAlpha);
            int bottom = Skin.tint(colors[i], 0f);
            paint.setShader(new LinearGradient(0, 0, 0, getHeight(), top, bottom, Shader.TileMode.CLAMP));
            cv.drawRect(i * seg, 0, (i + 1) * seg, getHeight(), paint);
        }
        paint.setShader(null);
    }
}
