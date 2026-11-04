package com.wwxiaoqi.guanshe.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.Skin;

import java.util.ArrayList;
import java.util.List;

/**
 * 整屏暗场 + 弧线悬浮卡 + 指南针刻度盘 + 大分享按钮。
 *
 * 暗场背景直接用这组配色铺满（再压一层黑），卡片悬在中间偏上；
 * 上方是半圆刻度盘，翻卡时刻度跟着转、每过一格轻震一次；
 * 下方是一个大大的圆形分享按钮，点和点卡片效果一样。
 *
 * 卡片有 6 种版式：海报 / 色票卡 / 撞色 / 拼贴 / 票根 / 暗场布带。
 */
public final class ShareCards {

    private static final int LIGHT = 0xE6FFFFFF;
    private static final int DIM = 0x99FFFFFF;

    private ShareCards() {
    }

    /** 一组配色：6 张版式卡。 */
    public static void palette(final Activity a, int[] colors, String label, final Runnable onDismiss) {
        if (a == null || colors == null || colors.length == 0) return;
        List<Bitmap> cards = new ArrayList<Bitmap>(6);
        cards.add(fit(ShareCard.palette(colors, label, ShareCard.STYLE_POSTER)));
        cards.add(fit(ShareCard.palette(colors, label, ShareCard.STYLE_CARD)));
        cards.add(fit(ShareCard.palette(colors, label, ShareCard.STYLE_SPLIT)));
        cards.add(fit(ShareCard.palette(colors, label, ShareCard.STYLE_COLLAGE)));
        cards.add(fit(ShareCard.palette(colors, label, ShareCard.STYLE_TICKET)));
        cards.add(fit(ShareCard.palette(colors, label, ShareCard.STYLE_RIBBON)));
        show(a, cards, Share.paletteText(colors, label), colors, onDismiss);
    }

    /** 单个颜色：只有一张卡，点卡片分享。 */
    public static void colorInteractive(final Activity a, int color, String title,
                                        final Runnable onDismiss) {
        if (a == null) return;
        List<Bitmap> cards = new ArrayList<Bitmap>(1);
        cards.add(fit(ShareCard.color(color, title)));
        show(a, cards, Share.colorText(color, title), new int[]{color}, onDismiss);
    }

    /** 单个颜色：只有一张卡，直接分享（顶部按钮用）。 */
    public static void color(final Activity a, int color, String title) {
        if (a == null) return;
        Bitmap bm = ShareCard.color(color, title);
        Share.image(a, bm, Share.colorText(color, title));
    }

    private static void show(final Activity a, final List<Bitmap> cards,
                             final String shareText, final int[] stageColors,
                             final Runnable onDismiss) {
        final ViewGroup parent = (ViewGroup) a.findViewById(android.R.id.content);
        if (parent == null) return;

        final float density = a.getResources().getDisplayMetrics().density;
        int screenW = a.getResources().getDisplayMetrics().widthPixels;
        int screenH = a.getResources().getDisplayMetrics().heightPixels;
        final int cardW = (int) Math.min(screenW * 0.66f,
                Math.min(dp(density, 300), screenH * 0.272f));
        final int cardH = Math.round(cardW * 1.25f);

        final FrameLayout layer = new FrameLayout(a);
        layer.setClickable(true);
        layer.setFocusable(true);
        layer.setFocusableInTouchMode(true);

        // 背景：把这组颜色铺满整屏，再压一层黑
        layer.addView(new ColorStage(a, stageColors), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final ArcCards arc = new ArcCards(a);
        for (int i = 0; i < cards.size(); i++) {
            final int index = i;
            final FrameLayout page = new FrameLayout(a);
            page.setLayoutParams(new FrameLayout.LayoutParams(cardW, cardH));
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageBitmap(cards.get(i));
            iv.setContentDescription("分享卡片 " + (i + 1) + " / " + cards.size());
            page.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            Surface.card(page, 22f, 0f);
            page.setBackground(Skin.round(a, 0xFFFFFFFF, 22f));
            arc.addCard(page);
        }
        layer.addView(arc, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 指南针刻度盘：放在卡片下方、分享按钮上方
        final CompassDial dial = new CompassDial(a);
        int dialH = Math.min(dp(density, 96), (int) (screenH * 0.115f));
        FrameLayout.LayoutParams dialLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dialH, Gravity.TOP);
        dialLp.topMargin = (int) (screenH * 0.44f + cardH / 2f) + dp(density, 54);
        layer.addView(dial, dialLp);
        dial.setCount(cards.size());
        if (cards.size() <= 1) dial.setVisibility(View.GONE);

        // 关闭
        final TextView close = new TextView(a);
        close.setText("×");
        close.setTextSize(22f);
        close.setGravity(Gravity.CENTER);
        close.setTextColor(LIGHT);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0x26FFFFFF);
        closeBg.setCornerRadius(dp(density, 20));
        close.setBackground(closeBg);
        close.setContentDescription("关闭分享卡片");
        Tap.chip(close);
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                dp(density, 40), dp(density, 40), Gravity.TOP | Gravity.END);
        closeLp.topMargin = Bars.top(a) + dp(density, 8);
        closeLp.rightMargin = dp(density, 16);
        layer.addView(close, closeLp);

