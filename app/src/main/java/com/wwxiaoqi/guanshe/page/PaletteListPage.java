package com.wwxiaoqi.guanshe.page;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.MainActivity;
import com.wwxiaoqi.guanshe.R;
import com.wwxiaoqi.guanshe.Skin;
import com.wwxiaoqi.guanshe.core.ColorMath;
import com.wwxiaoqi.guanshe.core.Favorites;
import com.wwxiaoqi.guanshe.data.DataStore;
import com.wwxiaoqi.guanshe.ui.BandView;
import com.wwxiaoqi.guanshe.ui.Chips;
import com.wwxiaoqi.guanshe.ui.Haptics;
import com.wwxiaoqi.guanshe.ui.Motion;
import com.wwxiaoqi.guanshe.ui.Page;
import com.wwxiaoqi.guanshe.ui.Rows;
import com.wwxiaoqi.guanshe.ui.Surface;
import com.wwxiaoqi.guanshe.ui.Tap;

/**
 * 配色库：色系 × 场景两个条件是与关系，2 列卡片，每页 60 组。
 *
 * 这一版把页头和筛选固定在顶部，只有卡片列表滚动 —— 用户滑到很下面也能直接换筛选；
 * 换筛选时列表淡出重建再淡入，并自动回到列表顶部；右下有"回到顶部"。
 */
public class PaletteListPage extends BasePage {

    private static final int PAGE_SIZE = 60;

    private int famGroup = -1;
    private int sceneGroup = -1;

    private int[] hits = new int[0];
    private int rendered;

    private TextView subTitle;
    private TextView resultCount;
    private TextView clearBtn;
    private Chips.Bar famBar;
    private Chips.Bar sceneBar;
    private LinearLayout list;
    private ScrollView listScroller;
    private View fab;
    private boolean fabShown;
    private int animatedRows;

    public PaletteListPage(MainActivity host) {
        super(host);
    }

    @Override
    protected View build() {
        DataStore s = host.store();

        LinearLayout root = Page.root(host, Skin.bg(host), true);

        // 页头
        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Skin.dp(host, Page.PAD), Skin.dp(host, 10),
                Skin.dp(host, Page.PAD - 12), Skin.dp(host, 4));
        LinearLayout headLeft = new LinearLayout(host);
        headLeft.setOrientation(LinearLayout.VERTICAL);
        headLeft.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView title = Skin.medium(host, "配色", Skin.T_TITLE, Skin.text(host));
        title.setLetterSpacing(-0.02f);
        headLeft.addView(title);
        subTitle = Skin.text(host, "色系 × 场景交叉筛选 · 13,665 组", Skin.T_NOTE, Skin.text2(host));
        subTitle.setPadding(0, Skin.dp(host, 3), 0, 0);
        headLeft.addView(subTitle);
        head.addView(headLeft);
        head.addView(Rows.iconButton(host, R.drawable.lc_search, Skin.text2(host),
                new View.OnClickListener() {
                    public void onClick(View v) {
                        host.openSearchFrom(v);
                    }
                }));
        root.addView(head);

        // 固定筛选区
        LinearLayout filters = new LinearLayout(host);
        filters.setOrientation(LinearLayout.VERTICAL);
        filters.setPadding(0, Skin.dp(host, 4), 0, Skin.dp(host, 2));

        String[] fams = new String[17];
        fams[0] = "全部色系";
        for (int i = 0; i < 16; i++) fams[i + 1] = s.groupName(DataStore.KIND_FAMILY, i);
        famBar = Chips.bar(host, fams, famGroup < 0 ? 0 : famGroup + 1, true,
                new Chips.OnPick() {
                    public void onPick(int index, String label) {
                        famGroup = index - 1;
                        recompute(true);
                    }
                });
        filters.addView(famBar.view);

