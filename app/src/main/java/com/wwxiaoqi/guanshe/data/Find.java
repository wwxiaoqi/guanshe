package com.wwxiaoqi.guanshe.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索里的"跳转类"结果：来源、色系、场景。
 *
 * 色号/传统色是"点开看一个颜色"，这几类是"点开去一个地方"——分开放，搜索页才好分组。
 * 纯 Java，不碰 android，桌面上能直接断言。
 */
public final class Find {

    public final List<Sources.Brand> brands = new ArrayList<Sources.Brand>();
    public final List<Integer> families = new ArrayList<Integer>();
    public final List<Integer> scenes = new ArrayList<Integer>();
    public final List<Integer> namedSets = new ArrayList<Integer>();

    private Find() {
    }

    public boolean any() {
        return !brands.isEmpty() || !families.isEmpty() || !scenes.isEmpty() || !namedSets.isEmpty();
    }

    public static Find look(DataStore s, String raw) {
        Find f = new Find();
        String q = raw == null ? "" : raw.trim();
        if (q.length() == 0) return f;
        String low = q.toLowerCase();

        for (Sources.Brand b : Sources.BRANDS) {
            String title = b.title.toLowerCase();
            // 标题命中排前面："NCS" 先给 NCS，再给名字里也含 ncs 的 CNCSCOLOR
            if (title.startsWith(low)) {
                f.brands.add(b);
                continue;
            }
            boolean hit = title.contains(low);
            // 单字不去匹配套名：多乐士「红（RR）」这种套名会让搜"红"的人先看到整张色卡
            if (!hit && q.length() >= 2) {
                for (DataStore.Meta m : Sources.setsOf(s, b)) {
                    if (m.name.toLowerCase().contains(low)) {
                        hit = true;
                        break;
                    }
                }
            }
            if (hit) f.brands.add(b);
        }

        for (DataStore.Meta m : s.metas()) {
            if ("family".equals(m.kind)) {
                if (m.name.contains(q)) f.families.add(m.id);
            } else if ("scene".equals(m.kind)) {
                if (m.name.contains(q)) f.scenes.add(m.id);
            } else if ("namedset".equals(m.kind)) {
                if (m.name.contains(q)) f.namedSets.add(m.id);
            }
        }
        return f;
    }
}
