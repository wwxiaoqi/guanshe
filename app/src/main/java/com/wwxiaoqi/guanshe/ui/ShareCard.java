package com.wwxiaoqi.guanshe.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.wwxiaoqi.guanshe.core.ColorMath;

/**
 * 把「一组配色 / 一个颜色」画成可分享的卡片图。
 *
 * 三种版式共用同一个 4:5 画布（1080×1350），这样在弹层里翻页时卡片不会忽大忽小：
 * - POSTER：上半幅四条色带，下半幅纸面写字，像一张小海报；
 * - CARD：纸底上一张圆角白卡，四块圆角色票 2×2 排开，像一张色票卡；
 * - TICKET：四条色带铺满整张，底部压一条纸带写色号，像一张票根。
 *
 * 所有文案都是产品自己的数据（组名 / 色号），没有占位内容。
 */
public final class ShareCard {

    public static final int STYLE_POSTER = 0;
    public static final int STYLE_CARD = 1;
    public static final int STYLE_TICKET = 2;
    public static final int STYLE_SPLIT = 3;
    public static final int STYLE_COLLAGE = 4;
    public static final int STYLE_RIBBON = 5;

    public static final int W = 1080;
    public static final int H = 1350;

    private static final int PAPER = 0xFFF6F4EF;
    private static final int INK = 0xFF17171A;
    private static final int INK2 = 0xFF6F6A62;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int HAIR = 0x2217171A;

    private ShareCard() {
    }

    // ---------------------------------------------------------------- 配色卡

