package com.zalexdev.stryker.ota;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;

public final class ManifestService {

    private static final int MAX_MANIFEST_BYTES = 512 * 1024;
    private static final String KEY_CACHE = "manifest_cache";

    private ManifestService() {
    }

    public static RemoteManifest fetch(Context context) {
        SharedPreferences prefs = prefs(context);
        try {
            String json = Net.getString(StrykerEndpoints.MANIFEST_URL, MAX_MANIFEST_BYTES);
            RemoteManifest manifest = RemoteManifest.fromJson(json);
            prefs.edit().putString(KEY_CACHE, json).apply();
            return manifest;
        } catch (Exception e) {
            return cached(context);
        }
    }

    public static RemoteManifest cached(Context context) {
        String json = prefs(context).getString(KEY_CACHE, null);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return RemoteManifest.fromJson(json);
        } catch (JSONException e) {
            return null;
        }
    }

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(StrykerEndpoints.PREFS, Context.MODE_PRIVATE);
    }
}
