package com.wwxiaoqi.guanshe;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.data.DataStore;
import com.wwxiaoqi.guanshe.data.Store;
import com.wwxiaoqi.guanshe.ui.BandView;
import com.wwxiaoqi.guanshe.ui.Bars;
import com.wwxiaoqi.guanshe.ui.BaseActivity;
import com.wwxiaoqi.guanshe.ui.CrashScreen;
import com.wwxiaoqi.guanshe.ui.Page;
import com.wwxiaoqi.guanshe.ui.Rows;
import com.wwxiaoqi.guanshe.ui.Tap;

import java.util.ArrayList;
import java.util.List;

/**
 * 某个来源下的套列表（潘通 10 套 / 劳尔 6 套 / CBCC 1 套 / 立邦 1 套）。
 *
 * 相比旧版：每行前面多了一条 36dp 的迷你色带（该套前 4 个色号），
 * 不点进去也能看出这套大概是"黄调 / 蓝调 / 中性"；行 64dp、整行可点，
 * 点下去从色带位置"长出来"打开网格；页面一级元素依次升起。
 *
 * CBCC 与立邦各只有 1 套，也走同一个页面 —— 结构一致，用户不用学两套。
 */
public class SourceSetsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUi();
        } catch (Throwable e) {
            // 页面起不来也别让进程退出：把堆栈画在屏幕上，用户能截图/复制
            CrashScreen.show(this, "色卡来源", e);
        }
    }

    private void buildUi() {
        final DataStore s = Store.get(this);
        String group = getIntent().getStringExtra("group");
        if (group == null) group = "pantone";

        LinearLayout root = Page.root(this, Skin.bg(this), true);
        root.addView(header(titleOf(group)));

        List<DataStore.Meta> sets = setsOf(s, group);
        root.addView(Page.section(this, "选择一套", sets.size() + " 套"));

        if (sets.isEmpty()) {
            LinearLayout wrap = Page.wrap(this);
            Page.addFull(wrap, Page.empty(this, "这个来源下没有色卡",
                    "回到色卡页换一个来源看看。", "返回",
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            close();
                        }
                    }));
            root.addView(wrap);
        } else {
            LinearLayout card = Skin.listCard(this);
            for (DataStore.Meta m : sets) {
                if (card.getChildCount() > 0) card.addView(Page.hairline(this));
                card.addView(setRow(s, m));
            }
            LinearLayout wrap = Page.wrap(this);
            Page.addFull(wrap, card);
            root.addView(wrap);
        }

        // 滚动内容底部留白 24dp + 导航/手势条：最后一行不要贴着手势条
        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(1, Skin.dp(this, 24) + Bars.bottom(this)));
        root.addView(gap);

        ScrollView sc = Page.scroller(this);
        sc.addView(root);
        // Page.root 透明，纸张渐变铺在滚动容器上：滚动时底不跟着滑
        sc.setBackground(Skin.background(this));
        sc.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(sc);
        Page.reveal(root);
    }

    // ---------------------------------------------------------------- 头部
    private View header(String title) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Skin.dp(this, 8), Skin.dp(this, 8),
                Skin.dp(this, Page.PAD), Skin.dp(this, 6));

        LinearLayout back = Rows.iconButton(this, R.drawable.lc_back, Skin.text(this),
                new View.OnClickListener() {
                    public void onClick(View v) {
                        close();
                    }
                });
        back.setContentDescription("返回");
        head.addView(back);

        TextView t = Skin.medium(this, title, Skin.T_TITLE, Skin.text(this));
        t.setLetterSpacing(-0.02f);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(t);
        return head;
    }

    // ---------------------------------------------------------------- 行
    /** 一套一行：迷你色带 | 名称 + N 色 | chevron，整行可点。 */
    private View setRow(final DataStore s, final DataStore.Meta m) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Skin.dp(this, 16), Skin.dp(this, 10),
                Skin.dp(this, 16), Skin.dp(this, 10));
        row.setMinimumHeight(Skin.dp(this, 64));

        final View band = miniBand(s, m);
        row.addView(band);

        TextView name = Skin.medium(this, m.name, Skin.T_CARD, Skin.text(this));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameLp.leftMargin = Skin.dp(this, 14);
        row.addView(name, nameLp);

        row.addView(Skin.text(this, fmt(m.count) + " 色", Skin.T_NOTE, Skin.text2(this)));

        ImageView chevron = Skin.icon(this, R.drawable.lc_chevron, Skin.text3(this), 20f);
        LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(
                Skin.dp(this, 20), Skin.dp(this, 20));
        chevronLp.leftMargin = Skin.dp(this, 8);
        row.addView(chevron, chevronLp);

        row.setContentDescription(m.name + "，" + fmt(m.count) + " 色");
        Tap.row(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(SourceSetsActivity.this, CardSetActivity.class);
                i.putExtra("kind", m.kind);
                i.putExtra("id", m.id);
                // 从被点的色带位置放大打开网格，用户能感到"我点的这套变成了那一页"
                openFrom(i, band);
            }
        });
        return row;
    }

    /**
     * 迷你色带：该套前 4 色，外面套 1dp 描边。
     * 描边是必要的 —— 潘通高级金属、TPG 这类套的整体颜色接近白色，白卡上不描边会融进背景。
     */
    private View miniBand(DataStore s, DataStore.Meta m) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        int ring = Skin.dp(this, 1);
        box.setPadding(ring, ring, ring, ring);
        box.setBackground(Skin.stroke(this, Skin.cardColor(this), Skin.cardStroke(this), 10f, 0.8f));
        int size = Skin.dp(this, 36);
        box.addView(BandView.sized(this, sampleColors(s, m), size, size, 10f, false));
        return box;
    }

    /** 该套前 4 个色号；不足 4 个就用最后一个补齐。取不到色号时退回 meta 里的代表色。 */
    private int[] sampleColors(DataStore s, DataStore.Meta m) {
        int[] idx = s.codesOf(m.id);
        int[] out = new int[4];
        if (idx.length == 0) {
            out[0] = m.hex;
            out[1] = m.hex;
            out[2] = m.hex;
            out[3] = m.hex;
            return out;
        }
        for (int i = 0; i < 4; i++) {
            out[i] = s.codeColor(idx[Math.min(i, idx.length - 1)]);
        }
        return out;
    }

    // ---------------------------------------------------------------- 数据
    private String titleOf(String group) {
        if ("pantone".equals(group)) return "潘通色卡";
        if ("ral".equals(group)) return "劳尔色卡";
        if ("cbcc".equals(group)) return "CBCC 中国建筑";
        if ("niban".equals(group)) return "立邦墙面漆";
        return "色卡";
    }

    private List<DataStore.Meta> setsOf(DataStore s, String group) {
        List<DataStore.Meta> out = new ArrayList<DataStore.Meta>();
        for (DataStore.Meta m : s.metas()) {
            if ("pantone".equals(group)) {
                if ("pantset".equals(m.kind) && !m.name.startsWith("CBCC")
                        && !m.name.startsWith("立邦")) {
                    out.add(m);
                }
            } else if ("ral".equals(group)) {
                if ("ralset".equals(m.kind)) out.add(m);
            } else if ("cbcc".equals(group)) {
                if ("pantset".equals(m.kind) && m.name.startsWith("CBCC")) out.add(m);
            } else if ("niban".equals(group)) {
                if ("pantset".equals(m.kind) && m.name.startsWith("立邦")) out.add(m);
            } else {
                // 未知来源（比如以后 Sources 页改了 group 名）：把全部色卡套列出来，别给用户一张空页
                if ("pantset".equals(m.kind) || "ralset".equals(m.kind)) out.add(m);
            }
        }
        return out;
    }

    private static String fmt(int n) {
        if (n < 1000) return String.valueOf(n);
        return n / 1000 + "," + String.format("%03d", n % 1000);
    }
}
