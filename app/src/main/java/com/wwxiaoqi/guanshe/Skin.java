package com.wwxiaoqi.guanshe;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 设计系统。取色全部走 R.color（values / values-night 两套），所以深色模式不用改一行逻辑。
 *
 * 这一版的三条规矩：
 * 1. 暖纸 + 墨的底色不变，但允许一层很浅的暖影：影子只加在"能按 / 浮在页面上"的东西上，
 *    静态文字与小图标仍然不用阴影（见 ui/Surface）。
 * 2. 主色仍然只上小面积；动效用 ui/Motion，触感用 ui/Haptics，不许在页面里另写一套曲线。
 * 3. 字重只到 medium，不用 BOLD；层级靠字号断层（24 / 34 vs 12），不靠加粗。
 */
public final class Skin {

    public static final float R_CARD = 18f;
    public static final float R_PILL = 20f;
    /** 详情页白色 sheet 的圆角（比卡片更圆一点，压住色带时更柔和）。 */
    public static final float R_SHEET = 26f;

    // 字号阶梯
    public static final float T_TITLE = 24f;
    public static final float T_METRIC = 34f;
    public static final float T_CARD = 17f;
    public static final float T_BODY = 14f;
    public static final float T_LABEL = 14f;
    public static final float T_NOTE = 12f;
    public static final float T_CAPTION = 12f;
    public static final float T_CHIP = 12f;

    private Skin() {
    }

    // ---------- 颜色 ----------
    public static int c(Context ctx, int res) {
        return ctx.getResources().getColor(res, ctx.getTheme());
    }

    public static int bg(Context c) { return c(c, R.color.bg); }

    /** 页面背景：kit 提供了 bg_gradient 就用渐变（warm），否则纯色 */
    public static android.graphics.drawable.Drawable background(Context c) {
        int id = c.getResources().getIdentifier("bg_gradient", "drawable", c.getPackageName());
        if (id != 0) {
            try {
                return c.getResources().getDrawable(id, c.getTheme());
            } catch (Exception ignored) {
                // 退回纯色
            }
        }
        return new android.graphics.drawable.ColorDrawable(bg(c));
    }
    public static int cardColor(Context c) { return c(c, R.color.card); }
    public static int cardStroke(Context c) { return c(c, R.color.cardStroke); }
    public static int accentFill(Context c) { return c(c, R.color.accentFill); }
    public static int okText(Context c) { return c(c, R.color.okText); }
    public static int warnText(Context c) { return c(c, R.color.warnText); }
    public static int dangerText(Context c) { return c(c, R.color.dangerText); }
    public static int sep(Context c) { return c(c, R.color.sep); }
    public static int track(Context c) { return c(c, R.color.track); }
    public static int text(Context c) { return c(c, R.color.text); }
    public static int text2(Context c) { return c(c, R.color.text2); }
    public static int text3(Context c) { return c(c, R.color.text3); }
    public static int accent(Context c) { return c(c, R.color.accent); }
    public static int ok(Context c) { return c(c, R.color.ok); }
    public static int warn(Context c) { return c(c, R.color.warn); }
    public static int danger(Context c) { return c(c, R.color.danger); }

