package com.wwxiaoqi.guanshe.ui;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * 触感反馈。
 *
 * 这一版收紧成白名单：只有四个动作会震 ——
 *   1. 切换底部 tab；
 *   2. 随手翻「换一批」；
 *   3. 下拉准备变成分享卡；
 *   4. 点分享卡。
 *
 * 其余所有按钮（chip、色卡网格、复制、收藏、搜索……）都保持安静。
 * 旧的 tap / select / longPress / success 保留方法签名，但不再触发震动，
 * 这样调用点不用一个个删，也不会漏掉某处。
 */
public final class Haptics {

    private Haptics() {
    }

    // ---------------------------------------------------------------- 白名单

    /** 1. 底部 tab 切换。 */
    public static void tabSwitch(View v) {
        perform(v, Build.VERSION.SDK_INT >= 23
                ? HapticFeedbackConstants.CONTEXT_CLICK : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** 2. 随手翻「换一批」。 */
    public static void shuffle(View v) {
        perform(v, Build.VERSION.SDK_INT >= 21
                ? HapticFeedbackConstants.CLOCK_TICK : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** 3. 下拉到阈值、白纸开始往下让位时。 */
    public static void shareOpen(View v) {
        perform(v, Build.VERSION.SDK_INT >= 23
                ? HapticFeedbackConstants.CONTEXT_CLICK : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** 5. 分享卡翻过一格（刻度盘滚过一个刻度）。 */
    public static void shareTick(View v) {
        if (v == null) return;
        try {
            int tick = Build.VERSION.SDK_INT >= 21
                    ? HapticFeedbackConstants.CLOCK_TICK : HapticFeedbackConstants.VIRTUAL_KEY;
            // 有些 ROM 对 CLOCK_TICK 返回 false（没执行），退化为普通的虚拟键反馈
            if (!v.performHapticFeedback(tick)) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        } catch (Throwable ignored) {
            // 没有马达的设备不该崩
        }
    }

    /** 4. 点中卡片、准备生成分享图时。 */
    public static void shareTap(View v) {
        perform(v, Build.VERSION.SDK_INT >= 21
                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ---------------------------------------------------------------- 静默的旧接口
    // 这些调用点很多（Tap.chip / Motion.interact / Snack / 各页面），全部静默。

    public static void tap(View v) {
    }

    public static void select(View v) {
    }

    public static void longPress(View v) {
    }

    public static void success(View v) {
    }

    private static void perform(View v, int constant) {
        if (v == null) return;
        try {
            v.performHapticFeedback(constant);
        } catch (Throwable ignored) {
            // 没有震动马达的设备（模拟器 / 部分平板）不该崩
        }
    }
}
