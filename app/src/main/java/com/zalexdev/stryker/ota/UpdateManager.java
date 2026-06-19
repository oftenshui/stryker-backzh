package com.zalexdev.stryker.ota;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zalexdev.stryker.BuildConfig;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.utils.Core;

import java.io.File;

public final class UpdateManager {

    private static final String KEY_SKIP = "ota_skip_version";
    private static final String APK_NAME = "stryker-update.apk";

    private UpdateManager() {
    }

    public static void checkAndPrompt(Activity activity) {
        checkAndPrompt(activity, false);
    }

    public static void checkAndPrompt(Activity activity, boolean force) {
        if (activity == null) {
            return;
        }
        Context appContext = activity.getApplicationContext();
        new Thread(() -> {
            RemoteManifest manifest = ManifestService.fetch(appContext);
            if (manifest == null || manifest.app == null) {
                return;
            }
            RemoteManifest.AppUpdate app = manifest.app;
            if (app.versionCode <= BuildConfig.VERSION_CODE) {
                return;
            }
            if (app.url == null || !app.url.startsWith("https://")) {
                return;
            }
            SharedPreferences prefs = ManifestService.prefs(appContext);
            if (!force && !app.mandatory && prefs.getInt(KEY_SKIP, 0) == app.versionCode) {
                return;
            }
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            activity.runOnUiThread(() -> showDialog(activity, app, prefs));
        }).start();
    }

    private static void showDialog(Activity activity, RemoteManifest.AppUpdate app, SharedPreferences prefs) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        String message = activity.getString(R.string.ota_dialog_body, app.versionName);
        if (app.changelog != null && !app.changelog.isEmpty()) {
            message = message + "\n\n" + app.changelog;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ota_dialog_title)
                .setMessage(message)
                .setCancelable(!app.mandatory)
                .setPositiveButton(R.string.ota_dialog_update, (dialog, which) -> downloadAndInstall(activity, app));
        if (!app.mandatory) {
            builder.setNegativeButton(R.string.ota_dialog_later, null);
            builder.setNeutralButton(R.string.ota_dialog_skip,
                    (dialog, which) -> prefs.edit().putInt(KEY_SKIP, app.versionCode).apply());
        }
        builder.show();
    }

    public static void downloadAndInstall(Activity activity, RemoteManifest.AppUpdate app) {
        Context appContext = activity.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !appContext.getPackageManager().canRequestPackageInstalls()) {
            requestUnknownSources(activity);
            return;
        }
        NotificationCenter.ensureChannel(appContext);
        Toast.makeText(appContext, R.string.ota_downloading, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File apk = new File(appContext.getFilesDir(), APK_NAME);
            NotificationManager nm =
                    (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, NotificationCenter.CHANNEL_ID)
                    .setSmallIcon(R.drawable.bolt)
                    .setContentTitle(appContext.getString(R.string.ota_dialog_title))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(100, 0, true);
            safeNotify(nm, builder);

            VerifiedDownloader.Result result = VerifiedDownloader.download(
                    app.url, apk, app.sha256, app.size,
                    (downloaded, total) -> {
                        if (total > 0) {
                            builder.setProgress(100, (int) ((downloaded * 100L) / total), false);
                            safeNotify(nm, builder);
                        }
                    });

            if (nm != null) {
                nm.cancel(NotificationCenter.OTA_PROGRESS_ID);
            }
            if (!result.ok) {
                toastOnMain(appContext, R.string.ota_download_failed);
                return;
            }
            new Core(appContext).installApplication(appContext, apk.getAbsolutePath());
        }).start();
    }

    private static void requestUnknownSources(Activity activity) {
        Toast.makeText(activity, R.string.ota_enable_unknown_sources, Toast.LENGTH_LONG).show();
        try {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES));
            } catch (Exception ignored) {
            }
        }
    }

    private static void safeNotify(NotificationManager nm, NotificationCompat.Builder builder) {
        if (nm != null) {
            try {
                nm.notify(NotificationCenter.OTA_PROGRESS_ID, builder.build());
            } catch (SecurityException ignored) {
            }
        }
    }

    private static void toastOnMain(Context appContext, int resId) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(appContext, resId, Toast.LENGTH_LONG).show());
    }
}
