package com.zalexdev.stryker.netdetect;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyDeviceDb {

    private static final String TAG = "LegacyDeviceDb";
    private static final Pattern DRIVER_TAG = Pattern.compile("\\[([^\\]]+)\\]");

    private static volatile Map<String, String> cache;

    private LegacyDeviceDb() {}

    public static String lookupRaw(Context ctx, String vid, String pid) {
        if (ctx == null || vid == null || pid == null) return null;
        Map<String, String> map = cache;
        if (map == null) {
            synchronized (LegacyDeviceDb.class) {
                map = cache;
                if (map == null) {
                    map = load(ctx);
                    cache = map;
                }
            }
        }
        return map.get((vid + ":" + pid).toLowerCase());
    }

    public static ChipsetInfo lookup(Context ctx, String vid, String pid) {
        String raw = lookupRaw(ctx, vid, pid);
        if (raw == null) return null;

        String driver = null;
        String label  = raw;
        Matcher m = DRIVER_TAG.matcher(raw);
        if (m.find()) {
            driver = m.group(1).trim();
            label = raw.substring(0, m.start()).trim();
        }

        String vendor = null;
        String model  = label;
        int sp = label.indexOf(' ');
        if (sp > 0) {
            vendor = label.substring(0, sp);
            model  = label.substring(sp + 1);
        }

        return new ChipsetInfo(vendor, model, driver,
                ChipsetInfo.Kind.OTHER,
                ChipsetInfo.Band.NA,
                ChipsetInfo.Capability.NA,
                ChipsetInfo.Capability.NA,
                null, null,
                "Legacy DB entry — capabilities not curated.");
    }

    private static Map<String, String> load(Context ctx) {
        Map<String, String> out = new HashMap<>(32768);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ctx.getAssets().open("devices.txt")))) {
            StringBuilder sb = new StringBuilder(2_000_000);
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONObject root = new JSONObject(sb.toString());
            JSONArray list = root.getJSONArray("list");
            for (int i = 0; i < list.length(); i++) {
                JSONObject entry = list.getJSONObject(i);
                String key = entry.keys().next();
                out.put(key.toLowerCase(), entry.getString(key));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load devices.txt", e);
        }
        return out;
    }
}
