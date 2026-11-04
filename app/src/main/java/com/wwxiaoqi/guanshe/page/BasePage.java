package com.wwxiaoqi.guanshe.page;

import android.view.View;
import android.widget.ScrollView;

import com.wwxiaoqi.guanshe.MainActivity;

/**
 * 四个 tab 页的基类：负责"只建一次"和"记住滚动位置"。
 *
 * 承诺来自 specs/app-shell：切 tab 再切回来，列表不能跳回顶部。
 */
public abstract class BasePage {

    protected final MainActivity host;
    private View root;
    protected ScrollView scroller;

    protected BasePage(MainActivity host) {
        this.host = host;
    }

    public final View view() {
        if (root == null) root = build();
        return root;
    }

    protected abstract View build();

    /** 每次被切到这个 tab 时调用（可以在这里刷新收藏/最近）。 */
    public void onShow() {
    }

    public int scrollY() {
        return scroller == null ? 0 : scroller.getScrollY();
    }

    /** 再点一次当前 tab 时回到顶部。 */
    public void scrollToTop() {
        if (scroller != null) scroller.smoothScrollTo(0, 0);
    }

    public void restoreScroll(int y) {
        if (scroller == null) return;
        final ScrollView s = scroller;
        final int target = y;
        s.post(new Runnable() {
            public void run() {
                s.scrollTo(0, target);
            }
        });
    }

    /**
     * 底栏现在浮在页面之上：给滚动区补一段底部内边距，最后一个元素才能滚到底栏上方。
     * 页面自己的悬浮件（如右下 FAB）可以覆写 {@link #onBottomInset} 跟着上移。
     */
    public void setBottomInset(int px) {
        if (scroller != null) {
            scroller.setClipToPadding(false);
            scroller.setPadding(scroller.getPaddingLeft(), scroller.getPaddingTop(),
                    scroller.getPaddingRight(), Math.max(0, px));
        }
        onBottomInset(Math.max(0, px));
    }

    /** 给页面里的悬浮件预留底栏高度。 */
    protected void onBottomInset(int px) {
    }
}
