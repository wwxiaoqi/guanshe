package com.wwxiaoqi.guanshe.data;

import com.wwxiaoqi.guanshe.core.ColorMath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 全部配色数据的只读索引。
 *
 * 刻意**不依赖 android**：读文件走 {@link Source} 接口，Android 传 AssetManager，
 * 桌面的 SelfTest 传 FileInputStream —— 于是"2 万行能解析成什么"可以在容器里断言。
 *
 * 内存：13665×4 个 int（218KB）+ 20095 个色号 + 874 个传统色，冷启动读一次。
 */
public final class DataStore {

    /** 数据源；Android 端用 assets，桌面测试用文件。 */
    public interface Source {
        InputStream open(String name) throws IOException;
    }

    public static final int KIND_FAMILY = 0;
    public static final int KIND_SCENE = 1;

    // ---- 配色
    private int[] palColors;        // 4 色 / 组
    private byte[] palKind;
    private int[] palGroup;
    // ---- 色号
    private int[] codeColor;
    private String[] codeName;
    private int[] codeSet;
    // ---- 传统色
    private int[] namedColor;
    private String[] namedName;
    private String[] namedReading;
    private String[] namedFamily;
    private int[] namedSet;

    private final List<Meta> metas = new ArrayList<Meta>();

    /** meta.tsv 的一行：分组/色卡/色集/图片集的元信息。 */
    public static final class Meta {
        public final String kind;   // family|scene|pantset|ralset|namedset|imageset
        public final int id;
        public final String name;
        public final int hex;
        public final int count;

        Meta(String kind, int id, String name, int hex, int count) {
            this.kind = kind;
            this.id = id;
            this.name = name;
            this.hex = hex;
            this.count = count;
        }
    }

    // ---------------------------------------------------------------- 加载
    public static DataStore load(Source src) throws IOException {
        DataStore s = new DataStore();

        List<String> fam = readLines(src, "families.tsv");
        List<String> sce = readLines(src, "scenes.tsv");
        int total = fam.size() + sce.size();
        s.palColors = new int[total * 4];
        s.palKind = new byte[total];
        s.palGroup = new int[total];
        int at = 0;
        at = s.fillPalettes(fam, KIND_FAMILY, at);
        s.fillPalettes(sce, KIND_SCENE, at);

        List<String> codes = readLines(src, "codes.tsv");
        s.codeColor = new int[codes.size()];
        s.codeName = new String[codes.size()];
        s.codeSet = new int[codes.size()];
        for (int i = 0; i < codes.size(); i++) {
            String[] p = split3(codes.get(i), 3);
            s.codeSet[i] = parseInt(p[0]);
            s.codeColor[i] = ColorMath.parse(p[1]);
            s.codeName[i] = p[2];
        }

        List<String> named = readLines(src, "named.tsv");
        s.namedColor = new int[named.size()];
        s.namedName = new String[named.size()];
        s.namedReading = new String[named.size()];
        s.namedFamily = new String[named.size()];
        s.namedSet = new int[named.size()];
        for (int i = 0; i < named.size(); i++) {
            String[] p = split3(named.get(i), 5);
            s.namedSet[i] = parseInt(p[0]);
            s.namedFamily[i] = p[1];
            s.namedName[i] = p[2];
            s.namedReading[i] = p[3];
            s.namedColor[i] = ColorMath.parse(p[4]);
        }

        for (String line : readLines(src, "meta.tsv")) {
            String[] p = split3(line, 5);
            s.metas.add(new Meta(p[0], parseInt(p[1]), p[2], ColorMath.parse(p[3]), parseInt(p[4])));
        }
        return s;
    }

    private int fillPalettes(List<String> lines, int kind, int from) {
        int at = from;
        for (String line : lines) {
            String[] p = split3(line, 2);
            palKind[at] = (byte) kind;
            palGroup[at] = parseInt(p[0]);
            String hex = p[1];
            for (int k = 0; k < 4; k++) {
                palColors[at * 4 + k] = ColorMath.parse(hex.substring(k * 6, k * 6 + 6));
            }
            at++;
        }
        return at;
    }

