package com.zalexdev.stryker.hid.payload;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zalexdev.stryker.utils.Core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PayloadLibrary {

    private static final String ASSET_DIR = "hid/payloads";
    private static final String EXTENSION = ".ducky";

    private static final String PREF_HIDDEN = "hid_payload_hidden";
    private static final String PREF_ORDER  = "hid_payload_order";

    private final Context context;
    private final Core core;

    public PayloadLibrary(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.core = new Core(this.context);
    }

    @NonNull
    public List<Payload> listAll() {
        List<Payload> assets = listAssetsRaw();
        List<Payload> user   = listUserRaw();
        Set<String> hidden   = parseCsv(core.getString(PREF_HIDDEN));

        Map<String, Payload> byName = new LinkedHashMap<>();
        for (Payload u : user) byName.put(u.name, u);
        for (Payload a : assets) {
            if (hidden.contains(a.name)) continue;
            if (!byName.containsKey(a.name)) byName.put(a.name, a);
        }

        List<String> orderedNames = new ArrayList<>(parseCsv(core.getString(PREF_ORDER)));
        List<Payload> ordered = new ArrayList<>(byName.size());
        for (String n : orderedNames) {
            Payload p = byName.remove(n);
            if (p != null) ordered.add(p);
        }
        List<Payload> tail = new ArrayList<>(byName.values());
        Collections.sort(tail, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        ordered.addAll(tail);
        return ordered;
    }

    @NonNull
    public List<Payload> listAssets() {
        Set<String> hidden = parseCsv(core.getString(PREF_HIDDEN));
        List<Payload> out = new ArrayList<>();
        for (Payload p : listAssetsRaw()) {
            if (!hidden.contains(p.name)) out.add(p);
        }
        return out;
    }

    @NonNull
    public List<Payload> listUser() {
        return listUserRaw();
    }

    public boolean save(@NonNull String name, @NonNull String body) {
        File dir = userDir(true);
        if (dir == null) return false;
        String safe = sanitize(name);
        if (safe.isEmpty()) return false;
        File out = new File(dir, safe + EXTENSION);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(body.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean rename(@NonNull Payload payload, @NonNull String newName) {
        String safe = sanitize(newName);
        if (safe.isEmpty() || safe.equals(payload.name)) return false;

        if (payload.source == Payload.Source.ASSET) {
            if (!save(safe, payload.body)) return false;
            hide(payload.name);
        } else {
            File from = new File(userDir(false), payload.name + EXTENSION);
            File to   = new File(userDir(true), safe + EXTENSION);
            if (!from.exists()) return false;
            if (to.exists()) to.delete();
            boolean moved = from.renameTo(to);
            if (!moved) {
                String body = readFile(from);
                if (body == null) return false;
                if (!save(safe, body)) return false;
                from.delete();
            }
        }

        List<String> order = new ArrayList<>(parseCsv(core.getString(PREF_ORDER)));
        int idx = order.indexOf(payload.name);
        if (idx >= 0) order.set(idx, safe);
        else order.add(safe);
        core.putString(PREF_ORDER, joinCsv(order));
        return true;
    }

    public boolean delete(@NonNull Payload payload) {
        boolean ok;
        if (payload.source == Payload.Source.USER) {
            File f = new File(userDir(false), payload.name + EXTENSION);
            ok = f.delete();
        } else {
            hide(payload.name);
            ok = true;
        }
        List<String> order = new ArrayList<>(parseCsv(core.getString(PREF_ORDER)));
        if (order.remove(payload.name)) {
            core.putString(PREF_ORDER, joinCsv(order));
        }
        return ok;
    }

    public boolean move(@NonNull Payload payload, int direction) {
        if (direction == 0) return false;
        List<Payload> current = listAll();
        int idx = -1;
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).name.equals(payload.name)) { idx = i; break; }
        }
        if (idx < 0) return false;
        int target = idx + direction;
        if (target < 0 || target >= current.size()) return false;

        List<String> names = new ArrayList<>(current.size());
        for (Payload p : current) names.add(p.name);
        String tmp = names.get(idx);
        names.set(idx, names.get(target));
        names.set(target, tmp);
        core.putString(PREF_ORDER, joinCsv(names));
        return true;
    }

    public void restoreHidden() {
        core.putString(PREF_HIDDEN, "");
    }

    public void resetOrder() {
        core.putString(PREF_ORDER, "");
    }

    private void hide(@NonNull String name) {
        Set<String> hidden = new LinkedHashSet<>(parseCsv(core.getString(PREF_HIDDEN)));
        hidden.add(name);
        core.putString(PREF_HIDDEN, joinCsv(new ArrayList<>(hidden)));
    }

    @NonNull
    private List<Payload> listAssetsRaw() {
        List<Payload> list = new ArrayList<>();
        AssetManager am = context.getAssets();
        try {
            String[] files = am.list(ASSET_DIR);
            if (files == null) return list;
            for (String file : files) {
                if (!file.endsWith(EXTENSION)) continue;
                String body = readAsset(am, ASSET_DIR + "/" + file);
                if (body == null) continue;
                String stem = stem(file);
                list.add(new Payload(stem, prettify(stem), body, Payload.Source.ASSET));
            }
        } catch (IOException ignored) {
        }
        return list;
    }

    @NonNull
    private List<Payload> listUserRaw() {
        List<Payload> list = new ArrayList<>();
        File dir = userDir(false);
        if (dir == null || !dir.isDirectory()) return list;
        File[] files = dir.listFiles((d, name) -> name.endsWith(EXTENSION));
        if (files == null) return list;
        for (File f : files) {
            String body = readFile(f);
            if (body == null) continue;
            String stem = stem(f.getName());
            list.add(new Payload(stem, prettify(stem), body, Payload.Source.USER));
        }
        return list;
    }

    @Nullable
    private File userDir(boolean create) {
        File dir = new File(context.getFilesDir(), "hid/payloads");
        if (create && !dir.exists() && !dir.mkdirs()) return null;
        return dir;
    }

    @Nullable
    private static String readAsset(AssetManager am, String path) {
        try (InputStream in = am.open(path)) {
            return readAll(in);
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private static String readFile(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in);
        } catch (IOException e) {
            return null;
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

    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String prettify(String stem) {
        String spaced = stem.replace('_', ' ').replace('-', ' ');
        StringBuilder sb = new StringBuilder(spaced.length());
        boolean upper = true;
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            if (Character.isWhitespace(c)) { upper = true; sb.append(c); continue; }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }

    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private static Set<String> parseCsv(@Nullable String s) {
        if (s == null || s.isEmpty()) return Collections.emptySet();
        Set<String> out = new LinkedHashSet<>();
        for (String p : s.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String joinCsv(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String x : items) {
            if (sb.length() > 0) sb.append(',');
            sb.append(x);
        }
        return sb.toString();
    }
}