    public static Bitmap palette(int[] colors, String label, int style) {
        Bitmap bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] cs = normalize(colors);
        String name = label == null || label.trim().length() == 0 ? "这组配色" : label.trim();
        if (style == STYLE_CARD) {
            drawCardStyle(cv, p, cs, name);
        } else if (style == STYLE_TICKET) {
            drawTicketStyle(cv, p, cs, name);
        } else if (style == STYLE_SPLIT) {
            drawSplitStyle(cv, p, cs, name);
        } else if (style == STYLE_COLLAGE) {
            drawCollageStyle(cv, p, cs, name);
        } else if (style == STYLE_RIBBON) {
            drawRibbonStyle(cv, p, cs, name);
        } else {
            drawPosterStyle(cv, p, cs, name);
        }
        return bm;
    }

    private static void drawPosterStyle(Canvas cv, Paint p, int[] cs, String name) {
        cv.drawColor(PAPER);
        float bandH = 780f;
        float cell = W / (float) cs.length;
        for (int i = 0; i < cs.length; i++) {
            p.setColor(cs[i]);
            cv.drawRect(i * cell, 0, (i + 1) * cell, bandH, p);
        }

        Paint title = font(62, INK, true);
        fit(title, name, W - 180f, 38f);
        cv.drawText(name, 90, 892, title);

        cv.drawText(meta(cs.length), 90, 952, font(30, INK2, false));

        p.setColor(HAIR);
        p.setStrokeWidth(2f);
        cv.drawLine(90, 1006, W - 90, 1006, p);
        p.setStrokeWidth(0f);

        float rowTop = 1042f;
        float rowH = 62f;
        for (int i = 0; i < cs.length; i++) {
            float y = rowTop + i * rowH;
            RectF chip = new RectF(90, y, 146, y + 56);
            p.setColor(cs[i]);
            cv.drawRoundRect(chip, 14f, 14f, p);
            p.setColor(HAIR);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            cv.drawRoundRect(chip, 14f, 14f, p);
            p.setStyle(Paint.Style.FILL);
            cv.drawText(ColorMath.hex(cs[i]), 178, y + 40, font(36, INK, false));
        }

        cv.drawText("观色 · 离线配色手册", 90, 1318, font(26, INK2, false));
        cv.drawText("github.com/wwxiaoqi/guanshe", 640, 1318, font(24, INK2, false));
    }

    private static void drawCardStyle(Canvas cv, Paint p, int[] cs, String name) {
        cv.drawColor(PAPER);

        RectF card = new RectF(56, 56, W - 56, H - 56);
        p.setStyle(Paint.Style.FILL);
        p.setColor(WHITE);
        cv.drawRoundRect(card, 44f, 44f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(HAIR);
        cv.drawRoundRect(card, 44f, 44f, p);
        p.setStyle(Paint.Style.FILL);

        Paint title = font(48, INK, true);
        fit(title, name, W - 320f, 30f);
        cv.drawText(name, 110, 168, title);
        Paint brand = font(26, INK2, false);
        brand.setTextAlign(Paint.Align.RIGHT);
        cv.drawText("观色", W - 110, 166, brand);
        brand.setTextAlign(Paint.Align.LEFT);

        float tileW = 420f;
        float tileH = 366f;
        for (int i = 0; i < cs.length && i < 4; i++) {
            float x = 110 + (i % 2) * (tileW + 30f);
            float y = 220 + (i / 2) * (tileH + 30f);
            RectF tile = new RectF(x, y, x + tileW, y + tileH);
            p.setColor(cs[i]);
            cv.drawRoundRect(tile, 26f, 26f, p);
            cv.drawText(ColorMath.hex(cs[i]), x + 28, y + tileH - 30, font(32, inkOn(cs[i]), true));
        }

        cv.drawText(meta(cs.length), 110, 1158, font(28, INK2, false));
        cv.drawText("离线配色手册 · github.com/wwxiaoqi/guanshe", 110, 1212,
                font(24, INK2, false));

        // 右下角落款的小色块：应用图标的四个颜色
        float s = 26f;
        float x = W - 110 - s * 4 - 18f;
        for (int i = 0; i < Math.min(4, cs.length); i++) {
            p.setColor(cs[i]);
            cv.drawRoundRect(new RectF(x + i * (s + 6f), 1160, x + i * (s + 6f) + s, 1160 + s),
                    8f, 8f, p);
        }
    }

    private static void drawTicketStyle(Canvas cv, Paint p, int[] cs, String name) {
        float cell = W / (float) cs.length;
        for (int i = 0; i < cs.length; i++) {
            p.setColor(cs[i]);
            cv.drawRect(i * cell, 0, (i + 1) * cell, H, p);
        }

        RectF strip = new RectF(0, 892, W, H + 48);
        p.setColor(PAPER);
        cv.drawRoundRect(strip, 40f, 40f, p);

        Paint title = font(56, INK, true);
        fit(title, name, W - 160f, 34f);
        cv.drawText(name, 80, 1012, title);

        float x = 80f;
        for (int i = 0; i < cs.length; i++) {
            cv.drawText(ColorMath.hex(cs[i]), x, 1102, font(30, INK, true));
            x += 236f;
        }

        cv.drawText(meta(cs.length) + " · github.com/wwxiaoqi/guanshe", 80, 1200,
                font(24, INK2, false));
        cv.drawText("观色 · 离线配色手册", 80, 1288, font(26, INK2, false));
    }

    /** 撞色：对角两块最冲突的颜色，色号直接压在色块上。 */
    private static void drawSplitStyle(Canvas cv, Paint p, int[] cs, String name) {
        int a = 0;
        int b = cs.length - 1;
        double best = -1;
        for (int i = 0; i < cs.length; i++) {
            for (int j = i + 1; j < cs.length; j++) {
                double c = ColorMath.contrast(cs[i], cs[j]);
                if (c > best) {
                    best = c;
                    a = i;
                    b = j;
                }
            }
        }
        int top = cs[a];
        int bottom = cs[b];
        cv.drawColor(top);

        Path path = new Path();
        path.moveTo(W, 0);
        path.lineTo(W, 900);
        path.lineTo(0, 900);
        path.close();
        p.setColor(bottom);
        cv.drawPath(path, p);

        p.setColor(0x33FFFFFF);
        p.setStrokeWidth(3f);
        cv.drawLine(W, 0, 0, 900, p);
        p.setStrokeWidth(0f);

        Paint title = font(64, inkOn(top), true);
        fit(title, name, W - 160f, 34f);
        cv.drawText(name, 80, 172, title);
        cv.drawText("4 色 · 撞色卡 · 观色", 80, 232, font(28, inkOn(top), false));
        cv.drawText(ColorMath.hex(top), 80, 830, font(50, inkOn(top), true));

        Paint right = font(50, inkOn(bottom), true);
        right.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(ColorMath.hex(bottom), W - 80, 850, right);
        right.setTextAlign(Paint.Align.LEFT);

        p.setColor(PAPER);
        cv.drawRect(0, 900, W, H, p);
        cv.drawText(name, 80, 1012, font(46, INK, true));

        float y = 1082f;
        for (int i = 0; i < cs.length && i < 4; i++) {
            RectF chip = new RectF(80, y - 26, 122, y + 16);
            p.setColor(cs[i]);
            cv.drawRoundRect(chip, 10f, 10f, p);
            cv.drawText(ColorMath.hex(cs[i]), 146, y + 10, font(32, INK, false));
            y += 62f;
        }
        cv.drawText("观色 · 离线配色手册", 80, 1318, font(24, INK2, false));
        cv.drawText("github.com/wwxiaoqi/guanshe", 640, 1318, font(22, INK2, false));
    }

    /** 混搭：大小不一的色块轻微歪斜，像贴出来的一页拼贴。 */
    private static void drawCollageStyle(Canvas cv, Paint p, int[] cs, String name) {
        cv.drawColor(PAPER);

        Paint title = font(54, INK, true);
        fit(title, name, W - 300f, 32f);
        cv.drawText(name, 72, 152, title);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(0x3317171A);
        cv.drawCircle(W - 132, 148, 58f, p);
        p.setStyle(Paint.Style.FILL);
        Paint stamp = font(26, INK2, true);
        stamp.setTextAlign(Paint.Align.CENTER);
        cv.drawText("观色", W - 132, 158, stamp);
        stamp.setTextAlign(Paint.Align.LEFT);

        block(cv, p, new RectF(64, 208, 584, 706), pick(cs, 0), -2.2f);
        block(cv, p, new RectF(620, 208, 1016, 452), pick(cs, 1), 2.4f);
        block(cv, p, new RectF(620, 482, 1016, 706), pick(cs, 2), -1.6f);
        block(cv, p, new RectF(64, 742, 1016, 966), pick(cs, 3), 0.8f);

        cv.drawText(meta(cs.length), 72, 1042, font(28, INK2, false));
        cv.drawText("混搭色块 · github.com/wwxiaoqi/guanshe", 72, 1096,
                font(24, INK2, false));
        cv.drawText("观色 · 离线配色手册", 72, 1280, font(26, INK2, false));
    }

    private static void block(Canvas cv, Paint p, RectF rect, int color, float deg) {
        cv.save();
        cv.rotate(deg, rect.centerX(), rect.centerY());
        p.setColor(color);
        cv.drawRoundRect(rect, 26f, 26f, p);
        Paint hex = font(28, inkOn(color), true);
        cv.drawText(ColorMath.hex(color), rect.left + 24, rect.bottom - 24, hex);
        cv.restore();
    }

    private static int pick(int[] cs, int i) {
        return cs[i % cs.length];
    }

    /** 暗场：黑底上四条长短不一的颜色布带，像挂在暗房里的色卡。 */
    private static void drawRibbonStyle(Canvas cv, Paint p, int[] cs, String name) {
        cv.drawColor(0xFF121110);
        float[] heights = {780f, 1000f, 860f, 930f};
        float barW = 206f;
        float gap = 42f;
        float x = 66f;
        for (int i = 0; i < 4; i++) {
            int c = pick(cs, i);
            float h = heights[i];
            p.setColor(c);
            cv.drawRoundRect(new RectF(x, -40f, x + barW, h), 20f, 20f, p);
            cv.drawText(ColorMath.hex(c), x, h + 46f, font(26, 0xCCFFFFFF, false));
            x += barW + gap;
        }

        Paint title = font(58, 0xFFFFFFFF, true);
        fit(title, name, W - 130f, 36f);
        cv.drawText(name, 66, 1160, title);
        cv.drawText(meta(cs.length), 66, 1222, font(26, 0x99FFFFFF, false));

        p.setColor(0x33FFFFFF);
        p.setStrokeWidth(2f);
        cv.drawLine(66, 1268, W - 66, 1268, p);
        cv.drawText("观色 · 离线配色手册", 66, 1318, font(24, 0x99FFFFFF, false));
        Paint gh = font(22, 0x66FFFFFF, false);
        gh.setTextAlign(Paint.Align.RIGHT);
        cv.drawText("github.com/wwxiaoqi/guanshe", W - 66, 1318, gh);
        gh.setTextAlign(Paint.Align.LEFT);
    }

    // ---------------------------------------------------------------- 单色卡

    public static Bitmap color(int color, String title) {
        Bitmap bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        cv.drawColor(PAPER);
        p.setColor(color);
        cv.drawRect(0, 0, W, 760, p);

        RectF strip = new RectF(0, 700, W, H + 48);
        p.setColor(PAPER);
        cv.drawRoundRect(strip, 40f, 40f, p);

        String name = title == null || title.trim().length() == 0
                ? ColorMath.hex(color) : title.trim();
        Paint titlePaint = font(64, INK, true);
        fit(titlePaint, name, W - 160f, 36f);
        cv.drawText(name, 80, 872, titlePaint);

        int[] rgb = ColorMath.rgb(color);
        float[] hsl = ColorMath.hsl(color);
        Paint sub = font(32, INK2, false);
        cv.drawText(ColorMath.hex(color) + "   RGB " + rgb[0] + ", " + rgb[1] + ", " + rgb[2],
                80, 940, sub);
        cv.drawText("HSL " + Math.round(hsl[0]) + "°, " + Math.round(hsl[1]) + "%, "
                + Math.round(hsl[2]) + "%", 80, 998, sub);

        double white = ColorMath.contrast(color, 0xFFFFFFFF);
        double black = ColorMath.contrast(color, INK);
        String contrast = "白字 " + one(white) + ":1 · 黑字 " + one(black) + ":1";
        cv.drawText(contrast, 80, 1072, font(28, INK2, false));

        p.setColor(HAIR);
        p.setStrokeWidth(2f);
        cv.drawLine(80, 1130, W - 80, 1130, p);

        cv.drawText("观色 · 离线配色手册", 80, 1204, font(26, INK2, false));
        cv.drawText("github.com/wwxiaoqi/guanshe", 80, 1262, font(24, INK2, false));

        RectF chip = new RectF(W - 200, 1152, W - 80, 1272);
        p.setColor(color);
        cv.drawRoundRect(chip, 24f, 24f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(HAIR);
        cv.drawRoundRect(chip, 24f, 24f, p);
        return bm;
    }

    // ---------------------------------------------------------------- 小工具

    private static String one(double v) {
        long x = Math.round(v * 10);
        return (x / 10) + "." + (x % 10);
    }

    private static String meta(int n) {
        return n + " 色 · 观色 · 离线配色手册";
    }

    private static int[] normalize(int[] in) {
        if (in == null || in.length == 0) return new int[]{0xFF17171A, 0xFFF6F4EF};
        if (in.length == 1) return new int[]{in[0], in[0]};
        return in;
    }

    private static Paint font(int size, int color, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setTextSize(size);
        p.setColor(color);
        p.setLetterSpacing(0.01f);
        return p;
    }

    /** 太长就缩小字号，保证不溢出画布。 */
    private static void fit(Paint p, String s, float maxWidth, float minSize) {
        float size = p.getTextSize();
        while (size > minSize && p.measureText(s) > maxWidth) {
            size -= 2f;
            p.setTextSize(size);
        }
    }

    private static int inkOn(int bg) {
        return ColorMath.contrast(bg, INK) >= ColorMath.contrast(bg, WHITE) ? INK : WHITE;
    }
}
