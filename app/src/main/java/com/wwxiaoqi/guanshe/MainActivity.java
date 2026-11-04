package com.wwxiaoqi.guanshe;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.core.Today;
import com.wwxiaoqi.guanshe.data.DataStore;
import com.wwxiaoqi.guanshe.data.Store;
import com.wwxiaoqi.guanshe.page.BasePage;
import com.wwxiaoqi.guanshe.page.CardsPage;
import com.wwxiaoqi.guanshe.page.DiscoverPage;
import com.wwxiaoqi.guanshe.page.MinePage;
import com.wwxiaoqi.guanshe.page.PaletteListPage;
import com.wwxiaoqi.guanshe.ui.AuraView;
import com.wwxiaoqi.guanshe.ui.Bars;
import com.wwxiaoqi.guanshe.ui.BaseActivity;
import com.wwxiaoqi.guanshe.ui.FavStore;
import com.wwxiaoqi.guanshe.ui.Haptics;
import com.wwxiaoqi.guanshe.ui.Motion;
import com.wwxiaoqi.guanshe.ui.Snack;
import com.wwxiaoqi.guanshe.ui.Surface;
import com.wwxiaoqi.guanshe.ui.SwipePager;
import com.wwxiaoqi.guanshe.ui.Tap;

/**
 * 四个入口的容器（specs/app-shell）：发现 / 配色 / 色卡 / 我的。
 *
 * 这一版的外壳：
 * - 底部是**浮起来**的胶囊栏：外层透明，页面内容可以从它后面滚过去；
 * - 支持左右滑动切 tab，切页时两页实时跟手；点 tab 也走同一套横向转场；
 * - 底栏有一块墨色选中胶囊，会跟着当前 tab 滑动；
 * - 页面底色是极淡的纸张渐变 + 顶部一层来自今日配色的色晕；
 * - 再点一次当前 tab 回到顶部；在发现页按返回先提示"再按一次退出"。
 */
public class MainActivity extends BaseActivity {

    public static final int TAB_DISCOVER = 0;
    public static final int TAB_PALETTE = 1;
    public static final int TAB_CARDS = 2;
    public static final int TAB_MINE = 3;

    private static final String[] TAB_LABEL = {"发现", "配色", "色卡", "我的"};
    private static final int[] TAB_ICON = {R.drawable.lc_home, R.drawable.lc_palette,
            R.drawable.lc_grid, R.drawable.lc_user};

    private final BasePage[] pages = new BasePage[4];
    private final FrameLayout[] pageHosts = new FrameLayout[4];
    private final boolean[] built = new boolean[4];
    private final ImageView[] tabIcons = new ImageView[4];
    private final TextView[] tabLabels = new TextView[4];
    private final LinearLayout[] tabItems = new LinearLayout[4];

    private SwipePager pager;
    private FrameLayout rootView;
    private View statusScrim;
    private int topInset;
    private LinearLayout dock;
    private FrameLayout dockBar;
    private LinearLayout tabRow;
    private View indicator;
    private int currentTab = -1;
    private int bottomInset;

    private DataStore store;
    private FavStore favs;

