package com.zalexdev.stryker.geomac;

import android.content.Context;

import com.zalexdev.stryker.geomac.model.GeoPin;
import com.zalexdev.stryker.utils.Core;

import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GeoHooks {

    private static final String TAG = "GeoHooks";
    private static final ExecutorService LOOKUP_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "geomac-hook");
        t.setDaemon(true);
        return t;
    });
    private static final HashSet<String> ATTEMPTED = new HashSet<>();

    private GeoHooks() {}

    public static void recordHandshake(Context ctx, String bssid, String ssid) {
        record(ctx, bssid, ssid, GeoPin.Category.CRACKED_HANDSHAKE, true);
    }

    public static void recordPixie(Context ctx, String bssid, String ssid) {
        record(ctx, bssid, ssid, GeoPin.Category.CRACKED_PIXIE, true);
    }

    public static void recordScan(Context ctx, String bssid, String ssid) {
        record(ctx, bssid, ssid, GeoPin.Category.SCAN, false);
    }

    private static void record(Context ctx, String bssid, String ssid,
                               GeoPin.Category category, boolean forceLookup) {
        if (ctx == null || bssid == null || bssid.isEmpty()) return;
        final String mac = bssid.toUpperCase();
        synchronized (ATTEMPTED) {
            if (!forceLookup && !ATTEMPTED.add(mac)) return;
        }
        LOOKUP_EXEC.submit(() -> {
            Core core = new Core(ctx);
            boolean ok = GeoLookup.lookupAndStore(ctx, core, mac, ssid, category);
        });
    }
}
