package com.wwxiaoqi.guanshe;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.core.ColorMath;
import com.wwxiaoqi.guanshe.core.Favorites;
import com.wwxiaoqi.guanshe.data.DataStore;
import com.wwxiaoqi.guanshe.data.Store;
import com.wwxiaoqi.guanshe.ui.BandView;
import com.wwxiaoqi.guanshe.ui.ColorWall;
import com.wwxiaoqi.guanshe.ui.Bars;
import com.wwxiaoqi.guanshe.ui.BaseActivity;
import com.wwxiaoqi.guanshe.ui.CrashScreen;
import com.wwxiaoqi.guanshe.ui.FavStore;
import com.wwxiaoqi.guanshe.ui.Haptics;
import com.wwxiaoqi.guanshe.ui.Motion;
import com.wwxiaoqi.guanshe.ui.Page;
import com.wwxiaoqi.guanshe.ui.Snack;
import com.wwxiaoqi.guanshe.ui.PullScrollView;
import com.wwxiaoqi.guanshe.ui.PullToShare;
import com.wwxiaoqi.guanshe.ui.ShareCards;
import com.wwxiaoqi.guanshe.ui.Surface;
import com.wwxiaoqi.guanshe.ui.SwatchView;
import com.wwxiaoqi.guanshe.ui.Tap;

/**
 * 配色详情（specs/palette-library · 数值与复制 + 相似配色）。
 *
 * 结构：色带钉在屏幕最上沿（顶到状态栏后面），白 sheet 像一张纸从下方滑上来盖住色带下缘。
 * 滚动时 sheet 按 1:1 上移、色带按 0.35 上移并轻微放大，于是色带看起来"被推走"而不是跟着滚，
 * 页面也就有了前后两层，而不是一条长列表。
 *
 * 色带缩回去之后，返回 / 星标两个圆钮会从半透明变成带描边的白色按扣 —— 否则压在白 sheet 上会看不见。
 *
 * 投影只给真正"浮"起来的东西：这一页唯一有影的是白 sheet（Skin.sheet 自带 SHEET 影），
 * 色块 / 圆钮 / 相似配色色带一律不抬影（Surface.elevate(..., 0f)），层次靠描边与底色差。
 */
public class PaletteDetailActivity extends BaseActivity {

    private static final float HERO_H = 256f;
    private static final float SHEET_OVERLAP = 28f;
    private static final float PARALLAX = 0.35f;
    private static final float PARALLAX_MAX_SCALE = 1.08f;

    private int[] colors;
    private String label;
    private String favKey;
    private FavStore favs;

    private FrameLayout hero;
    private PullScrollView scroller;
    private LinearLayout sheet;
    private LinearLayout navLayer;
    private LinearLayout backBox;
    private LinearLayout starBox;
    private LinearLayout shareBox;
    private ImageView backIcon;
    private ImageView starIcon;
    private ImageView shareIcon;
    private TextView favPill;
    private TextView shareHint;
    private float pullStartY;
    private float pullMax;
    private boolean pulling;

