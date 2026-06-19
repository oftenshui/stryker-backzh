package com.zalexdev.stryker.geomac;

import android.content.Context;

import com.zalexdev.stryker.geomac.model.GeoPin;
import com.zalexdev.stryker.geomac.store.GeoStore;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GeoLookup {

    private static final Pattern COORDS =
            Pattern.compile("([-]?[0-9]+\\.[0-9]+)\\s*,\\s*([-]?[0-9]+\\.[0-9]+)");

    private GeoLookup() {}

    public static double[] coordsFor(Core core, String bssid) {
        if (core == null || bssid == null || bssid.isEmpty()) return null;
        ArrayList<String> out = core.customChrootCommand(
                "./modules/GeoMac/geomac " + bssid, true);
        for (String line : out) {
            Matcher m = COORDS.matcher(line);
            if (m.find()) {
                try {
                    return new double[]{
                            Double.parseDouble(m.group(1)),
                            Double.parseDouble(m.group(2))
                    };
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public static boolean lookupAndStore(Context ctx, Core core, String bssid, String ssid,
                                         GeoPin.Category category) {
        double[] coords = coordsFor(core, bssid);
        if (coords == null) return false;
        GeoStore store = new GeoStore(ctx);
        store.upsert(new GeoPin(bssid, ssid, coords[0], coords[1], category,
                System.currentTimeMillis(), null));
        return true;
    }
}
