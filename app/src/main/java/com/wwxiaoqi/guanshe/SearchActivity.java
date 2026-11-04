package com.wwxiaoqi.guanshe;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
import com.wwxiaoqi.guanshe.ui.FavStore;
import com.wwxiaoqi.guanshe.ui.Motion;
import com.wwxiaoqi.guanshe.ui.Page;
import com.wwxiaoqi.guanshe.ui.Rows;
import com.wwxiaoqi.guanshe.ui.Snack;
import com.wwxiaoqi.guanshe.ui.Surface;
import com.wwxiaoqi.guanshe.ui.SwatchView;
import com.wwxiaoqi.guanshe.ui.Tap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 搜索（specs/search）：HEX / 色号 / 中文色名 / 英文名 四种输入，结果分三组。
 *
 * 这一版的交互：
 * - 空输入不摆一张白纸，给"最近搜索"和"试试搜"两条快捷路径；"试试搜"每次打开从数据里
 *   随机抽 2 传统色 + 2 色号 + 1 组配色的首色，抽不到再退回固定池；
 * - 输入框有内容才浮出"×"，聚焦时抬高一层 —— 焦点在哪看得出来；
 * - 结果按 配色 / 色号 / 传统色 分组、依次升起；新查询把结果区拉回顶部；
 * - 输入停下半秒才写进最近搜索：逐字输入时不会把 "RA"、"RAL" 这种中间前缀灌进历史
 *   （回车、点开任意结果时立刻写，那一刻才说明这次搜索有效）；
 * - 回车 = 收起键盘，无结果给一个能按的出口；
 * - 键盘是盖在页面上的（targetSdk 34 + edge-to-edge 不会自动 resize），所以自己盯
 *   IME inset，把结果区的底部让出来。
 */
public class SearchActivity extends BaseActivity {

    private static final int LIMIT = 20;
    private static final long DEBOUNCE = 180L;
    /** 输入停多久算"这次的词确定了"——要明显长于键盘连打间隔，短于人的思考停顿。 */
    private static final long RECORD_DELAY = 500L;

    /** 数据抽不出来时的兜底池（旧版固定建议）。 */
    private static final String[] FALLBACK_SUGGESTIONS = {"妃色", "月白", "RAL 1000", "#2D2B28",
            "012 C"};

    private DataStore s;
    private FavStore favs;

    private EditText input;
    private FrameLayout inputWrap;
    private View clear;
    private ScrollView scroller;
    private LinearLayout results;

