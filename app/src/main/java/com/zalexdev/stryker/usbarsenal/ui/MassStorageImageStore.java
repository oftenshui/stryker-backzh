package com.zalexdev.stryker.usbarsenal.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class MassStorageImageStore {

    private MassStorageImageStore() {}

    public static File workingDir(@NonNull Context context) {
        File dir = new File(context.getFilesDir(), "arsenal/images");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    @Nullable
    public static String copyToWorkingDir(@NonNull Context context, @NonNull Uri uri) {
        String name = resolveName(context, uri);
        File out = new File(workingDir(context), name);
        ContentResolver cr = context.getContentResolver();
        try (InputStream in = cr.openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) return null;
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        } catch (IOException e) {
            return null;
        }
        try {
            new java.io.RandomAccessFile(out, "r").close();
        } catch (IOException ignored) {
        }
        out.setReadable(true, false);
        return out.getAbsolutePath();
    }

    @NonNull
    private static String resolveName(@NonNull Context context, @NonNull Uri uri) {
        String fallback = "image_" + System.currentTimeMillis() + ".img";
        try (android.database.Cursor c = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = c.getString(idx);
                    if (name != null && !name.isEmpty()) {
                        return sanitize(name);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }
}
