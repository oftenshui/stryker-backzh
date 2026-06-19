package com.zalexdev.stryker.hid.ui.editor;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class PayloadFileIo {

    public static final String MIME = "text/plain";

    private PayloadFileIo() {}

    @Nullable
    public static String read(@NonNull Context ctx, @NonNull Uri uri) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean write(@NonNull Context ctx, @NonNull Uri uri, @NonNull String body) {
        try (OutputStream out = ctx.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) return false;
            out.write(body.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
