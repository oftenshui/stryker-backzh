package com.zalexdev.stryker.geomac.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class GeoPin {

    public enum Category {
        LOOKUP,
        SCAN,
        CRACKED_HANDSHAKE,
        CRACKED_PIXIE,
        MANUAL
    }

    public final String bssid;
    public String ssid;
    public final double lat;
    public final double lon;
    public Category category;
    public long timestampMs;
    public String note;

    public GeoPin(String bssid, String ssid, double lat, double lon, Category category,
                  long timestampMs, String note) {
        this.bssid = bssid == null ? "" : bssid.trim().toUpperCase();
        this.ssid = ssid;
        this.lat = lat;
        this.lon = lon;
        this.category = category == null ? Category.LOOKUP : category;
        this.timestampMs = timestampMs == 0 ? System.currentTimeMillis() : timestampMs;
        this.note = note;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("bssid", bssid);
        o.put("ssid", ssid == null ? "" : ssid);
        o.put("lat", lat);
        o.put("lon", lon);
        o.put("cat", category.name());
        o.put("ts", timestampMs);
        if (note != null && !note.isEmpty()) o.put("note", note);
        return o;
    }

    public static GeoPin fromJson(JSONObject o) throws JSONException {
        Category cat;
        try {
            cat = Category.valueOf(o.optString("cat", "LOOKUP"));
        } catch (IllegalArgumentException e) {
            cat = Category.LOOKUP;
        }
        return new GeoPin(
                o.optString("bssid", ""),
                o.optString("ssid", ""),
                o.optDouble("lat", 0.0),
                o.optDouble("lon", 0.0),
                cat,
                o.optLong("ts", System.currentTimeMillis()),
                o.optString("note", null)
        );
    }

    public static GeoPin parseLegacy(String line) {
        if (line == null || line.isEmpty()) return null;
        int semi = line.indexOf(';');
        if (semi < 0) return null;
        String mac = line.substring(0, semi);
        String coords = line.substring(semi + 1).replace(" ", "");
        int comma = coords.indexOf(',');
        if (comma < 0) return null;
        try {
            double lat = Double.parseDouble(coords.substring(0, comma));
            double lon = Double.parseDouble(coords.substring(comma + 1));
            return new GeoPin(mac, "", lat, lon, Category.LOOKUP, System.currentTimeMillis(), null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
