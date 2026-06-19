package com.zalexdev.stryker.ota;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import com.zalexdev.stryker.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class Net {

    private static volatile OkHttpClient client;

    private Net() {
    }

    public static OkHttpClient client() {
        if (client == null) {
            synchronized (Net.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .callTimeout(0, TimeUnit.SECONDS)
                            .followRedirects(true)
                            .followSslRedirects(false)
                            .retryOnConnectionFailure(true)
                            .build();
                }
            }
        }
        return client;
    }

    public static String userAgent() {
        return "Stryker/" + BuildConfig.VERSION_NAME
                + " (Android " + Build.VERSION.RELEASE + "; " + Build.DEVICE + ")";
    }

    public static String getString(String url, int maxBytes) throws IOException {
        return new String(getBytes(url, maxBytes), StandardCharsets.UTF_8);
    }

    public static Bitmap getBitmap(String url, int maxBytes) {
        try {
            byte[] bytes = getBytes(url, maxBytes);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IOException | OutOfMemoryError e) {
            return null;
        }
    }

    private static byte[] getBytes(String url, int maxBytes) throws IOException {
        if (url == null || !url.startsWith("https://")) {
            throw new IOException("Refusing non-HTTPS URL");
        }
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent())
                .build();
        try (Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code());
            }
            InputStream in = body.byteStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Response exceeds " + maxBytes + " bytes");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
