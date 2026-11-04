package com.wwxiaoqi.guanshe.data;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;

/**
 * 全局唯一的数据入口（Android 侧）。桌面测试不走这里，直接 {@link DataStore#load}。
 */
public final class Store {

    private static DataStore store;

    private Store() {
    }

    public static synchronized DataStore get(Context c) {
        if (store == null) {
            final android.content.res.AssetManager am = c.getApplicationContext().getAssets();
            DataStore.Source src = new DataStore.Source() {
                public InputStream open(String name) throws IOException {
                    return am.open("data/" + name);
                }
            };
            try {
                store = DataStore.load(src);
            } catch (IOException e) {
                throw new IllegalStateException("配色数据读不出来：" + e.getMessage(), e);
            }
        }
        return store;
    }

    /** 已经在后台线程预热过就不再等（见 MainActivity 的冷启动）。 */
    public static boolean ready() {
        return store != null;
    }
}
