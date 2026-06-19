package com.zalexdev.stryker.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zalexdev.stryker.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PromoDialogs {

    private static final String PREFS = "stryker_promo";
    private static final String KEY_LAUNCHES = "launches";
    private static final String KEY_GITHUB = "github_done";
    private static final String KEY_BLOG = "blog_done";

    private static final String GITHUB_URL = "https://github.com/zalexdev/strykerapp";
    private static final String BLOG_URL = "https://zalexdev.com";

    private static final int MIN_LAUNCHES = 3;
    private static final int SHOW_PERCENT = 30;

    private static final int PROMO_GITHUB = 0;
    private static final int PROMO_BLOG = 1;

    private static boolean shownThisSession = false;

    private PromoDialogs() {
    }

    public static void maybeShow(Activity activity) {
        if (activity == null || activity.isFinishing() || shownThisSession) {
            return;
        }
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int launches = prefs.getInt(KEY_LAUNCHES, 0) + 1;
        prefs.edit().putInt(KEY_LAUNCHES, launches).apply();
        if (launches < MIN_LAUNCHES) {
            return;
        }
        List<Integer> available = new ArrayList<>();
        if (!prefs.getBoolean(KEY_GITHUB, false)) {
            available.add(PROMO_GITHUB);
        }
        if (!prefs.getBoolean(KEY_BLOG, false)) {
            available.add(PROMO_BLOG);
        }
        if (available.isEmpty()) {
            return;
        }
        Random random = new Random();
        if (random.nextInt(100) >= SHOW_PERCENT) {
            return;
        }
        shownThisSession = true;
        int choice = available.get(random.nextInt(available.size()));
        if (choice == PROMO_GITHUB) {
            show(activity, prefs, KEY_GITHUB, R.string.promo_github_title,
                    R.string.promo_github_message, R.string.promo_github_action, GITHUB_URL);
        } else {
            show(activity, prefs, KEY_BLOG, R.string.promo_blog_title,
                    R.string.promo_blog_message, R.string.promo_blog_action, BLOG_URL);
        }
    }

    private static void show(Activity activity, SharedPreferences prefs, String key,
                             int titleRes, int messageRes, int actionRes, String url) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(actionRes, (dialog, which) -> {
                    prefs.edit().putBoolean(key, true).apply();
                    openUrl(activity, url);
                })
                .setNegativeButton(R.string.promo_later, null)
                .show();
    }

    private static void openUrl(Activity activity, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
