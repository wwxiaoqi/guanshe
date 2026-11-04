package com.wwxiaoqi.guanshe.page;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.MainActivity;
import com.wwxiaoqi.guanshe.R;
import com.wwxiaoqi.guanshe.Skin;
import com.wwxiaoqi.guanshe.core.ColorMath;
import com.wwxiaoqi.guanshe.core.Favorites;
import com.wwxiaoqi.guanshe.core.Today;
import com.wwxiaoqi.guanshe.data.DataStore;
import com.wwxiaoqi.guanshe.data.Resolve;
import com.wwxiaoqi.guanshe.ui.BandView;
import com.wwxiaoqi.guanshe.ui.Chips;
import com.wwxiaoqi.guanshe.ui.Crash;
import com.wwxiaoqi.guanshe.ui.Overlay;
import com.wwxiaoqi.guanshe.ui.Page;
import com.wwxiaoqi.guanshe.ui.QrView;
import com.wwxiaoqi.guanshe.ui.Rows;
import com.wwxiaoqi.guanshe.ui.Snack;
import com.wwxiaoqi.guanshe.ui.Surface;
import com.wwxiaoqi.guanshe.ui.SwatchView;
import com.wwxiaoqi.guanshe.ui.Tap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * 我的（specs/favorites + specs/app-shell 的外观设置）。
 *
 * 收藏是"引用键"，每次进入都重新解析一次：数据对不上的旧键会被安静跳过。
 * 这一版把页面拆成固定骨架 + 可刷新区块：切回来时只重建收藏/最近/关于，滚动位置和静态卡不重排。
 */
public class MinePage extends BasePage {

    private static final int RECENT_MAX = 5;
    private static final String GITHUB_URL = "https://github.com/wwxiaoqi/guanshe";
    private static final String GITHUB_DISPLAY = "github.com/wwxiaoqi/guanshe";

    private final Random random = new Random();
    /** 一次 refreshAll 内的 key→color 缓存：codeIndex 是 20,095 行的顺序扫描，别对同一个 key 反复解析。 */
    private final HashMap<String, Integer> colorCache = new HashMap<String, Integer>();

    private LinearLayout content;
    private TextView statsText;
    private LinearLayout palettesBlock;
    private LinearLayout colorsBlock;
    private LinearLayout recentsBlock;
    private LinearLayout aboutBlock;
    private boolean built;
    private boolean firstShow = true;

    public MinePage(MainActivity host) {
        super(host);
    }

    @Override
    protected View build() {
        final LinearLayout root = Page.root(host, Skin.bg(host), true);
        root.addView(Page.header(host, "我的", "收藏与最近都只存在这台设备", null, null));

        content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        Page.addFull(root, content);

        content.addView(inset(identityCard(), Skin.dp(host, 6)));
        content.addView(inset(quickPickCard(), Skin.dp(host, 12)));

        palettesBlock = block();
        content.addView(palettesBlock);
        colorsBlock = block();
        content.addView(colorsBlock);
        recentsBlock = block();
        content.addView(recentsBlock);

        // 外观：切外观会 recreate 整页，所以这一块建一次就够，不用进 onShow 刷新
        content.addView(Page.section(host, "外观", null));
        String mode = host.favs().appearance();
        String[] options = {"跟随系统", "浅色", "深色"};
        int selected = "light".equals(mode) ? 1 : ("dark".equals(mode) ? 2 : 0);
        Chips.Bar bar = Chips.bar(host, options, selected, false, new Chips.OnPick() {
            public void onPick(int index, String label) {
                host.applyAppearance(index == 1 ? "light" : (index == 2 ? "dark" : "system"));
            }
        });
        Page.addFull(content, bar.view);

        aboutBlock = block();
        content.addView(aboutBlock);

        refreshAll();
        built = true;
        content.post(new Runnable() {
            public void run() {
                Page.reveal(content);       // 首屏一级区块依次升起（后面的直接显示）
            }
        });

        ScrollView sc = Page.scroller(host);
        sc.addView(root);
        scroller = sc;
        return sc;
    }

