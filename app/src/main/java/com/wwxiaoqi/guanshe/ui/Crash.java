package com.wwxiaoqi.guanshe.ui;

import android.content.Context;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 崩溃兜底：把上一次的堆栈存下来，在「我的 → 关于」里可以被复制出来。
 *
 * 这不是给用户看的功能，是给"闪退但说不清在哪"这种情况留的取证手段 ——
 * 容器里没有 adb，只能让 App 自己把现场留住。
 */
public final class Crash {

    private static final String SP = "guanshe";
    private static final String K = "lastcrash";

    private Crash() {
    }

    public static void install(final android.app.Application app) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    sw.append("\n—— 线程 ").append(t.getName()).append(" · ")
                            .append(android.os.Build.MANUFACTURER).append(' ')
                            .append(android.os.Build.MODEL).append(" · API ")
                            .append(String.valueOf(android.os.Build.VERSION.SDK_INT));
                    app.getSharedPreferences(SP, Context.MODE_PRIVATE)
                            .edit().putString(K, sw.toString()).commit();
                } catch (Throwable ignored) {
                    // 连记录都失败就放弃，别在崩溃路径上再抛一次
                }
                if (previous != null) previous.uncaughtException(t, e);
            }
        });
    }

    public static String last(Context c) {
        String s = c.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE)
                .getString(K, null);
        return s == null || s.length() == 0 ? null : s;
    }

    public static void clear(Context c) {
        c.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE)
                .edit().remove(K).apply();
    }
}
