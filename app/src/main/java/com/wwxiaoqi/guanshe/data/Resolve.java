package com.wwxiaoqi.guanshe.data;

import com.wwxiaoqi.guanshe.core.ColorMath;
import com.wwxiaoqi.guanshe.core.Favorites;

/**
 * 把收藏键还原成"能显示的东西"。
 *
 * 键里只存引用（specs/favorites 的契约），所以还原失败是正常情况（数据更新后旧项可能对不上），
 * 一律返回 null 让调用方跳过，不崩。
 */
public final class Resolve {

    /** 图片取色结果的伪色集序号。 */
    public static final int SET_PICKED = -1;

    private Resolve() {
    }

    // ---------------------------------------------------------------- 配色
    public static int[] palette(DataStore s, String key) {
        int[] pk = Favorites.parsePaletteKey(key);
        if (pk == null || pk[1] < 0 || pk[1] >= s.paletteCount()) return null;
        if (s.paletteKind(pk[1]) != pk[0]) return null;
        return s.palette(pk[1]);
    }

    public static String paletteLabel(DataStore s, String key) {
        int[] pk = Favorites.parsePaletteKey(key);
        if (pk == null || pk[1] < 0 || pk[1] >= s.paletteCount()) return "";
        return s.groupName(s.paletteKind(pk[1]), s.paletteGroup(pk[1]));
    }

    // ---------------------------------------------------------------- 单色
    /** @return 0xFFRRGGBB；还原不出来返回 0 */
    public static int color(DataStore s, String key) {
        String[] ck = Favorites.parseColorKey(key);
        if (ck == null) return 0;
        if ("named".equals(ck[0])) return namedColor(s, ck[1]);
        int set = parseInt(ck[0], SET_PICKED);
        if (set == SET_PICKED) {
            try {
                return ColorMath.parse(ck[1]);
            } catch (IllegalArgumentException e) {
                return 0;
            }
        }
        int idx = s.codeIndex(set, ck[1]);
        return idx < 0 ? 0 : s.codeColor(idx);
    }

    /** color:named:<set>:<名称> 的 id 形如 "0:妃色"。 */
    private static int namedColor(DataStore s, String id) {
        int cut = id.indexOf(':');
        if (cut <= 0) return 0;
        int set = parseInt(id.substring(0, cut), -1);
        String name = id.substring(cut + 1);
        for (int i = 0; i < s.namedCount(); i++) {
            if (s.namedSet(i) == set && s.namedName(i).equals(name)) return s.namedColor(i);
        }
        return 0;
    }

    public static String colorLabel(DataStore s, String key) {
        String[] ck = Favorites.parseColorKey(key);
        if (ck == null) return "";
        if ("named".equals(ck[0])) {
            int cut = ck[1].indexOf(':');
            int set = cut > 0 ? parseInt(ck[1].substring(0, cut), -1) : -1;
            DataStore.Meta m = s.meta("namedset", set);
            return m == null ? "传统色" : m.name;
        }
        int set = parseInt(ck[0], SET_PICKED);
        if (set == SET_PICKED) return "取色";
        DataStore.Meta m = s.meta("pantset", set);
        if (m == null) m = s.meta("ralset", set);
        return m == null ? "" : m.name;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
