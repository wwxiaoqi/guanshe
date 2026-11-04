package com.wwxiaoqi.guanshe.core;

/**
 * 收藏键的字符串部分（纯逻辑，可在桌面断言）。
 *
 * 契约见 specs/favorites：
 *   配色键 palette:<family|scene>:<序号>
 *   单色键 color:<色集序号>:<色号或 HEX>
 * 只存引用不存副本 —— 数据更新后仍能对上原来那一项。
 */
public final class Favorites {

    public static final int KIND_FAMILY = 0;
    public static final int KIND_SCENE = 1;
    /** 取色结果：不在库里，序号指向本地存的那份色值。 */
    public static final int KIND_LOCAL = 2;

    private Favorites() {
    }

    public static String kindName(int kind) {
        if (kind == KIND_SCENE) return "scene";
        if (kind == KIND_LOCAL) return "local";
        return "family";
    }

    public static int kindOf(String name) {
        if ("scene".equals(name)) return KIND_SCENE;
        if ("local".equals(name)) return KIND_LOCAL;
        return KIND_FAMILY;
    }

    public static String paletteKey(int kind, int index) {
        return "palette:" + kindName(kind) + ":" + index;
    }

    public static String colorKey(int set, String id) {
        return "color:" + set + ":" + id;
    }

    /**
     * 传统色的收藏键。
     *
     * 传统色没有"色卡里的色号"，它的 set 序号（0..2）和色卡 set 序号（1..13/101..106）会撞车，
     * 所以单独放在 color:named: 命名空间里 —— 否则收藏一个传统色，会被当成同号色卡里查找不到而丢掉。
     */
    public static String namedKey(int set, String name) {
        return "color:named:" + set + ":" + name;
    }

    public static boolean isNamedKey(String key) {
        return key != null && key.startsWith("color:named:");
    }

    public static boolean isPalette(String key) {
        return key != null && key.startsWith("palette:");
    }

    public static boolean isColor(String key) {
        return key != null && key.startsWith("color:");
    }

    /** "palette:scene:120" → {kind, index}；非法返回 null。 */
    public static int[] parsePaletteKey(String key) {
        if (!isPalette(key)) return null;
        String[] p = key.split(":");
        if (p.length != 3) return null;
        try {
            return new int[]{kindOf(p[1]), Integer.parseInt(p[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "color:12:012 C" → {set, id}；非法返回 null。 */
    public static String[] parseColorKey(String key) {
        if (!isColor(key)) return null;
        String rest = key.substring("color:".length());
        int cut = rest.indexOf(':');
        if (cut <= 0 || cut == rest.length() - 1) return null;
        return new String[]{rest.substring(0, cut), rest.substring(cut + 1)};
    }
}
