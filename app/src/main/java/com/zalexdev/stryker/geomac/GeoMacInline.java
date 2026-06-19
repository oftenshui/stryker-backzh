package com.zalexdev.stryker.geomac;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.geomac.model.GeoPin;
import com.zalexdev.stryker.geomac.store.GeoStore;
import com.zalexdev.stryker.geomac.ui.GeoMarkers;
import com.zalexdev.stryker.utils.Core;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus;
import org.osmdroid.views.overlay.OverlayItem;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeoMacInline extends AppCompatActivity {

    private static final Pattern MAC = Pattern.compile("((\\w{2}:){5}\\w{2})");
    private static final Pattern COORDS = Pattern.compile("[0-9]*\\.[0-9]+,\\s*[0-9]*\\.[0-9]+");

    private Core core;
    private GeoStore store;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_geomac_inline);

        core = new Core(this);
        store = new GeoStore(core);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        ImageButton exit = findViewById(R.id.exit);
        exit.setOnClickListener(v -> finish());

        CharSequence raw = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        if (raw == null) {
            core.toaster("Nothing to look up");
            finish();
            return;
        }
        Matcher m = MAC.matcher(raw);
        if (!m.find()) {
            core.toaster("No valid MAC address found");
            finish();
            return;
        }
        final String mac = m.group();

        LinearProgressIndicator progress = findViewById(R.id.progressBar);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);

        MapView map = findViewById(R.id.geomap);
        map.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        IMapController controller = map.getController();
        controller.setZoom(3.0);

        TextView macView = findViewById(R.id.macaddress);
        macView.setText("MAC Address: " + mac);

        new Thread(() -> {
            String coords = lookup(mac);
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (coords.isEmpty()) {
                    core.toaster(getResources().getString(R.string.no_results));
                    return;
                }
                try {
                    String[] parts = coords.replace(" ", "").split(",");
                    double lat = Double.parseDouble(parts[0]);
                    double lon = Double.parseDouble(parts[1]);

                    GeoPin pin = new GeoPin(mac, "", lat, lon,
                            GeoPin.Category.LOOKUP, System.currentTimeMillis(), null);
                    store.upsert(pin);

                    ArrayList<OverlayItem> items = new ArrayList<>();
                    OverlayItem item = new OverlayItem(mac, coords, new GeoPoint(lat, lon));
                    item.setMarker(GeoMarkers.forCategory(this, GeoPin.Category.LOOKUP));
                    items.add(item);

                    ItemizedOverlayWithFocus<OverlayItem> overlay =
                            new ItemizedOverlayWithFocus<>(items,
                                    new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
                                        @Override
                                        public boolean onItemSingleTapUp(int index, OverlayItem item) {
                                            controller.animateTo(new GeoPoint(lat, lon));
                                            controller.setZoom(18.0);
                                            copy(coords);
                                            return true;
                                        }

                                        @Override
                                        public boolean onItemLongPress(int index, OverlayItem item) {
                                            copy(coords);
                                            return true;
                                        }
                                    }, this);
                    overlay.setFocusItemsOnTap(true);
                    map.getOverlays().add(overlay);
                    controller.animateTo(new GeoPoint(lat, lon));
                    controller.setZoom(18.0);
                } catch (NumberFormatException e) {
                    core.toaster(getResources().getString(R.string.no_results));
                }
            });
        }, "geomac-inline").start();
    }

    private String lookup(String mac) {
        ArrayList<String> out = core.customChrootCommand("./modules/GeoMac/geomac " + mac);
        for (String line : out) {
            Matcher m = COORDS.matcher(line);
            if (m.find()) return m.group();
        }
        return "";
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("GeoMac", text));
        core.toaster("Copied " + text);
    }
}
