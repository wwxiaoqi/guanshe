package com.wwxiaoqi.guanshe.page;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.MainActivity;
import com.wwxiaoqi.guanshe.R;
import com.wwxiaoqi.guanshe.Skin;
import com.wwxiaoqi.guanshe.core.ColorMath;
import com.wwxiaoqi.guanshe.core.Favorites;
import com.wwxiaoqi.guanshe.data.DataStore;
import com.wwxiaoqi.guanshe.data.Resolve;
import com.wwxiaoqi.guanshe.ui.BandView;
import com.wwxiaoqi.guanshe.ui.Page;
import com.wwxiaoqi.guanshe.ui.Snack;
import com.wwxiaoqi.guanshe.ui.Surface;
import com.wwxiaoqi.guanshe.ui.SwatchView;
import com.wwxiaoqi.guanshe.ui.Tap;

import java.util.ArrayList;
import java.util.List;

/**
 * 色卡库入口（specs/color-cards · 来源分组）：7 个来源，数字全部从 meta 现算，不写死。
 *
 * 这一版把"按来源"从一列大行换成 2 列卡片：顶部 56dp 采样色带就是"这套色卡长什么样"，
 * 比一行"11 套 · 13,472 色"更快被认出来。最近查看的清空从"删了就没"改成可撤销。
 */
public class CardsPage extends BasePage {

    private static final int RECENT_MAX = 3;
    /** 采样失败时的兜底四色：墨 / 橙 / 青绿 / 蓝，和整体纸墨色系一致。 */
    private static final int[] FALLBACK = {0xFF1F1D1A, 0xFFE07A3F, 0xFF32D2A8, 0xFF459DFF};

    private View recentsSection;
    private LinearLayout recentsSlot;
    private boolean built;

    public CardsPage(MainActivity host) {
        super(host);
    }

    @Override
    protected View build() {
        final DataStore s = host.store();
        final LinearLayout root = Page.root(host, Skin.bg(host), true);

        LinearLayout header = Page.header(host, "色卡", "20,969 个色号 · 7 个来源",
                new int[]{R.drawable.lc_search},
                new View.OnClickListener[]{new View.OnClickListener() {
                    public void onClick(View v) {
                        host.openSearchFrom(v);
                    }
                }});
        // Page.header 只管排版；纯图标按钮的读屏文案要调用方补
        View headSearch = header.getChildAt(header.getChildCount() - 1);
        if (headSearch != null) headSearch.setContentDescription("搜索色号、色名或色值");
        root.addView(header);

        LinearLayout searchWrap = Page.wrap(host);
        searchWrap.setPadding(Skin.dp(host, Page.PAD), Skin.dp(host, 4),
                Skin.dp(host, Page.PAD), Skin.dp(host, 2));
        Page.addFull(searchWrap, searchBar());
        root.addView(searchWrap);

        root.addView(Page.section(host, "按来源", "点开看色卡"));
        root.addView(sourceGrid(s));

        final LinearLayout recentBlock = new LinearLayout(host);
        recentBlock.setOrientation(LinearLayout.VERTICAL);
        recentsSection = Page.section(host, "最近查看", "清空", new View.OnClickListener() {
            public void onClick(View v) {
                clearRecents();
            }
        });
        recentsSlot = Page.wrap(host);
        recentBlock.addView(recentsSection);
        recentBlock.addView(recentsSlot);
        root.addView(recentBlock);
        fillRecents();

        LinearLayout foot = Page.wrap(host);
        foot.setPadding(Skin.dp(host, Page.PAD), Skin.dp(host, 18),
                Skin.dp(host, Page.PAD), Skin.dp(host, 24));
        TextView note = Skin.text(host, "色号数据在本机 · 潘通与劳尔为商标数据，仅供个人参考。",
                Skin.T_CHIP, Skin.text3(host));
        note.setLineSpacing(Skin.dp(host, 3), 1f);
        foot.addView(note);
        root.addView(foot);

        ScrollView sc = Page.scroller(host);
        sc.addView(root);
        scroller = sc;
        built = true;
        root.post(new Runnable() {
            public void run() {
                Page.reveal(root);
            }
        });
        return sc;
    }

    @Override
    public void onShow() {
        if (built) fillRecents();       // 切回这个 tab 时最近查看可能已经变了
    }

    // ---------------------------------------------------------------- 搜索入口

