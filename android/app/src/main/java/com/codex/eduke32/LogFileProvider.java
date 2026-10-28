package com.codex.eduke32;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Exposes only the generated runtime log for sharing with other apps. */
public final class LogFileProvider extends ContentProvider {
    private File logFile() {
        return new File(new File(getContext().getFilesDir(), "shared-logs"), "eduke32-runtime.txt");
    }

    private boolean isLogUri(Uri uri) {
        return uri != null && "com.codex.eduke32.logprovider".equals(uri.getAuthority())
                && "/eduke32-runtime.txt".equals(uri.getPath());
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        return isLogUri(uri) ? "text/plain" : null;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        if (!isLogUri(uri)) return null;
        File file = logFile();
        String[] columns = projection == null
                ? new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE }
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = file.getName();
            else if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!isLogUri(uri) || !"r".equals(mode)) throw new FileNotFoundException("Unsupported log URI");
        File file = logFile();
        if (!file.isFile()) throw new FileNotFoundException("Runtime log has not been exported");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