    @Override
    public void onShow() {
        if (!built) return;
        if (firstShow) {          // 首次展示的内容已经在 build() 里铺好
            firstShow = false;
            return;
        }
        refreshAll();
    }

    // ---------------------------------------------------------------- 数据刷新

    private void refreshAll() {
        DataStore s = host.store();
        colorCache.clear();
        List<String> favs = host.favs().favorites();
        List<String> palKeys = new ArrayList<String>();
        List<String> colorKeys = new ArrayList<String>();
        for (String key : favs) {
            if (Favorites.isPalette(key)) {
                if (host.favs().resolvePalette(s, key) != null) palKeys.add(key);
            } else if (Favorites.isColor(key)) {
                if (resolveColor(key) != 0) colorKeys.add(key);
            }
        }
        List<String> recentKeys = new ArrayList<String>();
        for (String key : host.favs().recents()) {
            if (Favorites.isColor(key) && resolveColor(key) != 0) recentKeys.add(key);
        }

        fillStats(palKeys.size(), colorKeys.size(), recentKeys.size());
        fillPalettes(palKeys);
        fillColors(colorKeys);
        fillRecents(recentKeys);
        fillAbout();
    }

    /** key→color 在一次 refreshAll 里只解析一次：过滤、格子、行都从这里取。 */
    private int resolveColor(String key) {
        Integer hit = colorCache.get(key);
        if (hit != null) return hit;
        int color = Resolve.color(host.store(), key);
        colorCache.put(key, color);
        return color;
    }

    /** 收藏键里的显示名：色号名 / 传统色（color:named:set:名称 取最后一段）/ HEX。 */
    private String colorName(String key) {
        String[] ck = Favorites.parseColorKey(key);
        if (ck == null) return "";
        if ("named".equals(ck[0])) {
            int cut = ck[1].indexOf(':');
            return cut >= 0 ? ck[1].substring(cut + 1) : ck[1];
        }
        return ck[1];
    }

    private void fillStats(int palettes, int colors, int recents) {
        if (statsText == null) return;
        statsText.setText("收藏配色 " + palettes + " · 收藏颜色 " + colors
                + " · 最近查看 " + recents);
    }

    // ---------------------------------------------------------------- 身份卡

    /** 身份卡：图标 + 名称 + 版本 + 今日配色小色带 + 一行统计。 */
    private View identityCard() {
        DataStore s = host.store();
        LinearLayout card = Skin.card(host);

        LinearLayout top = new LinearLayout(host);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageView app = new ImageView(host);
        app.setImageResource(R.mipmap.ic_launcher);
        app.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);   // 装饰图标
        top.addView(app, new LinearLayout.LayoutParams(Skin.dp(host, 52), Skin.dp(host, 52)));

        LinearLayout titles = new LinearLayout(host);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Skin.dp(host, 14);
        TextView name = Skin.medium(host, "观色", Skin.T_CARD, Skin.text(host));
        name.setLetterSpacing(-0.01f);
        titles.addView(name);
        TextView ver = Skin.text(host, "v" + versionName() + " · 离线配色手册",
                Skin.T_CAPTION, Skin.text3(host));
        ver.setPadding(0, Skin.dp(host, 3), 0, 0);
        titles.addView(ver);
        top.addView(titles, tlp);
        card.addView(top);

        // 今日配色：6dp 的小色带，让身份卡带一点"今天"的气息
        int idx = Today.pick(Today.daysSinceEpoch(System.currentTimeMillis()), s.paletteCount());
        int[] today = s.palette(idx);
        String label = s.groupName(s.paletteKind(idx), s.paletteGroup(idx));

        LinearLayout cap = new LinearLayout(host);
        cap.setOrientation(LinearLayout.HORIZONTAL);
        cap.setGravity(Gravity.CENTER_VERTICAL);
        cap.setPadding(0, Skin.dp(host, 18), 0, Skin.dp(host, 8));
        TextView capLeft = Skin.text(host, "今日配色", Skin.T_CAPTION, Skin.text3(host));
        capLeft.setLetterSpacing(0.05f);
        capLeft.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cap.addView(capLeft);
        cap.addView(Skin.text(host, label, Skin.T_CAPTION, Skin.text3(host)));
        card.addView(cap);

