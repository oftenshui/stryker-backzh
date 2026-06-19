package com.zalexdev.stryker.ota;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.zalexdev.stryker.custom.News;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NewsRepository {

    public interface Callback {
        void onNews(List<News> news);
    }

    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;

    private NewsRepository() {
    }

    public static List<News> defaults() {
        News oss = new News();
        oss.title = "StrykerOSS is open source";
        oss.description = "Released as free and open-source software. Visit the GitHub repository "
                + "for source, issues and contributions.";
        oss.newsDate = "2026";
        oss.id = 1;
        oss.pinned = true;
        oss.actionbutton1 = true;
        oss.actionbutton1text = "Open GitHub";
        oss.actionbutton1url = StrykerEndpoints.GITHUB_REPO;
        oss.actionbutton2 = false;
        List<News> list = new ArrayList<>();
        list.add(oss);
        return list;
    }

    public static void load(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            RemoteManifest manifest = ManifestService.fetch(appContext);
            List<News> list;
            if (manifest != null && !manifest.news.isEmpty()) {
                list = new ArrayList<>(manifest.news);
                Collections.sort(list, (a, b) -> Boolean.compare(b.pinned, a.pinned));
                for (News item : list) {
                    if (item.imageUrl != null && item.imageUrl.startsWith("https://")) {
                        item.image = Net.getBitmap(item.imageUrl, MAX_IMAGE_BYTES);
                    }
                }
            } else {
                list = defaults();
            }
            final List<News> result = list;
            main.post(() -> callback.onNews(result));
        }).start();
    }
}
