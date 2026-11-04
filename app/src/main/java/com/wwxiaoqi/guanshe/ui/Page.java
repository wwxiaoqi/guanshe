package com.wwxiaoqi.guanshe.ui;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.R;
import com.wwxiaoqi.guanshe.Skin;

/**
 * 页面骨架：底色 / insets / 标题区 / 分节标题 / 空状态。
 *
 * 这一版的变化：标题更大（24sp）、分节标题字距放宽、图标按钮统一 48dp 触达区、
 * 新增 {@link #empty} 空状态卡片 —— 没内容时也给用户一个能按的出口。
 */
public final class Page {

    /** 与 Skin.padToBars 的位标志一致：1=top 2=bottom 4=左右 */
    public static final int TOP = 1, BOTTOM = 2, HORIZONTAL = 4;

    public static final int PAD = 20;

    private Page() {
    }

    /** 页面根容器。调用前 Activity 必须已经 `Skin.setupWindow(this)`。
     *  根容器不铺底色（透明）：纸张渐变与色晕由宿主（MainActivity / 各 Activity 的 frame）提供，
     *  这样切页和滑动时底层背景不会跟着闪，也能在标题后面看到一点极淡的颜色。 */
    public static LinearLayout root(Activity a, int pageBg, boolean padTop) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0x00000000);
        root.setClipToPadding(false);
        if (padTop) root.setPadding(0, Bars.top(a), 0, 0);
        Bars.icons(a, pageBg);
        return root;
    }

    public static ScrollView scroller(Context c) {
        ScrollView s = new ScrollView(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        s.setFillViewport(false);
        s.setClipToPadding(false);
        s.setVerticalScrollBarEnabled(false);
        s.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        return s;
    }

    /** 详情页用：能整块下拉的 ScrollView（白纸任意位置都能拖下来）。 */
    public static PullScrollView pullScroller(Context c) {
        PullScrollView s = new PullScrollView(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return s;
    }

    public static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setLayoutParams(new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return v;
    }

    /** 标题区：大标题 + 副标 + 右侧 0/1/2 个图标按钮。 */
    public static LinearLayout header(Context c, String title, String sub,
                                      int[] icons, View.OnClickListener[] listeners) {
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(Skin.dp(c, PAD), Skin.dp(c, 10), Skin.dp(c, PAD - 12), Skin.dp(c, 6));

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = Skin.medium(c, title, Skin.T_TITLE, Skin.text(c));
        t.setLetterSpacing(-0.02f);
        left.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = Skin.text(c, sub, Skin.T_NOTE, Skin.text2(c));
            s.setPadding(0, Skin.dp(c, 3), 0, 0);
            left.addView(s);
        }
        wrap.addView(left);

        if (icons != null) {
            for (int i = 0; i < icons.length; i++) {
                View.OnClickListener l = (listeners != null && i < listeners.length)
                        ? listeners[i] : null;
                wrap.addView(Rows.iconButton(c, icons[i], Skin.text2(c), l));
            }
        }
        return wrap;
    }

    /** 分节标题：左边小字，右边可选的脚注。 */
    public static LinearLayout section(Context c, String left, String right) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Skin.dp(c, PAD), Skin.dp(c, 22), Skin.dp(c, PAD), Skin.dp(c, 8));
        TextView l = Skin.text(c, left, Skin.T_NOTE, Skin.text2(c));
        l.setLetterSpacing(0.05f);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);
        if (right != null && !right.isEmpty()) {
            row.addView(Skin.text(c, right, Skin.T_NOTE, Skin.text3(c)));
        }
        return row;
    }

    /** 分节标题：右边可以是一个可点的动作（如"清空"）。 */
    public static LinearLayout section(Context c, String left, String right, View.OnClickListener onRight) {
        LinearLayout row = section(c, left, right);
        if (onRight != null && row.getChildCount() > 1) {
            View action = row.getChildAt(1);
            action.setOnClickListener(onRight);
            Tap.row(action, 999f);
            int p = Skin.dp(c, 8);
            action.setPadding(p, p / 2, p, p / 2);
        }
        return row;
    }

    /** 左右 20dp 的内容区（直接贴在纸面上）。 */
    public static LinearLayout wrap(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(Skin.dp(c, PAD), 0, Skin.dp(c, PAD), 0);
        return v;
    }

    public static void gap(Context c, LinearLayout parent, float dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Skin.dp(c, dp)));
        parent.addView(v);
    }

    /** 整宽加一个子 View（显式 MATCH_PARENT，否则大圆角卡会被裁角）。 */
    public static void addFull(LinearLayout parent, View child) {
        LinearLayout.LayoutParams lp;
        if (child.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            lp = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) child.getLayoutParams());
        } else {
            lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        parent.addView(child, lp);
    }

    /** 一行文字（左 label / 右 value / 可选 chevron）。 */
    public static LinearLayout listRow(Context c, String label, String value, boolean chevron,
                                       View.OnClickListener l) {
        return Rows.row(c, label, value, chevron, l);
    }

    /** 卡内的一条发丝线（左缩进 16dp，右到边）。 */
    public static View hairline(Context c) {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Skin.dp(c, 0.7f)));
        lp.leftMargin = Skin.dp(c, 16);
        v.setLayoutParams(lp);
        v.setBackgroundColor(Skin.sep(c));
        return v;
    }

    /**
     * 空状态：一张有影子的卡，标题 + 一句解释 + 一个主按钮。
     * 比一行灰字友好 —— 用户知道下一步能做什么。
     */
    public static View empty(Context c, String title, String body, String action,
                             View.OnClickListener l) {
        LinearLayout card = Skin.card(c);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(Skin.dp(c, 24), Skin.dp(c, 26), Skin.dp(c, 24), Skin.dp(c, 26));

        TextView t = Skin.medium(c, title, Skin.T_CARD, Skin.text(c));
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(-0.01f);
        card.addView(t);

        if (body != null && !body.isEmpty()) {
            TextView b = Skin.text(c, body, Skin.T_NOTE, Skin.text2(c));
            b.setGravity(Gravity.CENTER);
            b.setLineSpacing(Skin.dp(c, 3), 1f);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = Skin.dp(c, 6);
            card.addView(b, bp);
        }

        if (action != null && l != null) {
            TextView pill = Skin.pill(c, action, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Skin.dp(c, 16);
            Tap.chip(pill);
            pill.setOnClickListener(l);
            card.addView(pill, lp);
        }
        return card;
    }

    /** 页面首次展示 / 内容重建后：让一级子元素依次升起。 */
    public static void reveal(View root) {
        if (root instanceof ViewGroup) Motion.staggerIn((ViewGroup) root, 0, 8);
    }
}
