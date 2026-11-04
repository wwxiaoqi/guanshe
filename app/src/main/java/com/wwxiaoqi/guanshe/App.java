package com.wwxiaoqi.guanshe;

import android.app.Application;

import com.wwxiaoqi.guanshe.ui.Crash;

/** 应用入口：装崩溃兜底（见 ui/Crash）。 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Crash.install(this);
    }
}
