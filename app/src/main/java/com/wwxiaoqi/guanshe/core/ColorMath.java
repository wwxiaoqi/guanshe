package com.wwxiaoqi.guanshe.core;

/**
 * 颜色换算。全 App 只有这一份实现：页面上看到的 HEX / RGB / HSL / CMYK / ΔE 都从 int 现算，
 * 不冗余存储，避免三处表示互相矛盾。
 *
 * 纯 Java，无 android 依赖 —— 可以用 tools/SelfTest.java 在桌面上直接断言。
 */
public final class ColorMath {

    private ColorMath() {
    }

    /** 解析 "#F76E11" / "F76E11" / " #f76e11 "，返回 0xFFRRGGBB。非法输入抛 IllegalArgumentException。 */
    public static int parse(String s) {
        if (s == null) throw new IllegalArgumentException("颜色为空");
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 8) t = t.substring(2);          // 容忍带 alpha 的 AARRGGBB
        if (t.length() != 6) throw new IllegalArgumentException("不是 6 位色值：" + s);
        int v = 0;
        for (int i = 0; i < 6; i++) {
            int d = Character.digit(t.charAt(i), 16);
            if (d < 0) throw new IllegalArgumentException("含非十六进制字符：" + s);
            v = (v << 4) | d;
        }
        return 0xFF000000 | v;
    }

    /** 0xFFF76E11 → "#F76E11"（大写）。 */
    public static String hex(int c) {
        return String.format("#%02X%02X%02X", (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
    }

    /** 一组色 → "#A,#B,#C"（逗号分隔、无空格、大写）—— 复制到剪贴板用的就是这个形状。 */
    public static String hexList(int[] cs) {
        StringBuilder sb = new StringBuilder(cs.length * 8);
        for (int i = 0; i < cs.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(hex(cs[i]));
        }
        return sb.toString();
    }

    public static int[] rgb(int c) {
        return new int[]{(c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF};
    }

    public static int argb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** {h 0-360, s 0-100, l 0-100}；灰色 s=0、h=0。 */
    public static float[] hsl(int c) {
        float r = ((c >> 16) & 0xFF) / 255f;
        float g = ((c >> 8) & 0xFF) / 255f;
        float b = (c & 0xFF) / 255f;
        float mx = Math.max(r, Math.max(g, b));
        float mn = Math.min(r, Math.min(g, b));
        float l = (mx + mn) / 2f;
        float h = 0f, s = 0f;
        if (mx != mn) {
            float d = mx - mn;
            s = l > 0.5f ? d / (2f - mx - mn) : d / (mx + mn);
            if (mx == r) h = (g - b) / d + (g < b ? 6f : 0f);
            else if (mx == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
        }
        return new float[]{h, s * 100f, l * 100f};
    }

    /** {h 0-360, s 0-100, l 0-100} → 0xFFRRGGBB。取色模块补足明度阶梯时要用。 */
    public static int fromHsl(float h, float s, float l) {
        float hh = ((h % 360f) + 360f) % 360f / 360f;
        float ss = Math.max(0f, Math.min(100f, s)) / 100f;
        float ll = Math.max(0f, Math.min(100f, l)) / 100f;
        if (ss == 0f) {
            int v = Math.round(ll * 255f);
            return argb(v, v, v);
        }
        float q = ll < 0.5f ? ll * (1 + ss) : ll + ss - ll * ss;
        float p = 2 * ll - q;
        return argb(Math.round(hue(p, q, hh + 1f / 3f) * 255f),
                Math.round(hue(p, q, hh) * 255f),
                Math.round(hue(p, q, hh - 1f / 3f) * 255f));
    }

    private static float hue(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }

    /** {c,m,y,k} 整数 0-100（四舍五入）。 */
    public static int[] cmyk(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        if (r == 0 && g == 0 && b == 0) return new int[]{0, 0, 0, 100};
        int k = 255 - Math.max(r, Math.max(g, b));
        float d = 255 - k;
        return new int[]{
                Math.round((255 - r - k) / d * 100f),
                Math.round((255 - g - k) / d * 100f),
                Math.round((255 - b - k) / d * 100f),
                Math.round(k / 255f * 100f)};
    }

    /** WCAG 相对亮度 0-1。 */
    public static double luma(int c) {
        double r = lin(((c >> 16) & 0xFF) / 255.0);
        double g = lin(((c >> 8) & 0xFF) / 255.0);
        double b = lin((c & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double lin(double v) {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** WCAG 对比度（1-21）。 */
    public static double contrast(int a, int b) {
        double la = luma(a), lb = luma(b);
        double hi = Math.max(la, lb), lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    /** CIE L*a*b*（D65）。 */
    public static double[] lab(int c) {
        double r = lin(((c >> 16) & 0xFF) / 255.0);
        double g = lin(((c >> 8) & 0xFF) / 255.0);
        double b = lin((c & 0xFF) / 255.0);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = (0.2126 * r + 0.7152 * g + 0.0722 * b);
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (7.787 * t + 16.0 / 116.0);
    }

    /** CIEDE2000 色差（0 = 同色）。 */
    public static double deltaE2000(int c1, int c2) {
        double[] l1 = lab(c1), l2 = lab(c2);
        double L1 = l1[0], a1 = l1[1], b1 = l1[2];
        double L2 = l2[0], a2 = l2[1], b2 = l2[2];
        double C1 = Math.hypot(a1, b1), C2 = Math.hypot(a2, b2);
        double Cb = (C1 + C2) / 2.0;
        double Cb7 = Math.pow(Cb, 7);
        double G = 0.5 * (1 - Math.sqrt(Cb7 / (Cb7 + 6103515625.0)));   // 25^7
        double a1p = (1 + G) * a1, a2p = (1 + G) * a2;
        double C1p = Math.hypot(a1p, b1), C2p = Math.hypot(a2p, b2);
        double h1p = C1p == 0 ? 0 : Math.toDegrees(Math.atan2(b1, a1p)) % 360;
        double h2p = C2p == 0 ? 0 : Math.toDegrees(Math.atan2(b2, a2p)) % 360;
        if (h1p < 0) h1p += 360;
        if (h2p < 0) h2p += 360;

        double dL = L2 - L1, dC = C2p - C1p, dh;
        if (C1p * C2p == 0) {
            dh = 0;
        } else {
            dh = h2p - h1p;
            if (dh > 180) dh -= 360;
            else if (dh < -180) dh += 360;
        }
        double dH = 2 * Math.sqrt(C1p * C2p) * Math.sin(Math.toRadians(dh / 2));

        double Lb = (L1 + L2) / 2, Cbp = (C1p + C2p) / 2, hb;
        if (C1p * C2p == 0) {
            hb = h1p + h2p;
        } else if (Math.abs(h1p - h2p) <= 180) {
            hb = (h1p + h2p) / 2;
        } else if (h1p + h2p < 360) {
            hb = (h1p + h2p + 360) / 2;
        } else {
            hb = (h1p + h2p - 360) / 2;
        }
        double T = 1 - 0.17 * Math.cos(Math.toRadians(hb - 30))
                + 0.24 * Math.cos(Math.toRadians(2 * hb))
                + 0.32 * Math.cos(Math.toRadians(3 * hb + 6))
                - 0.20 * Math.cos(Math.toRadians(4 * hb - 63));
        double dt = 30 * Math.exp(-Math.pow((hb - 275) / 25, 2));
        double Cbp7 = Math.pow(Cbp, 7);
        double Rc = 2 * Math.sqrt(Cbp7 / (Cbp7 + 6103515625.0));
        double SL = 1 + (0.015 * Math.pow(Lb - 50, 2)) / Math.sqrt(20 + Math.pow(Lb - 50, 2));
        double SC = 1 + 0.045 * Cbp;
        double SH = 1 + 0.015 * Cbp * T;
        double RT = -Math.sin(Math.toRadians(2 * dt)) * Rc;
        return Math.sqrt(Math.pow(dL / SL, 2) + Math.pow(dC / SC, 2) + Math.pow(dH / SH, 2)
                + RT * (dC / SC) * (dH / SH));
    }

    /** 色相大类：0 红 / 1 橙 / 2 黄 / 3 绿 / 4 青 / 5 蓝 / 6 紫 / 7 无彩色。色卡网格的筛选用。 */
    public static int hueClass(int c) {
        float[] h = hsl(c);
        if (h[1] < 8f || h[2] < 6f || h[2] > 96f) return 7;
        float d = h[0];
        if (d < 15f || d >= 345f) return 0;
        if (d < 45f) return 1;
        if (d < 70f) return 2;
        if (d < 160f) return 3;
        if (d < 195f) return 4;
        if (d < 265f) return 5;
        return 6;
    }

    /** 在 pool 里找离 target 最近的 limit 个，返回下标，按 ΔE 升序。 */
    public static int[] nearest(int target, int[] pool, int limit) {
        int n = Math.min(limit, pool.length);
        double[] d = new double[pool.length];
        Integer[] idx = new Integer[pool.length];
        for (int i = 0; i < pool.length; i++) {
            d[i] = deltaE2000(target, pool[i]);
            idx[i] = i;
        }
        final double[] dd = d;
        java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                int c = Double.compare(dd[a], dd[b]);
                return c != 0 ? c : a.compareTo(b);      // 同 ΔE 时按下标，保证稳定
            }
        });
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = idx[i];
        return out;
    }
}
