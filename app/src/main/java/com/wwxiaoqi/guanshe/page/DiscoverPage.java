package com.wwxiaoqi.guanshe.page;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
import com.wwxiaoqi.guanshe.ui.BandView;
import com.wwxiaoqi.guanshe.ui.Chips;
import com.wwxiaoqi.guanshe.ui.Haptics;
import com.wwxiaoqi.guanshe.ui.Motion;
import com.wwxiaoqi.guanshe.ui.Page;
import com.wwxiaoqi.guanshe.ui.Rows;
import com.wwxiaoqi.guanshe.ui.Snack;
import com.wwxiaoqi.guanshe.ui.ShareCards;
import com.wwxiaoqi.guanshe.ui.Surface;
import com.wwxiaoqi.guanshe.ui.Tap;

import java.util.List;
import java.util.Random;

/**
 * 发现页：一张"今日配色"的大卡 + 色系 / 场景两个分流入口 + 最近收藏。
 *
 * 相比旧版：
 * - 今日配色从一条裸色带变成一张有影子的卡，点色带进详情、点星标收藏、点"换一组"换一组；
 * - 页头加了一句"今日配色 · 第 N 组"，用户知道自己在看什么；
 * - 最近收藏改成一排可点的缩略卡，没有收藏时给"去挑一组"的出口。
 */
public class DiscoverPage extends BasePage {

    private static final int[] SHOWN_SCENES = {0, 1, 2, 3, 4, 5, 10, 11};   // 春夏秋冬 日落 深夜 婚礼 圣诞
    private static final float BAND_H = 208f;

    private final Random random = new Random(System.nanoTime());

    private int paletteIdx;
    private int[] colors;
    private String label;
    private String favKey;

    private FrameLayout bandHolder;
    private View band;
    private LinearLayout hexRow;
    private LinearLayout starBox;
    private TextView subTitle;
    private TextView heroLabel;
    private ImageView starIcon;
    private LinearLayout favHolder;
    private LinearLayout randomHolder;

    public DiscoverPage(MainActivity host) {
        super(host);
    }

    @Override
    protected View build() {
        DataStore s = host.store();
        paletteIdx = Today.pick(Today.daysSinceEpoch(System.currentTimeMillis()), s.paletteCount());
        colors = s.palette(paletteIdx);
        label = s.groupName(s.paletteKind(paletteIdx), s.paletteGroup(paletteIdx))
                + " · 第 " + (paletteIdx + 1) + " 组";
        favKey = Favorites.paletteKey(s.paletteKind(paletteIdx), paletteIdx);

        LinearLayout root = Page.root(host, Skin.bg(host), true);
        root.setBackgroundColor(0x00000000);

        // 底色与顶部色晕由 MainActivity 统一提供（透明页面才能看到纸底渐变）
        FrameLayout frame = new FrameLayout(host);
        frame.setBackgroundColor(0x00000000);

        root.addView(buildHeader());
        root.addView(buildHero());
        root.addView(buildRandomSection());

        // 色系
        root.addView(Page.section(host, "色系", "16 个"));
        String[] fams = new String[16];
        for (int i = 0; i < 16; i++) fams[i] = s.groupName(DataStore.KIND_FAMILY, i);
        Chips.Bar famBar = Chips.bar(host, fams, -1, true, new Chips.OnPick() {
            public void onPick(int index, String name) {
                host.filterPalettes(DataStore.KIND_FAMILY, index);
            }
        });
        root.addView(famBar.view);

        // 场景
        root.addView(Page.section(host, "场景", "28 个"));
        String[] scenes = new String[SHOWN_SCENES.length + 1];
        for (int i = 0; i < SHOWN_SCENES.length; i++) {
            scenes[i] = s.groupName(DataStore.KIND_SCENE, SHOWN_SCENES[i]);
        }
        scenes[SHOWN_SCENES.length] = "全部";
        Chips.Bar sceneBar = Chips.bar(host, scenes, -1, true, new Chips.OnPick() {
            public void onPick(int index, String name) {
                if (index >= SHOWN_SCENES.length) {
                    host.filterPalettes(DataStore.KIND_SCENE, -1);
                } else {
                    host.filterPalettes(DataStore.KIND_SCENE, SHOWN_SCENES[index]);
                }
            }
        });
        root.addView(sceneBar.view);

        // 最近收藏（会随收藏变化重建）
        favHolder = new LinearLayout(host);
        favHolder.setOrientation(LinearLayout.VERTICAL);
        root.addView(favHolder);
        refreshFavorites();

        // 脚注
        LinearLayout foot = Page.wrap(host);
        foot.setPadding(Skin.dp(host, Page.PAD), Skin.dp(host, 26),
                Skin.dp(host, Page.PAD), Skin.dp(host, 26));
        TextView count = Skin.text(host, "13,665 组配色 · 20,969 个色号", 11, Skin.text3(host));
        count.setLetterSpacing(0.02f);
        foot.addView(count);
        TextView offline = Skin.text(host, "全部数据在本机，连网也更新不了它", 11, Skin.text3(host));
        offline.setPadding(0, Skin.dp(host, 3), 0, 0);
        foot.addView(offline);
        root.addView(foot);

        ScrollView sc = Page.scroller(host);
        sc.addView(root);
        frame.addView(sc, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroller = sc;

        final LinearLayout entrance = root;
        entrance.post(new Runnable() {
            public void run() {
                Motion.staggerIn(entrance, 0, 8);
            }
        });
        return frame;
    }

    @Override
    public void onShow() {
        refreshFavorites();
        paintStar(false);
    }

    // ---------------------------------------------------------------- 头部
    private View buildHeader() {
        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Skin.dp(host, Page.PAD), Skin.dp(host, 10),
                Skin.dp(host, Page.PAD - 12), Skin.dp(host, 6));

        LinearLayout left = new LinearLayout(host);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView title = Skin.medium(host, "观色", Skin.T_TITLE, Skin.text(host));
        title.setLetterSpacing(-0.02f);
        left.addView(title);
        subTitle = Skin.text(host, "今日配色 · " + label, Skin.T_NOTE, Skin.text2(host));
        subTitle.setPadding(0, Skin.dp(host, 3), 0, 0);
        left.addView(subTitle);
        head.addView(left);
        head.addView(Rows.iconButton(host, R.drawable.lc_search, Skin.text2(host),
                new View.OnClickListener() {
                    public void onClick(View v) {
                        host.openSearchFrom(v);
                    }
                }));
        return head;
    }

