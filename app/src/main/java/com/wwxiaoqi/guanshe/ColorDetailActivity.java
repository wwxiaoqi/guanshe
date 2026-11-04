package com.wwxiaoqi.guanshe;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.core.ColorMath;
import com.wwxiaoqi.guanshe.data.DataStore;
import com.wwxiaoqi.guanshe.data.Store;
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
import com.wwxiaoqi.guanshe.ui.ColorWall;
import com.wwxiaoqi.guanshe.ui.ShareCards;
import com.wwxiaoqi.guanshe.ui.Surface;
import com.wwxiaoqi.guanshe.ui.SwatchView;
import com.wwxiaoqi.guanshe.ui.Tap;

/**
 * 单色详情（specs/color-cards · 单色详情）：HEX/RGB/HSL/CMYK + 黑白对比度 + 近似传统色。
 *
 * 和配色详情同一套骨架：大色块钉在顶上、白 sheet 盖住下缘、滚动时色块轻微视差。
 * 区别在于这张纸上的每一行都是"可复制的数值"，所以每行都带复制图标和整行点击；
 * 对比度行多一个 WCAG 等级徽标，用户不用自己拿计算器算 AA/AAA。
 *
 * 近似传统色的排序用 CIEDE2000（ColorMath），最多 3 条。
 *
 * 投影只给真正"浮"起来的东西：这一页唯一有影的是白 sheet（Skin.sheet 自带 SHEET 影），
 * 色块 / 圆钮一律不抬影（Surface.elevate(..., 0f)），层次靠描边与底色差。
 */
public class ColorDetailActivity extends BaseActivity {

    private static final float HERO_H = 210f;
    private static final float SHEET_OVERLAP = 28f;
    private static final float PARALLAX = 0.35f;
    private static final float PARALLAX_MAX_SCALE = 1.08f;

    private int color;
    private String name;
    private String favKey;
    private FavStore favs;

    private FrameLayout hero;
    private PullScrollView scroller;
    private LinearLayout sheet;
    private LinearLayout infoRows;
    private LinearLayout navLayer;
    private LinearLayout backBox;
    private LinearLayout starBox;
    private LinearLayout shareBox;
    private ImageView backIcon;
    private ImageView starIcon;
    private ImageView shareIcon;
    private TextView shareHint;

