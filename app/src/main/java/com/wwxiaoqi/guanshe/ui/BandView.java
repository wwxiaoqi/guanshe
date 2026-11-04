package com.wwxiaoqi.guanshe.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.LinearLayout;

/**
 * 4 段等高色带 —— 全 App 唯一的图形语言（docs/ui-plan.md §4）。
 *
 * 四种高度：196（今日配色）/ 104（瀑布卡）/ 34（回忆、相似配色）/ 264（详情页顶部）。
 * 圆角用 Path 裁一次再画 4 段（不是每段各裁一次，那样接缝会透底色）。
 */
public final class BandView extends View {

    private final int[] colors;
    private final float radiusDp;
    private final boolean roundTopOnly;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();

    private BandView(Context c, int[] colors, float radiusDp, boolean roundTopOnly) {
        super(c);
        this.colors = colors == null || colors.length == 0 ? new int[]{0xFFCCCCCC} : colors;
        this.radiusDp = radiusDp;
        this.roundTopOnly = roundTopOnly;
        paint.setStyle(Paint.Style.FILL);
    }

    /** 放进 LinearLayout 里用这个（宽 MATCH_PARENT、高固定）。 */
    public static BandView of(Context c, int[] colors, float heightDp, float radiusDp, boolean roundTopOnly) {
        BandView v = new BandView(c, colors, radiusDp, roundTopOnly);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(c, heightDp))));
        return v;
    }

    /** 放进 LinearLayout 里、宽高都要自己控制时用这个。 */
    public static BandView sized(Context c, int[] colors, int widthPx, int heightPx,
                                 float radiusDp, boolean roundTopOnly) {
        BandView v = new BandView(c, colors, radiusDp, roundTopOnly);
        v.setLayoutParams(new LinearLayout.LayoutParams(widthPx, heightPx));
        return v;
    }

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDraw(Canvas cv) {
        box.set(0, 0, getWidth(), getHeight());
        float r = dp(getContext(), radiusDp);
        clip.reset();
        if (roundTopOnly) {
            clip.addRoundRect(box, new float[]{r, r, r, r, 0, 0, 0, 0}, Path.Direction.CW);
        } else if (r > 0) {
            clip.addRoundRect(box, new float[]{r, r, r, r, r, r, r, r}, Path.Direction.CW);
        } else {
            clip.addRect(box, Path.Direction.CW);
        }
        cv.save();
        cv.clipPath(clip);
        float w = getWidth() / (float) colors.length;
        for (int i = 0; i < colors.length; i++) {
            paint.setColor(colors[i]);
            cv.drawRect(i * w, 0, (i + 1) * w, getHeight(), paint);
        }
        cv.restore();
    }
}