    private final Handler handler = new Handler();
    private boolean backArmed;
    private final Runnable disarm = new Runnable() {
        public void run() {
            backArmed = false;
        }
    };
    private final Runnable updateInsets = new Runnable() {
        public void run() {
            applyPageInsets();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = Store.get(this);            // 冷启动一次读 2 万行
        favs = new FavStore(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Skin.background(this));

        // 顶部极淡的色晕：颜色来自今日配色，所有 tab 共用，页面因此不是一块死白的纸
        int[] aura = todayColors();
        FrameLayout.LayoutParams auraLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(this, 320), Gravity.TOP);
        root.addView(AuraView.of(this, aura, 320, 0.09f), auraLp);

        pager = new SwipePager(this);
        root.addView(pager, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (int i = 0; i < 4; i++) {
            FrameLayout host = new FrameLayout(this);
            host.setClipChildren(false);
            pageHosts[i] = host;
            pager.addPage(host);
        }
        pager.setListener(new SwipePager.Listener() {
            public void onPageSelected(int index) {
                onTabSettled(index);
            }
        });

        // 状态栏渐隐：滚动内容从下面淡出，不会和系统图标叠在一起
        statusScrim = new View(this);
        statusScrim.setBackgroundResource(R.drawable.status_scrim);
        statusScrim.setClickable(false);
        root.addView(statusScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(this, 64), Gravity.TOP));

        dock = buildDock();
        root.addView(dock, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        setContentView(root);
        rootView = root;

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                readInsets(in);
                return in;
            }
        });

        pages[TAB_DISCOVER] = new DiscoverPage(this);
        pages[TAB_PALETTE] = new PaletteListPage(this);
        pages[TAB_CARDS] = new CardsPage(this);
        pages[TAB_MINE] = new MinePage(this);

        int startTab = TAB_DISCOVER;
        int startScroll = 0;
        if (savedInstanceState != null) {
            startTab = savedInstanceState.getInt("tab", TAB_DISCOVER);
            startScroll = savedInstanceState.getInt("scroll", 0);
        }
        if (startTab < 0 || startTab > 3) startTab = TAB_DISCOVER;
        prepareTab(startTab);
        pager.show(startTab);
        onTabSettled(startTab);
        if (startScroll > 0) pages[startTab].restoreScroll(startScroll);

        // 其余三页等首帧画完再建，冷启动不抢时间；滑动前它们也基本就绪
        handler.postDelayed(new Runnable() {
            public void run() {
                for (int i = 0; i < 4; i++) {
                    if (!built[i]) prepareTab(i);
                }
            }
        }, 120);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", currentTab < 0 ? 0 : currentTab);
        out.putInt("scroll", currentTab >= 0 ? pages[currentTab].scrollY() : 0);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从详情 / 搜索 / 取色页回来时，当前 tab 的收藏与最近记录要刷新
        if (currentTab >= 0 && currentTab < 4 && built[currentTab] && pages[currentTab] != null) {
            pages[currentTab].onShow();
        }
    }

    // ---------------------------------------------------------------- 页面
    private int[] todayColors() {
        try {
            int idx = Today.pick(Today.daysSinceEpoch(System.currentTimeMillis()), store.paletteCount());
            return store.palette(idx);
        } catch (Throwable t) {
            return new int[]{0xFFF6F5F2, 0xFFF6F5F2, 0xFFF6F5F2, 0xFFF6F5F2};
        }
    }

    private void prepareTab(int index) {
        if (index < 0 || index >= 4 || pages[index] == null || built[index]) return;
        View v = pages[index].view();
        pageHosts[index].removeAllViews();
        pageHosts[index].addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        built[index] = true;
        pages[index].setBottomInset(dockCoverHeight() + Skin.dp(this, 12));
        pageHosts[index].post(new Runnable() {
            public void run() {
                applyPageInsets();
            }
        });
    }

    private void onTabSettled(int index) {
        if (index < 0 || index >= 4) return;
        boolean changed = index != currentTab;
        currentTab = index;
        prepareTab(index);
        if (changed) pages[index].onShow();
        paintTabs(true);
        updateIndicator(true);
        Bars.icons(this, Skin.bg(this));
        applyPageInsets();
    }

    /** 点 tab：先把上一段转场收尾，再滑过去，连续快速点也不会两页叠在一起。 */
    public void switchTo(int index) {
        if (index < 0 || index >= 4 || pages[index] == null) return;
        // 先让上一段转场立刻结束，否则连续点 tab 时两页会同时停在屏幕上
        if (pager != null) pager.finishNow();
        if (pager != null && pager.current() >= 0 && pager.current() != currentTab) {
            currentTab = pager.current();
            prepareTab(currentTab);
            paintTabs(false);
            updateIndicator(false);
        }
        if (index == currentTab) {
            pages[index].scrollToTop();
            return;
        }
        Haptics.tabSwitch(tabItems[index]);
        prepareTab(index);
        pages[index].onShow();
        currentTab = index;
        paintTabs(true);
        updateIndicator(true);
        Bars.icons(this, Skin.bg(this));
        pager.switchTo(index, true);
        applyPageInsets();
    }

    private void onBackFromSwipe() {
        // 滑动由 pager 自己处理，这里只保留给返回键用
    }

    @Override
    public void onBackPressed() {
        if (currentTab != TAB_DISCOVER) {
            switchTo(TAB_DISCOVER);
            return;
        }
        if (!backArmed) {
            backArmed = true;
            handler.postDelayed(disarm, 2000);
            Snack.show(tabRow, "再按一次退出观色");
            return;
        }
        handler.removeCallbacks(disarm);
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- 底栏
    /** 浮起来的小胶囊底栏：宽度只包住四个 tab，居中悬浮，像手机播放器的底栏。 */
    private LinearLayout buildDock() {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setBackgroundColor(0x00000000);
        holder.setClipChildren(false);

        dockBar = new FrameLayout(this);
        float r = 31f;
        dockBar.setBackground(Skin.stroke(this, Skin.cardColor(this), Skin.cardStroke(this), r, 1f));
        Surface.card(dockBar, r, 3f);
        dockBar.setClipChildren(false);

        indicator = new View(this);
        GradientDrawable ind = new GradientDrawable();
        ind.setColor(Skin.text(this));
        ind.setCornerRadius(Skin.dp(this, 999));
        indicator.setBackground(ind);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                Skin.dp(this, 60), Skin.dp(this, 46), Gravity.TOP | Gravity.START);
        ilp.topMargin = Skin.dp(this, 8);
        dockBar.addView(indicator, ilp);

        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        int itemW = Skin.dp(this, 72);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setClipChildren(false);

            ImageView icon = new ImageView(this);
            icon.setImageResource(TAB_ICON[i]);
            icon.setColorFilter(Skin.text3(this));
            icon.setLayoutParams(new LinearLayout.LayoutParams(
                    Skin.dp(this, 24), Skin.dp(this, 24)));
            tabIcons[i] = icon;
            item.addView(icon);

            TextView label = Skin.text(this, TAB_LABEL[i], 10.5f, Skin.text3(this));
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, Skin.dp(this, 2), 0, 0);
            tabLabels[i] = label;
            item.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            item.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    switchTo(idx);
                }
            });
            Tap.chip(item);
            tabItems[i] = item;
            tabRow.addView(item, new LinearLayout.LayoutParams(itemW, Skin.dp(this, 62)));
        }
        dockBar.addView(tabRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Skin.dp(this, 62)));
        holder.addView(dockBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Skin.dp(this, 62)));

        holder.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or, int ob) {
                if (b - t != ob - ot || r - l != or - ol) {
                    updateIndicator(false);
                    applyPageInsets();
                }
            }
        });
        return holder;
    }

    private void paintTabs(boolean animate) {
        int activeFg = Skin.bg(this);
        for (int i = 0; i < 4; i++) {
            boolean on = i == currentTab;
            int color = on ? activeFg : Skin.text3(this);
            if (animate) {
                Motion.textColor(tabLabels[i], color);
                tintIcon(tabIcons[i], color);
            } else {
                tabLabels[i].setTextColor(color);
                tabIcons[i].setColorFilter(color);
            }
            if (on && animate) Motion.pop(tabIcons[i]);
            tabItems[i].setContentDescription(TAB_LABEL[i] + "，标签页 "
                    + (i + 1) + "/4" + (on ? "，已选中" : ""));
        }
    }

    private void tintIcon(final ImageView icon, int color) {
        Object tag = icon.getTag();
        int from = tag instanceof Integer ? (Integer) tag : color;
        icon.setTag(color);
        if (from == color || !Motion.enabled(this)) {
            icon.setColorFilter(color);
            return;
        }
        ValueAnimator a = ValueAnimator.ofObject(new ArgbEvaluator(), from, color);
        a.setDuration(180);
        a.setInterpolator(Motion.ease());
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator anim) {
                icon.setColorFilter((Integer) anim.getAnimatedValue());
            }
        });
        a.start();
    }

    private void updateIndicator(boolean animate) {
        if (indicator == null || tabRow == null || currentTab < 0) return;
        int width = tabRow.getWidth();
        if (width <= 0) return;
        int itemW = width / 4;
        int targetW = Math.min(Skin.dp(this, 60), itemW - Skin.dp(this, 8));
        if (targetW < Skin.dp(this, 50)) targetW = Skin.dp(this, 50);
        int x = currentTab * itemW + (itemW - targetW) / 2;

        ViewGroup.LayoutParams lp = indicator.getLayoutParams();
        if (lp.width != targetW) {
            lp.width = targetW;
            indicator.setLayoutParams(lp);
        }
        if (animate && Motion.enabled(this)) {
            indicator.animate().translationX(x)
                    .setDuration(Motion.BASE).setInterpolator(Motion.ease()).start();
        } else {
            indicator.animate().cancel();
            indicator.setTranslationX(x);
        }
    }

    // ---------------------------------------------------------------- insets
    private void readInsets(WindowInsets in) {
        int top;
        int bottom;
        if (Build.VERSION.SDK_INT >= 30) {
            Insets bars = in.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            top = bars.top;
            bottom = Math.max(bars.bottom,
                    in.getInsets(WindowInsets.Type.tappableElement()).bottom);
        } else {
            top = in.getSystemWindowInsetTop();
            bottom = in.getSystemWindowInsetBottom();
        }
        bottomInset = bottom;
        topInset = top;
        if (statusScrim != null) {
            FrameLayout.LayoutParams slp =
                    (FrameLayout.LayoutParams) statusScrim.getLayoutParams();
            int h = top + Skin.dp(this, 28);
            if (slp.height != h) {
                slp.height = h;
                statusScrim.setLayoutParams(slp);
            }
        }
        if (dock != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dock.getLayoutParams();
            int margin = Math.max(bottom + Skin.dp(this, 16), Skin.dp(this, 20));
            if (lp.bottomMargin != margin) {
                lp.bottomMargin = margin;
                dock.setLayoutParams(lp);
            }
        }
        handler.removeCallbacks(updateInsets);
        handler.postDelayed(updateInsets, 60);
    }

    private void applyPageInsets() {
        if (dock == null || rootView == null) return;
        int inset = dockCoverHeight() + Skin.dp(this, 10);
        for (int i = 0; i < 4; i++) {
            if (built[i] && pages[i] != null) pages[i].setBottomInset(inset);
        }
    }

    /**
     * 浮起的底栏实际遮住多高：底栏卡高度 + 它的 bottomMargin（导航条 + 12dp）。
     * 只算 getHeight() 会漏掉 margin，页面最后的元素会被底栏压住。
     */
    private int dockCoverHeight() {
        if (dock == null) return 0;
        if (rootView != null && rootView.getHeight() > 0 && dock.getTop() > 0) {
            return rootView.getHeight() - dock.getTop();
        }
        int margin = 0;
        ViewGroup.LayoutParams lp = dock.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            margin = ((FrameLayout.LayoutParams) lp).bottomMargin;
        }
        return dock.getHeight() + margin;
    }

    /** 提示条要浮在底栏之上：这个高度给 ui/Snack 用。 */
    public int snackBottomMargin() {
        int covered = dockCoverHeight();
        if (covered <= 0) return Skin.dp(this, 110);
        return covered + Skin.dp(this, 12);
    }

    // ---------------------------------------------------------------- 页面要用的能力
    public DataStore store() {
        return store;
    }

    public FavStore favs() {
        return favs;
    }

    public void openPalette(int[] colors, String label, String favKey) {
        startActivity(paletteIntent(colors, label, favKey));
    }

    public void openPaletteFrom(int[] colors, String label, String favKey, View origin) {
        openFrom(paletteIntent(colors, label, favKey), origin);
    }

    private Intent paletteIntent(int[] colors, String label, String favKey) {
        Intent i = new Intent(this, PaletteDetailActivity.class);
        i.putExtra("colors", colors);
        i.putExtra("label", label);
        i.putExtra("favkey", favKey);
        return i;
    }

    public void openColor(int color, String name, String favKey) {
        startActivity(colorIntent(color, name, favKey));
    }

    public void openColorFrom(int color, String name, String favKey, View origin) {
        openFrom(colorIntent(color, name, favKey), origin);
    }

    private Intent colorIntent(int color, String name, String favKey) {
        Intent i = new Intent(this, ColorDetailActivity.class);
        i.putExtra("color", color);
        i.putExtra("name", name);
        i.putExtra("favkey", favKey);
        return i;
    }

    public void openSearch() {
        startActivity(new Intent(this, SearchActivity.class));
    }

    public void openSearchFrom(View origin) {
        openFrom(new Intent(this, SearchActivity.class), origin);
    }

    /** 从发现页点色系/场景：切到配色页并套上筛选。 */
    public void filterPalettes(int kind, int group) {
        if (currentTab != TAB_PALETTE) {
            prepareTab(TAB_PALETTE);
            currentTab = TAB_PALETTE;
            paintTabs(true);
            updateIndicator(true);
            pager.switchTo(TAB_PALETTE, true);
            Bars.icons(this, Skin.bg(this));
        }
        ((PaletteListPage) pages[TAB_PALETTE]).setFilter(kind, group);
    }

    public void openSourceSets(String group) {
        Intent i = new Intent(this, SourceSetsActivity.class);
        i.putExtra("group", group);
        startActivity(i);
    }

    public void openSourceSetsFrom(String group, View origin) {
        Intent i = new Intent(this, SourceSetsActivity.class);
        i.putExtra("group", group);
        openFrom(i, origin);
    }

    public void openSet(String kind, int id) {
        Intent i = new Intent(this, CardSetActivity.class);
        i.putExtra("kind", kind);
        i.putExtra("id", id);
        startActivity(i);
    }

    public void openSetFrom(String kind, int id, View origin) {
        Intent i = new Intent(this, CardSetActivity.class);
        i.putExtra("kind", kind);
        i.putExtra("id", id);
        openFrom(i, origin);
    }

    public void openPick() {
        startActivity(new Intent(this, PickActivity.class));
    }

    public void tell(View anchor, String text) {
        Snack.show(anchor, text);
    }

    public void applyAppearance(final String mode) {
        favs.setAppearance(mode);
        final View content = findViewById(android.R.id.content);
        if (content != null && Motion.enabled(this)) {
            content.animate().alpha(0f).setDuration(Motion.FAST)
                    .withEndAction(new Runnable() {
                        public void run() {
                            recreate();
                        }
                    }).start();
        } else {
            recreate();
        }
    }
}