        // 底部：圆形大分享按钮 + 说明
        final LinearLayout bottom = new LinearLayout(a);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        final FrameLayout shareBtn = new FrameLayout(a);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.OVAL);
        btnBg.setColor(0x22FFFFFF);
        btnBg.setStroke(dp(density, 1), 0x66FFFFFF);
        shareBtn.setBackground(btnBg);
        ImageView btnIcon = new ImageView(a);
        btnIcon.setImageResource(com.wwxiaoqi.guanshe.R.drawable.lc_share);
        btnIcon.setColorFilter(0xFFF4F1EC);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                dp(density, 26), dp(density, 26), Gravity.CENTER);
        shareBtn.addView(btnIcon, iconLp);
        shareBtn.setContentDescription("分享当前卡片");
        Tap.chip(shareBtn);
        bottom.addView(shareBtn, new LinearLayout.LayoutParams(
                dp(density, 76), dp(density, 76)));
        TextView label = new TextView(a);
        label.setText("分享这张卡");
        label.setTextSize(11.5f);
        label.setTextColor(DIM);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(density, 8);
        bottom.addView(label, labelLp);
        TextView hint = new TextView(a);
        hint.setText(cards.size() > 1 ? "左右滑动翻卡 · 刻度会跟着转" : "点卡片或按钮直接分享");
        hint.setTextSize(10.5f);
        hint.setTextColor(0x66FFFFFF);
        hint.setGravity(Gravity.CENTER);
        hint.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(density, 6);
        bottom.addView(hint, hintLp);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottomLp.bottomMargin = Bars.bottom(a) + dp(density, 26);
        layer.addView(bottom, bottomLp);

        parent.addView(layer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layer.requestFocus();
        Bars.icons(a, 0xFF101013);

        final boolean[] dismissed = {false};
        final Runnable doDismiss = new Runnable() {
            public void run() {
                if (dismissed[0]) return;
                dismissed[0] = true;
                arc.stopBreathing();
                if (Motion.enabled(a)) {
                    arc.animate().translationY(dp(density, 160)).alpha(0f)
                            .setDuration(200).start();
                    layer.animate().alpha(0f).setDuration(230)
                            .withEndAction(new Runnable() {
                                public void run() {
                                    parent.removeView(layer);
                                    if (onDismiss != null) onDismiss.run();
                                    else Bars.icons(a, Skin.bg(a));
                                }
                            }).start();
                } else {
                    parent.removeView(layer);
                    if (onDismiss != null) onDismiss.run();
                    else Bars.icons(a, Skin.bg(a));
                }
            }
        };

        final Runnable shareCurrent = new Runnable() {
            public void run() {
                final int index = arc.current();
                if (index < 0 || index >= cards.size()) return;
                Haptics.shareTap(arc);
                View active = arc.getChildAt(index);
                if (Motion.enabled(a)) {
                    active.animate().scaleX(0.96f).scaleY(0.96f)
                            .setDuration(Motion.FAST)
                            .withEndAction(new Runnable() {
                                public void run() {
                                    Share.image(a, cards.get(index), shareText);
                                    active.animate().scaleX(1f).scaleY(1f)
                                            .setDuration(180).start();
                                }
                            }).start();
                } else {
                    Share.image(a, cards.get(index), shareText);
                }
            }
        };

        arc.setListener(new ArcCards.Listener() {
            public void onCardTap(int index) {
                shareCurrent.run();
            }

            public void onIndexChanged(int index) {
                // 震动在跨格时已经触发（onTick），这里不再重复
            }

            public void onTick(int index) {
                Haptics.shareTick(arc);
            }

            public void onBackdropTap() {
                doDismiss.run();
            }

            public void onOffsetChanged(float value) {
                dial.setOffset(value);
            }
        });

        shareBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                shareCurrent.run();
            }
        });
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDismiss.run();
            }
        });
        layer.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    doDismiss.run();
                    return true;
                }
                return false;
            }
        });

        arc.show(0);

        // 暗场淡入 → 卡片飞入 → 浮动；刻度盘从上、按钮从下轻轻归位
        if (Motion.enabled(a)) {
            layer.setAlpha(0f);
            layer.animate().alpha(1f).setDuration(220).start();
            arc.setAlpha(0f);
            arc.setTranslationY(dp(density, 200));
            arc.animate().alpha(1f).translationY(0f)
                    .setDuration(380)
                    .setInterpolator(new OvershootInterpolator(1.05f))
                    .withEndAction(new Runnable() {
                        public void run() {
                            arc.startBreathing();
                        }
                    }).start();
            if (dial.getVisibility() == View.VISIBLE) {
                dial.setAlpha(0f);
                dial.setTranslationY(-dp(density, 26));
                dial.animate().alpha(1f).translationY(0f)
                        .setDuration(320).setStartDelay(60).start();
            }
            bottom.setAlpha(0f);
            bottom.setTranslationY(dp(density, 30));
            bottom.animate().alpha(1f).translationY(0f)
                    .setDuration(320).setStartDelay(90).start();
        } else {
            arc.startBreathing();
        }
    }

    /** 6 张 1080×1350 的整图对老机器偏重，显示与分享都缩到 900 宽。 */
    private static Bitmap fit(Bitmap src) {
        if (src == null) return null;
        try {
            if (src.getWidth() <= 900) return src;
            Bitmap out = Bitmap.createScaledBitmap(src, 900, 1125, true);
            if (out != src) src.recycle();
            return out;
        } catch (Throwable t) {
            return src;
        }
    }

    private static int dp(float density, float v) {
        return Math.round(v * density);
    }

    /** 暗场背景：这组颜色铺满整屏，从上到下逐渐压暗。 */
    private static final class ColorStage extends View {
        private final int[] colors;
        private final Paint paint = new Paint();
        private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);

        ColorStage(Activity a, int[] colors) {
            super(a);
            this.colors = colors;
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (colors != null && colors.length > 1) {
                float cell = w / (float) colors.length;
                for (int i = 0; i < colors.length; i++) {
                    paint.setColor(colors[i]);
                    cv.drawRect(i * cell, 0, (i + 1) * cell, h, paint);
                }
            } else {
                paint.setColor(colors != null && colors.length == 1 ? colors[0] : 0xFF17171A);
                cv.drawRect(0, 0, w, h, paint);
            }
            dim.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0xB8000000, 0xCC000000, 0xF0000000},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, dim);
        }
    }
}