    private boolean blockLight;
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
            CrashScreen.show(this, "单色详情", e);
        }
    }

    private void buildUi() {
        color = getIntent().getIntExtra("color", 0);
        name = getIntent().getStringExtra("name");
        favKey = getIntent().getStringExtra("favkey");
        if (color == 0) {
            finish();
            return;
        }
        if (name == null) name = "";
        favs = new FavStore(this);
        if (favKey != null) favs.touchRecent(favKey);

        blockLight = Skin.isLightColor(color);
        motionOn = Motion.enabled(this);

        final int heroH = Skin.dp(this, HERO_H) + Bars.top(this);
        final int overlap = Skin.dp(this, SHEET_OVERLAP);
        final int navTop = Bars.top(this) + Skin.dp(this, 6);
        final int sheetTop = heroH - overlap;
        overSheetAt = Math.max(0, sheetTop - navTop - Skin.dp(this, 48));
        iconsFlipAt = Math.max(0, sheetTop);

        FrameLayout root = new FrameLayout(this);
        // 整屏同色墙：白 sheet 拽下来时露出的底，就是上面那块颜色本身
        root.addView(new ColorWall(this, new int[]{color}), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        hero = new FrameLayout(this);
        View block = new View(this);
        block.setBackgroundColor(color);
        hero.addView(block, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(hero, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                heroH, Gravity.TOP));

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

        Bars.icons(this, color);

        scroller.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            public void onScrollChange(View v, int sx, int sy, int osx, int osy) {
                onScrolled(sy);
            }
        });

        installPullShare(root, navTop);

        Motion.fadeIn(hero, 0);
        Motion.riseIn(sheet, 80);
        Motion.staggerIn(infoRows, 0, 8);
        Motion.fadeIn(navLayer, 60);
    }

    // ---------------------------------------------------------------- 内容
    private void buildSheetContent() {
        final String hex = ColorMath.hex(color);
        infoRows = new LinearLayout(this);
        infoRows.setOrientation(LinearLayout.VERTICAL);

        TextView title = Skin.medium(this, name.isEmpty() ? hex : name,
                Skin.T_TITLE, Skin.text(this));
        title.setLetterSpacing(-0.02f);
        sheet.addView(title);

        TextView hint = Skin.text(this, name.isEmpty() ? "轻点任意一行复制"
                        : hex + " · 轻点任意一行复制", Skin.T_NOTE, Skin.text2(this));
        hint.setLetterSpacing(0.02f);
        hint.setPadding(0, Skin.dp(this, 4), 0, Skin.dp(this, 10));
        sheet.addView(hint);

        int[] rgb = ColorMath.rgb(color);
        float[] hsl = ColorMath.hsl(color);
        int[] cmyk = ColorMath.cmyk(color);
        double onWhite = ColorMath.contrast(color, 0xFFFFFFFF);
        double onBlack = ColorMath.contrast(color, 0xFF000000);

        addInfoRow(infoRow("HEX", hex, null), false);
        addInfoRow(infoRow("RGB", rgb[0] + ", " + rgb[1] + ", " + rgb[2], null), true);
        addInfoRow(infoRow("HSL", Math.round(hsl[0]) + "°, " + Math.round(hsl[1]) + "%, "
                + Math.round(hsl[2]) + "%", null), true);
        addInfoRow(infoRow("CMYK", cmyk[0] + ", " + cmyk[1] + ", " + cmyk[2] + ", " + cmyk[3],
                null), true);
        addInfoRow(infoRow("白字对比度", fmt(onWhite) + ":1", contrastBadge(onWhite)), true);
        addInfoRow(infoRow("黑字对比度", fmt(onBlack) + ":1", contrastBadge(onBlack)), true);
        sheet.addView(infoRows);

        buildNamed();
        buildCopyButton(hex);
    }

    /**
     * 行和它上面的发丝线合成一组再加进去 —— 进场时按"行"依次升起，
     * 而不是把发丝线也当成一步，导致后两行直接跳出来。
     */
    private void addInfoRow(View row, boolean divider) {
        if (!divider) {
            infoRows.addView(row);
            return;
        }
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.addView(Page.hairline(this));
        group.addView(row);
        infoRows.addView(group);
    }

    private View infoRow(String label, final String value, TextView badge) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Skin.dp(this, 56));
        row.setPadding(0, Skin.dp(this, 12), 0, Skin.dp(this, 12));

        TextView l = Skin.text(this, label, Skin.T_BODY, Skin.text2(this));
        l.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);

        TextView v = Skin.medium(this, value, Skin.T_BODY, Skin.text(this));
        v.setFontFeatureSettings("'tnum'");
        row.addView(v);

        if (badge != null) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.leftMargin = Skin.dp(this, 8);
            row.addView(badge, blp);
        }

        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Skin.dp(this, 20),
                Skin.dp(this, 20));
        ilp.leftMargin = Skin.dp(this, 10);
        row.addView(Skin.icon(this, R.drawable.lc_copy, Skin.text3(this), 20f), ilp);

        row.setContentDescription(label + " " + value + "，轻点复制");
        Tap.row(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Snack.copy(view, value);
            }
        });
        return row;
    }

    /**
     * WCAG 等级徽标：≥7 AAA、≥4.5 AA、≥3 AA Large，再低就不达标。
     * 颜色语义用 ok / warn / danger，不自己配一套绿黄红。
     */
    private TextView contrastBadge(double ratio) {
        String label;
        int tintColor;
        if (ratio >= 7.0) {
            label = "AAA";
            tintColor = Skin.ok(this);
        } else if (ratio >= 4.5) {
            label = "AA";
            tintColor = Skin.ok(this);
        } else if (ratio >= 3.0) {
            label = "AA 大字";
            tintColor = Skin.warn(this);
        } else {
            label = "不达标";
            tintColor = Skin.danger(this);
        }
        TextView chip = Skin.chip(this, label, tintColor);
        chip.setContentDescription("对比度等级 " + label);
        return chip;
    }

    /** 近似传统色：色块 + 名称 + 家族·读音 + ΔE；点一行复制它的 HEX。 */
    private void buildNamed() {
        final DataStore s = Store.get(this);
        int[] near = s.nearestNamed(color, 3);
        if (near.length == 0) return;

        TextView title = Skin.text(this, "近似传统色", Skin.T_NOTE, Skin.text2(this));
        title.setLetterSpacing(0.05f);
        title.setPadding(0, Skin.dp(this, 24), 0, Skin.dp(this, 6));
        sheet.addView(title);

        for (int k = 0; k < near.length; k++) {
            if (k > 0) sheet.addView(Page.hairline(this));
            sheet.addView(namedRow(s, near[k]));
        }
    }

    private View namedRow(DataStore s, int idx) {
        final int c = s.namedColor(idx);
        final String hex = ColorMath.hex(c);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Skin.dp(this, 52));
        row.setPadding(0, Skin.dp(this, 9), 0, Skin.dp(this, 9));

        View swatch = SwatchView.of(this, c, 32, 8);
        Surface.round(swatch, 8f);
        Surface.elevate(swatch, 0f);
        row.addView(swatch);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        midLp.leftMargin = Skin.dp(this, 12);
        mid.addView(Skin.text(this, s.namedName(idx), Skin.T_BODY, Skin.text(this)));
        TextView read = Skin.text(this, s.namedFamily(idx) + " · " + s.namedReading(idx),
                11f, Skin.text3(this));
        read.setPadding(0, Skin.dp(this, 2), 0, 0);
        mid.addView(read);
        row.addView(mid, midLp);

        double de = ColorMath.deltaE2000(color, c);
        row.addView(Skin.text(this, "ΔE " + fmt(de), Skin.T_CHIP, Skin.text2(this)));

        row.setContentDescription(s.namedName(idx) + " " + hex + "，轻点复制");
        Tap.row(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Snack.copy(v, hex);
            }
        });
        return row;
    }

    private void buildCopyButton(final String hex) {
        TextView copy = Skin.pill(this, "复制 " + hex, true);
        Tap.chip(copy);
        copy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Snack.copy(v, hex);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Skin.dp(this, 22);
        sheet.addView(copy, lp);
    }

    /** 下拉分享：单个颜色只有一张卡，甩下去之后卡片飞进来，点一下即分享。 */
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

    /** 白纸已经让位：单卡暗场；关闭后白纸滑回、系统栏图标恢复。 */
    private void showShareCards() {
        if (navLayer != null) {
            navLayer.animate().alpha(0f).setDuration(160).start();
        }
        ShareCards.colorInteractive(this, color,
                name.isEmpty() ? ColorMath.hex(color) : name,
                new Runnable() {
                    public void run() {
                        if (navLayer != null) {
                            navLayer.animate().alpha(1f).setDuration(200).start();
                        }
                        PullToShare.restore(scroller);
                        Bars.icons(ColorDetailActivity.this,
                                scroller.getScrollY() >= overSheetAt
                                        ? Skin.cardColor(ColorDetailActivity.this) : color);
                    }
                });
    }

    // ---------------------------------------------------------------- 悬浮按钮
    private void buildNav(FrameLayout root, int navTop) {
        navLayer = new LinearLayout(this);
        navLayer.setOrientation(LinearLayout.HORIZONTAL);
        navLayer.setGravity(Gravity.CENTER_VERTICAL);
        navLayer.setPadding(Skin.dp(this, 12), 0, Skin.dp(this, 12), 0);

        backBox = circleButton(R.drawable.lc_back, "返回", new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        backIcon = (ImageView) backBox.getChildAt(0);
        navLayer.addView(backBox, new LinearLayout.LayoutParams(Skin.dp(this, 48),
                Skin.dp(this, 48)));

        View spacer = new View(this);
        navLayer.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        shareBox = circleButton(R.drawable.lc_share, "分享这个颜色", new View.OnClickListener() {
            public void onClick(View v) {
                // 单个颜色只有一种卡片：不弹选择，直接叫系统分享面板
                ShareCards.color(ColorDetailActivity.this, color,
                        name.isEmpty() ? ColorMath.hex(color) : name);
            }
        });
        shareIcon = (ImageView) shareBox.getChildAt(0);
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(Skin.dp(this, 48),
                Skin.dp(this, 48));
        shareLp.rightMargin = Skin.dp(this, 10);
        navLayer.addView(shareBox, shareLp);

        starBox = circleButton(R.drawable.lc_star, "收藏这个颜色", new View.OnClickListener() {
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

    private void applyNavStyle() {
        if (navOverSheet) {
            navIconColor = Skin.text(this);
        } else {
            navIconColor = blockLight ? 0xFF17171A : 0xFFFFFFFF;
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
        return Skin.round(this, blockLight ? 0x40FFFFFF : 0x33000000, 999f);
    }

    /** 星标只有一个入口（不在这张纸上重复放收藏按钮），状态只在这里维护。 */
    private void paintStar() {
        if (starIcon == null) return;
        boolean on = isFav();
        starIcon.setImageResource(on ? R.drawable.lc_star_fill : R.drawable.lc_star);
        starIcon.setColorFilter(on ? Skin.warn(this) : navIconColor);
        starBox.setContentDescription(on ? "取消收藏这个颜色" : "收藏这个颜色");
    }

    // ---------------------------------------------------------------- 行为
    private boolean isFav() {
        return favKey != null && favs.isFav(favKey);
    }

    private void toggleFav(View v) {
        if (favKey == null) {
            Snack.show(v, "这个颜色还没有收藏键");
            return;
        }
        boolean added = favs.toggleFav(favKey);
        paintStar();
        if (added) {
            Motion.pop(starIcon);
            Haptics.success(v);
            Snack.show(v, "已收藏这个颜色");
        } else {
            Snack.show(v, "已取消收藏");
        }
    }

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
            Bars.icons(this, onSheet ? Skin.cardColor(this) : color);
        }
    }

    private static String fmt(double v) {
        return String.valueOf(Math.round(v * 10) / 10.0);
    }
}