        String[] scenes = new String[29];
        scenes[0] = "不限场景";
        for (int i = 0; i < 28; i++) scenes[i + 1] = s.groupName(DataStore.KIND_SCENE, i);
        LinearLayout sceneWrap = new LinearLayout(host);
        sceneWrap.setPadding(0, Skin.dp(host, 8), 0, 0);
        sceneBar = Chips.bar(host, scenes, sceneGroup < 0 ? 0 : sceneGroup + 1, true,
                new Chips.OnPick() {
                    public void onPick(int index, String label) {
                        sceneGroup = index - 1;
                        recompute(true);
                    }
                });
        sceneWrap.addView(sceneBar.view);
        filters.addView(sceneWrap);
        root.addView(filters);

        // 结果行：数量 + 清除筛选
        LinearLayout resultRow = new LinearLayout(host);
        resultRow.setOrientation(LinearLayout.HORIZONTAL);
        resultRow.setGravity(Gravity.CENTER_VERTICAL);
        resultRow.setPadding(Skin.dp(host, Page.PAD), Skin.dp(host, 6),
                Skin.dp(host, Page.PAD), Skin.dp(host, 2));
        TextView resultCount = Skin.text(host, "", Skin.T_NOTE, Skin.text3(host));
        resultCount.setLetterSpacing(0.03f);
        resultCount.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        this.resultCount = resultCount;
        resultRow.addView(resultCount);

