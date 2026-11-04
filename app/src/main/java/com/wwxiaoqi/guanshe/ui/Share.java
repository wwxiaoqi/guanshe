package com.wwxiaoqi.guanshe.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import com.wwxiaoqi.guanshe.core.ColorMath;

import java.io.File;
import java.io.FileOutputStream;

/** 分享：把卡片图存进 cache，用 ShareProvider 交给系统分享面板。 */
public final class Share {

    private static final int KEEP_FILES = 12;

    private Share() {
    }

    public static void image(Activity a, Bitmap bm, String text) {
        if (a == null || bm == null) return;
        File f = write(a, bm);
        if (f == null) {
            text(a, text);
            return;
        }
        try {
            Uri uri = new Uri.Builder().scheme("content")
                    .authority(ShareProvider.AUTHORITY)
                    .appendPath(f.getName())
                    .build();
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/png");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            if (text != null && text.length() > 0) i.putExtra(Intent.EXTRA_TEXT, text);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, "分享这张卡片"));
        } catch (Throwable t) {
            text(a, text);
        }
    }

    public static void text(Activity a, String text) {
        if (a == null || text == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            a.startActivity(Intent.createChooser(i, "分享"));
        } catch (Throwable ignored) {
            // 系统没有分享面板：什么都不做，别把页面搞崩
        }
    }

    /** 一组配色的分享文案。 */
    public static String paletteText(int[] colors, String label) {
        StringBuilder sb = new StringBuilder("观色");
        if (label != null && label.length() > 0) sb.append(" · ").append(label);
        sb.append('\n');
        if (colors != null) {
            for (int i = 0; i < colors.length; i++) {
                if (i > 0) sb.append(' ');
                sb.append(ColorMath.hex(colors[i]));
            }
            sb.append('\n');
        }
        return sb.append("github.com/wwxiaoqi/guanshe").toString();
    }

    /** 单个颜色的分享文案。 */
    public static String colorText(int color, String title) {
        int[] rgb = ColorMath.rgb(color);
        float[] hsl = ColorMath.hsl(color);
        StringBuilder sb = new StringBuilder("观色");
        if (title != null && title.length() > 0) sb.append(" · ").append(title);
        sb.append('\n').append(ColorMath.hex(color));
        sb.append("  RGB ").append(rgb[0]).append(", ").append(rgb[1]).append(", ").append(rgb[2]);
        sb.append('\n').append("HSL ").append(Math.round(hsl[0])).append("°, ")
                .append(Math.round(hsl[1])).append("%, ").append(Math.round(hsl[2])).append('%');
        return sb.append('\n').append("github.com/wwxiaoqi/guanshe").toString();
    }

    private static File write(Activity a, Bitmap bm) {
        FileOutputStream out = null;
        try {
            File dir = new File(a.getCacheDir(), "share");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File f = new File(dir, "guanshe_" + System.currentTimeMillis() + ".png");
            out = new FileOutputStream(f);
            bm.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            cleanup(dir);
            return f;
        } catch (Throwable t) {
            return null;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 只保留最近几张，别让 cache 越攒越大。 */
    private static void cleanup(File dir) {
        try {
            File[] files = dir.listFiles();
            if (files == null || files.length <= KEEP_FILES) return;
            java.util.Arrays.sort(files, new java.util.Comparator<File>() {
                public int compare(File x, File y) {
                    return Long.compare(x.lastModified(), y.lastModified());
                }
            });
            for (int i = 0; i < files.length - KEEP_FILES; i++) {
                //noinspection ResultOfMethodCallIgnored
                files[i].delete();
            }
        } catch (Throwable ignored) {
        }
    }
}
