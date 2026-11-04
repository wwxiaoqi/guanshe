package com.wwxiaoqi.guanshe;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.core.ColorMath;
import com.wwxiaoqi.guanshe.core.Favorites;
import com.wwxiaoqi.guanshe.data.DataStore;
import com.wwxiaoqi.guanshe.data.Store;
import com.wwxiaoqi.guanshe.ui.BandView;
import com.wwxiaoqi.guanshe.ui.Bars;
import com.wwxiaoqi.guanshe.ui.BaseActivity;
import com.wwxiaoqi.guanshe.ui.Chips;
import com.wwxiaoqi.guanshe.ui.CrashScreen;
import com.wwxiaoqi.guanshe.ui.Haptics;
import com.wwxiaoqi.guanshe.ui.Motion;
import com.wwxiaoqi.guanshe.ui.Page;
import com.wwxiaoqi.guanshe.ui.Rows;
import com.wwxiaoqi.guanshe.ui.Snack;
import com.wwxiaoqi.guanshe.ui.Surface;
import com.wwxiaoqi.guanshe.ui.SwatchView;
import com.wwxiaoqi.guanshe.ui.Tap;

import java.util.ArrayList;

/**
 * 单套色卡 / 单个传统色集的浏览（specs/color-cards · 网格浏览 + 色相筛选）。
 *
 * 相比旧版：
 * - 色相筛选行只建一次，选中时改色不重建行（旧版每次选中 removeAllViews，整行会闪一下）；
 * - 网格按 120 个一批渲染，滚动接近底部再补下一批，新加的行只让前 4 行短 stagger 进场；
 * - 色块按下去会缩、长按复制 HEX；右上角搜索、右下角"回到顶部"都按 48dp 触达区做；
 * - "回到顶部"是白底圆钮 + 浮起阴影，滚过约 600dp 才带位移 / 缩放 / 透明度出现。
 */
public class CardSetActivity extends BaseActivity {

    /** 每批渲染 120 个色块（30 行）：一次全建会明显卡顿，太小滚起来又一直补。 */
    private static final int CHUNK = 120;
    private static final int COLS = 4;
    /** 新加载的行只动前 4 行，后面的直接出现 —— 长列表不是每个元素都值得弹。 */
    private static final int STAGGER_ROWS = 4;
    /** 滚过这么多才给"回到顶部"，别在首屏就贴着内容。 */
    private static final int FAB_AFTER_DP = 600;
    /** 离内容底部这么近就补下一批。 */
    private static final int LOAD_BEFORE_DP = 600;

    private static final String[] HUE_LABEL = {"全部", "红", "橙", "黄", "绿", "青", "蓝", "紫", "灰"};

    private DataStore s;
    private String kind;
    private int id;
    private boolean named;

    private int hueFilter = -1;          // -1 = 全部；否则 0..7（ColorMath.hueClass）
    private int[][] blocks;              // 分块渲染：单套色卡只有一块；传统色每家族一块
    private String[] blockNames;         // 传统色的家族名；单套色卡为 null
    private int blockIndex;
    private int blockOffset;
    private int totalCount;
    private boolean pendingRender;

    private LinearLayout grid;
    private ScrollView scroller;
    private Chips.Bar hueBar;
    private TextView sub;
    private LinearLayout backToTop;
    private boolean fabShown;

    private final Runnable loadTask = new Runnable() {
        public void run() {
            pendingRender = false;
            renderMore();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUi();
        } catch (Throwable e) {
            // 页面起不来也别让进程退出：把堆栈画在屏幕上，用户能截图/复制
            CrashScreen.show(this, "色卡网格", e);
        }
    }

