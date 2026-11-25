package ru.czalarm;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class UpdateApkProvider extends ContentProvider {
    private static final String MIME = "application/vnd.android.package-archive";

    static Uri uri(Context context, File file) {
        return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".updates")
                .appendPath(file.getName()).build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File file(Uri uri) {
        if (uri.getPathSegments().size() != 1) return null;
        File file = AppUpdates.apkFile(getContext(), uri.getLastPathSegment());
        return file != null && file.isFile() ? file : null;
    }

    @Override
    public String getType(Uri uri) {
        return file(uri) == null ? null : MIME;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = file(uri);
        if (!"r".equals(mode) || file == null) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        File file = file(uri);
        if (file == null) return null;
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = file.getName();
            if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
        }
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String where, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] args) {
        throw new UnsupportedOperationException();
    }
}