    /** 有影子的白底搜索条：整条可点，进独立搜索页。 */
    private View searchBar() {
        final LinearLayout bar = new LinearLayout(host);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(Skin.stroke(host, Skin.cardColor(host), Skin.cardStroke(host), 16f, 1f));
        bar.setPadding(Skin.dp(host, 16), 0, Skin.dp(host, 16), 0);
        bar.setMinimumHeight(Skin.dp(host, 48));
        bar.addView(Skin.icon(host, R.drawable.lc_search, Skin.text3(host), 18f));
        TextView ph = Skin.text(host, "搜色号 / 色名 / #2D2B28", Skin.T_BODY, Skin.text3(host));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Skin.dp(host, 10);
        bar.addView(ph, lp);
        bar.setContentDescription("搜索色号、色名或色值");
        Surface.card(bar, 16f, Surface.CARD);
        Tap.chip(bar);
        bar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                host.openSearchFrom(bar);
            }
        });
        return bar;
    }

    // ---------------------------------------------------------------- 来源卡

    private View sourceGrid(DataStore s) {
        int pantoneSets = 0, pantoneColors = 0, ralSets = 0, ralColors = 0;
        int cbcc = 0, niban = 0;
        int firstPantone = -1, firstRal = -1, cbccSet = -1, nibanSet = -1;
        int[] namedCount = new int[3];
        String[] namedName = new String[3];
        for (DataStore.Meta m : s.metas()) {
            if ("pantset".equals(m.kind)) {
                if (m.name.startsWith("CBCC")) {
                    cbcc += m.count;
                    if (cbccSet < 0) cbccSet = m.id;
                } else if (m.name.startsWith("立邦")) {
                    niban += m.count;
                    if (nibanSet < 0) nibanSet = m.id;
                } else {
                    pantoneSets++;
                    pantoneColors += m.count;
                    if (firstPantone < 0) firstPantone = m.id;
                }
            } else if ("ralset".equals(m.kind)) {
                ralSets++;
                ralColors += m.count;
                if (firstRal < 0) firstRal = m.id;
            } else if ("namedset".equals(m.kind) && m.id >= 0 && m.id < 3) {
                namedCount[m.id] = m.count;
                namedName[m.id] = m.name;
            }
        }

        List<View> cards = new ArrayList<View>();
        cards.add(sourceCard("潘通色卡", pantoneSets + " 套 · " + fmt(pantoneColors) + " 色",
                sampleCodes(firstPantone), new View.OnClickListener() {
                    public void onClick(View v) {
                        host.openSourceSetsFrom("pantone", v);
                    }
                }));
        cards.add(sourceCard("劳尔色卡", ralSets + " 套 · " + fmt(ralColors) + " 色",
                sampleCodes(firstRal), new View.OnClickListener() {
                    public void onClick(View v) {
                        host.openSourceSetsFrom("ral", v);
                    }
                }));
        cards.add(sourceCard("CBCC 中国建筑", fmt(cbcc) + " 色",
                sampleCodes(cbccSet), new View.OnClickListener() {
                    public void onClick(View v) {
                        host.openSourceSetsFrom("cbcc", v);
                    }
                }));
        cards.add(sourceCard("立邦墙面漆", fmt(niban) + " 色",
                sampleCodes(nibanSet), new View.OnClickListener() {
                    public void onClick(View v) {
                        host.openSourceSetsFrom("niban", v);
                    }
                }));
        for (int i = 0; i < 3; i++) {
            final int set = i;
            if (namedName[i] == null) continue;
            int families = s.namedFamilies(set).length;
            cards.add(sourceCard(namedName[i],
                    families + " 家族 · " + fmt(namedCount[i]) + " 色", sampleNamed(set),
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            host.openSetFrom("namedset", set, v);
                        }
                    }));
        }
        return twoColumns(cards);
    }

    /** 一张来源卡：顶部 56dp 采样色带 + 名称 + 数量。 */
    private View sourceCard(final String name, String sub, int[] colors,
                            final View.OnClickListener click) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        float r = 16f;
        card.setBackground(Skin.stroke(host, Skin.cardColor(host), Skin.cardStroke(host), r, 1f));
        Surface.card(card, r, Surface.CARD);
        card.addView(BandView.of(host, colors, 56, r, true));

        LinearLayout inner = new LinearLayout(host);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(Skin.dp(host, 14), Skin.dp(host, 10),
                Skin.dp(host, 14), Skin.dp(host, 12));
        TextView title = Skin.medium(host, name, Skin.T_BODY, Skin.text(host));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        inner.addView(title);
        TextView meta = Skin.text(host, sub, Skin.T_CAPTION, Skin.text3(host));
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(0, Skin.dp(host, 3), 0, 0);
        inner.addView(meta);
        card.addView(inner);

        card.setContentDescription(name + "，" + sub + "，轻点打开");
        Tap.card(card, Surface.CARD);
        card.setOnClickListener(click);
        return card;
    }

    private View twoColumns(List<View> cards) {
        LinearLayout wrap = Page.wrap(host);
        int gap = Skin.dp(host, 12);
        for (int i = 0; i < cards.size(); i += 2) {
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rowLp.topMargin = gap;
            wrap.addView(row, rowLp);
            row.addView(cards.get(i), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (i + 1 < cards.size()) {
                LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                right.leftMargin = gap;
                row.addView(cards.get(i + 1), right);
            } else {
                View spacer = new View(host);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                sp.leftMargin = gap;
                row.addView(spacer, sp);
            }
        }
        return wrap;
    }

    /** 取某套色卡的前 4 个色号色作为采样；取不到就用兜底色补。 */
    private int[] sampleCodes(int set) {
        int[] out = new int[4];
        int[] idx = set < 0 ? new int[0] : host.store().codesOf(set);
        for (int i = 0; i < 4; i++) {
            out[i] = i < idx.length ? host.store().codeColor(idx[i]) : FALLBACK[i];
        }
        return out;
    }

    private int[] sampleNamed(int set) {
        int[] out = new int[4];
        int[] idx = host.store().namedOf(set);
        for (int i = 0; i < 4; i++) {
            out[i] = i < idx.length ? host.store().namedColor(idx[i]) : FALLBACK[i];
        }
        return out;
    }

    // ---------------------------------------------------------------- 最近查看

    private void fillRecents() {
        if (recentsSlot == null) return;
        recentsSlot.removeAllViews();
        DataStore s = host.store();
        LinearLayout card = Skin.listCard(host);
        int shown = 0;
        for (String key : host.favs().recents()) {
            if (!Favorites.isColor(key)) continue;      // 配色记录这里不展示，和旧版一致
            int color = Resolve.color(s, key);
            if (color == 0) continue;                   // 还原不出来的旧键安静跳过
            if (card.getChildCount() > 0) card.addView(Page.hairline(host));
            card.addView(recentRow(color, key));
            if (++shown >= RECENT_MAX) break;
        }
        boolean has = shown > 0;
        recentsSection.setVisibility(has ? View.VISIBLE : View.GONE);
        recentsSlot.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) Page.addFull(recentsSlot, card);
    }

    private View recentRow(final int color, final String key) {
        String name = colorName(key);
        final String hex = ColorMath.hex(color);
        // 取色结果的"名字"就是个 HEX，别在行里印两遍
        final String titleText = name.startsWith("#") ? "取色" : (name.isEmpty() ? hex : name);

        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Skin.dp(host, 16), Skin.dp(host, 9), Skin.dp(host, 16), Skin.dp(host, 9));
        row.setMinimumHeight(Skin.dp(host, 52));
        row.addView(SwatchView.of(host, color, 32, 6));

        LinearLayout mid = new LinearLayout(host);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        midLp.leftMargin = Skin.dp(host, 12);
        TextView title = Skin.text(host, titleText, Skin.T_BODY, Skin.text(host));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(title);
        row.addView(mid, midLp);
        row.addView(Skin.text(host, hex, Skin.T_NOTE, Skin.text2(host)));

        row.setContentDescription(titleText + " " + hex + "，轻点查看");
        Tap.row(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                host.openColorFrom(color, "", key, v);
            }
        });
        return row;
    }

    /** 收藏键里的显示名：传统色 color:named:set:名称 取最后一段（否则会显示成 "0:妃色"）。 */
    private String colorName(String key) {
        String[] ck = Favorites.parseColorKey(key);
        if (ck == null) return "";
        if ("named".equals(ck[0])) {
            int cut = ck[1].indexOf(':');
            return cut >= 0 ? ck[1].substring(cut + 1) : ck[1];
        }
        return ck[1];
    }

    /** 清空可撤销：先记住旧列表，撤销时整体写回。 */
    private void clearRecents() {
        final List<String> old = host.favs().recents();
        host.favs().clearRecents();
        fillRecents();
        Snack.show(scroller, "已清空最近查看", "撤销", new Runnable() {
            public void run() {
                host.favs().setRecents(old);
                fillRecents();
            }
        });
    }

    private static String fmt(int n) {
        if (n < 1000) return String.valueOf(n);
        return n / 1000 + "," + String.format("%03d", n % 1000);
    }
}