    private boolean bandLight;
    private boolean motionOn;
    private boolean navOverSheet;
    private boolean iconsOnSheet;
    private int navIconColor;
    private int overSheetAt;
    private int iconsFlipAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUi();
        } catch (Throwable e) {
            // 页面起不来就别让进程退出：把堆栈画在屏幕上，用户能截图/复制
            CrashScreen.show(this, "配色详情", e);
        }
    }

    private void buildUi() {
        colors = getIntent().getIntArrayExtra("colors");
        label = getIntent().getStringExtra("label");
        favKey = getIntent().getStringExtra("favkey");
        if (colors == null || colors.length < 4) {
            finish();
            return;
        }
        if (label == null) label = "";
        favs = new FavStore(this);
        if (favKey != null) favs.touchRecent(favKey);

        bandLight = Skin.isLightColor(colors[0]);
        motionOn = Motion.enabled(this);

        final int heroH = Skin.dp(this, HERO_H);
        final int overlap = Skin.dp(this, SHEET_OVERLAP);
        final int navTop = Bars.top(this) + Skin.dp(this, 6);
        final int sheetTop = heroH - overlap;
        overSheetAt = Math.max(0, sheetTop - navTop - Skin.dp(this, 48));
        iconsFlipAt = Math.max(0, sheetTop);

        FrameLayout root = new FrameLayout(this);
        // 整屏颜色墙：白 sheet 拽下来时露出的底，就是这组颜色本身（和顶部色带同源）
        root.addView(new ColorWall(this, colors), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 色带固定在窗口最上层（不加任何 top padding），状态栏压在它上面
        hero = new FrameLayout(this);
        hero.addView(BandView.of(this, colors, HERO_H, 0, false),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(hero, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                heroH, Gravity.TOP));

        // 滚动内容：先让出 hero 的高度，再让白 sheet 向上盖住 28dp
        scroller = Page.pullScroller(this);
        LinearLayout column = Page.column(this);
        View heroPlaceholder = new View(this);
        heroPlaceholder.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heroH));
        column.addView(heroPlaceholder);

        sheet = Skin.sheet(this);
        sheet.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 20), Skin.dp(this, Page.PAD),
                Skin.dp(this, 24));
        LinearLayout.LayoutParams sheetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sheetLp.topMargin = -overlap;
        column.addView(sheet, sheetLp);

        buildSheetContent();
        scroller.addView(column);
        root.addView(scroller, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        buildNav(root, navTop);
        setContentView(root);
        padBottomToBars(sheet, 24);

        // 内容比一屏短时让白纸铺到底，避免屏幕下半截露出页底色
        scroller.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        int h = scroller.getHeight();
                        if (h <= 0) return;
                        ViewTreeObserver vto = scroller.getViewTreeObserver();
                        if (vto.isAlive()) vto.removeOnGlobalLayoutListener(this);
                        int need = h - sheetTop;
                        if (need > sheet.getHeight()) sheet.setMinimumHeight(need);
                    }
                });

        Bars.icons(this, colors[0]);

        scroller.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            public void onScrollChange(View v, int sx, int sy, int osx, int osy) {
                onScrolled(sy);
            }
        });

        installPullShare(root, navTop);

        Motion.fadeIn(hero, 0);
        Motion.riseIn(sheet, 80);
        Motion.fadeIn(navLayer, 60);
    }

    // ---------------------------------------------------------------- 内容
    private void buildSheetContent() {
        TextView title = Skin.medium(this, label.isEmpty() ? "配色" : label,
                Skin.T_TITLE, Skin.text(this));
        title.setLetterSpacing(-0.02f);
        sheet.addView(title);

        TextView hint = Skin.text(this,
                colors.length + " 色 · 轻点色值复制 · 长按复制整组 · 下拉分享",
                Skin.T_NOTE, Skin.text2(this));
        hint.setPadding(0, Skin.dp(this, 4), 0, Skin.dp(this, 8));
        sheet.addView(hint);

        for (int i = 0; i < colors.length; i++) {
            if (i > 0) sheet.addView(Page.hairline(this));
            sheet.addView(colorRow(i));
        }

        // 操作区：复制全部 / 收藏（与顶部星标同一状态）
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, Skin.dp(this, 16), 0, 0);

        TextView copyAll = Skin.pill(this, "复制全部", true);
        Tap.chip(copyAll);
        copyAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Snack.copy(v, ColorMath.hexList(colors),
                        "已复制整组 " + colors.length + " 个色值");
            }
        });
        actions.addView(copyAll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        favPill = Skin.pill(this, isFav() ? "取消收藏" : "收藏", false);
        Tap.chip(favPill);
        favPill.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleFav(v);
            }
        });
        LinearLayout.LayoutParams favLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        favLp.leftMargin = Skin.dp(this, 10);
        actions.addView(favPill, favLp);
        sheet.addView(actions);

        buildSimilar();
    }

    private View colorRow(final int i) {
        final int c = colors[i];
        final String hex = ColorMath.hex(c);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Skin.dp(this, 56));
        row.setPadding(0, Skin.dp(this, 8), 0, Skin.dp(this, 8));

        View swatch = SwatchView.of(this, c, 44, 8);
        Surface.round(swatch, 8f);
        Surface.elevate(swatch, 0f);
        row.addView(swatch);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        midLp.leftMargin = Skin.dp(this, 14);
        TextView t = Skin.medium(this, hex, 15f, Skin.text(this));
        t.setLetterSpacing(0.04f);
        mid.addView(t);
        int[] rgb = ColorMath.rgb(c);
        float[] hsl = ColorMath.hsl(c);
        TextView sub = Skin.text(this, "RGB " + rgb[0] + ", " + rgb[1] + ", " + rgb[2]
                + " · HSL " + Math.round(hsl[0]) + "° " + Math.round(hsl[1]) + "% "
                + Math.round(hsl[2]) + "%", 11f, Skin.text3(this));
        sub.setPadding(0, Skin.dp(this, 3), 0, 0);
        mid.addView(sub);
        row.addView(mid, midLp);

        row.addView(Skin.icon(this, R.drawable.lc_copy, Skin.text3(this), 20f));

        row.setContentDescription("色值 " + hex + "，轻点复制，长按复制整组");
        Tap.row(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Snack.copy(v, hex);
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                Haptics.longPress(v);
                Snack.copy(v, ColorMath.hexList(colors),
                        "已复制整组 " + colors.length + " 个色值");
                return true;
            }
        });
        return row;
    }

    /** 同色系/同场景里相邻的几组（数据里相邻的通常色相相近）。 */
    private void buildSimilar() {
        int[] similar = similarPalettes();
        if (similar.length == 0) return;
        DataStore s = Store.get(this);

        TextView title = Skin.text(this, "相似配色", Skin.T_NOTE, Skin.text2(this));
        title.setLetterSpacing(0.05f);
        title.setPadding(0, Skin.dp(this, 24), 0, Skin.dp(this, 10));
        sheet.addView(title);

        for (final int idx : similar) {
            final int[] cs = s.palette(idx);
            final String simLabel = s.groupName(s.paletteKind(idx), s.paletteGroup(idx));
            final String simKey = Favorites.paletteKey(s.paletteKind(idx), idx);

            final BandView band = BandView.of(this, cs, 34, 10, false);
            Surface.round(band, 10f);
            Surface.elevate(band, 0f);

            // 色带本身 34dp 是视觉高度；外面套 48dp 的透明容器把可点区域补到规范要求
            FrameLayout tap = new FrameLayout(this);
            tap.addView(band, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Skin.dp(this, 34), Gravity.CENTER_VERTICAL));
            Motion.interact(tap, true, false, 0f);
            tap.setContentDescription("相似配色 " + simLabel + "，轻点打开");
            tap.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Haptics.tap(v);
                    Intent i = new Intent(PaletteDetailActivity.this,
                            PaletteDetailActivity.class);
                    i.putExtra("colors", cs);
                    i.putExtra("label", simLabel);
                    i.putExtra("favkey", simKey);
                    openFrom(i, band);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(this, 48));
            lp.bottomMargin = Skin.dp(this, 6);
            sheet.addView(tap, lp);
        }
    }

    // ---------------------------------------------------------------- 悬浮按钮
    private void buildNav(FrameLayout root, int navTop) {
        navLayer = new LinearLayout(this);
        navLayer.setOrientation(LinearLayout.HORIZONTAL);
        navLayer.setGravity(Gravity.CENTER_VERTICAL);
        navLayer.setPadding(Skin.dp(this, 12), 0, Skin.dp(this, 12), 0);

        backBox = circleButton(R.drawable.lc_back, "返回配色库", new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        backIcon = (ImageView) backBox.getChildAt(0);
        navLayer.addView(backBox, new LinearLayout.LayoutParams(Skin.dp(this, 48),
                Skin.dp(this, 48)));

        View spacer = new View(this);
        navLayer.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        shareBox = circleButton(R.drawable.lc_share, "分享这组配色", new View.OnClickListener() {
            public void onClick(View v) {
                openShareCards();
            }
        });
        shareIcon = (ImageView) shareBox.getChildAt(0);
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(Skin.dp(this, 48),
                Skin.dp(this, 48));
        shareLp.rightMargin = Skin.dp(this, 10);
        navLayer.addView(shareBox, shareLp);

        starBox = circleButton(R.drawable.lc_star, "收藏这组配色", new View.OnClickListener() {
            public void onClick(View v) {
                toggleFav(v);
            }
        });
        starIcon = (ImageView) starBox.getChildAt(0);
        navLayer.addView(starBox, new LinearLayout.LayoutParams(Skin.dp(this, 48),
                Skin.dp(this, 48)));

        applyNavStyle();
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        navLp.topMargin = navTop;
        root.addView(navLayer, navLp);
    }

    private LinearLayout circleButton(int res, String desc, View.OnClickListener l) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        Surface.round(box, 999f);
        Surface.elevate(box, 0f);
        ImageView icon = new ImageView(this);
        icon.setImageResource(res);
        box.addView(icon, new LinearLayout.LayoutParams(Skin.dp(this, 19), Skin.dp(this, 19)));
        box.setContentDescription(desc);
        Tap.chip(box);
        box.setOnClickListener(l);
        return box;
    }

    /**
     * 色带上的按钮用半透明底；滚到白 sheet 上时换成白底描边，并重新挂波纹。
     * 两个按钮各建一份 Drawable —— 同一个 Drawable 不能同时挂在两个 View 上。
     */
    private void applyNavStyle() {
        if (navOverSheet) {
            navIconColor = Skin.text(this);
        } else {
            navIconColor = bandLight ? 0xFF17171A : 0xFFFFFFFF;
        }
        backBox.setBackground(navBackground());
        Tap.chip(backBox);
        starBox.setBackground(navBackground());
        Tap.chip(starBox);
        shareBox.setBackground(navBackground());
        Tap.chip(shareBox);
        backIcon.setColorFilter(navIconColor);
        shareIcon.setColorFilter(navIconColor);
        paintStar();
    }

    private Drawable navBackground() {
        if (navOverSheet) {
            return Skin.stroke(this, Skin.cardColor(this), Skin.cardStroke(this), 999f, 1f);
        }
        return Skin.round(this, bandLight ? 0x40FFFFFF : 0x33000000, 999f);
    }

    private void paintStar() {
        if (starIcon == null) return;
        boolean on = isFav();
        starIcon.setImageResource(on ? R.drawable.lc_star_fill : R.drawable.lc_star);
        starIcon.setColorFilter(on ? Skin.warn(this) : navIconColor);
        starBox.setContentDescription(on ? "取消收藏这组配色" : "收藏这组配色");
        if (favPill != null) favPill.setText(on ? "取消收藏" : "收藏");
    }

    /** 顶部按钮：白纸滑出屏幕，暗场里弹出分享卡。 */
    private void openShareCards() {
        PullToShare.hideThenShow(this, scroller, new PullToShare.Listener() {
            public void onOpen() {
                showShareCards();
            }
        });
    }

    /** 白纸已经让位，卡片直接进场；暗场期间先收起顶部的返回/分享/星标。 */
    private void showShareCards() {
        if (navLayer != null) {
            navLayer.animate().alpha(0f).setDuration(160).start();
        }
        ShareCards.palette(this, colors, label, new Runnable() {
            public void run() {
                restoreAfterShare();
            }
        });
    }

    /** 卡片关闭后白纸滑回来，系统栏图标也跟着恢复。 */
    private void restoreAfterShare() {
        if (navLayer != null) {
            navLayer.animate().alpha(1f).setDuration(200).start();
        }
        PullToShare.restore(scroller);
        Bars.icons(this, scroller.getScrollY() >= overSheetAt
                ? Skin.cardColor(this) : colors[0]);
    }

    /** 下拉分享：白纸跟着手指下移，松手过阈值就甩出屏幕、弹分享卡。 */
    private void installPullShare(FrameLayout root, int navTop) {
        shareHint = Skin.pill(this, "松手，变成分享卡", false);
        shareHint.setAlpha(0f);
        shareHint.setClickable(false);
        shareHint.setFocusable(false);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hintLp.topMargin = navTop + Skin.dp(this, 54);
        root.addView(shareHint, hintLp);
        PullToShare.attach(this, scroller, shareHint, new PullToShare.Listener() {
            public void onOpen() {
                showShareCards();
            }
        });
    }

    // ---------------------------------------------------------------- 行为
    private boolean isFav() {
        return favKey != null && favs.isFav(favKey);
    }

    private void toggleFav(View v) {
        if (favKey == null) {
            Snack.show(v, "这组配色还没有收藏键");
            return;
        }
        boolean added = favs.toggleFav(favKey);
        paintStar();
        if (added) {
            Motion.pop(starIcon);
            Haptics.success(v);
            Snack.show(v, "已收藏这组配色");
        } else {
            Snack.show(v, "已取消收藏");
        }
    }

    /** 色带视差 + 到顶后换系统栏图标 / 悬浮按钮配色（只在状态翻转时做，避免每帧设系统栏）。 */
    private void onScrolled(int y) {
        if (motionOn) {
            hero.setTranslationY(-y * PARALLAX);
            float scale = 1f + y * 0.0003f;
            if (scale > PARALLAX_MAX_SCALE) scale = PARALLAX_MAX_SCALE;
            hero.setScaleX(scale);
            hero.setScaleY(scale);
        }
        boolean over = y >= overSheetAt;
        if (over != navOverSheet) {
            navOverSheet = over;
            applyNavStyle();
        }
        boolean onSheet = y >= iconsFlipAt;
        if (onSheet != iconsOnSheet) {
            iconsOnSheet = onSheet;
            Bars.icons(this, onSheet ? Skin.cardColor(this) : colors[0]);
        }
    }

    /** 数据里找"自己"，然后取同色系/同场景紧随其后的 3 组。 */
    private int[] similarPalettes() {
        DataStore s = Store.get(this);
        int self = -1;
        for (int i = 0; i < s.paletteCount(); i++) {
            if (sameColors(s.palette(i), colors)) {
                self = i;
                break;
            }
        }
        if (self < 0) return new int[0];
        int[] out = new int[3];
        int n = 0;
        for (int d = 1; d <= 6 && n < 3; d++) {
            int cand = self + d;
            if (cand < s.paletteCount() && s.paletteKind(cand) == s.paletteKind(self)
                    && s.paletteGroup(cand) == s.paletteGroup(self)) {
                out[n++] = cand;
            }
        }
        int[] trimmed = new int[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    private static boolean sameColors(int[] a, int[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }
}
