package com.zalexdev.stryker.ota;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;

public final class NotificationCenter {

    public static final String CHANNEL_ID = "stryker_news_channel";
    public static final int OTA_ID = 7999;
    public static final int OTA_PROGRESS_ID = 7998;
    private static final int NEWS_BASE = 700000;

    private NotificationCenter() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        context.getString(R.string.ota_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT);
                nm.createNotificationChannel(channel);
            }
        }
    }

    public static void postNews(Context context, int notifId, String title, String body, String url) {
        ensureChannel(context);
        PendingIntent intent = (url != null && url.startsWith("http"))
                ? openUrl(context, url)
                : openApp(context, null);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.bolt)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(intent);
        safeNotify(context, NEWS_BASE + Math.abs(notifId % 100000), builder);
    }

    public static void postUpdate(Context context, String title, String body) {
        ensureChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.bolt)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(openApp(context, MainActivity.EXTRA_OPEN_UPDATE));
        safeNotify(context, OTA_ID, builder);
    }

    private static PendingIntent openApp(Context context, String extraFlag) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (extraFlag != null) {
            intent.putExtra(extraFlag, true);
        }
        return PendingIntent.getActivity(context, extraFlag == null ? 0 : 1, intent, flags());
    }

    private static PendingIntent openUrl(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, url.hashCode(), intent, flags());
    }

    private static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }

    private static void safeNotify(Context context, int id, NotificationCompat.Builder builder) {
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException ignored) {
        }
    }
}