        clearBtn = Skin.medium(host, "清除筛选", 11.5f, Skin.text2(host));
        clearBtn.setPadding(Skin.dp(host, 12), Skin.dp(host, 7),
                Skin.dp(host, 12), Skin.dp(host, 7));
        clearBtn.setBackground(Skin.round(host, Skin.tint(Skin.text(host), 0.06f), 999f));
        clearBtn.setVisibility(View.GONE);
        clearBtn.setContentDescription("清除筛选");
        Tap.chip(clearBtn);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                famGroup = -1;
                sceneGroup = -1;
                famBar.select(0);
                sceneBar.select(0);
                recompute(true);
            }
        });
        resultRow.addView(clearBtn);
        root.addView(resultRow);

        // 可滚动的列表
        list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        int bottom = Skin.dp(host, 24) + Skin.dp(host, 56);   // 给右下 FAB 留出位置
        list.setPadding(Skin.dp(host, Page.PAD), 0, Skin.dp(host, Page.PAD), bottom);
        listScroller = Page.scroller(host);
        scroller = listScroller;          // 交给 BasePage：底部 inset / 回顶 / 滚动位置都靠它
        listScroller.addView(list);
        root.addView(listScroller);

        listScroller.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            public void onScrollChange(View v, int sx, int sy, int osx, int osy) {
                View content = listScroller.getChildAt(0);
                if (content == null) return;
                if (sy + listScroller.getHeight() >= content.getHeight() - Skin.dp(host, 700)) {
                    if (rendered < hits.length) renderMore();
                }
                boolean show = sy > Skin.dp(host, 900);
                if (show != fabShown) {
                    fabShown = show;
                    showFab(show);
                }
            }
        });

        // 右下角回到顶部
        FrameLayout frame = new FrameLayout(host);
        frame.setBackgroundColor(0x00000000);
        frame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fab = buildFab();
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(
                Skin.dp(host, 48), Skin.dp(host, 48), Gravity.BOTTOM | Gravity.END);
        fabLp.rightMargin = Skin.dp(host, 18);
        fabLp.bottomMargin = Skin.dp(host, 20);
        frame.addView(fab, fabLp);
        fab.setVisibility(View.GONE);
        fab.setAlpha(0f);

        recompute(false);
        root.post(new Runnable() {
            public void run() {
                Motion.staggerIn(root, 0, 6);
            }
        });
        return frame;
    }

    /** 从发现页点色系/场景进来时调用。group < 0 表示清掉这一维。 */
    public void setFilter(int kind, int group) {
        if (kind == DataStore.KIND_FAMILY) famGroup = group;
        else if (kind == DataStore.KIND_SCENE) sceneGroup = group;
        if (famBar == null) return;      // 还没 build，字段会在 build 时生效
        famBar.select(famGroup < 0 ? 0 : famGroup + 1);
        sceneBar.select(sceneGroup < 0 ? 0 : sceneGroup + 1);
        recompute(true);
    }

    @Override
    protected void onBottomInset(int px) {
        // 底栏浮在页面之上：右下角 FAB 也要跟着抬到底栏上面
        if (fab != null && fab.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fab.getLayoutParams();
            int margin = px + Skin.dp(host, 22);
            if (lp.bottomMargin != margin) {
                lp.bottomMargin = margin;
                fab.setLayoutParams(lp);
            }
        }
    }

    // ---------------------------------------------------------------- 列表
    private boolean match(DataStore s, int i) {
        if (famGroup >= 0 && !(s.paletteKind(i) == DataStore.KIND_FAMILY
                && s.paletteGroup(i) == famGroup)) return false;
        if (sceneGroup >= 0 && !(s.paletteKind(i) == DataStore.KIND_SCENE
                && s.paletteGroup(i) == sceneGroup)) return false;
        return true;
    }

    private void recompute(boolean animate) {
        DataStore s = host.store();
        int n = 0;
        for (int i = 0; i < s.paletteCount(); i++) if (match(s, i)) n++;
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < s.paletteCount(); i++) if (match(s, i)) out[k++] = i;
        hits = out;

        String fam = famGroup < 0 ? "全部色系" : s.groupName(DataStore.KIND_FAMILY, famGroup);
        String scene = sceneGroup < 0 ? "" : " · " + s.groupName(DataStore.KIND_SCENE, sceneGroup);
        if (resultCount != null) resultCount.setText(fam + scene + " · " + n + " 组");

        boolean filtered = famGroup >= 0 || sceneGroup >= 0;
        showClear(filtered, animate);

        if (list == null) return;
        list.animate().cancel();
        if (animate && Motion.enabled(host)) {
            list.animate().alpha(0f).setDuration(Motion.FAST).withEndAction(new Runnable() {
                public void run() {
                    rebuildList();
                    list.setAlpha(0f);
                    Motion.fadeIn(list, 40);
                }
            }).start();
        } else {
            rebuildList();
            list.setAlpha(1f);
        }
        if (listScroller != null) listScroller.scrollTo(0, 0);
        showFab(false);
    }

    private void rebuildList() {
        list.removeAllViews();
        rendered = 0;
        animatedRows = 0;
        renderMore();
    }

    private void renderMore() {
        if (rendered >= hits.length) {
            if (hits.length == 0) showEmpty();
            return;
        }
        int target = Math.min(hits.length, rendered + PAGE_SIZE);
        while (rendered < target) {
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rendered > 0) rowLp.topMargin = Skin.dp(host, 12);
            row.addView(card(hits[rendered]), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (rendered + 1 < hits.length) {
                LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                right.leftMargin = Skin.dp(host, 12);
                row.addView(card(hits[rendered + 1]), right);
                rendered += 2;
            } else {
                View spacer = new View(host);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                sp.leftMargin = Skin.dp(host, 12);
                row.addView(spacer, sp);
                rendered += 1;
            }
            list.addView(row, rowLp);
            animateRow(row);
        }
    }

    /** 列表重建时前 10 行依次升起，之后直接显示；滚动加载的新行只做一次淡入。 */
    private void animateRow(View row) {
        if (!Motion.enabled(host)) return;
        row.setAlpha(0f);
        row.setTranslationY(Skin.dp(host, 14));
        long delay = animatedRows < 10 ? animatedRows * Motion.STEP : 0;
        row.animate().alpha(1f).translationY(0f)
                .setDuration(Motion.SLOW).setStartDelay(delay)
                .setInterpolator(Motion.out()).start();
        animatedRows++;
    }

    private View card(final int idx) {
        final DataStore s = host.store();
        final int[] cs = s.palette(idx);
        final String label = s.groupName(s.paletteKind(idx), s.paletteGroup(idx));
        final String favKey = Favorites.paletteKey(s.paletteKind(idx), idx);

        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        float r = 16f;
        card.setBackground(Skin.stroke(host, Skin.cardColor(host), Skin.cardStroke(host), r, 1f));
        Surface.card(card, r, Surface.CARD);

        View band = BandView.of(host, cs, 108, r, true);
        card.addView(band, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(host, 108)));

        LinearLayout cap = new LinearLayout(host);
        cap.setOrientation(LinearLayout.VERTICAL);
        cap.setPadding(Skin.dp(host, 12), Skin.dp(host, 9), Skin.dp(host, 12), Skin.dp(host, 11));
        TextView l = Skin.text(host, label, 12, Skin.text(host));
        l.setSingleLine(true);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cap.addView(l);
        TextView hex = Skin.text(host, ColorMath.hex(cs[0]), 10.5f, Skin.text3(host));
        hex.setPadding(0, Skin.dp(host, 2), 0, 0);
        cap.addView(hex);
        card.addView(cap);

        final String full = label + " · 第 " + (idx + 1) + " 组";
        card.setContentDescription("配色，" + label);
        Tap.card(card);
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                host.openPaletteFrom(cs, full, favKey, band);
            }
        });
        return card;
    }

    private void showEmpty() {
        LinearLayout wrap = Page.wrap(host);
        wrap.setPadding(Skin.dp(host, Page.PAD), Skin.dp(host, 30),
                Skin.dp(host, Page.PAD), Skin.dp(host, 20));
        Page.addFull(wrap, Page.empty(host, "这个组合还没有收录配色",
                "换个色系或场景试试，也可以直接清除筛选。",
                "清除筛选", new View.OnClickListener() {
                    public void onClick(View v) {
                        famGroup = -1;
                        sceneGroup = -1;
                        famBar.select(0);
                        sceneBar.select(0);
                        recompute(true);
                    }
                }));
        list.addView(wrap);
    }

    // ---------------------------------------------------------------- 小件
    private View buildFab() {
        LinearLayout box = new LinearLayout(host);
        box.setGravity(Gravity.CENTER);
        float r = 999f;
        box.setBackground(Skin.stroke(host, Skin.cardColor(host),
                Skin.cardStroke(host), r, 1f));
        Surface.card(box, r, Surface.FLOAT);
        ImageView arrow = new ImageView(host);
        arrow.setImageResource(R.drawable.lc_arrow_up);
        arrow.setColorFilter(Skin.text(host));
        box.addView(arrow, new LinearLayout.LayoutParams(
                Skin.dp(host, 20), Skin.dp(host, 20)));
        box.setContentDescription("回到顶部");
        Tap.chip(box);
        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (listScroller != null) listScroller.smoothScrollTo(0, 0);
                Haptics.tap(v);
            }
        });
        return box;
    }

    private void showFab(boolean show) {
        if (fab == null) return;
        fab.animate().cancel();
        if (show) {
            fab.setVisibility(View.VISIBLE);
            fab.setAlpha(0f);
            fab.setScaleX(0.8f);
            fab.setScaleY(0.8f);
            fab.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(Motion.BASE).setInterpolator(Motion.over()).start();
        } else if (fab.getVisibility() == View.VISIBLE) {
            fab.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f)
                    .setDuration(Motion.FAST).withEndAction(new Runnable() {
                        public void run() {
                            fab.setVisibility(View.GONE);
                        }
                    }).start();
        }
    }

    private void showClear(boolean show, boolean animate) {
        if (clearBtn == null) return;
        clearBtn.animate().cancel();
        if (show && clearBtn.getVisibility() != View.VISIBLE) {
            clearBtn.setVisibility(View.VISIBLE);
            if (animate && Motion.enabled(host)) {
                clearBtn.setAlpha(0f);
                clearBtn.setScaleX(0.9f);
                clearBtn.setScaleY(0.9f);
                clearBtn.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(Motion.BASE).setInterpolator(Motion.over()).start();
            } else {
                clearBtn.setAlpha(1f);
            }
        } else if (!show && clearBtn.getVisibility() == View.VISIBLE) {
            if (animate && Motion.enabled(host)) {
                clearBtn.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f)
                        .setDuration(Motion.FAST).withEndAction(new Runnable() {
                            public void run() {
                                clearBtn.setVisibility(View.GONE);
                            }
                        }).start();
            } else {
                clearBtn.setVisibility(View.GONE);
            }
        }
    }
}
