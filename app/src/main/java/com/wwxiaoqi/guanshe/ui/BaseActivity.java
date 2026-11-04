package com.wwxiaoqi.guanshe.ui;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;

import com.wwxiaoqi.guanshe.Skin;

/**
 * 所有 Activity 的基类：
 * 1. 把「外观」偏好翻译成 uiMode（手动浅色 / 深色时覆盖 Configuration）；
 * 2. 铺边到边窗口（Skin.setupWindow）；
 * 3. 提供两个开页面的入口 —— 普通淡入，或"从被点的卡片里长出来"。
 *
 * 页面之间的转场动画由主题里的 windowAnimationStyle 统一控制（res/anim/act_*）。
 */
public abstract class BaseActivity extends Activity {

    public static final String APPEAR_SYSTEM = "system";
    public static final String APPEAR_LIGHT = "light";
    public static final String APPEAR_DARK = "dark";

    @Override
    protected void attachBaseContext(Context base) {
        String mode = new FavStore(base).appearance();
        if (APPEAR_SYSTEM.equals(mode)) {
            super.attachBaseContext(base);
            return;
        }
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        int night = APPEAR_DARK.equals(mode) ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Skin.setupWindow(this);          // setContentView 之前，只做窗口级
    }

    /** 打开新页面：主题动画负责淡入 + 升起。 */
    public void open(Intent intent) {
        startActivity(intent);
    }

    /**
     * 从某个控件的位置"放大"打开详情 —— 用户能感到"我点的这个色带变成了那一页"。
     * 控件还没测量（宽高为 0）时退回普通打开。
     */
    public void openFrom(Intent intent, View origin) {
        if (origin == null || origin.getWidth() <= 0 || origin.getHeight() <= 0) {
            startActivity(intent);
            return;
        }
        try {
            Bundle options = ActivityOptions.makeScaleUpAnimation(origin, 0, 0,
                    origin.getWidth(), origin.getHeight()).toBundle();
            startActivity(intent, options);
        } catch (Throwable t) {
            startActivity(intent);
        }
    }

    /** 返回上一页；转场同样由主题动画负责。 */
    public void close() {
        finish();
    }

    /**
     * 把手势导航条的高度实时补到某个 View 的底部内边距里。
     *
     * 为什么不在 build 时只算一次：Android 15+ 全面 edge-to-edge 后，insets 在
     * setContentView 之前经常拿不到（会退化成 0），底部按钮就会被手势条压住。
     * 这里挂 OnApplyWindowInsetsListener，等系统真正分发时再算。
     *
     * @param target 需要补底部的 View（通常是详情页的白色 sheet）
     * @param baseDp 设计稿里的基础底部留白
     */
    protected void padBottomToBars(final View target, final int baseDp) {
        if (target == null) return;
        final int left = target.getPaddingLeft();
        final int top = target.getPaddingTop();
        final int right = target.getPaddingRight();
        final int base = Skin.dp(this, baseDp);
        View content = findViewById(android.R.id.content);
        if (content == null) {
            target.setPadding(left, top, right, base + Bars.bottom(this));
            return;
        }
        content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                int bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets bars = in.getInsets(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.displayCutout());
                    bottom = Math.max(bars.bottom,
                            in.getInsets(WindowInsets.Type.tappableElement()).bottom);
                } else {
                    bottom = in.getSystemWindowInsetBottom();
                }
                target.setPadding(left, top, right, base + bottom);
                return in;
            }
        });
        content.requestApplyInsets();
    }
}
