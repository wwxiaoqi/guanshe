package com.wwxiaoqi.guanshe.ui;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 分享图片用的最小 ContentProvider（等价于 FileProvider，但不需要 androidx）。
 *
 * 只做一件事：把 cache/share/ 下的 png 以 content:// 暴露给系统分享面板。
 * exported=false + 临时授权，别的 App 只能读这一次分享出去的文件。
 */
public class ShareProvider extends ContentProvider {

    public static final String AUTHORITY = "com.wwxiaoqi.guanshe.share";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri == null ? null : uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) {
            throw new FileNotFoundException("bad share name");
        }
        File dir = new File(getContext().getCacheDir(), "share");
        File f = new File(dir, name);
        if (!f.exists()) throw new FileNotFoundException(name);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
