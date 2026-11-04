package com.wwxiaoqi.guanshe.ui;

import android.app.Activity;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.wwxiaoqi.guanshe.Skin;

/**
 * 页面内模态卡片（不用系统 Dialog）：点遮罩或返回键关闭。
 *
 * 用它替代 AlertDialog：系统弹窗在深色主题里是另一套观感，也做不出"卡片从下方升起"的手感。
 */
public final class Overlay {

    private Overlay() {
    }

    /** 弹出一张卡片（必须已经在 Activity 的 content 里）。返回遮罩层，可用于主动关闭。 */
    public static View show(Activity a, View card) {
        View content = a.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return null;
        final ViewGroup parent = (ViewGroup) content;

        final FrameLayout layer = new FrameLayout(a);
        layer.setBackgroundColor(0x66000000);
        layer.setClickable(true);
        layer.setFocusable(true);
        layer.setFocusableInTouchMode(true);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        lp.leftMargin = Skin.dp(a, 28);
        lp.rightMargin = Skin.dp(a, 28);
        layer.addView(card, lp);
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // 吃掉点击，避免穿透到遮罩
            }
        });
        layer.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dismiss(layer);
            }
        });
        layer.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    dismiss(layer);
                    return true;
                }
                return false;
            }
        });

        parent.addView(layer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layer.requestFocus();
        if (Motion.enabled(a)) {
            layer.setAlpha(0f);
            layer.animate().alpha(1f).setDuration(Motion.BASE).start();
        }

        if (Motion.enabled(a)) {
            card.setAlpha(0f);
            card.setScaleX(0.92f);
            card.setScaleY(0.92f);
            card.setTranslationY(Skin.dp(a, 18));
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(Motion.BASE).setInterpolator(Motion.over()).start();
        }
        return layer;
    }

    public static void dismiss(View layer) {
        if (layer == null || layer.getParent() == null) return;
        final ViewGroup parent = (ViewGroup) layer.getParent();
        if (!(layer instanceof ViewGroup)) {
            parent.removeView(layer);
            return;
        }
        final View card = ((ViewGroup) layer).getChildAt(0);
        if (Motion.enabled(layer.getContext())) {
            if (card != null) {
                card.animate().alpha(0f).scaleX(0.95f).scaleY(0.95f)
                        .setDuration(Motion.FAST).start();
            }
            layer.animate().alpha(0f).setDuration(Motion.FAST)
                    .withEndAction(new Runnable() {
                        public void run() {
                            parent.removeView(layer);
                        }
                    }).start();
        } else {
            parent.removeView(layer);
        }
    }

    /** 一张模态卡：白底、圆角 24、有影、内边距 22。 */
    public static LinearLayout card(Activity a) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Skin.stroke(a, Skin.cardColor(a), Skin.cardStroke(a), 24f, 1f));
        Surface.card(card, 24f, Surface.FLOAT);
        int p = Skin.dp(a, 22);
        card.setPadding(p, p, p, p);
        return card;
    }
}
