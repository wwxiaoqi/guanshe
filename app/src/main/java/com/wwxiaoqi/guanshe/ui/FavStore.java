package com.wwxiaoqi.guanshe.ui;

import android.content.Context;
import android.content.SharedPreferences;

import com.wwxiaoqi.guanshe.core.ColorMath;
import com.wwxiaoqi.guanshe.core.Favorites;
import com.wwxiaoqi.guanshe.data.DataStore;
import com.wwxiaoqi.guanshe.data.Resolve;

import java.util.ArrayList;
import java.util.List;

/**
 * 收藏与最近查看（只存本机，见 specs/favorites）。
 *
 * 存的是**引用键**不是颜色副本：`palette:scene:120` / `color:12:012 C`，
 * 数据更新后仍能对上原来那一项。顺序单独用 `\n` 拼的字符串保存。
 */
public final class FavStore {

    private static final String SP = "guanshe";
    private static final String K_FAV = "fav";
    private static final String K_RECENT = "recent";
    private static final String K_APPEAR = "appear";
    private static final String K_LOCAL = "localpal";
    private static final String K_SEARCH = "search";
    private static final String K_NOTE = "note";       // 行格式：<收藏键>\t<备注>

    public static final int MAX_RECENT = 20;
    public static final int MAX_SEARCH = 8;

    private final SharedPreferences p;

    public FavStore(Context c) {
        p = c.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- 收藏
    public List<String> favorites() {
        return split(p.getString(K_FAV, ""));
    }

    public boolean isFav(String key) {
        return favorites().contains(key);
    }

    /** @return true = 现在已收藏 */
    public boolean toggleFav(String key) {
        List<String> list = favorites();
        boolean added;
        if (list.contains(key)) {
            list.remove(key);
            added = false;
            removeNote(key);          // 取消收藏时备注一起清掉，不留孤儿
        } else {
            list.add(0, key);
            added = true;
        }
        p.edit().putString(K_FAV, join(list)).apply();
        return added;
    }

    // ---------------------------------------------------------------- 备注
    /** 某个收藏项的备注；没有返回空串。 */
    public String note(String key) {
        if (key == null) return "";
        for (String line : split(p.getString(K_NOTE, ""))) {
            int cut = line.indexOf('\t');
            if (cut > 0 && line.substring(0, cut).equals(key)) return line.substring(cut + 1);
        }
        return "";
    }

    /** 写备注；传空串等于删除。备注压成一行（换行/制表符换成空格）。 */
    public void setNote(String key, String text) {
        if (key == null) return;
        String v = text == null ? "" : text.replace('\n', ' ').replace('\t', ' ').trim();
        List<String> out = new ArrayList<String>();
        for (String line : split(p.getString(K_NOTE, ""))) {
            int cut = line.indexOf('\t');
            String k = cut > 0 ? line.substring(0, cut) : line;
            if (!k.equals(key)) out.add(line);
        }
        if (v.length() > 0) out.add(0, key + "\t" + v);
        p.edit().putString(K_NOTE, join(out)).apply();
    }

    public void removeNote(String key) {
        setNote(key, "");
    }

    public boolean hasNote(String key) {
        return note(key).length() > 0;
    }

    // ---------------------------------------------------------------- 最近查看
    public List<String> recents() {
        return split(p.getString(K_RECENT, ""));
    }

    public void touchRecent(String key) {
        List<String> list = recents();
        list.remove(key);
        list.add(0, key);
        while (list.size() > MAX_RECENT) list.remove(list.size() - 1);
        p.edit().putString(K_RECENT, join(list)).apply();
    }

    public void clearRecents() {
        p.edit().putString(K_RECENT, "").apply();
    }

    /** 给"撤销清空"用：整体写回。 */
    public void setRecents(java.util.List<String> list) {
        p.edit().putString(K_RECENT, join(list)).apply();
    }

    // ---------------------------------------------------------------- 搜索历史
    public List<String> searches() {
        return split(p.getString(K_SEARCH, ""));
    }

    public void addSearch(String query) {
        if (query == null) return;
        String q = query.trim();
        if (q.length() < 2) return;
        List<String> list = searches();
        list.remove(q);
        list.add(0, q);
        while (list.size() > MAX_SEARCH) list.remove(list.size() - 1);
        p.edit().putString(K_SEARCH, join(list)).apply();
    }

    public void clearSearches() {
        p.edit().putString(K_SEARCH, "").apply();
    }

    // ---------------------------------------------------------------- 本地配色（取色结果）
    /** 取色结果没有库序号，色值就存在本地；相同内容复用同一个序号。 */
    public int addLocalPalette(String hexList) {
        List<String> all = split(p.getString(K_LOCAL, ""));
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).equals(hexList)) return i;
        }
        all.add(hexList);
        p.edit().putString(K_LOCAL, join(all)).apply();
        return all.size() - 1;
    }

    public int[] localPalette(int index) {
        List<String> all = split(p.getString(K_LOCAL, ""));
        if (index < 0 || index >= all.size()) return null;
        String[] parts = all.get(index).split(",");
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) out[i] = ColorMath.parse(parts[i]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return out;
    }

    /** 统一入口：先认本地取色，再认库里的色系/场景。 */
    public int[] resolvePalette(DataStore s, String key) {
        int[] pk = Favorites.parsePaletteKey(key);
        if (pk == null) return null;
        return pk[0] == Favorites.KIND_LOCAL ? localPalette(pk[1]) : Resolve.palette(s, key);
    }

    public String resolvePaletteLabel(DataStore s, String key) {
        int[] pk = Favorites.parsePaletteKey(key);
        if (pk == null) return "";
        return pk[0] == Favorites.KIND_LOCAL ? "取色 · 第 " + (pk[1] + 1) + " 组"
                : Resolve.paletteLabel(s, key);
    }

    // ---------------------------------------------------------------- 外观
    public String appearance() {
        return p.getString(K_APPEAR, BaseActivity.APPEAR_SYSTEM);
    }

    public void setAppearance(String mode) {
        p.edit().putString(K_APPEAR, mode).apply();
    }

    // ---------------------------------------------------------------- 工具
    private static List<String> split(String s) {
        List<String> out = new ArrayList<String>();
        if (s == null || s.length() == 0) return out;
        for (String part : s.split("\n")) {
            if (part.length() > 0) out.add(part);
        }
        return out;
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
