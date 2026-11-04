package com.wwxiaoqi.guanshe.ui;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowManager;

/**
 * 系统栏高度（像素）。
 *
 * 为什么不用 `Skin.padToBars`：它靠 `OnApplyWindowInsetsListener` 回调，而 tab 页是**后加**
 * 到容器里的 —— 回调在视图还没挂到窗口时就请求过了，等于没收到 insets，页面标题会直接压在状态栏上。
 * 这里改成主动读一次窗口度量，数值稳定、时序无关。
 */
public final class Bars {

    private Bars() {
    }

    public static int top(Activity a) {
        WindowInsets in = insets(a);
        if (in != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return in.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()).top;
            }
            return in.getSystemWindowInsetTop();
        }
        return dimen(a, "status_bar_height", 24);
    }

    public static int bottom(Activity a) {
        WindowInsets in = insets(a);
        if (in != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // 手势导航下某些 ROM 的 systemBars.bottom 是 0，但手势条仍要占位置；
                // 取 systemBars ∪ tappableElement 的并集，谁大听谁的。
                return in.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.tappableElement()).bottom;
            }
            return in.getSystemWindowInsetBottom();
        }
        return dimen(a, "navigation_bar_height", 48);
    }

    /** 先问 DecorView 的实时 insets，拿不到再退回窗口度量。 */
    private static WindowInsets insets(Activity a) {
        WindowInsets in = a.getWindow().getDecorView().getRootWindowInsets();
        if (in != null) return in;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return a.getWindowManager().getCurrentWindowMetrics().getWindowInsets();
        }
        return null;
    }

    private static int dimen(Context c, String name, int fallbackDp) {
        int id = c.getResources().getIdentifier(name, "dimen", "android");
        if (id > 0) {
            int px = c.getResources().getDimensionPixelSize(id);
            if (px > 0) return px;
        }
        return Math.round(fallbackDp * c.getResources().getDisplayMetrics().density);
    }

    /**
     * 按页面底色决定状态栏/导航栏图标明暗。
     *
     * **必须先 `getDecorView()`**：API 37（Android 16）上 `PhoneWindow.getInsetsController()` 在
     * DecorView 还是 null 时直接抛 NullPointerException —— 而 `Skin.systemBars()` 走的正是这条路径。
     * 老版本上它只是返回 null，所以这个坑在 API 30~34 上看不出来。
     */
    public static void icons(final Activity a, final int pageBg) {
        try {
            final View decor = a.getWindow().getDecorView();
            com.wwxiaoqi.guanshe.Skin.systemBars(a, pageBg);
            if (!decor.isAttachedToWindow()) {
                // 还没挂窗口时先记一次，挂上之后再补一次（那时图标设置才真正生效）
                decor.post(new Runnable() {
                    public void run() {
                        try {
                            com.wwxiaoqi.guanshe.Skin.systemBars(a, pageBg);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
            // 系统栏图标是"锦上添花"，任何机型差异都不该让页面起不来
        }
    }

    /** 兼容旧调用：有些地方只要 WindowManager 实例。 */
    @SuppressWarnings("unused")
    public static WindowManager wm(Activity a) {
        return a.getWindowManager();
    }
}
