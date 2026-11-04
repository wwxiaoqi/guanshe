package com.wwxiaoqi.guanshe.ui;

import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.Skin;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 页面起不来时，把异常直接画在屏幕上，而不是让进程退出。
 *
 * 容器里没有 adb，闪退只能靠"用户读给我听"。有了这一屏，用户能截图，也能一键复制堆栈。
 */
public final class CrashScreen {

    private CrashScreen() {
    }

    public static void show(final BaseActivity a, String where, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        final String trace = where + "\n" + sw;

        LinearLayout root = Page.root(a, Skin.bg(a), true);
        LinearLayout box = Page.wrap(a);
        box.setPadding(Skin.dp(a, Page.PAD), Skin.dp(a, 40), Skin.dp(a, Page.PAD), Skin.dp(a, 24));

        box.addView(Skin.medium(a, "这个页面起不来", Skin.T_CARD, Skin.text(a)));
        TextView sub = Skin.text(a, where, Skin.T_NOTE, Skin.text2(a));
        sub.setPadding(0, Skin.dp(a, 4), 0, Skin.dp(a, 12));
        box.addView(sub);
        box.addView(Skin.text(a, "原因", Skin.T_NOTE, Skin.text3(a)));
        TextView body = Skin.text(a, e.getClass().getSimpleName() + "：" + e.getMessage(),
                Skin.T_BODY, Skin.text(a));
        body.setLineSpacing(Skin.dp(a, 4), 1f);
        box.addView(body);

        TextView copy = Skin.pill(a, "复制完整堆栈", true);
        copy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Snack.copy(v, trace);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Skin.dp(a, 16);
        box.addView(copy, lp);

        TextView detail = Skin.text(a, trace, 10, Skin.text3(a));
        detail.setLineSpacing(Skin.dp(a, 2), 1f);
        detail.setPadding(0, Skin.dp(a, 18), 0, 0);
        detail.setTextIsSelectable(true);
        box.addView(detail);

        root.addView(box);
        ScrollView sc = Page.scroller(a);
        sc.addView(root);
        a.setContentView(sc);
        Bars.icons(a, Skin.bg(a));
        if (TextUtils.isEmpty(e.getMessage())) {
            // 只有类型没有消息时也留一行，免得界面看起来是空的
            body.setText(e.getClass().getName());
        }
    }
}
