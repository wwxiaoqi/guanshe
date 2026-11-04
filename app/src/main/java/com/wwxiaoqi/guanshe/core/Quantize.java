package com.wwxiaoqi.guanshe.core;

/**
 * 从图片像素里取代表色（中位切分 / median cut）。
 *
 * 两条硬要求（见 specs/image-pick）：
 * 1. 同一张图两次结果必须完全一致 —— 所以全程没有随机数，切分用稳定的计数排序；
 * 2. 任意两色 ΔE ≥ 3 —— 所以最后一步要去重并用明度阶梯补足。
 *
 * 纯 Java，入参是 int[] 像素，不碰 Bitmap / Context，可以在桌面直接断言。
 */
public final class Quantize {

    private static final double MIN_DELTA_E = 3.0;

    private Quantize() {
    }

    public static int[] medianCut(int[] pixels, int k) {
        if (k < 1) k = 1;
        // 1) 丢掉半透明以下
        int[] buf = new int[Math.max(1, pixels.length)];
        int n = 0;
        for (int p : pixels) {
            if (((p >>> 24) & 0xFF) >= 128) buf[n++] = p;
        }
        if (n == 0) return finish(new int[]{0xFF808080}, k);
        int[] px = new int[n];
        System.arraycopy(buf, 0, px, 0, n);

        // 2) 反复把"最胖"的桶从中间劈开（区间表示，不复制像素）
        int[] lo = new int[Math.max(1, k)];
        int[] hi = new int[Math.max(1, k)];
        int boxes = 1;
        lo[0] = 0;
        hi[0] = n;
        while (boxes < k) {
            int pick = -1, bestRange = 0, bestShift = 16;
            for (int i = 0; i < boxes; i++) {
                if (hi[i] - lo[i] < 2) continue;
                for (int shift = 16; shift >= 0; shift -= 8) {
                    int[] mm = minMax(px, lo[i], hi[i], shift);
                    int r = mm[1] - mm[0];
                    if (r > bestRange) {
                        bestRange = r;
                        pick = i;
                        bestShift = shift;
                    }
                }
            }
            if (pick < 0 || bestRange == 0) break;                   // 图太单一，劈不动
            sortRange(px, lo[pick], hi[pick], bestShift);
            int mid = (lo[pick] + hi[pick]) / 2;
            lo[boxes] = mid;
            hi[boxes] = hi[pick];
            hi[pick] = mid;
            boxes++;
        }

        // 3) 每桶取均值
        int[] colors = new int[boxes];
        for (int i = 0; i < boxes; i++) colors[i] = average(px, lo[i], hi[i]);
        return finish(colors, k);
    }

    // ---------------------------------------------------------------- 内部
    private static int[] minMax(int[] a, int lo, int hi, int shift) {
        int mn = 255, mx = 0;
        for (int i = lo; i < hi; i++) {
            int v = (a[i] >> shift) & 0xFF;
            if (v < mn) mn = v;
            if (v > mx) mx = v;
        }
        return new int[]{mn, mx};
    }

    /** 按某个通道做 32 桶计数排序（稳定、确定、O(n)）。 */
    private static void sortRange(int[] a, int lo, int hi, int shift) {
        int[] cnt = new int[32];
        for (int i = lo; i < hi; i++) cnt[((a[i] >> shift) & 0xFF) >> 3]++;
        int[] pos = new int[33];
        int acc = 0;                                  // tmp 是 0 基，这里不能用绝对下标
        for (int b = 0; b < 32; b++) {
            pos[b] = acc;
            acc += cnt[b];
        }
        int[] tmp = new int[hi - lo];
        for (int i = lo; i < hi; i++) tmp[pos[((a[i] >> shift) & 0xFF) >> 3]++] = a[i];
        System.arraycopy(tmp, 0, a, lo, tmp.length);
    }

    private static int average(int[] a, int lo, int hi) {
        long r = 0, g = 0, b = 0;
        for (int i = lo; i < hi; i++) {
            r += (a[i] >> 16) & 0xFF;
            g += (a[i] >> 8) & 0xFF;
            b += a[i] & 0xFF;
        }
        int cnt = Math.max(1, hi - lo);
        return ColorMath.argb((int) (r / cnt), (int) (g / cnt), (int) (b / cnt));
    }

    /** 去重 + 补足到 k 个 + 按明度升序。 */
    private static int[] finish(int[] colors, int k) {
        java.util.ArrayList<Integer> keep = new java.util.ArrayList<>();
        for (int c : colors) {
            boolean dup = false;
            for (int i = 0; i < keep.size(); i++) {
                if (ColorMath.deltaE2000(keep.get(i), c) < MIN_DELTA_E) {
                    dup = true;
                    break;
                }
            }
            if (!dup) keep.add(c);
        }
        int guard = 0;
        while (keep.size() < k && guard < k * 24) {
            int base = colors[guard % colors.length];
            float[] hsl = ColorMath.hsl(base);
            float step = 0.15f * (1 + guard / colors.length);
            float l = hsl[2] / 100f + ((guard / colors.length) % 2 == 0 ? step : -step);
            if (l > 0.96f) l = 0.96f - step;
            if (l < 0.04f) l = 0.04f + step;
            int cand = ColorMath.fromHsl(hsl[0], hsl[1], l * 100f);
            boolean dup = false;
            for (int i = 0; i < keep.size(); i++) {
                if (ColorMath.deltaE2000(keep.get(i), cand) < MIN_DELTA_E) {
                    dup = true;
                    break;
                }
            }
            if (!dup) keep.add(cand);
            guard++;
        }
        int[] out = new int[keep.size()];
        for (int i = 0; i < out.length; i++) out[i] = keep.get(i);
        // 明度升序（k ≤ 4，插入排序就够）
        for (int i = 1; i < out.length; i++) {
            int v = out[i], j = i - 1;
            while (j >= 0 && ColorMath.luma(out[j]) > ColorMath.luma(v)) {
                out[j + 1] = out[j];
                j--;
            }
            out[j + 1] = v;
        }
        return out;
    }
}