    private final Handler handler = new Handler();
    private final Random random = new Random();
    private Runnable pendingSearch;
    private Runnable pendingRecord;
    private String currentQuery = "";
    private String[] suggestions = FALLBACK_SUGGESTIONS;
    private boolean clearShown;
    private int lastBottomPad = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUi();
        } catch (Throwable e) {
            // 页面起不来就别让进程退出：把堆栈画在屏幕上，用户能截图/复制
            CrashScreen.show(this, "搜索", e);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------------------------------------------------------------- 骨架
    private void buildUi() {
        s = Store.get(this);
        favs = new FavStore(this);
        suggestions = buildSuggestions();

        LinearLayout root = Page.root(this, Skin.bg(this), true);
        root.addView(buildHead());

        scroller = Page.scroller(this);
        results = Page.column(this);
        lastBottomPad = Skin.dp(this, 24) + Bars.bottom(this);
        results.setPadding(0, Skin.dp(this, 6), 0, lastBottomPad);
        scroller.addView(results);
        root.addView(scroller);
        installImeInsets(root);

        // 纸张渐变铺在 frame 上：Page.root 是透明的，滚动时底层纸面不跟着闪
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Skin.background(this));
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(frame);
        root.requestApplyInsets();

        renderIdle();

        input.post(new Runnable() {
            public void run() {
                focusInput();
            }
        });
    }

    // ---------------------------------------------------------------- 键盘 insets
    /**
     * targetSdk 34 + edge-to-edge 下系统不会为键盘 resize 窗口（setDecorFitsSystemWindows(false)），
     * 所以自己盯 IME inset：键盘弹起时把结果区底部让出键盘高度，收起时还原。
     */
    private void installImeInsets(View root) {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                applyKeyboardInset(keyboardInset(in));
                return in;
            }
        });
    }

    private int keyboardInset(WindowInsets in) {
        if (Build.VERSION.SDK_INT >= 30) {
            return in.getInsets(WindowInsets.Type.ime()).bottom;    // 收起时是 0
        }
        // 30 以下没有 ime()，底部 inset 里含导航条：减掉才是键盘自己的高度
        int bottom = in.getSystemWindowInsetBottom();
        int nav = Bars.bottom(this);
        return bottom > nav ? bottom - nav : 0;
    }

    private void applyKeyboardInset(int keyboard) {
        int pad = keyboard > 0
                ? keyboard + Skin.dp(this, 16) + Bars.bottom(this)
                : Skin.dp(this, 24) + Bars.bottom(this);
        if (pad == lastBottomPad) return;
        lastBottomPad = pad;
        results.setPadding(0, Skin.dp(this, 6), 0, pad);
    }

    private View buildHead() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, Page.PAD),
                Skin.dp(this, 10));

        LinearLayout back = Rows.iconButton(this, R.drawable.lc_back, Skin.text(this),
                new View.OnClickListener() {
                    public void onClick(View v) {
                        finish();
                    }
                });
        back.setContentDescription("返回");
        head.addView(back);

        // 输入框：白底圆角 14 + 一层浅影，聚焦时再抬 2dp
        inputWrap = new FrameLayout(this);
        inputWrap.setBackground(Skin.stroke(this, Skin.cardColor(this), Skin.cardStroke(this),
                14f, 1f));
        Surface.card(inputWrap, 14f, 0f);

        input = new EditText(this);
        input.setHint("搜色号 / 色名 / #2D2B28");
        input.setTextSize(Skin.T_BODY);
        input.setTextColor(Skin.text(this));
        input.setHintTextColor(Skin.text3(this));
        input.setBackground(null);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setMinHeight(Skin.dp(this, 48));
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setPadding(Skin.dp(this, 14), Skin.dp(this, 12), Skin.dp(this, 46),
                Skin.dp(this, 12));
        inputWrap.setMinimumHeight(Skin.dp(this, 48));
        inputWrap.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL));

        clear = buildClear();
        FrameLayout.LayoutParams clearLp = new FrameLayout.LayoutParams(
                Skin.dp(this, 40), Skin.dp(this, 40), Gravity.END | Gravity.CENTER_VERTICAL);
        clearLp.rightMargin = Skin.dp(this, 4);
        inputWrap.addView(clear, clearLp);
        clear.setVisibility(View.GONE);
        clear.setAlpha(0f);
        clear.setScaleX(0.7f);
        clear.setScaleY(0.7f);

        // 输入框右侧空白（清除按钮隐藏时）也能聚焦
        inputWrap.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                focusInput();
            }
        });

        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inputLp.leftMargin = Skin.dp(this, 4);
        head.addView(inputWrap, inputLp);

        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                // 聚焦反馈用描边，不用阴影：输入框是页面的一部分，抬起来反而像浮错了层
                inputWrap.setTranslationZ(0f);
                int fill = Skin.cardColor(SearchActivity.this);
                int stroke = hasFocus
                        ? Skin.tint(Skin.text(SearchActivity.this), 0.55f)
                        : Skin.cardStroke(SearchActivity.this);
                inputWrap.setBackground(Skin.stroke(SearchActivity.this, fill, stroke, 14f,
                        hasFocus ? 1.6f : 1f));
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence cs, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence cs, int a, int b, int c) {
            }

            public void afterTextChanged(Editable e) {
                String text = e.toString();
                showClear(text.trim().length() > 0);

                if (pendingSearch != null) {
                    handler.removeCallbacks(pendingSearch);
                    pendingSearch = null;
                }
                final String q = text.trim();
                if (q.length() == 0) {
                    renderIdle();
                    return;
                }
                pendingSearch = new Runnable() {
                    public void run() {
                        pendingSearch = null;
                        performSearch(q);
                    }
                };
                handler.postDelayed(pendingSearch, DEBOUNCE);
            }
        });

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean enter = actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN);
                if (!enter) return false;
                hideKeyboard();
                if (pendingSearch != null) {
                    handler.removeCallbacks(pendingSearch);
                    pendingSearch = null;
                }
                String q = v.getText().toString().trim();
                if (q.length() > 0) {
                    performSearch(q);
                    recordNow(q);
                }
                return true;
            }
        });
        return head;
    }

    private View buildClear() {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        box.addView(Skin.icon(this, R.drawable.lc_x, Skin.text3(this), 18f));
        box.setContentDescription("清除搜索内容");
        Tap.chip(box);
        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                input.setText("");
                focusInput();
            }
        });
        return box;
    }

    /** 有内容才浮出"×"：出现是"掉"下来，消失是"缩"回去。 */
    private void showClear(boolean show) {
        if (clear == null || show == clearShown) return;
        clearShown = show;
        clear.animate().cancel();
        if (show) {
            clear.setVisibility(View.VISIBLE);
            if (!Motion.enabled(this)) {
                clear.setAlpha(1f);
                clear.setScaleX(1f);
                clear.setScaleY(1f);
                return;
            }
            clear.setAlpha(0f);
            clear.setScaleX(0.6f);
            clear.setScaleY(0.6f);
            clear.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(Motion.FAST).setInterpolator(Motion.over()).start();
        } else {
            if (!Motion.enabled(this)) {
                clear.setVisibility(View.GONE);
                return;
            }
            clear.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f)
                    .setDuration(Motion.FAST).setInterpolator(Motion.ease())
                    .withEndAction(new Runnable() {
                        public void run() {
                            clear.setVisibility(View.GONE);
                        }
                    }).start();
        }
    }

    private void focusInput() {
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }

    // ---------------------------------------------------------------- 空查询：最近搜索 + 建议
    private void renderIdle() {
        currentQuery = "";
        results.removeAllViews();

        List<String> recent = favs.searches();
        if (!recent.isEmpty()) {
            results.addView(Page.section(this, "最近搜索", "清空",
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            clearHistory(v);
                        }
                    }));
            Chips.Bar bar = Chips.bar(this, recent.toArray(new String[recent.size()]), -1,
                    true, new Chips.OnPick() {
                        public void onPick(int index, String label) {
                            commitQuery(label);
                        }
                    });
            Page.addFull(results, bar.view);
        }

        results.addView(Page.section(this, "试试搜", null));
        Chips.Bar suggest = Chips.bar(this, suggestions, -1, true, new Chips.OnPick() {
            public void onPick(int index, String label) {
                commitQuery(label);
            }
        });
        Page.addFull(results, suggest.view);

        LinearLayout foot = Page.wrap(this);
        foot.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 18), Skin.dp(this, Page.PAD), 0);
        TextView note = Skin.text(this, "输入 2 个字起搜：色号、色名、英文名，或 6 位色值", 11,
                Skin.text3(this));
        note.setLineSpacing(Skin.dp(this, 3), 1f);
        foot.addView(note);
        Page.addFull(results, foot);

        Motion.staggerIn(results, 0, 10);
    }

    /**
     * 「试试搜」每次打开现抽：2 个传统色名 + 2 个色号名 + 1 个配色的首色 HEX，再打乱。
     * 都从真实数据里来 —— 用户点开一定搜得到，也顺带知道数据里有什么；抽不到就用固定池补齐。
     */
    private String[] buildSuggestions() {
        List<String> picks = new ArrayList<String>(5);
        try {
            int namedCount = s.namedCount();
            if (namedCount > 0) {
                addUnique(picks, s.namedName(random.nextInt(namedCount)));
                addUnique(picks, s.namedName(random.nextInt(namedCount)));
            }
            int codeCount = s.codeCount();
            if (codeCount > 0) {
                int added = 0, guard = 0;
                while (added < 2 && guard < 40) {
                    guard++;
                    String name = s.codeName(random.nextInt(codeCount));
                    if (name == null || name.trim().length() < 2 || picks.contains(name)) continue;
                    picks.add(name);
                    added++;
                }
            }
            int paletteCount = s.paletteCount();
            if (paletteCount > 0) {
                int[] colors = s.palette(random.nextInt(paletteCount));
                if (colors != null && colors.length > 0) addUnique(picks, ColorMath.hex(colors[0]));
            }
        } catch (Throwable ignored) {
            // 数据异常不该把整个搜索页带崩：下面用固定池补齐
        }
        for (int i = 0; i < FALLBACK_SUGGESTIONS.length && picks.size() < 5; i++) {
            addUnique(picks, FALLBACK_SUGGESTIONS[i]);
        }
        for (int i = picks.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            String tmp = picks.get(i);
            picks.set(i, picks.get(j));
            picks.set(j, tmp);
        }
        return picks.toArray(new String[picks.size()]);
    }

    private static void addUnique(List<String> list, String value) {
        if (value == null) return;
        String v = value.trim();
        if (v.length() < 2 || list.contains(v)) return;
        list.add(v);
    }

    private void commitQuery(String q) {
        input.setText(q);
        input.setSelection(q.length());
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
        performSearch(q);
        recordNow(q);
        hideKeyboard();
    }

    /** 清空历史可直接撤销：按原顺序倒着 addSearch 回去即可复原（addSearch 是头插）。 */
    private void clearHistory(View anchor) {
        final List<String> old = favs.searches();
        if (old.isEmpty()) return;
        favs.clearSearches();
        renderIdle();
        Snack.show(anchor, "已清空最近搜索", "撤销", new Runnable() {
            public void run() {
                for (int i = old.size() - 1; i >= 0; i--) favs.addSearch(old.get(i));
                if (currentQuery.length() == 0) renderIdle();
            }
        });
    }

    // ---------------------------------------------------------------- 搜索
    private void performSearch(String q) {
        currentQuery = q;
        DataStore.Search r = s.search(q, LIMIT);
        if (r.tooShort) {
            renderTooShort();
            return;
        }
        if (r.palettes.length == 0 && r.codes.length == 0 && r.named.length == 0) {
            renderNoResult();
            return;
        }
        renderResults(r);
        scroller.scrollTo(0, 0);
        scheduleRecord(q);
    }

    private void renderTooShort() {
        results.removeAllViews();
        LinearLayout wrap = Page.wrap(this);
        wrap.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 22), Skin.dp(this, Page.PAD), 0);
        wrap.addView(Skin.medium(this, "再输入一个字", Skin.T_CARD, Skin.text(this)));
        TextView note = Skin.text(this, "一个字搜出来的东西太多，多打一个字结果更准",
                Skin.T_NOTE, Skin.text2(this));
        note.setPadding(0, Skin.dp(this, 6), 0, 0);
        wrap.addView(note);
        results.addView(wrap);
        Motion.riseIn(wrap);
    }

    private void renderNoResult() {
        results.removeAllViews();
        LinearLayout wrap = Page.wrap(this);
        wrap.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 24), Skin.dp(this, Page.PAD), 0);
        View empty = Page.empty(this, "没有匹配的色号",
                "试试输入 6 位色值、色号里的数字，或色名", "清空输入",
                new View.OnClickListener() {
                    public void onClick(View v) {
                        input.setText("");
                        focusInput();
                    }
                });
        Page.addFull(wrap, empty);
        results.addView(wrap);
        Motion.riseIn(wrap);
    }

    private void renderResults(DataStore.Search r) {
        results.removeAllViews();
        if (r.palettes.length > 0) {
            results.addView(Page.section(this, "配色 " + r.palettes.length + " 组", null));
            for (int idx : r.palettes) results.addView(paletteRow(idx));
        }
        if (r.codes.length > 0) {
            results.addView(Page.section(this, "色号 " + r.codes.length + " 个", null));
            for (int idx : r.codes) {
                final int color = s.codeColor(idx);
                final String name = s.codeName(idx);
                results.addView(colorRow(color, name, null,
                        Favorites.colorKey(s.codeSet(idx), name)));
            }
        }
        if (r.named.length > 0) {
            results.addView(Page.section(this, "传统色 " + r.named.length + " 个", null));
            for (int idx : r.named) {
                final int color = s.namedColor(idx);
                final String name = s.namedName(idx);
                results.addView(colorRow(color, name,
                        s.namedFamily(idx) + " · " + s.namedReading(idx),
                        Favorites.namedKey(s.namedSet(idx), name)));
            }
        }
        Motion.staggerIn(results, 0, 10);
    }

    // ---------------------------------------------------------------- 结果行
    private View paletteRow(final int idx) {
        final int[] colors = s.palette(idx);
        final int kind = s.paletteKind(idx);
        final String label = s.groupName(kind, s.paletteGroup(idx)) + " · 第 " + (idx + 1) + " 组";
        final String key = Favorites.paletteKey(kind, idx);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Skin.dp(this, 58));
        row.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 8),
                Skin.dp(this, Page.PAD), Skin.dp(this, 8));

        final View band = BandView.sized(this, colors, Skin.dp(this, 64), Skin.dp(this, 42),
                9f, false);
        Surface.card(band, 9f, Surface.CARD);
        row.addView(band);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        midLp.leftMargin = Skin.dp(this, 14);
        TextView name = Skin.medium(this, label, Skin.T_BODY, Skin.text(this));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(name);
        TextView hex = Skin.text(this, ColorMath.hexList(colors), 10.5f, Skin.text3(this));
        hex.setSingleLine(true);
        hex.setEllipsize(TextUtils.TruncateAt.END);
        hex.setLetterSpacing(0.03f);
        hex.setPadding(0, Skin.dp(this, 3), 0, 0);
        mid.addView(hex);
        row.addView(mid, midLp);

        LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(
                Skin.dp(this, 18), Skin.dp(this, 18));
        chevronLp.leftMargin = Skin.dp(this, 6);
        row.addView(Skin.icon(this, R.drawable.lc_chevron, Skin.text3(this), 18f), chevronLp);

        row.setContentDescription("配色 " + label + "，点击查看");
        Tap.row(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                recordNow(currentQuery);         // 点开结果 = 这次搜索有效，历史立刻记下
                Intent i = new Intent(SearchActivity.this, PaletteDetailActivity.class);
                i.putExtra("colors", colors);
                i.putExtra("label", label);
                i.putExtra("favkey", key);
                openFrom(i, band);
            }
        });
        return row;
    }

    private View colorRow(final int color, final String name, String sub, final String favKey) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Skin.dp(this, 56));
        row.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 8),
                Skin.dp(this, Page.PAD), Skin.dp(this, 8));

        View swatch = SwatchView.of(this, color, 38, 8);
        Surface.card(swatch, 8f, Surface.CARD);
        row.addView(swatch);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        midLp.leftMargin = Skin.dp(this, 12);
        TextView t1 = Skin.text(this, name, Skin.T_BODY, Skin.text(this));
        t1.setSingleLine(true);
        t1.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(t1);
        if (sub != null && sub.length() > 0) {
            TextView t2 = Skin.text(this, sub, 11, Skin.text3(this));
            t2.setSingleLine(true);
            t2.setEllipsize(TextUtils.TruncateAt.END);
            t2.setPadding(0, Skin.dp(this, 3), 0, 0);
            mid.addView(t2);
        }
        row.addView(mid, midLp);

        TextView hex = Skin.text(this, ColorMath.hex(color), 11, Skin.text2(this));
        hex.setLetterSpacing(0.04f);
        row.addView(hex);

        LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(
                Skin.dp(this, 18), Skin.dp(this, 18));
        chevronLp.leftMargin = Skin.dp(this, 6);
        row.addView(Skin.icon(this, R.drawable.lc_chevron, Skin.text3(this), 18f), chevronLp);

        row.setContentDescription(name + " " + ColorMath.hex(color) + "，点击查看");
        Tap.row(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                recordNow(currentQuery);
                Intent i = new Intent(SearchActivity.this, ColorDetailActivity.class);
                i.putExtra("color", color);
                i.putExtra("name", name);
                i.putExtra("favkey", favKey);
                openFrom(i, v);
            }
        });
        return row;
    }

    // ---------------------------------------------------------------- 最近搜索记录
    /** 延迟写入：连打字的中间前缀不该进历史；点开结果 / 回车 / 离开页面时会改成立刻写。 */
    private void scheduleRecord(final String q) {
        if (pendingRecord != null) handler.removeCallbacks(pendingRecord);
        pendingRecord = new Runnable() {
            public void run() {
                pendingRecord = null;
                favs.addSearch(q);
            }
        };
        handler.postDelayed(pendingRecord, RECORD_DELAY);
    }

    private void recordNow(String q) {
        if (pendingRecord != null) {
            handler.removeCallbacks(pendingRecord);
            pendingRecord = null;
        }
        favs.addSearch(q);
    }

    /** 离开页面时把还没落盘的搜索词补上，不让"搜了一下就走"丢历史。 */
    @Override
    protected void onPause() {
        super.onPause();
        if (pendingRecord != null) {
            handler.removeCallbacks(pendingRecord);
            pendingRecord.run();
        }
    }
}
