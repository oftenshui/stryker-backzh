package com.zalexdev.stryker.ota;

import android.content.Context;
import android.content.SharedPreferences;

import com.zalexdev.stryker.BuildConfig;
import com.zalexdev.stryker.R;

import java.util.HashSet;
import java.util.Set;

public final class NotificationPoller {

    private static final String KEY_SEEN = "seen_notifications";
    private static final String KEY_OTA_NOTIFIED = "ota_notified_version";

    private NotificationPoller() {
    }

    public static void poll(Context context) {
        RemoteManifest manifest = ManifestService.fetch(context);
        if (manifest == null) {
            return;
        }
        SharedPreferences prefs = ManifestService.prefs(context);

        Set<String> seen = new HashSet<>(prefs.getStringSet(KEY_SEEN, new HashSet<>()));
        boolean changed = false;
        for (RemoteManifest.NotificationItem item : manifest.notifications) {
            if (item.id == 0 || item.title == null || item.title.isEmpty()) {
                continue;
            }
            String key = String.valueOf(item.id);
            if (seen.contains(key)) {
                continue;
            }
            NotificationCenter.postNews(context, item.id, item.title, item.body, item.url);
            seen.add(key);
            changed = true;
        }
        if (changed) {
            prefs.edit().putStringSet(KEY_SEEN, seen).apply();
        }

        if (manifest.app != null && manifest.app.versionCode > BuildConfig.VERSION_CODE) {
            if (prefs.getInt(KEY_OTA_NOTIFIED, 0) != manifest.app.versionCode) {
                NotificationCenter.postUpdate(context,
                        context.getString(R.string.ota_update_available_title),
                        context.getString(R.string.ota_update_available_body, manifest.app.versionName));
                prefs.edit().putInt(KEY_OTA_NOTIFIED, manifest.app.versionCode).apply();
            }
        }
    }
}
