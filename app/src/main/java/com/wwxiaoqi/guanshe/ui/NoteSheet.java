package com.wwxiaoqi.guanshe.ui;

import android.app.Activity;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.Skin;
import com.wwxiaoqi.guanshe.ui.Snack;

/**
 * 收藏备注：从底部升起一张卡片，给收藏的配色/颜色写一句话（「客厅主墙」「备选」）。
 *
 * 字数不设上限；换行会在保存时压成空格（存储是一行一条）。
 * 页面是 edge-to-edge，系统不会替我们 resize，所以自己盯 IME inset 把卡片顶到键盘上方。
 */
public final class NoteSheet {

    private NoteSheet() {
    }

    /**
     * @param subject 收藏项的标题（色号 / 配色名），显示在输入框上方
     * @param onDone  保存或清除后回调，用来刷新列表 / 详情页
     */
    public static void show(final Activity a, final FavStore favs, final String key,
                            final String title, String subject, final Runnable onDone) {
        if (a == null || favs == null || key == null) return;
        View root = a.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;
        final ViewGroup parent = (ViewGroup) root;

        final FrameLayout layer = new FrameLayout(a);
        layer.setBackgroundColor(0x66000000);
        layer.setClickable(true);
        layer.setFocusable(true);
        layer.setFocusableInTouchMode(true);

        final LinearLayout card = Overlay.card(a);
        card.setClickable(true);

        TextView head = Skin.medium(a, title, Skin.T_TITLE, Skin.text(a));
        head.setLetterSpacing(-0.02f);
        card.addView(head);

        if (subject != null && subject.length() > 0) {
            TextView sub = Skin.text(a, subject, Skin.T_NOTE, Skin.text3(a));
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setPadding(0, Skin.dp(a, 4), 0, Skin.dp(a, 12));
            card.addView(sub);
        }

        final EditText input = new EditText(a);
        input.setHint("写点提醒，比如「客厅主墙」「备选」");
        input.setTextSize(Skin.T_BODY);
        input.setTextColor(Skin.text(a));
        input.setHintTextColor(Skin.text3(a));
        input.setBackground(Skin.stroke(a, Skin.bg(a), Skin.cardStroke(a), 12f, 1f));
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinHeight(Skin.dp(a, 92));
        input.setMaxLines(6);                 // 写长了在框里滚，不把卡片顶出屏幕
        input.setVerticalScrollBarEnabled(true);
        input.setPadding(Skin.dp(a, 14), Skin.dp(a, 12), Skin.dp(a, 14), Skin.dp(a, 12));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        final String old = favs.note(key);
        input.setText(old);
        if (old.length() > 0) input.setSelection(old.length());
        card.addView(input);

        // 底部一行：清除（有旧备注时）· 取消 · 保存
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Skin.dp(a, 16), 0, 0);

        if (old.length() > 0) {
            TextView clear = Skin.pill(a, "清除", false);
            Tap.chip(clear);
            row.addView(clear);
            clear.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    favs.removeNote(key);
                    Overlay.dismiss(layer);
                    Snack.show(a.findViewById(android.R.id.content), "备注已清除");
                    if (onDone != null) onDone.run();
                }
            });
        }

        View spacer = new View(a);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView cancel = Skin.pill(a, "取消", false);
        Tap.chip(cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Overlay.dismiss(layer);
            }
        });
        row.addView(cancel);

        TextView save = Skin.pill(a, "保存", true);
        Tap.chip(save);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Skin.dp(a, 8);
        row.addView(save, slp);
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                favs.setNote(key, input.getText().toString());
                Overlay.dismiss(layer);
                Snack.show(a.findViewById(android.R.id.content), "备注已保存");
                if (onDone != null) onDone.run();
            }
        });
        card.addView(row);

        // 卡片贴底；键盘弹起时抬到键盘上方
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        lp.leftMargin = Skin.dp(a, 16);
        lp.rightMargin = Skin.dp(a, 16);
        final int baseBottom = Skin.dp(a, 16) + Bars.bottom(a);
        lp.bottomMargin = baseBottom;
        layer.addView(card, lp);

        layer.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                int ime = 0;
                if (Build.VERSION.SDK_INT >= 30) {
                    ime = in.getInsets(WindowInsets.Type.ime()).bottom;
                }
                int bottom = Math.max(baseBottom, ime + Skin.dp(a, 8));
                if (bottom != lp.bottomMargin) {
                    lp.bottomMargin = bottom;
                    card.setLayoutParams(lp);
                }
                return in;
            }
        });

        layer.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Overlay.dismiss(layer);
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // 吃掉点击，别穿透到遮罩
            }
        });
        layer.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    Overlay.dismiss(layer);
                    return true;
                }
                return false;
            }
        });

        parent.addView(layer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layer.requestFocus();
        layer.requestApplyInsets();

        if (Motion.enabled(a)) {
            layer.setAlpha(0f);
            layer.animate().alpha(1f).setDuration(Motion.FAST).start();
            card.setTranslationY(Skin.dp(a, 40));
            card.animate().translationY(0f).setDuration(Motion.BASE)
                    .setInterpolator(Motion.over()).start();
        }

        input.post(new Runnable() {
            public void run() {
                input.requestFocus();
                InputMethodManager imm =
                        (InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }
}