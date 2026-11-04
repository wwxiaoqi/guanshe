package com.wwxiaoqi.guanshe.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import com.wwxiaoqi.guanshe.Skin;

/**
 * 行与图标按钮的唯一构造入口。
 *
 * 图标按钮统一 48dp 触达区（Material 最小触摸目标），带按压缩放、波纹和轻震。
 */
public final class Rows {

    private Rows() {
    }

    public static LinearLayout row(Context c, String label, String value, boolean chevron,
                                   View.OnClickListener l) {
        LinearLayout row = Skin.row(c, label, value, chevron, l);
        if (l != null) {
            Tap.row(row);
        }
        return row;
    }

    public static LinearLayout iconButton(Context c, int res, int color, View.OnClickListener l) {
        LinearLayout box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER);
        int s = Skin.dp(c, 48);
        box.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        box.addView(Skin.icon(c, res, color, 22f));
        Tap.chip(box);
        if (l != null) box.setOnClickListener(l);
        return box;
    }
}
