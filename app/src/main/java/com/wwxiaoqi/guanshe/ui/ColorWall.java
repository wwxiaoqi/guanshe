package com.wwxiaoqi.guanshe.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * 整屏颜色墙：详情页白纸后面、拽下来时露出来的那片背景。
 *
 * 一组配色 → 四条等宽竖色带铺满全屏；单个颜色 → 整屏同色。
 * 和详情页顶部的色带完全同源，所以往下拽白纸时，露出来的就是"上面那片颜色"本身。
 */
public class ColorWall extends View {

    private final int[] colors;
    private final Paint paint = new Paint();

    public ColorWall(Context c, int[] colors) {
        super(c);
        this.colors = colors;
        setBackgroundColor(0);
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (colors != null && colors.length > 1) {
            float cell = w / (float) colors.length;
            for (int i = 0; i < colors.length; i++) {
                paint.setColor(colors[i]);
                cv.drawRect(i * cell, 0, (i + 1) * cell, h, paint);
            }
        } else {
            paint.setColor(colors != null && colors.length == 1 ? colors[0] : 0xFF17171A);
            cv.drawRect(0, 0, w, h, paint);
        }
    }
}