    // ---------------------------------------------------------------- 今日配色卡
    private View buildHero() {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        float r = 22f;
        card.setBackground(Skin.stroke(host, Skin.cardColor(host), Skin.cardStroke(host), r, 1f));
        Surface.card(card, r, 0f);

        bandHolder = new FrameLayout(host);
        band = newBand(colors);
        bandHolder.addView(band, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(host, BAND_H)));

        LinearLayout star = circleButton(R.drawable.lc_star, new View.OnClickListener() {
            public void onClick(View v) {
                toggleFav(v);
            }
        });
        star.setContentDescription("收藏这组配色");
        starBox = star;
        starIcon = (ImageView) star.getChildAt(0);
        paintStar(false);
        FrameLayout.LayoutParams starLp = new FrameLayout.LayoutParams(
                Skin.dp(host, 44), Skin.dp(host, 44), Gravity.TOP | Gravity.END);
        starLp.topMargin = Skin.dp(host, 12);
        starLp.rightMargin = Skin.dp(host, 12);
        bandHolder.addView(star, starLp);

        LinearLayout share = circleButton(R.drawable.lc_share, new View.OnClickListener() {
            public void onClick(View v) {
                String name = heroLabel == null ? "今日配色" : heroLabel.getText().toString();
                ShareCards.palette(host, colors, name, null);
            }
        });
        share.setContentDescription("分享这组配色");
        FrameLayout.LayoutParams shareLp = new FrameLayout.LayoutParams(
                Skin.dp(host, 44), Skin.dp(host, 44), Gravity.TOP | Gravity.END);
        shareLp.topMargin = Skin.dp(host, 12);
        shareLp.rightMargin = Skin.dp(host, 12) + Skin.dp(host, 44) + Skin.dp(host, 10);
        bandHolder.addView(share, shareLp);
        card.addView(bandHolder);

        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Skin.dp(host, 18), Skin.dp(host, 14), Skin.dp(host, 18), Skin.dp(host, 16));

        LinearLayout topRow = new LinearLayout(host);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView lab = Skin.text(host, label, Skin.T_NOTE, Skin.text2(host));
        heroLabel = lab;
        lab.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(lab);
        topRow.addView(textButton(R.drawable.lc_refresh, "换一组", new View.OnClickListener() {
            public void onClick(View v) {
                shuffle();
            }
        }));
        body.addView(topRow);

        hexRow = new LinearLayout(host);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setPadding(0, Skin.dp(host, 10), 0, 0);
        rebuildHexRow();
        body.addView(hexRow);

        TextView hint = Skin.text(host, "轻点色值复制 · 长按色带复制整组", 11, Skin.text3(host));
        hint.setPadding(0, Skin.dp(host, 8), 0, 0);
        body.addView(hint);

        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, Skin.dp(host, 14), 0, 0);
        TextView detail = Skin.pill(host, "查看详情", true);
        Tap.chip(detail);
        detail.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                host.openPaletteFrom(colors, label, favKey, band);
            }
        });
        actions.addView(detail, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout copyAll = circleButton(R.drawable.lc_copy, new View.OnClickListener() {
            public void onClick(View v) {
                Snack.copy(v, ColorMath.hexList(colors));
            }
        });
        copyAll.setContentDescription("复制这组配色");
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                Skin.dp(host, 44), Skin.dp(host, 44));
        copyLp.leftMargin = Skin.dp(host, 10);
        actions.addView(copyAll, copyLp);
        body.addView(actions);
        card.addView(body);

        LinearLayout wrap = Page.wrap(host);
        Page.addFull(wrap, card);
        return wrap;
    }

    private View newBand(final int[] cs) {
        View v = BandView.of(host, cs, BAND_H, 22, true);
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                host.openPaletteFrom(colors, label, favKey, view);
            }
        });
        v.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View view) {
                Haptics.longPress(view);
                Snack.copy(view, ColorMath.hexList(colors));
                return true;
            }
        });
        v.setContentDescription("配色色带，" + ColorMath.hexList(cs));
        return v;
    }

    private void rebuildHexRow() {
        hexRow.removeAllViews();
        for (int i = 0; i < colors.length; i++) {
            final int c = colors[i];
            TextView t = Skin.medium(host, ColorMath.hex(c), 11, Skin.text2(host));
            t.setGravity(Gravity.CENTER);
            t.setLetterSpacing(0.04f);
            t.setBackground(Skin.round(host, Skin.tint(Skin.text(host), 0.05f), 9f));
            t.setPadding(0, Skin.dp(host, 9), 0, Skin.dp(host, 9));
            t.setContentDescription("色值 " + ColorMath.hex(c) + "，点击复制");
            Tap.chip(t);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Snack.copy(v, ColorMath.hex(c));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = Skin.dp(host, 6);
            hexRow.addView(t, lp);
        }
    }

    /** 换一组：优先换到相邻序号（色彩关系更近），淡出旧色带再淡入新色带。 */
    private void shuffle() {
        DataStore s = host.store();
        int n = s.paletteCount();
        if (n <= 1) return;
        int next = paletteIdx;
        int guard = 0;
        while (next == paletteIdx && guard++ < 24) {
            next = (paletteIdx + 1 + random.nextInt(n - 1)) % n;
        }
        paletteIdx = next;
        colors = s.palette(paletteIdx);
        label = s.groupName(s.paletteKind(paletteIdx), s.paletteGroup(paletteIdx))
                + " · 第 " + (paletteIdx + 1) + " 组";
        favKey = Favorites.paletteKey(s.paletteKind(paletteIdx), paletteIdx);
        subTitle.setText("随机配色 · " + label);
        if (heroLabel != null) heroLabel.setText(label);

        if (Motion.enabled(host)) {
            final View old = band;
            old.animate().alpha(0f).translationY(Skin.dp(host, 6))
                    .setDuration(Motion.FAST).setInterpolator(Motion.ease())
                    .withEndAction(new Runnable() {
                        public void run() {
                            swapBand();
                            band.setAlpha(0f);
                            band.setTranslationY(Skin.dp(host, -8));
                            band.animate().alpha(1f).translationY(0f)
                                    .setDuration(Motion.BASE).setInterpolator(Motion.out()).start();
                        }
                    }).start();
        } else {
            swapBand();
        }
        rebuildHexRow();
        paintStar(false);
        Haptics.select(band);
    }

    private void swapBand() {
        int index = bandHolder.indexOfChild(band);
        bandHolder.removeView(band);
        band = newBand(colors);
        bandHolder.addView(band, index < 0 ? 0 : index, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(host, BAND_H)));
    }

    // ---------------------------------------------------------------- 随手翻
    /** 发现页不该只有"今日一组"：再放 6 张随机配色卡，点「换一批」整组换掉。 */
    private View buildRandomSection() {
        LinearLayout section = new LinearLayout(host);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Skin.dp(host, Page.PAD), Skin.dp(host, 24),
                Skin.dp(host, Page.PAD - 6), Skin.dp(host, 6));
        TextView title = Skin.text(host, "随手翻", Skin.T_NOTE, Skin.text2(host));
        title.setLetterSpacing(0.05f);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(title);
        head.addView(textButton(R.drawable.lc_refresh, "换一批", new View.OnClickListener() {
            public void onClick(View v) {
                Haptics.shuffle(v);
                refreshRandom(true);
            }
        }));
        section.addView(head);

        randomHolder = new LinearLayout(host);
        randomHolder.setOrientation(LinearLayout.VERTICAL);
        section.addView(randomHolder);
        refreshRandom(false);
        return section;
    }

    private void refreshRandom(boolean animate) {
        if (randomHolder == null) return;
        DataStore s = host.store();
        int n = s.paletteCount();
        if (n <= 0) return;
        int[] picked = new int[Math.min(6, n)];
        java.util.Set<Integer> used = new java.util.HashSet<Integer>();
        for (int i = 0; i < picked.length; i++) {
            int idx = random.nextInt(n);
            int guard = 0;
            while (used.contains(idx) && guard++ < 60) idx = random.nextInt(n);
            used.add(idx);
            picked[i] = idx;
        }

        randomHolder.removeAllViews();
        for (int i = 0; i < picked.length; i += 2) {
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = Skin.dp(host, i == 0 ? 4 : 12);
            rowLp.leftMargin = Skin.dp(host, Page.PAD);
            rowLp.rightMargin = Skin.dp(host, Page.PAD);
            row.addView(randomCard(picked[i]), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (i + 1 < picked.length) {
                LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                right.leftMargin = Skin.dp(host, 12);
                row.addView(randomCard(picked[i + 1]), right);
            } else {
                View spacer = new View(host);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                sp.leftMargin = Skin.dp(host, 12);
                row.addView(spacer, sp);
            }
            randomHolder.addView(row, rowLp);
        }
        if (animate) Motion.staggerIn(randomHolder, 0, 6);
    }

    private View randomCard(final int idx) {
        DataStore s = host.store();
        final int[] cs = s.palette(idx);
        final String name = s.groupName(s.paletteKind(idx), s.paletteGroup(idx));
        final String favKey = Favorites.paletteKey(s.paletteKind(idx), idx);

        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        float r = 14f;
        card.setBackground(Skin.stroke(host, Skin.cardColor(host), Skin.cardStroke(host), r, 1f));
        Surface.card(card, r, 0f);

        View band = BandView.of(host, cs, 88, r, true);
        card.addView(band, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(host, 88)));

        LinearLayout cap = new LinearLayout(host);
        cap.setOrientation(LinearLayout.HORIZONTAL);
        cap.setGravity(Gravity.CENTER_VERTICAL);
        cap.setPadding(Skin.dp(host, 10), Skin.dp(host, 8),
                Skin.dp(host, 10), Skin.dp(host, 9));
        TextView label = Skin.text(host, name, 11.5f, Skin.text2(host));
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cap.addView(label);
        cap.addView(Skin.text(host, ColorMath.hex(cs[0]), 9.5f, Skin.text3(host)));
        card.addView(cap);

        final String full = name + " · 第 " + (idx + 1) + " 组";
        card.setContentDescription("配色，" + name);
        Tap.card(card);
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                host.openPaletteFrom(cs, full, favKey, band);
            }
        });
        return card;
    }

    // ---------------------------------------------------------------- 最近收藏
    private void refreshFavorites() {
        if (favHolder == null) return;
        favHolder.removeAllViews();
        DataStore s = host.store();
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Skin.dp(host, Page.PAD), 0, Skin.dp(host, Page.PAD), 0);

        int shown = 0;
        List<String> favs = host.favs().favorites();
        for (final String key : favs) {
            int[] pk = Favorites.parsePaletteKey(key);
            if (pk == null) continue;
            final int[] pc = host.favs().resolvePalette(s, key);
            if (pc == null) continue;
            final String name = host.favs().resolvePaletteLabel(s, key);
            row.addView(favCard(pc, name, key), favLp(shown > 0));
            shown++;
            if (shown >= 4) break;
        }

        favHolder.addView(Page.section(host, "最近收藏", shown == 0 ? "" : shown + " 组"));
        if (shown == 0) {
            LinearLayout wrap = Page.wrap(host);
            Page.addFull(wrap, Page.empty(host, "还没有收藏的配色",
                    "在配色详情页点右上角的星标，就会出现在这里。",
                    "去挑一组", new View.OnClickListener() {
                        public void onClick(View v) {
                            host.switchTo(MainActivity.TAB_PALETTE);
                        }
                    }));
            favHolder.addView(wrap);
            return;
        }
        HorizontalScrollView hv = new HorizontalScrollView(host);
        hv.setHorizontalScrollBarEnabled(false);
        hv.setClipToPadding(false);
        hv.addView(row);
        favHolder.addView(hv);
    }

    private View favCard(final int[] pc, String name, final String key) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        float r = 14f;
        card.setBackground(Skin.stroke(host, Skin.cardColor(host), Skin.cardStroke(host), r, 1f));
        Surface.card(card, r, Surface.CARD);
        View band = BandView.of(host, pc, 46, r, true);
        card.addView(band, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(host, 46)));
        TextView t = Skin.text(host, name, 10.5f, Skin.text2(host));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(Skin.dp(host, 10), Skin.dp(host, 8), Skin.dp(host, 10), Skin.dp(host, 9));
        card.addView(t);
        card.setContentDescription("收藏的配色 " + name);
        Tap.card(card);
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                host.openPaletteFrom(pc, host.favs().resolvePaletteLabel(host.store(), key), key, v);
            }
        });
        return card;
    }

    private LinearLayout.LayoutParams favLp(boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Skin.dp(host, 132), ViewGroup.LayoutParams.WRAP_CONTENT);
        if (gap) lp.leftMargin = Skin.dp(host, 12);
        return lp;
    }

    // ---------------------------------------------------------------- 通用小件
    private LinearLayout circleButton(int res, View.OnClickListener l) {
        LinearLayout box = new LinearLayout(host);
        box.setGravity(Gravity.CENTER);
        box.setBackground(Skin.round(host,
                Skin.isLightColor(colors[0]) ? 0x40FFFFFF : 0x33000000, 999f));
        Surface.elevate(box, 0f);   // 普通按钮不抬影，层次交给半透明底色
        ImageView icon = new ImageView(host);
        icon.setImageResource(res);
        icon.setColorFilter(Skin.isLightColor(colors[0]) ? 0xFF17171A : 0xFFFFFFFF);
        box.addView(icon, new LinearLayout.LayoutParams(
                Skin.dp(host, 19), Skin.dp(host, 19)));
        Tap.chip(box);
        box.setOnClickListener(l);
        return box;
    }

    private View textButton(int res, String label, View.OnClickListener l) {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Skin.dp(host, 10), Skin.dp(host, 7), Skin.dp(host, 10), Skin.dp(host, 7));
        box.setBackground(Skin.round(host, Skin.tint(Skin.text(host), 0.05f), 999f));
        ImageView icon = Skin.icon(host, res, Skin.text2(host), 15f);
        box.addView(icon);
        TextView t = Skin.medium(host, label, 11.5f, Skin.text2(host));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Skin.dp(host, 5);
        box.addView(t, lp);
        Tap.chip(box);
        box.setOnClickListener(l);
        box.setContentDescription(label);
        return box;
    }

    private boolean isFav() {
        return favKey != null && host.favs().isFav(favKey);
    }

    private void paintStar(boolean animate) {
        if (starIcon == null) return;
        boolean on = isFav();
        starIcon.setImageResource(on ? R.drawable.lc_star_fill : R.drawable.lc_star);
        starIcon.setColorFilter(on ? Skin.warn(host)
                : (Skin.isLightColor(colors[0]) ? 0xFF17171A : 0xFFFFFFFF));
        if (starBox != null) {
            starBox.setBackground(Skin.round(host,
                    Skin.isLightColor(colors[0]) ? 0x40FFFFFF : 0x33000000, 999f));
        }
        if (animate) Motion.pop(starIcon);
    }

    private void toggleFav(View v) {
        if (favKey == null) return;
        boolean added = host.favs().toggleFav(favKey);
        paintStar(true);
        Haptics.success(v);
        Snack.show(v, added ? "已收藏这组配色" : "已取消收藏");
        refreshFavorites();
    }
}