    /** 语义色按 12% 透明度做底（chip / pill 的常见做法） */
    public static int tint(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(alpha * 255) << 24);
    }

    // ---------- 风格旋钮（每个 kit 覆盖 kit_type_scale / kit_radius_card） ----------

    public static float typeScale(Context c) {
        try {
            return Float.parseFloat(c.getString(R.string.kit_type_scale));
        } catch (Exception e) {
            return 1f;
        }
    }

    /** kit 的结构旋钮：分隔线 solid / dotted / none */
    public static String kitDivider(Context c) {
        try { return c.getString(R.string.kit_divider); } catch (Exception e) { return "solid"; }
    }

    /** kit 的结构旋钮：按钮 pill / rect / outline */
    public static String kitButton(Context c) {
        try { return c.getString(R.string.kit_button); } catch (Exception e) { return "pill"; }
    }

    public static float cardRadius(Context c) {
        return c.getResources().getDimension(R.dimen.kit_radius_card)
                / c.getResources().getDisplayMetrics().density;
    }

    // ---------- 尺寸 ----------
    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable stroke(Context c, int fill, int strokeColor, float radiusDp, float w) {
        GradientDrawable d = round(c, fill, radiusDp);
        d.setStroke(Math.max(1, dp(c, w)), strokeColor);
        return d;
    }

    // ---------- 文本 ----------
    public static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp * typeScale(c));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView medium(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(medium());
        return t;
    }

    /** 大数字：等宽数字，字距收紧一点（tabular numerals 让刷新时数字不左右跳） */
    public static TextView metric(Context c, String s, float sp, int color) {
        TextView t = medium(c, s, sp, color);
        t.setFontFeatureSettings("'tnum'");
        t.setLetterSpacing(-0.01f);
        return t;
    }

    public static TextView note(Context c, String s) {
        return text(c, s, T_NOTE, text2(c));
    }

    public static TextView caption(Context c, String s) {
        return text(c, s, T_CAPTION, text3(c));
    }

    // ---------- 容器 ----------
    /** 一张卡：白底 / 圆角 / 20dp 内边距 / 一层很浅的暖影 */
    public static LinearLayout card(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        float r = cardRadius(c);
        v.setBackground(stroke(c, cardColor(c), cardStroke(c), r, 1f));
        com.wwxiaoqi.guanshe.ui.Surface.card(v, r, com.wwxiaoqi.guanshe.ui.Surface.CARD);
        int p = dp(c, 20);
        v.setPadding(p, p, p, p);
        return v;
    }

    /** 详情页的白色 sheet：上圆角、下贴边，带一层压在色带上的影子。 */
    public static LinearLayout sheet(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        float r = dp(c, R_SHEET);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardColor(c));
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        v.setBackground(bg);
        // 只给轮廓、不裁剪：贴屏幕底时若裁剪，左右下角会各缺一个小圆角
        com.wwxiaoqi.guanshe.ui.Surface.outline(v, R_SHEET);
        com.wwxiaoqi.guanshe.ui.Surface.elevate(v, com.wwxiaoqi.guanshe.ui.Surface.SHEET);
        return v;
    }

    /** 行间分隔线：按 kit 决定实线 / 点线 / 不画 */
    public static View divider(Context c) {
        String mode = kitDivider(c);
        if ("none".equals(mode)) {
            return new View(c);
        }
        if ("dotted".equals(mode)) {
            View v = new View(c);
            final int color = sep(c);
            v.setBackground(new android.graphics.drawable.Drawable() {
                @Override
                public void draw(android.graphics.Canvas canvas) {
                    android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    p.setColor(color);
                    p.setStyle(android.graphics.Paint.Style.STROKE);
                    p.setStrokeWidth(dp(c, 1));
                    p.setPathEffect(new android.graphics.DashPathEffect(
                            new float[]{dp(c, 2), dp(c, 3)}, 0));
                    canvas.drawLine(0, getBounds().height() / 2f,
                            getBounds().width(), getBounds().height() / 2f, p);
                }

                @Override public void setAlpha(int a) { }
                @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
                @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
            });
            v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
            return v;
        }
        return hairline(c);
    }

    /** 0.7dp 发丝线（不是 1px：在 3x 屏上 0.7dp≈2px，看起来才是"一条线"） */
    public static View hairline(Context c) {
        View v = new View(c);
        v.setBackgroundColor(sep(c));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 0.7f)));
        return v;
    }

    public static void gap(Context c, LinearLayout parent, float dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dp)));
        parent.addView(v);
    }

    public static View spacer(Context c, float weight) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, weight));
        return v;
    }

    /** 图标：22dp 描边矢量，颜色跟文字二级走 */
    public static ImageView icon(Context c, int res, int color, float sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** 触摸区 48dp、图标 22dp 的无底按钮（刷新/齿轮） */
    public static LinearLayout iconButton(Context c, int res, int color, View.OnClickListener l) {
        LinearLayout box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER);
        int s = dp(c, 48);
        box.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        box.addView(icon(c, res, color, 22f));
        ripple(box);
        if (l != null) box.setOnClickListener(l);
        return box;
    }

    /** 触感用系统 ripple，颜色 8% 黑/白 */
    public static void ripple(View v) {
        Context c = v.getContext();
        int color = isNight(c) ? 0x33FFFFFF : 0x22000000;
        // 只有 3 参构造；mask 传 null 才是"铺满 view 边界"（传透明 drawable 会让波纹消失）
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(color), v.getBackground(), null));
    }

    public static boolean isNight(Context c) {
        int mode = c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** 3dp 配额条：轨道 + 填充，两端全圆角 */
    @SuppressWarnings("deprecation")
    public static LinearLayout bar(Context c, double percent, int color) {
        int h = dp(c, 3);
        LinearLayout track = new LinearLayout(c);
        track.setBackground(round(c, track(c), 1.5f));
        track.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

        View fill = new View(c);
        float p = (float) Math.max(0, Math.min(100, percent));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h, p / 100f);
        LinearLayout rest = new LinearLayout(c);
        rest.setLayoutParams(new LinearLayout.LayoutParams(0, h, (100 - p) / 100f));
        fill.setBackground(round(c, color, 1.5f));
        track.addView(fill, lp);
        track.addView(rest);
        return track;
    }

    // ---------- 组件（四类 App 通用的那几种） ----------

    /** 无内边距的卡：装行列表 / 设置项用（行自己带内边距） */
    public static LinearLayout listCard(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        float r = cardRadius(c);
        v.setBackground(stroke(c, cardColor(c), cardStroke(c), r, 1f));
        com.wwxiaoqi.guanshe.ui.Surface.card(v, r, com.wwxiaoqi.guanshe.ui.Surface.CARD);
        v.setPadding(0, 0, 0, 0);
        return v;
    }

    /** 一行：label 左 / 值右 / 可选 chevron。返回值上的 tag="value" 是右侧那个 TextView */
    public static LinearLayout row(Context c, String label, String value, boolean chevron, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = dp(c, 18), v = dp(c, 15);
        row.setPadding(h, v, h, v);
        row.setMinimumHeight(dp(c, 54));
        row.addView(text(c, label, T_BODY, text(c)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = text(c, value == null ? "" : value, T_NOTE, text2(c));
        val.setTag("value");
        row.addView(val);
        if (chevron) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 20), dp(c, 20));
            lp.leftMargin = dp(c, 8);
            row.addView(icon(c, R.drawable.lc_chevron, text3(c), 20f), lp);
        }
        if (l != null) {
            ripple(row);
            row.setOnClickListener(l);
        }
        return row;
    }

    /** 行与行之间的发丝线：从文字起点开始，不贯到卡片左缘 */
    public static View rowHairline(Context c) {
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(c, 18), 0, 0, 0);
        wrap.addView(divider(c));
        return wrap;
    }

    /** 两列分项：赠送余额 / 充值余额 这种 */
    public static LinearLayout statRow(Context c, String l1, String v1, String l2, String v2) {
        LinearLayout cols = new LinearLayout(c);
        cols.addView(stat(c, l1, v1), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cols.addView(stat(c, l2, v2), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return cols;
    }

    private static LinearLayout stat(Context c, String label, String value) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(c, label, T_LABEL, text2(c)));
        gap(c, box, 4);
        box.addView(text(c, value, T_BODY, text(c)));
        return box;
    }

    /** 一块的标题（列表上方的 "服务" / "关于"） */
    public static TextView section(Context c, String s) {
        TextView t = text(c, s, T_NOTE, text2(c));
        t.setPadding(dp(c, 18), dp(c, 6), dp(c, 18), dp(c, 6));
        return t;
    }

    /** 大数字 + 小单位（单位降一号、灰一档，且不破坏基线） */
    public static TextView metric(Context c, String value, String unit) {
        TextView t = metric(c, value, T_METRIC, text(c));
        if (unit != null && !unit.isEmpty()) {
            SpannableStringBuilder sb = new SpannableStringBuilder(value + " " + unit);
            int start = value.length() + 1;
            sb.setSpan(new RelativeSizeSpan(0.44f), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(text2(c)), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            t.setText(sb);
        }
        return t;
    }

    /** 主按钮：实心 accent；次按钮：描边 accent。最小高度 44dp，圆角 22 */
    public static TextView pill(Context c, String text, boolean filled) {
        TextView t = medium(c, text, T_LABEL, filled ? 0xFFFFFFFF : accent(c));
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(0.01f);
        String shape = kitButton(c);
        float r = "pill".equals(shape) ? R_PILL + 2f : ("rect".equals(shape) ? 6f : 4f);
        if (!filled || "outline".equals(shape)) {
            t.setBackground(stroke(c, 0x00000000, "outline".equals(shape) ? text(c) : accent(c), r, 1.2f));
            t.setTextColor("outline".equals(shape) ? text(c) : accent(c));
        } else {
            t.setBackground(round(c, accentFill(c), r));
        }
        t.setMinHeight(dp(c, 44));
        int h = dp(c, 20), v = dp(c, 11);
        t.setPadding(h, v, h, v);
        ripple(t);
        return t;
    }

    /** 状态胶囊：12% 透明底 + 同色字 */
    public static TextView chip(Context c, String text, int color) {
        TextView t = medium(c, text, T_CHIP, color);
        t.setBackground(round(c, tint(color, 0.12f), 10f));
        t.setPadding(dp(c, 9), dp(c, 4), dp(c, 9), dp(c, 4));
        return t;
    }

    /** 一排胶囊（筛选器 / 快捷操作）：返回容器，调用方自己给每个设监听 */
    public static LinearLayout pillRow(Context c, String... labels) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(c, 8);
            row.addView(pill(c, labels[i], false), lp);
        }
        return row;
    }

    /** 横向间距 */
    public static void hgap(Context c, LinearLayout parent, float dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, dp), 1));
        parent.addView(v);
    }

    /** 已经 addView 过的进度条，原地更新（不要每次重建，否则会闪） */
    public static void setBar(LinearLayout bar, double percent, int color) {
        if (bar.getChildCount() < 2) return;
        float p = (float) Math.max(0, Math.min(100, percent));
        View fill = bar.getChildAt(0);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) fill.getLayoutParams();
        lp.weight = p;
        fill.setLayoutParams(lp);
        View rest = bar.getChildAt(1);
        LinearLayout.LayoutParams lp2 = (LinearLayout.LayoutParams) rest.getLayoutParams();
        lp2.weight = 100 - p;
        rest.setLayoutParams(lp2);
        Context c = bar.getContext();
        fill.setBackground(round(c, color, 1.5f));
    }

    /** 输入框：14sp、圆角 14、1px 描边、14dp 内边距。多行传 false=单行 */
    public static EditText input(Context c, String hint, boolean multiline) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(T_BODY);
        e.setTextColor(text(c));
        e.setHintTextColor(text3(c));
        e.setBackground(stroke(c, c(c, R.color.bg), cardStroke(c), 14f, 1f));
        int p = dp(c, 14);
        e.setPadding(p, p, p, p);
        e.setSingleLine(!multiline);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        e.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    /** 搜索行：listCard 的第一行，放大镜 + 无边框输入 */
    public static LinearLayout searchRow(Context c, String hint) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = dp(c, 18), v = dp(c, 12);
        row.setPadding(h, v, h, v);
        row.addView(icon(c, R.drawable.lc_search, text3(c), 18f));
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(T_BODY);
        e.setTextColor(text(c));
        e.setHintTextColor(text3(c));
        e.setBackground(null);
        e.setSingleLine(true);
        e.setTag("input");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(c, 10);
        row.addView(e, lp);
        return row;
    }

    /** 开关行：label 左 / Switch 右。Switch 用 accent 上色 */
    public static LinearLayout switchRow(Context c, String label, boolean on,
                                         android.widget.CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = dp(c, 18), v = dp(c, 10);
        row.setPadding(h, v, h, v);
        row.addView(text(c, label, T_BODY, text(c)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(c);
        sw.setChecked(on);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new android.content.res.ColorStateList(states,
                new int[]{0xFFFFFFFF, 0xFFFFFFFF}));
        sw.setTrackTintList(new android.content.res.ColorStateList(states,
                new int[]{accentFill(c), sep(c)}));
        if (l != null) sw.setOnCheckedChangeListener(l);
        sw.setTag("switch");
        row.addView(sw);
        return row;
    }

    // ---------- 沉浸式（edge-to-edge）----------

    /**
     * 在 onCreate() 里、setContentView() **之前**调。
     * **只做窗口级**；绝不在这里碰 WindowInsetsController（DecorView 还没建，直接 FATAL NPE）。
     */
    public static void setupWindow(Activity a) {
        int sdk = android.os.Build.VERSION.SDK_INT;
        Window w = a.getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        if (sdk < 35) {
            // 35+ 上这两个已被系统"废弃且禁用"，只在老版本有意义
            w.setStatusBarColor(0x00000000);
            w.setNavigationBarColor(sdk >= 29 ? 0x00000000 : 0x40000000);
        }
        if (sdk >= 29) w.setNavigationBarContrastEnforced(false);   // 三键导航别再叠 80% 稀松布

        if (sdk >= 30) {
            w.setDecorFitsSystemWindows(false);                     // 30+ 唯一"铺满"入口
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            w.setAttributes(lp);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            if (sdk >= 28) {
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                w.setAttributes(lp);
            }
        }
    }

    /**
     * 系统栏图标明暗：setContentView() **之后**调（decor 已建），幂等、可每次切页调。
     * 官方没有"按背景自动采样"的机制，所以由调用方按页面底色决定（systemBars 做了亮度判断）。
     */
    @SuppressWarnings("deprecation")
    public static void setDarkIcons(Activity a, boolean darkIcons) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = a.getWindow().getInsetsController();
            int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if (c != null) c.setSystemBarsAppearance(darkIcons ? mask : 0, mask);
        } else {
            View d = a.getWindow().getDecorView();
            int vis = d.getSystemUiVisibility();
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | (android.os.Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
            d.setSystemUiVisibility(darkIcons ? (vis | flags) : (vis & ~flags));
        }
    }

    /** 按页面底色自动定图标明暗（浅底→深图标）。切页、切主题时调。 */
    public static void systemBars(Activity a, int pageBg) {
        setDarkIcons(a, isLightColor(pageBg));
    }

    /** 相对亮度（WCAG），用于决定状态栏图标用深色还是浅色 */
    public static boolean isLightColor(int c) {
        double l = 0.2126 * lin((c >> 16) & 0xFF) + 0.7152 * lin((c >> 8) & 0xFF)
                + 0.0722 * lin(c & 0xFF);
        return l > 0.35;
    }

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    @SuppressWarnings("deprecation")
    public static int insetTop(android.view.WindowInsets in) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            return in.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout()).top;
        }
        return in.getSystemWindowInsetTop();
    }

    /** 底部 inset：只兜"真 0"；先问 tappableElement（三键导航），再退回系统资源 */
    @SuppressWarnings("deprecation")
    public static int insetBottom(View v, android.view.WindowInsets in) {
        int b;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            b = in.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout()).bottom;
            if (b <= 0) b = in.getInsets(android.view.WindowInsets.Type.tappableElement()).bottom;
        } else {
            b = in.getSystemWindowInsetBottom();
        }
        if (b <= 0) {
            int id = v.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (id > 0) b = v.getResources().getDimensionPixelSize(id);
        }
        return b;
    }

    /**
     * 简单场景（整页滚动）用这个：edges 1=上 2=下 4=左右，加在"画底色的那层"上。
     * 底部有固定栏时别用它——栏的高度要 += inset（见 design-system §13）。
     */
    public static void padToBars(final View v, final int edges) {
        final int l0 = v.getPaddingLeft(), t0 = v.getPaddingTop();
        final int r0 = v.getPaddingRight(), b0 = v.getPaddingBottom();
        v.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View view, android.view.WindowInsets in) {
                int l, t, r, b;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets i = in.getInsets(
                            android.view.WindowInsets.Type.systemBars()
                                    | android.view.WindowInsets.Type.displayCutout());
                    l = i.left; t = i.top; r = i.right; b = i.bottom;
                } else {
                    // 21–29：四个旧 getter（30+ 才废弃，返回值一直是 int）
                    l = in.getSystemWindowInsetLeft();
                    t = in.getSystemWindowInsetTop();
                    r = in.getSystemWindowInsetRight();
                    b = in.getSystemWindowInsetBottom();
                }
                view.setPadding(l0 + ((edges & 4) != 0 ? l : 0),
                        t0 + ((edges & 1) != 0 ? t : 0),
                        r0 + ((edges & 4) != 0 ? r : 0),
                        b0 + ((edges & 2) != 0 ? b : 0));
                return in;
            }
        });
        v.requestApplyInsets();
    }
}
