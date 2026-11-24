package com.wwxiaoqi.guanshe.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 色卡来源：把 meta.tsv 里的套按品牌归拢，界面只认这里给出的顺序和标题。
 *
 * 数据约定：pantset 的名字以品牌前缀开头（CBCC / 立邦 / 多乐士 / NCS / CNCSCOLOR / DIC /
 * TOYO / HKS），其余 pantset 一律算潘通；ralset 都算劳尔；namedset 各自是一张直达卡。
 * 以后加新色卡体系，只要在 meta.tsv 里按前缀命名，再往 {@link #BRANDS} 加一行即可，
 * 色卡页和套列表都不用改结构。
 */
public final class Sources {

    /** 一个来源（品牌/体系）。 */
    public static final class Brand {
        public final String key;      // 路由用的稳定名字
        public final String title;    // 界面标题
        public final String prefix;   // meta 名字前缀；null 表示兜底（潘通）或按 kind 归类（劳尔）
        public final String kind;     // pantset / ralset

        Brand(String key, String title, String prefix, String kind) {
            this.key = key;
            this.title = title;
            this.prefix = prefix;
            this.kind = kind;
        }
    }

    /** 展示顺序：印刷/色彩体系 → 建筑与墙面漆 →（传统色由色卡页追加）。 */
    public static final Brand[] BRANDS = {
            new Brand("pantone", "潘通色卡", null, "pantset"),
            new Brand("ral", "劳尔色卡", null, "ralset"),
            new Brand("ncs", "NCS 颜色系统", "NCS", "pantset"),
            new Brand("munsell", "Munsell 孟塞尔", "Munsell", "pantset"),
            new Brand("dic", "DIC 色卡", "DIC", "pantset"),
            new Brand("toyo", "TOYO INK 东洋油墨", "TOYO", "pantset"),
            new Brand("hks", "HKS 色卡", "HKS", "pantset"),
            new Brand("cncs", "CNCSCOLOR 色彩体系", "CNCSCOLOR", "pantset"),
            new Brand("cbcc", "CBCC 中国建筑", "CBCC", "pantset"),
            new Brand("niban", "立邦墙面漆", "立邦", "pantset"),
            new Brand("dulux", "多乐士墙面漆", "多乐士", "pantset"),
            new Brand("skshu", "三棵树墙面漆", "三棵树", "pantset"),
            new Brand("carpoly", "嘉宝莉墙面漆", "嘉宝莉", "pantset"),
            new Brand("dufa", "都芳墙面漆", "都芳", "pantset"),
            new Brand("tikkurila", "芬琳墙面漆", "芬琳", "pantset"),
            new Brand("gb", "国标漆膜（GB/T 3181）", "国标", "pantset"),
    };

    private Sources() {
    }

    public static Brand byKey(String key) {
        if (key == null) return null;
        for (Brand b : BRANDS) {
            if (b.key.equals(key)) return b;
        }
        return null;
    }

    /** 某个 meta 归属的品牌；传统色不属于任何品牌，返回 null（它们各自成卡）。 */
    public static Brand of(DataStore.Meta m) {
        if (m == null) return null;
        if ("ralset".equals(m.kind)) return byKey("ral");
        if (!"pantset".equals(m.kind)) return null;
        for (Brand b : BRANDS) {
            if (b.prefix != null && m.name.startsWith(b.prefix)) return b;
        }
        return byKey("pantone");
    }

    /** 某品牌下的全部套（保持 meta 顺序）。 */
    public static List<DataStore.Meta> setsOf(DataStore s, Brand b) {
        List<DataStore.Meta> out = new ArrayList<DataStore.Meta>();
        if (b == null) return out;
        for (DataStore.Meta m : s.metas()) {
            if (of(m) == b) out.add(m);
        }
        return out;
    }

    /** 某品牌的色号总数。 */
    public static int colorsOf(DataStore s, Brand b) {
        int n = 0;
        for (DataStore.Meta m : setsOf(s, b)) n += m.count;
        return n;
    }
}