    private void buildUi() {
        s = Store.get(this);
        kind = getIntent().getStringExtra("kind");
        named = "namedset".equals(kind);
        if (kind == null) kind = "pantset";
        id = getIntent().getIntExtra("id", 0);

        LinearLayout root = Page.root(this, Skin.bg(this), true);
        FrameLayout frame = new FrameLayout(this);
        // 纸是极轻的渐变：Page.root 透明，纸底交给 frame，滚动内容不会带着底一起滑
        frame.setBackground(Skin.background(this));

        root.addView(header());

        // 这套色卡的"色相名片"：等距采 10 个色，一眼看出这套偏什么调
        buildStrip(root);

        if (!named) {
            // 筛选行只建一次：选中只重画网格。重建行会让"我点的东西去哪了"变得不确定。
            hueBar = Chips.bar(this, HUE_LABEL, 0, true, new Chips.OnPick() {
                public void onPick(int index, String label) {
                    hueFilter = index - 1;
                    rebuildGrid();
                    if (scroller != null) scroller.smoothScrollTo(0, 0);
                }
            });
            root.addView(hueBar.view);
        }

        root.addView(buildHint());

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 12), Skin.dp(this, Page.PAD), 0);
        root.addView(grid);

        // 滚动内容底部留白 24dp + 导航/手势条，最后一行色块别被手势条压住
        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(1, Skin.dp(this, 24) + Bars.bottom(this)));
        root.addView(gap);

        final ScrollView sc = Page.scroller(this);
        sc.addView(root);
        scroller = sc;
        sc.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            public void onScrollChange(View v, int sx, int sy, int osx, int osy) {
                updateFab(sy > Skin.dp(CardSetActivity.this, FAB_AFTER_DP));
                if (blocks == null || blockIndex >= blocks.length || pendingRender) return;
                View content = sc.getChildAt(0);
                if (content == null) return;
                if (sy + sc.getHeight()
                        >= content.getHeight() - Skin.dp(CardSetActivity.this, LOAD_BEFORE_DP)) {
                    pendingRender = true;
                    sc.post(loadTask);
                }
            }
        });
        frame.addView(sc, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        backToTop = buildBackToTop();
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(
                Skin.dp(this, 48), Skin.dp(this, 48), Gravity.BOTTOM | Gravity.END);
        fabLp.rightMargin = Skin.dp(this, Page.PAD);
        fabLp.bottomMargin = Skin.dp(this, 24) + Bars.bottom(this);
        frame.addView(backToTop, fabLp);

        setContentView(frame);
        rebuildGrid();
    }

    // ---------------------------------------------------------------- 头部
    /** 返回 48dp + 标题 / 数量副标 + 搜索入口（纯图标按钮都补了 contentDescription）。 */
    private View header() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Skin.dp(this, 8), Skin.dp(this, 8),
                Skin.dp(this, Page.PAD - 12), Skin.dp(this, 4));

        LinearLayout back = Rows.iconButton(this, R.drawable.lc_back, Skin.text(this),
                new View.OnClickListener() {
                    public void onClick(View v) {
                        close();
                    }
                });
        back.setContentDescription("返回");
        head.addView(back);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView titleView = Skin.medium(this, title(), Skin.T_TITLE, Skin.text(this));
        titleView.setLetterSpacing(-0.02f);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(titleView);
        sub = Skin.text(this, "", Skin.T_NOTE, Skin.text2(this));
        sub.setPadding(0, Skin.dp(this, 3), 0, 0);
        mid.addView(sub);
        head.addView(mid);

        LinearLayout search = Rows.iconButton(this, R.drawable.lc_search, Skin.text2(this),
                new View.OnClickListener() {
                    public void onClick(View v) {
                        openFrom(new Intent(CardSetActivity.this, SearchActivity.class), v);
                    }
                });
        search.setContentDescription("搜索色号");
        head.addView(search);
        return head;
    }

    /** 长按复制这种事没法被"看出来"，只能写一句在内容起点。 */
    private View buildHint() {
        LinearLayout wrap = Page.wrap(this);
        wrap.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 10),
                Skin.dp(this, Page.PAD), 0);
        wrap.addView(Skin.text(this, "轻点色块看详情 · 长按复制色值", 11, Skin.text3(this)));
        return wrap;
    }

    // ---------------------------------------------------------------- 采样条
    /** 顶部 10dp 色相采样条：圆角 6，只给轮廓不给影子 —— 普通色带是纸面的一部分，不是浮层。 */
    private void buildStrip(LinearLayout root) {
        int[] strip = stripColors();
        if (strip.length < 2) return;
        LinearLayout wrap = Page.wrap(this);
        wrap.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 10),
                Skin.dp(this, Page.PAD), Skin.dp(this, 4));
        View band = BandView.of(this, strip, 10, 6, false);
        Surface.round(band, 6f);
        wrap.addView(band);
        root.addView(wrap);
    }

    /** 等距取 10 个色，拼成这套色卡的色相条。 */
    private int[] stripColors() {
        int[] idx = named ? s.namedOf(id) : s.codesOf(id);
        if (idx.length == 0) return new int[0];
        int k = Math.min(10, idx.length);
        int[] out = new int[k];
        for (int i = 0; i < k; i++) {
            int j = (int) ((long) idx.length * i / k);
            out[i] = named ? s.namedColor(idx[j]) : s.codeColor(idx[j]);
        }
        return out;
    }

    private String title() {
        DataStore.Meta m = s.meta(named ? "namedset" : kind, id);
        return m == null ? "色卡" : m.name;
    }

    // ---------------------------------------------------------------- 数据 / 分块
    private void rebuildGrid() {
        if (grid == null) return;
        grid.removeAllViews();
        if (scroller != null) scroller.removeCallbacks(loadTask);
        blockIndex = 0;
        blockOffset = 0;
        pendingRender = false;

        if (named) {
            // 传统色按家族分段：每家族一块，渲染到下一块时补分段标题
            String[] fams = s.namedFamilies(id);
            blockNames = fams;
            blocks = new int[fams.length][];
            totalCount = 0;
            for (int i = 0; i < fams.length; i++) {
                blocks[i] = s.namedOf(id, fams[i]);
                totalCount += blocks[i].length;
            }
        } else {
            blockNames = null;
            int[] all = filteredCodes();
            blocks = new int[][]{all};
            totalCount = all.length;
        }
        paintSub();

        if (totalCount == 0) {
            showEmpty();
            return;
        }
        renderMore();
    }

    /** 当前色相下的色号下标；hueFilter < 0 就是全部。 */
    private int[] filteredCodes() {
        int[] all = s.codesOf(id);
        if (hueFilter < 0) return all;
        ArrayList<Integer> keep = new ArrayList<Integer>();
        for (int idx : all) {
            if (ColorMath.hueClass(s.codeColor(idx)) == hueFilter) keep.add(idx);
        }
        int[] out = new int[keep.size()];
        for (int i = 0; i < out.length; i++) out[i] = keep.get(i);
        return out;
    }

    private void paintSub() {
        if (sub == null) return;
        if (named) {
            int fams = blockNames == null ? 0 : blockNames.length;
            sub.setText(fmt(totalCount) + " 色 · " + fams + " 家族");
        } else if (hueFilter < 0) {
            sub.setText(fmt(totalCount) + " 色 · 按编号排序");
        } else {
            sub.setText(HUE_LABEL[hueFilter + 1] + "色系 · " + fmt(totalCount) + " 色");
        }
    }

    /** 分块渲染：一次最多补 CHUNK 个色块，新行交给 Motion 做短 stagger。 */
    private void renderMore() {
        if (grid == null || blocks == null) return;
        int budget = CHUNK;
        LinearLayout batch = new LinearLayout(this);
        batch.setOrientation(LinearLayout.VERTICAL);

        while (budget > 0 && blockIndex < blocks.length) {
            int[] block = blocks[blockIndex];
            int remain = block.length - blockOffset;
            if (remain <= 0) {
                blockIndex++;
                blockOffset = 0;
                continue;
            }
            // 整行切片：预算不够一整行就留到下一批。否则家族末尾的余数会把后续的行错开，
            // 网格中间会冒出只有 1~3 个色块的一行。
            int take = Math.min(COLS, remain);
            if (take > budget) break;
            if (blockOffset == 0 && blockNames != null) {
                batch.addView(familyHeader(blockNames[blockIndex], block.length));
            }
            int[] slice = new int[take];
            System.arraycopy(block, blockOffset, slice, 0, take);
            batch.addView(buildRow(slice));
            blockOffset += take;
            budget -= take;
            if (blockOffset >= block.length) {
                blockIndex++;
                blockOffset = 0;
            }
        }

        grid.addView(batch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (batch.getChildCount() > 0) Motion.staggerIn(batch, 0, STAGGER_ROWS);
    }

    /** 传统色的家族分段标题：排版与 Page.section 一致，但不能用 Page.section —— 它自带 20dp 左右内边距，落在已缩进的网格里会双重缩进。 */
    private View familyHeader(String name, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Skin.dp(this, 22);
        lp.bottomMargin = Skin.dp(this, 6);
        row.setLayoutParams(lp);

        TextView left = Skin.text(this, name, Skin.T_NOTE, Skin.text2(this));
        left.setLetterSpacing(0.05f);
        left.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(left);
        row.addView(Skin.text(this, fmt(count) + " 色", Skin.T_NOTE, Skin.text3(this)));
        return row;
    }

    private View buildRow(int[] slice) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Skin.dp(this, 10);
        row.setLayoutParams(rowLp);
        for (int k = 0; k < COLS; k++) {
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (k > 0) cellLp.leftMargin = Skin.dp(this, 10);
            if (k < slice.length) row.addView(buildCell(slice[k]), cellLp);
            else row.addView(new View(this), cellLp);
        }
        return row;
    }

    private View buildCell(final int idx) {
        final int color = named ? s.namedColor(idx) : s.codeColor(idx);
        final String name = named ? s.namedName(idx) : s.codeName(idx);
        final String favKey = named
                ? Favorites.namedKey(s.namedSet(idx), name)
                : Favorites.colorKey(s.codeSet(idx), name);

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);

        View block = new View(this);
        SwatchView.apply(block, this, color, 6f);
        cell.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(this, 60)));

        TextView label = Skin.text(this, name, 9, Skin.text2(this));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, Skin.dp(this, 4), 0, 0);
        cell.addView(label);

        cell.setContentDescription(name + "，" + ColorMath.hex(color) + "，轻点查看详情");
        Tap.feedback(cell, 6f);
        Motion.interact(cell, true);

        // 长按复制 HEX：复制完 Snack 自带提示，这里再补一次长按触感，用户能确认"按对了"
        cell.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                Haptics.longPress(v);
                Snack.copy(v, ColorMath.hex(color));
                return true;
            }
        });
        cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(CardSetActivity.this, ColorDetailActivity.class);
                i.putExtra("color", color);
                i.putExtra("name", name);
                i.putExtra("favkey", favKey);
                openFrom(i, v);
            }
        });
        return cell;
    }

    private void showEmpty() {
        Page.gap(this, grid, 8);
        View card;
        if (named) {
            // 传统色没有色相筛选：这里的空只可能是整个色集没收录，给一句解释就够，
            // 别放"查看全部"这种按了没用的按钮。
            card = Page.empty(this, "这个色集还没有收录颜色",
                    "数据可能没有加载完整，回到色卡页重新选一套试试。", null, null);
        } else {
            card = Page.empty(this, "这个色相下没有色号",
                    "换一个色相看看，或者回到全部色号。", "查看全部",
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            clearFilter();
                        }
                    });
        }
        Page.addFull(grid, card);
        Motion.riseIn(card, 60);
        updateFab(false);
    }

    private void clearFilter() {
        hueFilter = -1;
        if (hueBar != null) hueBar.select(0);
        rebuildGrid();
        if (scroller != null) scroller.smoothScrollTo(0, 0);
    }

    // ---------------------------------------------------------------- 回到顶部
    /** 右下角回顶：48dp 白底圆钮 + 浮起阴影，默认不可见。 */
    private LinearLayout buildBackToTop() {
        final LinearLayout fab = new LinearLayout(this);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(Skin.stroke(this, Skin.cardColor(this), Skin.cardStroke(this), 24f, 1f));
        Surface.card(fab, 24f, Surface.FLOAT);
        fab.addView(Skin.icon(this, R.drawable.lc_arrow_up, Skin.text(this), 22f));
        fab.setAlpha(0f);
        fab.setScaleX(0.8f);
        fab.setScaleY(0.8f);
        fab.setVisibility(View.INVISIBLE);
        fab.setClickable(false);
        fab.setContentDescription("回到顶部");
        Tap.chip(fab);                       // 波纹 + 按压缩放 + 按压轻震
        fab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (scroller != null) scroller.smoothScrollTo(0, 0);
            }
        });
        return fab;
    }

    /** 出现：淡入 + 从下方 12dp 升起 + 缩放；消失反过来，不占交互。 */
    private void updateFab(boolean show) {
        if (backToTop == null || show == fabShown) return;
        fabShown = show;
        backToTop.animate().cancel();
        backToTop.setClickable(show);
        if (!Motion.enabled(this)) {
            backToTop.setAlpha(show ? 1f : 0f);
            backToTop.setScaleX(1f);
            backToTop.setScaleY(1f);
            backToTop.setTranslationY(0f);
            backToTop.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            return;
        }
        if (show) {
            backToTop.setVisibility(View.VISIBLE);
            backToTop.setAlpha(0f);
            backToTop.setScaleX(0.8f);
            backToTop.setScaleY(0.8f);
            backToTop.setTranslationY(Skin.dp(this, 12));
            backToTop.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(Motion.BASE).setInterpolator(Motion.over()).start();
        } else {
            backToTop.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f)
                    .translationY(Skin.dp(this, 12))
                    .setDuration(Motion.FAST).setInterpolator(Motion.ease())
                    .withEndAction(new Runnable() {
                        public void run() {
                            backToTop.setVisibility(View.INVISIBLE);
                        }
                    }).start();
        }
    }

    private static String fmt(int n) {
        if (n < 1000) return String.valueOf(n);
        return n / 1000 + "," + String.format("%03d", n % 1000);
    }
}
