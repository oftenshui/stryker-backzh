package com.zalexdev.stryker.ota;

import com.zalexdev.stryker.custom.News;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class RemoteManifest {

    public static final class Asset {
        public final String url;
        public final String sha256;
        public final long size;

        public Asset(String url, String sha256, long size) {
            this.url = url;
            this.sha256 = sha256;
            this.size = size;
        }

        public boolean isUsable() {
            return url != null && url.startsWith("https://");
        }
    }

    public static final class AppUpdate {
        public final int versionCode;
        public final String versionName;
        public final String url;
        public final String sha256;
        public final long size;
        public final boolean mandatory;
        public final String changelog;

        AppUpdate(int versionCode, String versionName, String url, String sha256,
                  long size, boolean mandatory, String changelog) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.url = url;
            this.sha256 = sha256;
            this.size = size;
            this.mandatory = mandatory;
            this.changelog = changelog;
        }
    }

    public static final class NotificationItem {
        public final int id;
        public final String title;
        public final String body;
        public final String url;

        NotificationItem(int id, String title, String body, String url) {
            this.id = id;
            this.title = title;
            this.body = body;
            this.url = url;
        }
    }

    public int manifestVersion = 1;
    public String coreVersion = "";
    public Asset chroot64;
    public Asset chroot32;
    public AppUpdate app;
    public final List<News> news = new ArrayList<>();
    public final List<NotificationItem> notifications = new ArrayList<>();

    public static RemoteManifest fromJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        RemoteManifest manifest = new RemoteManifest();
        manifest.manifestVersion = root.optInt("manifest_version", 1);

        JSONObject core = root.optJSONObject("core");
        if (core != null) {
            manifest.coreVersion = core.optString("version", "");
            manifest.chroot64 = asset(core.optJSONObject("chroot64"));
            manifest.chroot32 = asset(core.optJSONObject("chroot32"));
        }

        JSONObject app = root.optJSONObject("app");
        if (app != null) {
            manifest.app = new AppUpdate(
                    app.optInt("versionCode", 0),
                    app.optString("versionName", ""),
                    app.optString("url", ""),
                    app.optString("sha256", ""),
                    app.optLong("size", 0),
                    app.optBoolean("mandatory", false),
                    app.optString("changelog", ""));
        }

        JSONArray newsArray = root.optJSONArray("news");
        if (newsArray != null) {
            for (int i = 0; i < newsArray.length(); i++) {
                JSONObject o = newsArray.optJSONObject(i);
                if (o == null) continue;
                News n = new News();
                n.title = o.optString("title", n.title);
                n.description = o.optString("description", n.description);
                n.actionbutton1 = o.optBoolean("actionbutton1", false);
                n.actionbutton2 = o.optBoolean("actionbutton2", false);
                n.pinned = o.optBoolean("pin", o.optBoolean("pinned", false));
                n.actionbutton1text = o.optString("actionbutton1text", "Open");
                n.actionbutton2text = o.optString("actionbutton2text", "");
                n.actionbutton1url = o.optString("actionbutton1url", "");
                n.actionbutton2url = o.optString("actionbutton2url", "");
                n.newsUrl = o.optString("newsUrl", "");
                n.newsDate = o.optString("newsDate", "");
                n.imageUrl = o.optString("imageUrl", "");
                n.id = o.optInt("id", 0);
                manifest.news.add(n);
            }
        }

        JSONArray notifArray = root.optJSONArray("notifications");
        if (notifArray != null) {
            for (int i = 0; i < notifArray.length(); i++) {
                JSONObject o = notifArray.optJSONObject(i);
                if (o == null) continue;
                manifest.notifications.add(new NotificationItem(
                        o.optInt("id", 0),
                        o.optString("title", ""),
                        o.optString("body", ""),
                        o.optString("url", "")));
            }
        }
        return manifest;
    }

    private static Asset asset(JSONObject o) {
        if (o == null) return null;
        return new Asset(o.optString("url", ""), o.optString("sha256", ""), o.optLong("size", 0));
    }
}
