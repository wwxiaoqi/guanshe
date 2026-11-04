package com.wwxiaoqi.guanshe.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import com.wwxiaoqi.guanshe.Skin;

/**
 * 色票：单个颜色的方块。圆角 ≤ 4dp（色卡是纸，不是按钮），带 1px 外框
 * —— 不然 `#F5F5F5` 这种白卡上的白色会融进背景。
 */
public final class SwatchView {

    private SwatchView() {
    }

    public static View of(Context c, int color, int sizeDp, float radiusDp) {
        View v = new View(c);
        v.setBackground(Skin.stroke(c, color, Skin.c(c, com.wwxiaoqi.guanshe.R.color.cardStroke),
                radiusDp, 0.8f));
        v.setLayoutParams(new LinearLayout.LayoutParams(Skin.dp(c, sizeDp), Skin.dp(c, sizeDp)));
        return v;
    }

    /** 只要背景（自己控制宽高的位置用，比如网格单元）。 */
    public static void apply(View v, Context c, int color, float radiusDp) {
        v.setBackground(Skin.stroke(c, color, Skin.c(c, com.wwxiaoqi.guanshe.R.color.cardStroke),
                radiusDp, 0.8f));
    }
}
