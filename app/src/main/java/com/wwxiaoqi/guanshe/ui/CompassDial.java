package com.wwxiaoqi.guanshe.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * 分享卡下方的"指南针刻度盘"。
 *
 * 白色指针固定在正中央、不移动；刻度环随卡片的轮播整体转动。
 * 刻度全部等长、没有数字、没有小刻度；只有亮度按 4 格一组有细微变化，
 * 这样转动时能看出刻度确实在走，而不是一张静止的图。
 */
public class CompassDial extends View {

    /** 弧线可见的半角（两侧各 35°）。 */
    private static final float SPAN = 35f;
    /** 每张卡转动 30°。 */
    private static final float DEG_PER_CARD = 30f;
    /** 刻度间距 10°。 */
    private static final float TICK_STEP = 10f;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float offset;
    private int count = 3;

    public CompassDial(Context c) {
        super(c);
        float density = c.getResources().getDisplayMetrics().density;
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(1.2f * density);
        arcPaint.setColor(0x55FFFFFF);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        activePaint.setStyle(Paint.Style.STROKE);
        activePaint.setStrokeCap(Paint.Cap.ROUND);
        activePaint.setStrokeWidth(2.8f * density);
        activePaint.setColor(0xFFFFFFFF);

        pointerPaint.setStyle(Paint.Style.FILL);
        pointerPaint.setColor(0xE6FFFFFF);
    }

    public void setOffset(float value) {
        if (offset == value) return;
        offset = value;
        invalidate();
    }

    public void setCount(int n) {
        count = Math.max(1, n);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float density = getResources().getDisplayMetrics().density;

        float half = w * 0.445f;
        float radius = half / (float) Math.sin(Math.toRadians(SPAN));
        float topPad = 16f * density;
        float cx = w / 2f;
        float cy = topPad + radius;

        cv.drawArc(new RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                270f - SPAN, SPAN * 2f, false, arcPaint);

        if (count <= 1) return;

        float rotation = -offset * DEG_PER_CARD;
        // 刻度环整体转动；等长、无数字、无小刻度，亮度每 4 格一组变化
        for (int k = 0; k < 36; k++) {
            float deg = k * TICK_STEP + rotation;
            float delta = norm(deg - 270f);
            if (Math.abs(delta) > SPAN + 2f) continue;

            double rad = Math.toRadians(deg);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            float len = 16f * density;
            float x1 = cx + radius * cos;
            float y1 = cy + radius * sin;
            float x2 = cx + (radius - len) * cos;
            float y2 = cy + (radius - len) * sin;

            boolean active = Math.abs(delta) < TICK_STEP * 0.35f;
            boolean strong = (k % 4 == 0);
            tickPaint.setStrokeWidth((active ? 2.8f : (strong ? 2.2f : 1.8f)) * density);
            tickPaint.setColor(active ? 0xFFFFFFFF : (strong ? 0xAAFFFFFF : 0x55FFFFFF));
            cv.drawLine(x1, y1, x2, y2, active ? activePaint : tickPaint);
        }

        // 固定的白色小三角指针：始终在正中央
        Path tri = new Path();
        float tipY = topPad - 2f * density;
        tri.moveTo(cx, tipY);
        tri.lineTo(cx - 6f * density, tipY - 11f * density);
        tri.lineTo(cx + 6f * density, tipY - 11f * density);
        tri.close();
        cv.drawPath(tri, pointerPaint);
    }

    private static float norm(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }
}