    private static List<String> readLines(Source src, String name) throws IOException {
        List<String> out = new ArrayList<String>();
        InputStream in = src.open(name);
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.length() > 0) out.add(line);
        }
        r.close();
        return out;
    }

    /** 按需要切成 n 段；只在最后一段保留剩余内容（色号里可能含空格，不能按空白切）。 */
    private static String[] split3(String line, int n) {
        String[] out = new String[n];
        int from = 0;
        for (int i = 0; i < n - 1; i++) {
            int cut = line.indexOf('\t', from);
            if (cut < 0) cut = line.length();
            out[i] = line.substring(from, cut);
            from = Math.min(line.length(), cut + 1);
        }
        out[n - 1] = line.substring(from);
        return out;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- 配色
    public int paletteCount() {
        return palKind.length;
    }

    public int[] palette(int i) {
        return new int[]{palColors[i * 4], palColors[i * 4 + 1], palColors[i * 4 + 2], palColors[i * 4 + 3]};
    }

    public int paletteKind(int i) {
        return palKind[i];
    }

    public int paletteGroup(int i) {
        return palGroup[i];
    }

    /** 某个色系/场景下的全部配色序号（保持源顺序）。 */
    public int[] palettesOf(int kind, int group) {
        int n = 0;
        for (int i = 0; i < palKind.length; i++) {
            if (palKind[i] == kind && (group < 0 || palGroup[i] == group)) n++;
        }
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < palKind.length; i++) {
            if (palKind[i] == kind && (group < 0 || palGroup[i] == group)) out[k++] = i;
        }
        return out;
    }

    /** 包含某个色值的配色（HEX 搜索用）。 */
    public int[] palettesWithColor(int color) {
        int n = 0;
        for (int i = 0; i < palKind.length; i++) {
            for (int k = 0; k < 4; k++) {
                if (palColors[i * 4 + k] == color) {
                    n++;
                    break;
                }
            }
        }
        int[] out = new int[n];
        int m = 0;
        for (int i = 0; i < palKind.length; i++) {
            for (int k = 0; k < 4; k++) {
                if (palColors[i * 4 + k] == color) {
                    out[m++] = i;
                    break;
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 色号
    public int codeCount() {
        return codeColor.length;
    }

    public int codeColor(int i) {
        return codeColor[i];
    }

    public String codeName(int i) {
        return codeName[i];
    }

    public int codeSet(int i) {
        return codeSet[i];
    }

    /** 某套色卡的全部色号下标。 */
    public int[] codesOf(int set) {
        int n = 0;
        for (int i = 0; i < codeSet.length; i++) if (codeSet[i] == set) n++;
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < codeSet.length; i++) if (codeSet[i] == set) out[k++] = i;
        return out;
    }

    // ---------------------------------------------------------------- 传统色
    public int namedCount() {
        return namedColor.length;
    }

    public int namedColor(int i) {
        return namedColor[i];
    }

    public String namedName(int i) {
        return namedName[i];
    }

    public String namedReading(int i) {
        return namedReading[i];
    }

    public String namedFamily(int i) {
        return namedFamily[i];
    }

    public int namedSet(int i) {
        return namedSet[i];
    }

    /** 离某个色最近的 limit 个传统色（返回 named 下标，按 ΔE 升序）。 */
    public int[] nearestNamed(int color, int limit) {
        return ColorMath.nearest(color, namedColor, limit);
    }

    /** 某个色集里的某个家族（传统色用）。 */
    public String[] namedFamilies(int set) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (int i = 0; i < namedSet.length; i++) {
            if (namedSet[i] == set && !out.contains(namedFamily[i])) out.add(namedFamily[i]);
        }
        return out.toArray(new String[0]);
    }

    /** 某个传统色集的全部色下标（按家族顺序）。 */
    public int[] namedOf(int set) {
        int n = 0;
        for (int i = 0; i < namedSet.length; i++) if (namedSet[i] == set) n++;
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < namedSet.length; i++) if (namedSet[i] == set) out[k++] = i;
        return out;
    }

    /** 色集内某家族的全部传统色下标。 */
    public int[] namedOf(int set, String family) {
        int n = 0;
        for (int i = 0; i < namedSet.length; i++) {
            if (namedSet[i] == set && namedFamily[i].equals(family)) n++;
        }
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < namedSet.length; i++) {
            if (namedSet[i] == set && namedFamily[i].equals(family)) out[k++] = i;
        }
        return out;
    }

    /** 某套色卡里按"色号"找下标；找不到返回 -1。 */
    public int codeIndex(int set, String name) {
        for (int i = 0; i < codeSet.length; i++) {
            if (codeSet[i] == set && codeName[i].equals(name)) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- 元信息
    public List<Meta> metas() {
        return metas;
    }

    public Meta meta(String kind, int id) {
        for (Meta m : metas) {
            if (m.kind.equals(kind) && m.id == id) return m;
        }
        return null;
    }

    /** 某个色系/场景的中文名。 */
    public String groupName(int kind, int group) {
        Meta m = meta(kind == KIND_FAMILY ? "family" : "scene", group);
        return m == null ? "" : m.name;
    }

    // ---------------------------------------------------------------- 搜索
    /** 搜索结果：三组下标 + 是否因为输入太短而不搜。 */
    public static final class Search {
        public final int[] palettes;
        public final int[] codes;
        public final int[] named;
        public final boolean tooShort;

        Search(int[] palettes, int[] codes, int[] named, boolean tooShort) {
            this.palettes = palettes;
            this.codes = codes;
            this.named = named;
            this.tooShort = tooShort;
        }
    }

    public Search search(String raw, int limit) {
        String q = raw == null ? "" : raw.trim();
        if (q.length() < 2) {
            return new Search(new int[0], new int[0], new int[0], true);
        }
        String bare = q.startsWith("#") ? q.substring(1) : q;
        if (bare.length() == 6 && isHex(bare)) {
            int color = ColorMath.parse(bare);
            return new Search(trim(palettesWithColor(color), limit), codesWithHex(color, limit),
                    namedWithHex(color, limit), false);
        }
        List<Integer> c = new ArrayList<Integer>();
        List<Integer> n = new ArrayList<Integer>();
        String low = q.toLowerCase();
        for (int i = 0; i < codeName.length && c.size() < limit; i++) {
            if (codeName[i].toLowerCase().contains(low)) c.add(i);
        }
        for (int i = 0; i < namedName.length && n.size() < limit; i++) {
            if (namedName[i].contains(q) || namedFamily[i].contains(q)
                    || namedReading[i].toLowerCase().contains(low)) {
                n.add(i);
            }
        }
        return new Search(new int[0], toArray(c), toArray(n), false);
    }

    private int[] codesWithHex(int color, int limit) {
        List<Integer> out = new ArrayList<Integer>();
        for (int i = 0; i < codeColor.length && out.size() < limit; i++) {
            if (codeColor[i] == color) out.add(i);
        }
        return toArray(out);
    }

    private int[] namedWithHex(int color, int limit) {
        List<Integer> out = new ArrayList<Integer>();
        for (int i = 0; i < namedColor.length && out.size() < limit; i++) {
            if (namedColor[i] == color) out.add(i);
        }
        return toArray(out);
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), 16) < 0) return false;
        }
        return true;
    }

    private static int[] trim(int[] a, int limit) {
        if (a.length <= limit) return a;
        int[] out = new int[limit];
        System.arraycopy(a, 0, out, 0, limit);
        return out;
    }

    private static int[] toArray(List<Integer> l) {
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }
}