        View band = BandView.of(host, today, 6, 6, false);
        band.setContentDescription("今日配色色带，" + ColorMath.hexList(today));
        card.addView(band);

        statsText = Skin.text(host, "", Skin.T_NOTE, Skin.text2(host));
        statsText.setPadding(0, Skin.dp(host, 12), 0, 0);
        card.addView(statsText);
        return card;
    }

    // ---------------------------------------------------------------- 快捷入口

    private View quickPickCard() {
        LinearLayout card = Skin.card(host);
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout iconBox = new LinearLayout(host);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setBackground(Skin.round(host, Skin.tint(Skin.text(host), 0.06f), 12f));
        iconBox.addView(Skin.icon(host, R.drawable.lc_image, Skin.text2(host), 22f));
        row.addView(iconBox, new LinearLayout.LayoutParams(Skin.dp(host, 42), Skin.dp(host, 42)));

        LinearLayout texts = new LinearLayout(host);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Skin.dp(host, 14);
        texts.addView(Skin.medium(host, "从相册取色", Skin.T_CARD, Skin.text(host)));
        TextView sub = Skin.text(host, "从照片里抽出 4 个主色", Skin.T_CAPTION, Skin.text3(host));
        sub.setPadding(0, Skin.dp(host, 3), 0, 0);
        texts.addView(sub);
        row.addView(texts, tlp);
        row.addView(Skin.icon(host, R.drawable.lc_chevron, Skin.text3(host), 20f));
        card.addView(row);

        card.setContentDescription("从相册取色，从照片里抽出 4 个主色");
        Tap.card(card, Surface.CARD);
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                host.openPick();
            }
        });
        return card;
    }

    // ---------------------------------------------------------------- 收藏的配色

    private void fillPalettes(List<String> keys) {
        palettesBlock.removeAllViews();
        final DataStore s = host.store();
        palettesBlock.addView(Page.section(host, "收藏的配色", keys.size() + " 组"));
        if (keys.isEmpty()) {
            LinearLayout wrap = Page.wrap(host);
            Page.addFull(wrap, Page.empty(host, "还没有收藏的配色",
                    "在配色详情页点右上角的星标，就能把这组颜色存下来。",
                    "去配色页看看", new View.OnClickListener() {
                        public void onClick(View v) {
                            host.switchTo(MainActivity.TAB_PALETTE);
                        }
                    }));
            palettesBlock.addView(wrap);
            return;
        }
        for (String key : keys) {
            final String k = key;
            final int[] colors = host.favs().resolvePalette(s, k);
            if (colors == null) continue;
            final String label = host.favs().resolvePaletteLabel(s, k);
            LinearLayout wrap = Page.wrap(host);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Skin.dp(host, 10);
            wrap.addView(paletteItem(colors, label, k), lp);
            palettesBlock.addView(wrap);
        }
    }

    /** 一条收藏配色：40dp 色带 + 名称，整卡可点开详情。 */
    private View paletteItem(final int[] colors, final String label, final String key) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        float r = 16f;
        card.setBackground(Skin.stroke(host, Skin.cardColor(host), Skin.cardStroke(host), r, 1f));
        Surface.card(card, r, Surface.CARD);
        final View band = BandView.of(host, colors, 40, r, true);
        card.addView(band);

        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Skin.dp(host, 14), Skin.dp(host, 10), Skin.dp(host, 14), Skin.dp(host, 10));
        String title = label == null || label.isEmpty() ? "配色" : label;
        TextView name = Skin.medium(host, title, Skin.T_BODY, Skin.text(host));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Skin.icon(host, R.drawable.lc_chevron, Skin.text3(host), 18f));
        card.addView(row);

        card.setContentDescription("收藏的配色，" + title + "，轻点查看");
        Tap.card(card, Surface.CARD);
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                host.openPaletteFrom(colors, label, key, band);
            }
        });
        return card;
    }

    // ---------------------------------------------------------------- 收藏的颜色

    private void fillColors(List<String> keys) {
        colorsBlock.removeAllViews();
        colorsBlock.addView(Page.section(host, "收藏的颜色", keys.size() + " 个"));
        if (keys.isEmpty()) {
            LinearLayout wrap = Page.wrap(host);
            Page.addFull(wrap, Page.empty(host, "还没有收藏的颜色",
                    "在单色详情页点右上角的星标，就能把这个颜色存下来。",
                    "去色卡里挑颜色", new View.OnClickListener() {
                        public void onClick(View v) {
                            host.switchTo(MainActivity.TAB_CARDS);
                        }
                    }));
            colorsBlock.addView(wrap);
            return;
        }
        LinearLayout wrap = Page.wrap(host);
        LinearLayout row = null;
        int n = keys.size();
        for (int i = 0; i < n; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(host);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowLp.topMargin = Skin.dp(host, 14);
                wrap.addView(row, rowLp);
            }
            row.addView(colorCell(keys.get(i)), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        int rest = n % 4;
        if (rest != 0 && row != null) {
            for (int i = rest; i < 4; i++) {
                row.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
        colorsBlock.addView(wrap);
    }

    /** 4 列网格里的一格：56dp 色块 + 名称小字。 */
    private View colorCell(final String key) {
        final int color = resolveColor(key);
        String name = colorName(key);
        final String label = name.isEmpty() ? ColorMath.hex(color) : name;

        LinearLayout cell = new LinearLayout(host);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(0, Skin.dp(host, 4), 0, Skin.dp(host, 4));

        final View swatch = SwatchView.of(host, color, 56, 8);
        cell.addView(swatch);
        TextView nameView = Skin.text(host, label, Skin.T_CAPTION, Skin.text3(host));
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        nameView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Skin.dp(host, 6);
        nlp.gravity = Gravity.CENTER_HORIZONTAL;
        cell.addView(nameView, nlp);

        cell.setContentDescription(label + " " + ColorMath.hex(color) + "，轻点查看");
        Tap.row(cell, 12f);
        cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                host.openColorFrom(color, "", key, swatch);
            }
        });
        return cell;
    }

    // ---------------------------------------------------------------- 最近查看

    private void fillRecents(List<String> keys) {
        recentsBlock.removeAllViews();
        if (keys.isEmpty()) {
            recentsBlock.setVisibility(View.GONE);
            return;
        }
        recentsBlock.setVisibility(View.VISIBLE);
        recentsBlock.addView(Page.section(host, "最近查看", "清空", new View.OnClickListener() {
            public void onClick(View v) {
                clearRecents();
            }
        }));
        LinearLayout card = Skin.listCard(host);
        int shown = 0;
        for (String key : keys) {
            if (card.getChildCount() > 0) card.addView(Page.hairline(host));
            card.addView(recentRow(key));
            if (++shown >= RECENT_MAX) break;
        }
        LinearLayout wrap = Page.wrap(host);
        Page.addFull(wrap, card);
        recentsBlock.addView(wrap);
    }

    private View recentRow(final String key) {
        final int color = resolveColor(key);
        String name = colorName(key);
        final String hex = ColorMath.hex(color);
        // 取色结果的"名字"就是个 HEX，别再拿它当副标重复一遍
        boolean picked = name.startsWith("#");
        final String titleText = picked ? "取色" : (name.isEmpty() ? hex : name);
        String set = picked ? "" : Resolve.colorLabel(host.store(), key);

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
        if (set != null && set.length() > 0) {
            TextView sub = Skin.text(host, set, Skin.T_CAPTION, Skin.text3(host));
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(sub);
        }
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

    /** 清空可撤销：先记住旧列表，撤销时整体写回。 */
    private void clearRecents() {
        final List<String> old = host.favs().recents();
        host.favs().clearRecents();
        refreshAll();
        Snack.show(content, "已清空最近查看", "撤销", new Runnable() {
            public void run() {
                host.favs().setRecents(old);
                refreshAll();
            }
        });
    }

    // ---------------------------------------------------------------- 关于

    private void fillAbout() {
        aboutBlock.removeAllViews();
        aboutBlock.addView(Page.section(host, "关于", null));
        LinearLayout card = Skin.listCard(host);
        addAboutRow(card, "配色数据", "13,665 组 · 全部在本机");
        addAboutRow(card, "色号数据", "20,969 个 · 含传统色 874");
        addAboutRow(card, "权限", "不申请任何权限");
        addAboutRow(card, "版本", versionName());
        addAboutRow(card, "许可", "MIT");
        addAboutLinkRow(card, "开源", "MIT · GitHub", new View.OnClickListener() {
            public void onClick(View v) {
                showOpenSource();
            }
        });
        addAboutLinkRow(card, "赞助作者", "扫码支持", new View.OnClickListener() {
            public void onClick(View v) {
                showSponsor();
            }
        });

        final String crash = Crash.last(host);
        if (crash != null) {
            if (card.getChildCount() > 0) card.addView(Page.hairline(host));
            card.addView(Rows.row(host, "上次崩溃", "点击复制", true,
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            Snack.copy(v, crash, "崩溃日志已复制（" + crash.length() + " 字符）");
                        }
                    }));
            if (card.getChildCount() > 0) card.addView(Page.hairline(host));
            card.addView(Rows.row(host, "清除崩溃记录", "", true,
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            Crash.clear(host);
                            fillAbout();
                        }
                    }));
        }

        LinearLayout wrap = Page.wrap(host);
        Page.addFull(wrap, card);
        aboutBlock.addView(wrap);
        aboutBlock.addView(tail());
    }

    private void addAboutRow(LinearLayout card, String label, String value) {
        if (card.getChildCount() > 0) card.addView(Page.hairline(host));
        card.addView(Rows.row(host, label, value, false, null));
    }

    /** 「关于」里的可点行：整行可点，右侧带 chevron。 */
    private void addAboutLinkRow(LinearLayout card, String label, String value,
                                 View.OnClickListener l) {
        if (card.getChildCount() > 0) card.addView(Page.hairline(host));
        card.addView(Rows.row(host, label, value, true, l));
    }

    // ---------------------------------------------------------------- 开源 / 赞助

    /** 「开源」模态卡：说明协议、展示仓库地址、复制或打开。 */
    private void showOpenSource() {
        final LinearLayout card = Overlay.card(host);
        card.addView(Skin.medium(host, "开源", Skin.T_CARD, Skin.text(host)));

        TextView body = Skin.text(host, "观色是 MIT 协议的开源项目，代码和数据都在 GitHub 上。",
                Skin.T_BODY, Skin.text2(host));
        body.setLineSpacing(Skin.dp(host, 4), 1f);
        body.setPadding(0, Skin.dp(host, 8), 0, 0);
        card.addView(body);

        LinearLayout urlBox = new LinearLayout(host);
        urlBox.setOrientation(LinearLayout.HORIZONTAL);
        urlBox.setGravity(Gravity.CENTER_VERTICAL);
        urlBox.setBackground(Skin.round(host, Skin.tint(Skin.text(host), 0.06f), 12f));
        urlBox.setPadding(Skin.dp(host, 12), Skin.dp(host, 10),
                Skin.dp(host, 12), Skin.dp(host, 10));
        urlBox.addView(Skin.icon(host, R.drawable.lc_terminal, Skin.text3(host), 16f));
        TextView url = Skin.text(host, GITHUB_DISPLAY, Skin.T_BODY, Skin.text2(host));
        url.setSingleLine(true);
        url.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        urlLp.leftMargin = Skin.dp(host, 8);
        urlBox.addView(url, urlLp);
        LinearLayout.LayoutParams urlBoxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        urlBoxLp.topMargin = Skin.dp(host, 14);
        card.addView(urlBox, urlBoxLp);

        final TextView copyBtn = Skin.pill(host, "复制地址", false);
        Tap.chip(copyBtn);
        final TextView openBtn = Skin.pill(host, "打开链接", false);
        Tap.chip(openBtn);
        LinearLayout btnRow = new LinearLayout(host);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.addView(copyBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        openLp.leftMargin = Skin.dp(host, 10);
        btnRow.addView(openBtn, openLp);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.topMargin = Skin.dp(host, 16);
        card.addView(btnRow, btnRowLp);

        final TextView okBtn = Skin.pill(host, "知道了", true);
        Tap.chip(okBtn);
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okLp.topMargin = Skin.dp(host, 10);
        card.addView(okBtn, okLp);

        // layer 是 show() 的返回值，所以按钮的点击事件只能在弹卡之后挂上
        final View layer = Overlay.show(host, card);
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Snack.copy(copyBtn, GITHUB_URL);
            }
        });
        openBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openGithub();
            }
        });
        okBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Overlay.dismiss(layer);
            }
        });
    }

    /** 「赞助作者」模态卡：二维码每次用随机一组配色的两色重新上色，但扫出来是同一个码。 */
    private void showSponsor() {
        final LinearLayout card = Overlay.card(host);
        card.setMinimumWidth(Skin.dp(host, 286));
        card.addView(Skin.medium(host, "赞助作者", Skin.T_CARD, Skin.text(host)));
        TextView sub = Skin.text(host, "扫码请作者喝一杯咖啡", Skin.T_NOTE, Skin.text2(host));
        sub.setPadding(0, Skin.dp(host, 4), 0, 0);
        card.addView(sub);

        int[] colors = randomPalette();
        QrView qr = QrView.of(host);
        qr.colorFrom(colors[0], colors[3]);
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(
                Skin.dp(host, 216), Skin.dp(host, 216));
        qrLp.gravity = Gravity.CENTER_HORIZONTAL;
        qrLp.topMargin = Skin.dp(host, 16);
        card.addView(qr, qrLp);

        TextView note = Skin.text(host, "每次颜色不同，扫出来是同一个收款码。",
                Skin.T_CAPTION, Skin.text3(host));
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(Skin.dp(host, 3), 1f);
        note.setPadding(0, Skin.dp(host, 12), 0, 0);
        card.addView(note);

        final TextView closeBtn = Skin.pill(host, "关闭", true);
        Tap.chip(closeBtn);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.topMargin = Skin.dp(host, 14);
        card.addView(closeBtn, closeLp);

        final View layer = Overlay.show(host, card);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Overlay.dismiss(layer);
            }
        });
    }

    /** 随便来一组配色给二维码上色；数据异常就退回墨/纸色，别让弹卡失败。 */
    private int[] randomPalette() {
        try {
            DataStore s = host.store();
            int n = s.paletteCount();
            if (n > 0) return s.palette(random.nextInt(n));
        } catch (Throwable ignored) {
            // 取不到数据不该影响"赞助"入口
        }
        return new int[]{0xFF17171A, 0xFFF6F5F2, 0xFFE07A3F, 0xFF459DFF};
    }

    private void openGithub() {
        try {
            host.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)));
        } catch (Exception e) {
            host.tell(content, "没有可用的浏览器，地址已在上方");
        }
    }

    /** 页面底部 24dp 留白（tab 页在底栏之上，不用再加 Bars.bottom）。 */
    private View tail() {
        View v = new View(host);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Skin.dp(host, 24)));
        return v;
    }

    // ---------------------------------------------------------------- 小件

    private LinearLayout block() {
        LinearLayout v = new LinearLayout(host);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    /** 卡片统一左右 20dp 纸边；topPad 用来拉开块与块的距离。 */
    private View inset(View child, int topPad) {
        LinearLayout wrap = Page.wrap(host);
        wrap.setPadding(Skin.dp(host, Page.PAD), topPad, Skin.dp(host, Page.PAD), 0);
        Page.addFull(wrap, child);
        return wrap;
    }

    /** 版本号从 PackageManager 读，别写死（换版本时会忘） */
    private String versionName() {
        try {
            String v = host.getPackageManager().getPackageInfo(host.getPackageName(), 0).versionName;
            return v == null || v.length() == 0 ? "—" : v;
        } catch (Exception e) {
            return "—";
        }
    }
}
