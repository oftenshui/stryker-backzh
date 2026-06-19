package com.zalexdev.stryker.hid.keymap;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KeymapRegistry {

    private static final String ASSET_DIR = "hid/keymap";
    public static final List<String> SUPPORTED_CODES =
            Collections.unmodifiableList(Arrays.asList(
                    "us", "gb", "de", "fr", "es", "it", "ru"));

    private final AssetManager assets;
    private final Map<String, Keymap> cache = new HashMap<>();

    public KeymapRegistry(@NonNull Context context) {
        this.assets = context.getApplicationContext().getAssets();
    }

    @NonNull
    public Map<String, String> available() {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            String[] files = assets.list(ASSET_DIR);
            if (files == null) return result;
            for (String file : files) {
                if (!file.endsWith(".json")) continue;
                String code = file.substring(0, file.length() - 5);
                Keymap km = load(code);
                if (km != null) {
                    result.put(km.code, km.displayName);
                }
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    @Nullable
    public Keymap load(@NonNull String code) {
        Keymap cached = cache.get(code);
        if (cached != null) return cached;
        try (InputStream in = assets.open(ASSET_DIR + "/" + code + ".json")) {
            String raw = readAll(in);
            JSONObject root = new JSONObject(raw);
            String name = root.optString("name", code.toUpperCase(Locale.ROOT));
            JSONObject entries = root.optJSONObject("entries");
            Map<Integer, KeyEntry> table = new HashMap<>();
            if (entries != null) {
                Iterator<String> it = entries.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    int cp = parseCodepoint(key);
                    if (cp < 0) continue;
                    JSONObject v = entries.optJSONObject(key);
                    if (v == null) continue;
                    int kc = v.optInt("keycode", 0);
                    int mod = v.optInt("modifier", 0);
                    if (kc == 0) continue;
                    table.put(cp, new KeyEntry(mod, kc));
                }
            }
            Keymap km = new Keymap(code, name, table);
            cache.put(code, km);
            return km;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    @NonNull
    public String preferredCode(@NonNull Locale locale) {
        String lang = locale.getLanguage();
        String country = locale.getCountry().toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>(SUPPORTED_CODES.size());
        candidates.add(country);
        if ("en".equals(lang)) {
            candidates.add("gb".equals(country) ? "gb" : "us");
        } else if ("de".equals(lang)) {
            candidates.add("de");
        } else if ("fr".equals(lang)) {
            candidates.add("fr");
        } else if ("es".equals(lang)) {
            candidates.add("es");
        } else if ("it".equals(lang)) {
            candidates.add("it");
        } else if ("ru".equals(lang)) {
            candidates.add("ru");
        }
        for (String c : candidates) {
            if (SUPPORTED_CODES.contains(c)) return c;
        }
        return "us";
    }

    private static int parseCodepoint(String key) {
        if (key == null || key.isEmpty()) return -1;
        if (key.startsWith("0x") || key.startsWith("0X")) {
            try {
                return Integer.parseInt(key.substring(2), 16);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        if (key.length() == 1) {
            return key.charAt(0);
        }
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = r.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
