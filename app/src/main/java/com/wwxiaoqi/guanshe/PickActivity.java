package com.wwxiaoqi.guanshe;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wwxiaoqi.guanshe.core.ColorMath;
import com.wwxiaoqi.guanshe.core.Favorites;
import com.wwxiaoqi.guanshe.core.Quantize;
import com.wwxiaoqi.guanshe.ui.BandView;
import com.wwxiaoqi.guanshe.ui.Bars;
import com.wwxiaoqi.guanshe.ui.BaseActivity;
import com.wwxiaoqi.guanshe.ui.CrashScreen;
import com.wwxiaoqi.guanshe.ui.FavStore;
import com.wwxiaoqi.guanshe.ui.Haptics;
import com.wwxiaoqi.guanshe.ui.Motion;
import com.wwxiaoqi.guanshe.ui.Page;
import com.wwxiaoqi.guanshe.ui.Rows;
import com.wwxiaoqi.guanshe.ui.Snack;
import com.wwxiaoqi.guanshe.ui.Surface;
import com.wwxiaoqi.guanshe.ui.SwatchView;
import com.wwxiaoqi.guanshe.ui.Tap;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 从图片取色（specs/image-pick）。
 *
 * 零权限：走 ACTION_OPEN_DOCUMENT，系统把用户选中的那张图交给我们就够了；
 * 解码前先按长边 512 降采样，避免 8000×6000 的图把内存打穿。
 *
 * 这一版的时序：
 * 1. 先铺"正在打开相册…"再拉起系统选择器（取消时直接 finish，不留一张白纸）；
 * 2. 选中后立刻换成取色页，4 个色点做呼吸动画，解码 + 取色放到后台线程；
 * 3. 结果页：图片预览卡 / 4 色色带 / 逐色复制 / 复制全部 / 收藏 / 换一张图。
 */
public class PickActivity extends BaseActivity {

    private static final int REQUEST_PICK = 1101;
    private static final int MAX_EDGE = 512;
    /** 先让占位页真的画一帧，再让系统选择器盖上来。 */
    private static final long OPEN_DELAY = 90L;

    private FrameLayout frame;
    private FavStore favs;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<ValueAnimator> breathing = new ArrayList<ValueAnimator>();

    /** 换一张图后用户取消时，能把上一组结果原样放回来，而不是退回上一页。 */
    private int[] lastColors;
    private Bitmap lastBitmap;

