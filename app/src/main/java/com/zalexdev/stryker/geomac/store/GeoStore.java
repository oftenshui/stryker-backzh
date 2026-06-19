package com.zalexdev.stryker.geomac.store;

import android.content.Context;

import com.zalexdev.stryker.geomac.model.GeoPin;
import com.zalexdev.stryker.utils.Core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class GeoStore {

    private static final String KEY_PINS = "geomac_pins_v2";
    private static final String KEY_LEGACY = "coordinates";

    private final Core core;
    private final HashMap<String, Integer> index = new HashMap<>();
    private final ArrayList<GeoPin> pins = new ArrayList<>();
    private boolean loaded = false;

    public GeoStore(Context context) {
        this.core = new Core(context);
    }

    public GeoStore(Core core) {
        this.core = core;
    }

    public synchronized List<GeoPin> all() {
        ensureLoaded();
        return new ArrayList<>(pins);
    }

    public synchronized void upsert(GeoPin pin) {
        ensureLoaded();
        if (pin == null || pin.bssid == null || pin.bssid.isEmpty()) return;
        Integer existing = index.get(pin.bssid);
        if (existing != null) {
            GeoPin old = pins.get(existing);
            if (rank(pin.category) >= rank(old.category)) {
                pins.set(existing, pin);
            } else {
                old.timestampMs = pin.timestampMs;
                if (pin.ssid != null && !pin.ssid.isEmpty()) old.ssid = pin.ssid;
            }
        } else {
            pins.add(pin);
            index.put(pin.bssid, pins.size() - 1);
        }
        save();
    }

    public synchronized void replaceAll(List<GeoPin> incoming) {
        pins.clear();
        index.clear();
        for (GeoPin p : incoming) {
            if (p == null || p.bssid == null || p.bssid.isEmpty()) continue;
            index.put(p.bssid, pins.size());
            pins.add(p);
        }
        loaded = true;
        save();
    }

    public synchronized int merge(List<GeoPin> incoming) {
        ensureLoaded();
        int added = 0;
        for (GeoPin p : incoming) {
            if (p == null || p.bssid == null || p.bssid.isEmpty()) continue;
            if (!index.containsKey(p.bssid)) added++;
            upsertNoSave(p);
        }
        save();
        return added;
    }

    public synchronized void delete(String bssid) {
        ensureLoaded();
        if (bssid == null) return;
        Integer i = index.get(bssid.toUpperCase());
        if (i == null) return;
        pins.remove(i.intValue());
        rebuildIndex();
        save();
    }

    public synchronized void clear() {
        pins.clear();
        index.clear();
        loaded = true;
        save();
    }

    public synchronized int size() {
        ensureLoaded();
        return pins.size();
    }

    public synchronized int countByCategory(GeoPin.Category c) {
        ensureLoaded();
        int n = 0;
        for (GeoPin p : pins) if (p.category == c) n++;
        return n;
    }

    private void ensureLoaded() {
        if (loaded) return;
        String raw = core.getString(KEY_PINS);
        if (raw != null && !raw.isEmpty()) {
            loadFromJson(raw);
        } else {
            migrateLegacy();
        }
        loaded = true;
    }

    private void loadFromJson(String raw) {
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                GeoPin pin = GeoPin.fromJson(arr.getJSONObject(i));
                if (pin.bssid != null && !pin.bssid.isEmpty()) {
                    index.put(pin.bssid, pins.size());
                    pins.add(pin);
                }
            }
        } catch (JSONException ignored) {
        }
    }

    private void migrateLegacy() {
        ArrayList<String> legacy = core.getListString(KEY_LEGACY);
        if (legacy == null || legacy.isEmpty()) return;
        for (String line : legacy) {
            GeoPin p = GeoPin.parseLegacy(line);
            if (p == null) continue;
            index.put(p.bssid, pins.size());
            pins.add(p);
        }
        if (!pins.isEmpty()) save();
    }

    private void upsertNoSave(GeoPin pin) {
        Integer existing = index.get(pin.bssid);
        if (existing != null) {
            if (rank(pin.category) >= rank(pins.get(existing).category)) {
                pins.set(existing, pin);
            }
        } else {
            index.put(pin.bssid, pins.size());
            pins.add(pin);
        }
    }

    private void rebuildIndex() {
        index.clear();
        for (int i = 0; i < pins.size(); i++) index.put(pins.get(i).bssid, i);
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (GeoPin p : pins) arr.put(p.toJson());
            core.putString(KEY_PINS, arr.toString());
        } catch (JSONException ignored) {
        }
    }

    private static int rank(GeoPin.Category c) {
        switch (c) {
            case CRACKED_PIXIE: return 4;
            case CRACKED_HANDSHAKE: return 3;
            case SCAN: return 2;
            case MANUAL: return 1;
            case LOOKUP:
            default: return 0;
        }
    }
}
