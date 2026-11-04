package com.wwxiaoqi.guanshe.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;

import com.wwxiaoqi.guanshe.Skin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 用代码画出来的赞助二维码。
 *
 * 矩阵来源：把用户给的收款码解码一次，按同样的内容重新生成矩阵（assets/data/sponsor_qr.txt），
 * 于是这枚二维码可以用当前配色重新上色 —— 每次打开颜色都不一样，但扫出来还是同一个收款码。
 * 模块之间留 3 格静区，保证微信 / 支付宝能正常识别。
 */
public final class QrView extends View {

    private static final float QUIET = 4f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean[][] modules;
    private int n;
    private int fg = 0xFF17171A;
    private int bg = 0xFFFFFFFF;

    private QrView(Context c) {
        super(c);
        load(c);
        setContentDescription("赞助二维码");
    }

    /** 216dp 的二维码；调用方自己 add 到卡片里。 */
    public static QrView of(Context c) {
        QrView v = new QrView(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                Skin.dp(c, 216), Skin.dp(c, 216)));
        return v;
    }

    /**
     * 从一组配色里挑两色：模块色保证够深（可扫），纸底色保证够浅。
     * 调用方每次打开传不同配色，就得到"每次都不同"的二维码。
     */
    public QrView colorFrom(int colorA, int colorB) {
        int ink = contrastFill(colorA);
        int paper = paperTint(colorB);
        return colors(ink, paper);
    }

    public QrView colors(int fg, int bg) {
        this.fg = fg;
        this.bg = bg;
        invalidate();
        return this;
    }

    /** 太亮的颜色往黑里压，保证二维码可扫。 */
    public static int contrastFill(int color) {
        double lum = luminance(color);
        if (lum > 0.42) {
            return blend(color, 0xFF000000, 0.62f);
        }
        if (lum > 0.28) {
            return blend(color, 0xFF000000, 0.35f);
        }
        return color;
    }

    /** 纸底：白色里掺一点配色，仍然够浅。 */
    public static int paperTint(int color) {
        return blend(0xFFFDFCFA, color, 0.10f);
    }

    private static double luminance(int c) {
        return 0.2126 * lin(Color.red(c)) + 0.7152 * lin(Color.green(c))
                + 0.0722 * lin(Color.blue(c));
    }

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static int blend(int from, int to, float t) {
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.rgb(r, g, b);
    }

    private void load(Context c) {
        try {
            InputStream in = c.getAssets().open("data/sponsor_qr.txt");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            List<String> rows = new ArrayList<String>();
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#') continue;
                rows.add(line);
            }
            r.close();
            n = rows.size();
            modules = new boolean[n][];
            for (int y = 0; y < n; y++) {
                String s = rows.get(y);
                modules[y] = new boolean[n];
                for (int x = 0; x < n; x++) {
                    modules[y][x] = s.charAt(x) == '1';
                }
            }
        } catch (Throwable t) {
            modules = null;
            n = 0;
        }
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (modules == null || n <= 0) return;
        float size = Math.min(getWidth(), getHeight());
        float cell = size / (n + QUIET * 2f);
        float quiet = QUIET * cell;
        float overlap = Math.max(0.6f, cell * 0.03f);

        paint.setAntiAlias(false);
        paint.setColor(bg);
        cv.drawRect(0, 0, getWidth(), getHeight(), paint);

        // 实心模块之间留 3% 重叠，避免缩放后出现白色细缝 —— 有缝的二维码扫不出来（实测）
        paint.setColor(fg);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (!modules[y][x]) continue;
                float left = quiet + x * cell - overlap / 2f;
                float top = quiet + y * cell - overlap / 2f;
                cv.drawRect(left, top, left + cell + overlap, top + cell + overlap, paint);
            }
        }
        paint.setAntiAlias(true);
    }
}