    private boolean picking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUi(savedInstanceState);
        } catch (Throwable e) {
            // 页面起不来就别让进程退出：把堆栈画在屏幕上，用户能截图/复制
            CrashScreen.show(this, "取色", e);
        }
    }

    @Override
    protected void onDestroy() {
        stopBreathing();
        ui.removeCallbacksAndMessages(null);
        // 先把图片从视图上摘下来，再回收自己解码的那张 —— 别回收还在显示中的 Bitmap
        if (frame != null) frame.removeAllViews();
        recycleQuietly(lastBitmap);
        lastBitmap = null;
        super.onDestroy();
    }

    private void buildUi(Bundle savedInstanceState) {
        favs = new FavStore(this);
        frame = new FrameLayout(this);
        frame.setBackground(Skin.background(this));
        setContentView(frame);

        if (savedInstanceState == null) {
            showOpening();
            launchPickerSoon();
        } else {
            // 重建（切主题 / 内存回收）不该再把相册拉一次：留个手动入口
            showEmptyState("从相册取色", "选一张照片，观色会从里面抽出 4 个主色",
                    "选择一张照片", reopenPicker());
        }
    }

    // ---------------------------------------------------------------- 选图
    /** 延时拉起系统选择器：先让占位页真的画一帧；页面没了就不再拉起。 */
    private void launchPickerSoon() {
        ui.postDelayed(new Runnable() {
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                openPicker();
            }
        }, OPEN_DELAY);
    }

    private void openPicker() {
        if (picking) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        picking = true;
        try {
            startActivityForResult(i, REQUEST_PICK);
        } catch (ActivityNotFoundException e) {
            picking = false;
            showEmptyState("打不开相册", "这台设备上没有可用的图片选择器", "返回",
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            finish();
                        }
                    });
        }
    }

    /** 结果页 / 错误页的"重新选一张"。 */
    private View.OnClickListener reopenPicker() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                showOpening();
                launchPickerSoon();
            }
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK) return;
        picking = false;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            // 用户取消：没有结果就走人；已经有结果就把上一组放回来
            if (lastColors != null && lastColors.length >= 4) {
                showResult(lastColors, lastBitmap);
            } else {
                finish();
            }
            return;
        }
        analyze(data.getData());
    }

    private Bitmap decode(Uri uri) {
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            in.close();
            in = null;
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) {
                sample *= 2;
            }
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = sample;
            in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            return rotateIfNeeded(uri, BitmapFactory.decodeStream(in, null, opt));
        } catch (IOException e) {
            return null;
        } catch (SecurityException e) {
            return null;
        } catch (OutOfMemoryError e) {
            // 降采样已经做了还爆内存：认输，给用户换一张的出口，别让进程挂掉
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** 相机照片常把方向写在 EXIF 里：预览不转过来会横着显示（取色数值不受影响）。 */
    private Bitmap rotateIfNeeded(Uri uri, Bitmap bm) {
        if (bm == null || Build.VERSION.SDK_INT < 24) return bm;
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) return bm;
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            int degrees;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            else return bm;

            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
            if (rotated != bm) bm.recycle();
            return rotated;
        } catch (Throwable t) {
            return bm;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** 供色卡图片页复用：Bitmap → 4 色。 */
    public static int[] extractFrom(Bitmap bm) {
        if (bm == null) return null;
        int w = bm.getWidth();
        int h = bm.getHeight();
        if (w <= 0 || h <= 0) return null;
        int[] px = new int[w * h];
        bm.getPixels(px, 0, w, 0, 0, w, h);
        return Quantize.medianCut(px, 4);
    }

    /** 解码 + 取色都在后台线程，主线程只负责画"正在取色"。 */
    private void analyze(final Uri uri) {
        showAnalyzing();
        new Thread(new Runnable() {
            public void run() {
                Bitmap bm = null;
                int[] colors = null;
                boolean failed = false;
                try {
                    bm = decode(uri);
                    colors = bm == null ? null : extractFrom(bm);
                } catch (Throwable t) {
                    // extractFrom 里的 new int[w*h] / medianCut 可能抛 RuntimeException / OOM，
                    // 后台线程兜不住会把整个进程带走：这里认输，退回"取不出颜色"。
                    failed = true;
                    colors = null;
                    recycleQuietly(bm);
                    bm = null;
                }
                final Bitmap bitmap = bm;
                final int[] extracted = colors;
                final boolean hardFail = failed;
                ui.post(new Runnable() {
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            recycleQuietly(bitmap);   // 页面已经没了，没人会显示这张图
                            return;
                        }
                        if (hardFail) {
                            showEmptyState("这张图取不出颜色",
                                    "照片可能太单一、太暗，换一张试试", "重新选一张", reopenPicker());
                        } else if (bitmap == null) {
                            showEmptyState("这张图读不出来",
                                    "换一张试试，或者换一个图片格式", "重新选一张", reopenPicker());
                        } else if (extracted == null || extracted.length < 4) {
                            recycleQuietly(bitmap);
                            showEmptyState("这张图取不出颜色",
                                    "照片可能太单一、太暗，换一张试试", "重新选一张", reopenPicker());
                        } else {
                            showResult(extracted, bitmap);
                        }
                    }
                });
            }
        }, "guanshe-pick").start();
    }

    /** recycle 自己解码的 Bitmap；重复回收 / ROM 的古怪实现都不该让页面崩。 */
    private static void recycleQuietly(Bitmap bm) {
        if (bm == null || bm.isRecycled()) return;
        try {
            bm.recycle();
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- 占位页
    private void showOpening() {
        stopBreathing();
        frame.removeAllViews();
        LinearLayout root = Page.root(this, Skin.bg(this), true);
        root.addView(buildHead("取色", "从相册里选一张照片"));
        root.addView(spacer());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Skin.dp(this, Page.PAD), 0, Skin.dp(this, Page.PAD), 0);
        box.addView(Skin.icon(this, R.drawable.lc_image, Skin.text3(this), 30f));

        TextView title = Skin.medium(this, "正在打开相册…", Skin.T_CARD, Skin.text(this));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Skin.dp(this, 14);
        box.addView(title, titleLp);

        TextView sub = Skin.text(this, "选一张照片，观色会从里面抽出 4 个主色", Skin.T_NOTE,
                Skin.text2(this));
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Skin.dp(this, 6);
        box.addView(sub, subLp);

        Page.addFull(root, box);
        root.addView(spacer());
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---------------------------------------------------------------- 取色中
    private void showAnalyzing() {
        stopBreathing();
        frame.removeAllViews();
        LinearLayout root = Page.root(this, Skin.bg(this), true);
        root.addView(buildHead("取色", "正在从照片里取色"));
        root.addView(spacer());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Skin.dp(this, Page.PAD), 0, Skin.dp(this, Page.PAD), 0);
        box.addView(buildDots());

        TextView title = Skin.medium(this, "正在取色…", Skin.T_CARD, Skin.text(this));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Skin.dp(this, 20);
        box.addView(title, titleLp);

        TextView sub = Skin.text(this, "给你 4 个主色，稍等一下", Skin.T_NOTE, Skin.text2(this));
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Skin.dp(this, 6);
        box.addView(sub, subLp);

        Page.addFull(root, box);
        root.addView(spacer());
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** 4 个色点错峰呼吸：看得出"还在忙"，又不用转圈进度条。 */
    private View buildDots() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(Skin.text3(this));
            dot.setBackground(shape);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Skin.dp(this, 12), Skin.dp(this, 12));
            if (i > 0) lp.leftMargin = Skin.dp(this, 12);
            row.addView(dot, lp);
            breathe(dot, i);
        }
        return row;
    }

    private void breathe(final View dot, int index) {
        if (!Motion.enabled(this)) {
            dot.setAlpha(0.85f);
            return;
        }
        dot.setAlpha(0.2f);
        ValueAnimator a = ValueAnimator.ofFloat(0.2f, 1f, 0.2f);
        a.setDuration(1150);
        a.setStartDelay(index * 140L);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(Motion.ease());
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator anim) {
                float f = (Float) anim.getAnimatedValue();
                dot.setAlpha(f);
                float s = 0.72f + 0.28f * f;
                dot.setScaleX(s);
                dot.setScaleY(s);
            }
        });
        a.start();
        breathing.add(a);
    }

    private void stopBreathing() {
        for (int i = 0; i < breathing.size(); i++) breathing.get(i).cancel();
        breathing.clear();
    }

    // ---------------------------------------------------------------- 结果页
    private void showResult(final int[] colors, Bitmap bm) {
        stopBreathing();
        lastColors = colors;
        final Bitmap previous = lastBitmap;
        lastBitmap = bm;
        frame.removeAllViews();
        // 换图成功：上一张已经从视图上摘下来，可以回收（同一张被"取消后恢复"时别动）
        if (previous != null && previous != bm) recycleQuietly(previous);

        final String hexList = ColorMath.hexList(colors);
        final int localIndex = favs.addLocalPalette(hexList);
        final String favKey = Favorites.paletteKey(Favorites.KIND_LOCAL, localIndex);

        LinearLayout root = Page.root(this, Skin.bg(this), true);
        root.addView(buildHead("取色结果", "4 色 · 按明度排序 · 轻点色值复制"));

        ScrollView sc = Page.scroller(this);
        LinearLayout content = Page.column(this);
        content.setPadding(0, Skin.dp(this, 10), 0, Skin.dp(this, 24) + Bars.bottom(this));
        sc.addView(content);
        root.addView(sc);
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 图片预览卡
        LinearLayout previewWrap = Page.wrap(this);
        FrameLayout preview = new FrameLayout(this);
        preview.setBackground(Skin.stroke(this, Skin.cardColor(this), Skin.cardStroke(this),
                20f, 1f));
        Surface.card(preview, 20f, Surface.CARD);
        if (bm != null) {
            ImageView photo = new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setImageBitmap(bm);
            photo.setContentDescription("你选的照片");
            preview.addView(photo, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(this, 180)));
            // 图片可能和卡片一样白：描一圈发丝线，边界才看得出来
            View frameStroke = new View(this);
            frameStroke.setBackground(Skin.stroke(this, 0x00000000, Skin.cardStroke(this),
                    20f, 1f));
            preview.addView(frameStroke, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(this, 180)));
        }
        Page.addFull(previewWrap, preview);
        Page.addFull(content, previewWrap);

        // 抽出的 4 色色带
        LinearLayout bandWrap = Page.wrap(this);
        bandWrap.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 14),
                Skin.dp(this, Page.PAD), 0);
        View band = BandView.of(this, colors, 60, 10, false);
        Surface.card(band, 10f, Surface.CARD);
        band.setContentDescription("取色色带，" + hexList);
        Page.addFull(bandWrap, band);
        Page.addFull(content, bandWrap);

        // 4 行色值：整行点一下复制
        LinearLayout rowsWrap = Page.wrap(this);
        rowsWrap.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 16),
                Skin.dp(this, Page.PAD), 0);
        LinearLayout card = Skin.listCard(this);
        for (int i = 0; i < colors.length; i++) {
            if (i > 0) card.addView(Page.hairline(this));
            card.addView(colorValueRow(colors[i]));
        }
        Page.addFull(rowsWrap, card);
        Page.addFull(content, rowsWrap);

        // 操作
        LinearLayout actions = Page.wrap(this);
        actions.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 18),
                Skin.dp(this, Page.PAD), 0);

        TextView copyAll = Skin.pill(this, "复制全部", true);
        Tap.chip(copyAll);
        copyAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Snack.copy(v, hexList);
            }
        });
        Page.addFull(actions, copyAll);

        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);

        final TextView keep = Skin.pill(this, favs.isFav(favKey) ? "已收藏" : "收藏这组", false);
        Tap.chip(keep);
        keep.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean added = favs.toggleFav(favKey);
                keep.setText(added ? "已收藏" : "收藏这组");
                if (added) {
                    Haptics.success(v);
                    Motion.pop(v);
                }
                Snack.show(v, added ? "已收藏这组配色" : "已取消收藏");
            }
        });
        pair.addView(keep, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView again = Skin.pill(this, "换一张图", false);
        Tap.chip(again);
        again.setOnClickListener(reopenPicker());
        LinearLayout.LayoutParams againLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        againLp.leftMargin = Skin.dp(this, 10);
        pair.addView(again, againLp);

        LinearLayout.LayoutParams pairLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pairLp.topMargin = Skin.dp(this, 10);
        actions.addView(pair, pairLp);
        Page.addFull(content, actions);

        final LinearLayout entrance = content;
        content.post(new Runnable() {
            public void run() {
                Motion.staggerIn(entrance, 0, 8);
            }
        });
    }

    private View colorValueRow(final int color) {
        final String hex = ColorMath.hex(color);
        int[] rgb = ColorMath.rgb(color);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Skin.dp(this, 56));
        row.setPadding(Skin.dp(this, 16), Skin.dp(this, 11), Skin.dp(this, 16),
                Skin.dp(this, 11));

        View swatch = SwatchView.of(this, color, 40, 8);
        Surface.card(swatch, 8f, Surface.CARD);
        row.addView(swatch);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        midLp.leftMargin = Skin.dp(this, 14);
        TextView hexText = Skin.medium(this, hex, 15, Skin.text(this));
        hexText.setLetterSpacing(0.04f);
        mid.addView(hexText);
        TextView rgbText = Skin.text(this, "RGB " + rgb[0] + ", " + rgb[1] + ", " + rgb[2],
                11, Skin.text3(this));
        rgbText.setPadding(0, Skin.dp(this, 3), 0, 0);
        mid.addView(rgbText);
        row.addView(mid, midLp);

        row.addView(Skin.icon(this, R.drawable.lc_copy, Skin.text3(this), 18f));
        row.setContentDescription("色值 " + hex + "，点击复制");
        Tap.row(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Snack.copy(v, hex);
            }
        });
        return row;
    }

    // ---------------------------------------------------------------- 错误 / 空状态
    private void showEmptyState(String title, String body, String actionLabel,
                                View.OnClickListener action) {
        stopBreathing();
        frame.removeAllViews();
        LinearLayout root = Page.root(this, Skin.bg(this), true);
        root.addView(buildHead("取色", null));

        LinearLayout wrap = Page.wrap(this);
        wrap.setPadding(Skin.dp(this, Page.PAD), Skin.dp(this, 26),
                Skin.dp(this, Page.PAD), Skin.dp(this, 24) + Bars.bottom(this));
        Page.addFull(wrap, Page.empty(this, title, body, actionLabel, action));
        Page.addFull(root, wrap);

        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        Motion.riseIn(wrap);
    }

    // ---------------------------------------------------------------- 头部
    private View buildHead(String title, String sub) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, Page.PAD),
                Skin.dp(this, 6));

        LinearLayout back = Rows.iconButton(this, R.drawable.lc_back, Skin.text(this),
                new View.OnClickListener() {
                    public void onClick(View v) {
                        finish();
                    }
                });
        back.setContentDescription("返回");
        head.addView(back);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mid.setPadding(Skin.dp(this, 4), 0, 0, 0);
        TextView t = Skin.medium(this, title, Skin.T_TITLE, Skin.text(this));
        t.setLetterSpacing(-0.02f);
        mid.addView(t);
        if (sub != null && sub.length() > 0) {
            TextView s = Skin.text(this, sub, Skin.T_NOTE, Skin.text2(this));
            s.setPadding(0, Skin.dp(this, 3), 0, 0);
            mid.addView(s);
        }
        head.addView(mid);
        return head;
    }

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return v;
    }
}
