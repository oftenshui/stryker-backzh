package com.zalexdev.stryker.geomac;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.dashboard.Dashboard;
import com.zalexdev.stryker.geomac.io.GeoExporter;
import com.zalexdev.stryker.geomac.model.GeoPin;
import com.zalexdev.stryker.geomac.store.GeoStore;
import com.zalexdev.stryker.geomac.ui.GeoMarkers;
import com.zalexdev.stryker.utils.Core;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class GeoMac extends Fragment {

    private final MainActivity.Receiver receiver = new MainActivity.Receiver();

    private Core core;
    private Context context;
    private Activity activity;
    private GeoStore store;

    private MapView map;
    private IMapController mapController;
    private ItemizedIconOverlay<OverlayItem> overlay;
    private final ArrayList<OverlayItem> items = new ArrayList<>();
    private final ArrayList<GeoPin> shownPins = new ArrayList<>();

    private TextView subtitle;
    private TextView counter;
    private CircularProgressIndicator busy;

    private GeoPin.Category filter = null;

    private static final String PREF_FILTER = "geomac_filter";
    private static final String PREF_CRACKED_FILTER = "geomac_cracked_filter";

    private final AtomicBoolean alive = new AtomicBoolean(false);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_geomac, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        activity = getActivity();
        context = getContext();
        core = new Core(context);
        store = new GeoStore(core);
        alive.set(true);

        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context));

        map = view.findViewById(R.id.geomap);
        map.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        map.setTileSource(core.getBoolean("geomac_satellite")
                ? TileSourceFactory.USGS_SAT
                : TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        mapController = map.getController();
        mapController.setZoom(3.0);

        subtitle = view.findViewById(R.id.geomac_subtitle);
        counter = view.findViewById(R.id.geomac_counter_text);
        busy = view.findViewById(R.id.geomac_busy);

        wireSearch(view);
        wireFilterChips(view);
        wireActionBar(view);
        restoreFilterState(view);
        refreshOverlay();

        if (!core.checkFile("/data/local/stryker/release/modules/GeoMac/geomac")) {
            Toast.makeText(context, R.string.geomac_not_installed, Toast.LENGTH_LONG).show();
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.flContent, new Dashboard())
                    .commit();
            return;
        }

        receiver.setTitle("GeoMac");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }

    @Override
    public void onDestroyView() {
        alive.set(false);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        alive.set(false);
        receiver.restoreTitle();
        super.onDestroy();
    }

    private void wireSearch(View view) {
        TextInputLayout searchLayout = view.findViewById(R.id.geomac_search_layout);
        TextInputEditText search = view.findViewById(R.id.geomac_search);
        searchLayout.setEndIconOnClickListener(v -> {
            String q = search.getText() == null ? "" : search.getText().toString().trim();
            if (q.isEmpty()) return;
            runLookup(q);
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            String q = search.getText() == null ? "" : search.getText().toString().trim();
            if (!q.isEmpty()) runLookup(q);
            return true;
        });
    }

    private void wireFilterChips(View view) {
        ChipGroup group = view.findViewById(R.id.geomac_filter_group);
        group.setOnCheckedStateChangeListener((g, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.geomac_chip_lookup) filter = GeoPin.Category.LOOKUP;
            else if (id == R.id.geomac_chip_scan) filter = GeoPin.Category.SCAN;
            else if (id == R.id.geomac_chip_cracked) filter = null;
            else if (id == R.id.geomac_chip_manual) filter = GeoPin.Category.MANUAL;
            else filter = null;
            crackedFilter = id == R.id.geomac_chip_cracked;
            persistFilterState();
            refreshOverlay();
        });
    }

    private void persistFilterState() {
        if (core == null) return;
        core.putString(PREF_FILTER, filter == null ? "" : filter.name());
        core.putBoolean(PREF_CRACKED_FILTER, crackedFilter);
    }

    private void restoreFilterState(View view) {
        if (core == null) return;
        boolean cracked = core.getBoolean(PREF_CRACKED_FILTER);
        String saved = core.getString(PREF_FILTER);
        int chipId = 0;
        if (cracked) {
            chipId = R.id.geomac_chip_cracked;
        } else if (!saved.isEmpty()) {
            try {
                switch (GeoPin.Category.valueOf(saved)) {
                    case LOOKUP: chipId = R.id.geomac_chip_lookup; break;
                    case SCAN: chipId = R.id.geomac_chip_scan; break;
                    case MANUAL: chipId = R.id.geomac_chip_manual; break;
                    default: chipId = 0; break;
                }
            } catch (IllegalArgumentException ignored) {
                chipId = 0;
            }
        }
        if (chipId != 0) {
            ChipGroup group = view.findViewById(R.id.geomac_filter_group);
            group.check(chipId);
        }
    }

    private boolean crackedFilter = false;

    private void wireActionBar(View view) {
        MaterialButton fit = view.findViewById(R.id.geomac_btn_fit);
        MaterialButton importBtn = view.findViewById(R.id.geomac_btn_import);
        MaterialButton exportBtn = view.findViewById(R.id.geomac_btn_export);
        MaterialButton clearBtn = view.findViewById(R.id.geomac_btn_clear);

        fit.setOnClickListener(v -> fitToPins());
        importBtn.setOnClickListener(v -> openImportPicker());
        exportBtn.setOnClickListener(v -> openExportPicker());
        clearBtn.setOnClickListener(v -> confirmClear());
    }

    private boolean matches(GeoPin p) {
        if (crackedFilter) {
            return p.category == GeoPin.Category.CRACKED_HANDSHAKE
                    || p.category == GeoPin.Category.CRACKED_PIXIE;
        }
        return filter == null || p.category == filter;
    }

    private void refreshOverlay() {
        if (map == null) return;
        if (overlay != null) map.getOverlays().remove(overlay);
        items.clear();
        shownPins.clear();
        List<GeoPin> all = store.all();
        for (GeoPin pin : all) {
            if (!matches(pin)) continue;
            String title = pin.ssid == null || pin.ssid.isEmpty() ? pin.bssid : pin.ssid;
            OverlayItem item = new OverlayItem(title, pin.bssid,
                    new GeoPoint(pin.lat, pin.lon));
            item.setMarker(GeoMarkers.forCategory(context, pin.category));
            item.setMarkerHotspot(OverlayItem.HotspotPlace.BOTTOM_CENTER);
            items.add(item);
            shownPins.add(pin);
        }
        overlay = new ItemizedIconOverlay<>(items,
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
                    @Override
                    public boolean onItemSingleTapUp(int index, OverlayItem item) {
                        if (index >= 0 && index < shownPins.size()) {
                            showPinDialog(shownPins.get(index));
                        }
                        return true;
                    }

                    @Override
                    public boolean onItemLongPress(int index, OverlayItem item) {
                        if (index >= 0 && index < shownPins.size()) {
                            GeoPin p = shownPins.get(index);
                            setClipboard(p.lat + "," + p.lon);
                        }
                        return true;
                    }
                }, context);
        map.getOverlays().add(overlay);
        map.invalidate();
        updateCounter(all.size());
    }

    @SuppressLint("SetTextI18n")
    private void updateCounter(int total) {
        int hs = store.countByCategory(GeoPin.Category.CRACKED_HANDSHAKE);
        int px = store.countByCategory(GeoPin.Category.CRACKED_PIXIE);
        int sc = store.countByCategory(GeoPin.Category.SCAN);
        counter.setText(total + " · " + sc + "S " + (hs + px) + "C");
        subtitle.setText(getString(R.string.geomac_subtitle_count, total));
    }

    private void runLookup(String mac) {
        if (activity == null) return;
        busy.setVisibility(View.VISIBLE);
        subtitle.setText(getString(R.string.geomac_subtitle_lookup, mac));
        new Thread(() -> {
            double[] coords = GeoLookup.coordsFor(core, mac);
            if (activity == null || !isAdded() || !alive.get()) return;
            activity.runOnUiThread(() -> {
                if (activity == null || !isAdded() || !alive.get()) return;
                busy.setVisibility(View.GONE);
                if (coords == null) {
                    subtitle.setText(R.string.geomac_subtitle_no_match);
                    Toast.makeText(context, R.string.no_results, Toast.LENGTH_SHORT).show();
                    return;
                }
                GeoPin pin = new GeoPin(mac, "", coords[0], coords[1],
                        GeoPin.Category.LOOKUP, System.currentTimeMillis(), null);
                store.upsert(pin);
                refreshOverlay();
                mapController.animateTo(new GeoPoint(coords[0], coords[1]));
                mapController.setZoom(18.0);
                subtitle.setText(getString(R.string.geomac_subtitle_found, mac));
            });
        }, "geomac-lookup").start();
    }

    private void fitToPins() {
        if (shownPins.isEmpty()) {
            Toast.makeText(context, R.string.geomac_no_pins_to_fit, Toast.LENGTH_SHORT).show();
            return;
        }
        double north = -90, south = 90, east = -180, west = 180;
        for (GeoPin p : shownPins) {
            if (p.lat > north) north = p.lat;
            if (p.lat < south) south = p.lat;
            if (p.lon > east) east = p.lon;
            if (p.lon < west) west = p.lon;
        }
        BoundingBox box = new BoundingBox(north, east, south, west);
        map.zoomToBoundingBox(box, true, 80);
    }

    private void openExportPicker() {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.geomac_export_title)
                .setItems(new CharSequence[]{
                        getString(R.string.geomac_export_json),
                        getString(R.string.geomac_export_kml)
                }, (d, which) -> {
                    if (which == 0) doExport(true);
                    else doExport(false);
                })
                .show();
    }

    private void doExport(boolean json) {
        busy.setVisibility(View.VISIBLE);
        new Thread(() -> {
            File out;
            try {
                List<GeoPin> all = store.all();
                if (all.isEmpty()) {
                    if (activity == null || !isAdded() || !alive.get()) return;
                    activity.runOnUiThread(() -> {
                        if (activity == null || !isAdded() || !alive.get()) return;
                        busy.setVisibility(View.GONE);
                        Toast.makeText(context, R.string.geomac_export_empty, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                out = json ? GeoExporter.exportJson(all) : GeoExporter.exportKml(all);
            } catch (Exception e) {
                if (activity == null || !isAdded() || !alive.get()) return;
                activity.runOnUiThread(() -> {
                    if (activity == null || !isAdded() || !alive.get()) return;
                    busy.setVisibility(View.GONE);
                    Toast.makeText(context, getString(R.string.geomac_export_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
                return;
            }
            File finalOut = out;
            if (activity == null || !isAdded() || !alive.get()) return;
            activity.runOnUiThread(() -> {
                if (activity == null || !isAdded() || !alive.get()) return;
                busy.setVisibility(View.GONE);
                Toast.makeText(context,
                        getString(R.string.geomac_export_ok, finalOut.getAbsolutePath()),
                        Toast.LENGTH_LONG).show();
            });
        }, "geomac-export").start();
    }

    private void openImportPicker() {
        List<File> files = GeoExporter.listJsonExports();
        if (files.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.geomac_import_title)
                    .setMessage(getString(R.string.geomac_import_empty, GeoExporter.DIR_PATH))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        CharSequence[] labels = new CharSequence[files.size()];
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        for (int i = 0; i < files.size(); i++) {
            labels[i] = files.get(i).getName() + "\n"
                    + fmt.format(new Date(files.get(i).lastModified()));
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.geomac_import_title)
                .setItems(labels, (d, which) -> doImport(files.get(which)))
                .show();
    }

    private void doImport(File source) {
        busy.setVisibility(View.VISIBLE);
        new Thread(() -> {
            int added;
            try {
                List<GeoPin> incoming = GeoExporter.importJson(source);
                added = store.merge(incoming);
            } catch (Exception e) {
                if (activity == null || !isAdded() || !alive.get()) return;
                activity.runOnUiThread(() -> {
                    if (activity == null || !isAdded() || !alive.get()) return;
                    busy.setVisibility(View.GONE);
                    Toast.makeText(context, getString(R.string.geomac_import_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
                return;
            }
            int finalAdded = added;
            if (activity == null || !isAdded() || !alive.get()) return;
            activity.runOnUiThread(() -> {
                if (activity == null || !isAdded() || !alive.get()) return;
                busy.setVisibility(View.GONE);
                Toast.makeText(context, getString(R.string.geomac_import_ok, finalAdded),
                        Toast.LENGTH_LONG).show();
                refreshOverlay();
            });
        }, "geomac-import").start();
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.geomac_clear_title)
                .setMessage(R.string.geomac_clear_message)
                .setPositiveButton(R.string.geomac_clear_yes, (d, w) -> {
                    store.clear();
                    refreshOverlay();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @SuppressLint("SetTextI18n")
    private void showPinDialog(GeoPin pin) {
        final android.app.Dialog d = new android.app.Dialog(context);
        d.setContentView(R.layout.dialog_geomac_pin);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        TextView title = d.findViewById(R.id.geopin_title);
        TextView bssid = d.findViewById(R.id.geopin_bssid);
        TextView lat = d.findViewById(R.id.geopin_lat);
        TextView lon = d.findViewById(R.id.geopin_lon);
        TextView age = d.findViewById(R.id.geopin_age);
        TextView cat = d.findViewById(R.id.geopin_category);
        View swatch = d.findViewById(R.id.geopin_swatch);
        FrameLayout swatchWrap = d.findViewById(R.id.geopin_swatch_wrap);
        MaterialButton copy = d.findViewById(R.id.geopin_btn_copy);
        MaterialButton delete = d.findViewById(R.id.geopin_btn_delete);

        title.setText(pin.ssid == null || pin.ssid.isEmpty() ? pin.bssid : pin.ssid);
        bssid.setText(pin.bssid);
        lat.setText(String.format(Locale.US, "%.6f", pin.lat));
        lon.setText(String.format(Locale.US, "%.6f", pin.lon));
        cat.setText(GeoMarkers.labelOf(pin.category).toUpperCase());
        cat.setTextColor(GeoMarkers.colorOf(pin.category));
        if (cat.getBackground() != null) {
            cat.getBackground().mutate().setColorFilter(
                    new android.graphics.PorterDuffColorFilter(
                            GeoMarkers.colorOf(pin.category) & 0x33FFFFFF | 0x33000000,
                            android.graphics.PorterDuff.Mode.SRC_IN));
        }
        if (swatchWrap.getBackground() != null) {
            swatchWrap.getBackground().mutate().setColorFilter(
                    new android.graphics.PorterDuffColorFilter(
                            GeoMarkers.colorOf(pin.category) & 0x33FFFFFF | 0x33000000,
                            android.graphics.PorterDuff.Mode.SRC_IN));
        }
        if (swatch.getBackground() != null) {
            swatch.getBackground().mutate().setColorFilter(
                    new android.graphics.PorterDuffColorFilter(GeoMarkers.colorOf(pin.category),
                            android.graphics.PorterDuff.Mode.SRC_IN));
        }
        age.setText(getString(R.string.geomac_pin_age, formatAge(pin.timestampMs)));

        copy.setOnClickListener(v -> {
            setClipboard(pin.lat + "," + pin.lon);
            d.dismiss();
        });
        delete.setOnClickListener(v -> {
            store.delete(pin.bssid);
            refreshOverlay();
            d.dismiss();
        });

        d.show();
        mapController.animateTo(new GeoPoint(pin.lat, pin.lon));
    }

    private void setClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("GeoMac", text));
        Toast.makeText(context, R.string.geomac_copied, Toast.LENGTH_SHORT).show();
    }

    private String formatAge(long ts) {
        long delta = (System.currentTimeMillis() - ts) / 1000L;
        if (delta < 60) return delta + "s";
        if (delta < 3600) return (delta / 60) + "m";
        if (delta < 86400) return (delta / 3600) + "h";
        return (delta / 86400) + "d";
    }
}
